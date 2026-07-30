package com.laststardust.relics;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// ── T4 관문 기믹: 네더라이트 괴물의 「용암의 심판」 ──
//
// 마지막 관문은 - 새 어휘를 안 가르친다 - . 시험이다.
// T1 빨강 · T2 노랑 · T3 보라를 하나씩 배웠고 T3 2페이즈에서 처음 둘을 겹쳤다.
// 여기서는 - 배운 것을 순서대로, 반대 방향으로 - 요구한다 (docs/RESEARCH.md 「2. 전투·보스전」).
//
// 두 박자다:
//   1박  보라 — 사람마다 원. 서로 떨어져라.
//   2박  노랑 — 파티 근처에 결계 하나. 이제 전원 뭉쳐라.
// 흩어지자마자 다시 모아야 한다. 두 어휘가 정확히 반대라서, 하나라도 헷갈리면 두 번째에서 걸린다.
// 새 기호를 안 쓰므로 «처음 보는 것»은 없다 — 어려운 건 순서와 시간뿐이다.
//
// 2페이즈(50%)는 그 위에 빨강을 얹는다. 세 어휘 동시 = 이 팩에서 가장 복잡한 순간이고,
// 그 이상은 만들지 않는다. 넷을 겹치면 읽는 게 아니라 외우는 게 된다.
//
// ※ Telegraph 의 resolve 안에서 다시 Telegraph 를 부르면 ACTIVE 순회 중 add 가 되어
//   ConcurrentModificationException 이 난다(IgnisGimmick 주석 참고). 그래서 2박은
//   resolve 가 아니라 - 다음 주기를 앞당기는 방식 - 으로 낸다. f.echo 가 그 박자 번호다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class MonstrosityGimmick {
    private MonstrosityGimmick() {}

    public static final ResourceLocation BOSS_ID =
        ResourceLocation.fromNamespaceAndPath("cataclysm", "netherite_monstrosity");

    public static final String TEST_TAG = "ls_gimmick_monstrosity";

    // ── 조절 손잡이 ──
    public static final int FIRST_DELAY = 10 * 20;  // 교전 시작 후 첫 시전까지
    public static final int INTERVAL_P1 = 18 * 20;  // 1페이즈 주기 (두 박자를 한 묶음으로 본 간격)
    public static final int INTERVAL_P2 = 14 * 20;  // 2페이즈 주기
    public static final int BEAT        = 50;       // 1박 -> 2박 (2.5초)
    public static final float PHASE2_HP = 0.50f;

    public static final double RANGE        = 30.0;  // 교전으로 볼 거리
    public static final double SPREAD_RADIUS = 4.0;  // 1박: 이만큼은 떨어져야 한다
    public static final float SPREAD_DAMAGE  = 10.0f;
    public static final double STACK_RADIUS  = 5.0;  // 2박: 이 안으로 들어와야 한다
    public static final double STACK_MIN     = 4.0;  // 무게중심에서 결계까지
    public static final double STACK_MAX     = 6.0;
    public static final float STACK_DAMAGE   = 16.0f;
    public static final int STACK_BURN       = 3;
    public static final double CORE_RADIUS   = 6.0;  // 2페이즈 보스 발밑 빨강
    public static final float CORE_DAMAGE    = 12.0f;

    // 2.5초 안에 흩어진 자리에서 4~6칸 떨어진 결계까지 와야 한다. 4성이면 전원 이동기가
    // 열려 있어(2성 해금) 가능하지만 빠듯하다 — 마지막 관문이라 이 정도로 뒀다.
    // 너무 빡세면 BEAT 를, 너무 헐거우면 STACK_MAX 를 먼저 건드린다.

    private static final BossFightTracker TRACKER = new BossFightTracker(
        BOSS_ID, "네더라이트 괴물", TEST_TAG,
        FIRST_DELAY, new int[] { INTERVAL_P1, INTERVAL_P2 }, new float[] { PHASE2_HP },
        RANGE, new BossFightTracker.Handler() {

            @Override
            public void cast(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                LivingEntity boss = f.boss;

                if (f.echo == 0) {
                    // ── 1박: 흩어져라 ──
                    if (!f.taught) {
                        f.taught = true;
                        for (ServerPlayer p : near) {
                            p.sendSystemMessage(Component.literal(
                                "§6◆ 용암의 심판 §7— §d흩어졌다가§7 곧바로 §e뭉쳐야§7 한다. §8(새 기호는 없다. 순서와 시간뿐이다)"));
                        }
                    }
                    level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                        SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.5f);

                    for (ServerPlayer target : near) {
                        Vec3 c = target.position();
                        Telegraph.spread(level, c, SPREAD_RADIUS, () -> resolveSpread(level, boss, c));
                    }

                    f.echo = 1;
                    f.next = BEAT;      // 곧 2박
                    return;
                }

                // ── 2박: 뭉쳐라 ──
                f.echo = 0;             // 다음은 정상 주기 (tick 이 이미 f.next 를 채워놨다)
                Vec3 c = BossFightTracker.centroid(near);
                double a = level.random.nextDouble() * Math.PI * 2;
                double d = STACK_MIN + level.random.nextDouble() * (STACK_MAX - STACK_MIN);
                Vec3 ring = new Vec3(c.x + Math.cos(a) * d, c.y, c.z + Math.sin(a) * d);

                level.playSound(null, boss.getX(), boss.getY(), boss.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0f, 0.6f);
                Telegraph.stack(level, ring, STACK_RADIUS, () -> resolveStack(level, boss, ring, near));

                // 2페이즈에서만 그 위에 빨강을 얹는다. 1박이 아니라 2박에 붙이는 이유:
                // 뭉치는 자리가 곧 보스 발밑이 되면 «노랑으로 가되 빨강은 피해라»가 성립한다.
                if (f.phase >= 2) {
                    Vec3 core = boss.position();
                    Telegraph.danger(level, core, CORE_RADIUS, () -> resolveCore(level, boss, core, near));
                }
            }

            @Override
            public void onPhaseChange(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                level.playSound(null, f.boss.getX(), f.boss.getY(), f.boss.getZ(),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2f, 0.6f);
                for (ServerPlayer p : near) {
                    p.sendSystemMessage(Component.literal(
                        "§c⚠ 괴물이 용암을 토한다 §7— 이제 §d흩어지고§7 §e뭉치면서§7 §c발밑까지§7 봐야 한다."));
                }
                // 전환 직후에 두 박자 중간이면 꼬인다. 박자를 처음부터 다시 센다.
                f.echo = 0;
            }
        });

    // 혼자면 아무 일도 없다 — 보라의 정의(GauntletGimmick 주석 참고).
    private static void resolveSpread(ServerLevel level, LivingEntity boss, Vec3 center) {
        if (!boss.isAlive()) return;
        List<ServerPlayer> in = Telegraph.inside(level, center, SPREAD_RADIUS);
        if (in.size() < 2) return;
        for (ServerPlayer p : in) {
            if (!hittable(p, level)) continue;
            p.invulnerableTime = 0;
            p.hurt(level.damageSources().mobAttack(boss), SPREAD_DAMAGE);
            p.hurtMarked = true;
        }
    }

    // 노랑은 «안이 안전»이다. 밖만 맞는다 — 조금이라도 안쪽이 아프면 어휘가 흐려진다.
    private static void resolveStack(ServerLevel level, LivingEntity boss, Vec3 center, List<ServerPlayer> near) {
        if (!boss.isAlive()) return;
        List<ServerPlayer> in = Telegraph.inside(level, center, STACK_RADIUS);
        for (ServerPlayer p : near) {
            if (!hittable(p, level) || in.contains(p)) continue;
            p.invulnerableTime = 0;
            p.hurt(level.damageSources().mobAttack(boss), STACK_DAMAGE);
            p.igniteForSeconds(STACK_BURN);
            p.hurtMarked = true;
        }
    }

    private static void resolveCore(ServerLevel level, LivingEntity boss, Vec3 center, List<ServerPlayer> near) {
        if (!boss.isAlive()) return;
        for (ServerPlayer p : Telegraph.inside(level, center, CORE_RADIUS)) {
            if (!hittable(p, level) || !near.contains(p)) continue;
            p.invulnerableTime = 0;
            p.hurt(level.damageSources().mobAttack(boss), CORE_DAMAGE);
            p.push(0.0, 0.42, 0.0);
            p.hurtMarked = true;
        }
    }

    private static boolean hittable(ServerPlayer p, ServerLevel level) {
        return !p.isSpectator() && p.isAlive() && p.level() == level;
    }

    // ── 이벤트 위임 (BossFightTracker 주석 참고: 구독은 기믹이 한다) ──

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) { TRACKER.onJoin(event); }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) { TRACKER.tick(); }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { TRACKER.stop(); }

    // ── 시험용 ──

    public static boolean summon(ServerPlayer p) { return TRACKER.summon(p); }
    public static boolean forceNear(ServerPlayer p) { return TRACKER.forceNear(p); }
    public static int clearTest() { return TRACKER.clearTest(); }
    public static int tracked() { return TRACKER.tracked(); }
    public static List<Component> status() { return TRACKER.status(); }
}
