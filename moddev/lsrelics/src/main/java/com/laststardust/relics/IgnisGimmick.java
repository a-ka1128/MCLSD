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

// ── T2 관문 기믹: 이그니스의 「업화의 결계」 ──
// 어휘의 두 번째 수업 = 노랑(뭉쳐라). T1 과 같은 이유로 한 관문에 어휘 하나만 쓴다
// (docs/RESEARCH.md 「2. 전투·보스전」).
//
// 원이 누군가의 발밑이 아니라 파티에서 6~9칸 떨어진 곳에 뜨는 이유: 발밑에 띄우면 표적이 된
// 사람은 이미 안에 서 있어 아무것도 안 해도 되고, 1인 시험에서는 기믹이 통째로 무효가 된다.
// 떨어진 곳이어야 전원이 발을 뗀다.
//
// 안쪽을 완전 무피해로 둔 이유: 빨강의 정확한 반대여야 어휘가 선다. 조금이라도 아프면
// "노랑도 아프긴 하네"가 되어 다음 관문에서 판단이 흐려진다.
//
// 2페이즈(50%)는 주기가 짧아지고 결계가 한 번 더 온다 — 새 어휘 없이 같은 것을 더 빨리만.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class IgnisGimmick {
    private IgnisGimmick() {}

    public static final ResourceLocation BOSS_ID =
        ResourceLocation.fromNamespaceAndPath("cataclysm", "ignis");

    public static final String TEST_TAG = "ls_gimmick_ignis";

    // ── 조절 손잡이 ──
    public static final int FIRST_DELAY  = 10 * 20;  // 교전 시작 후 첫 시전까지
    public static final int INTERVAL_P1  = 16 * 20;  // 1페이즈 주기
    public static final int INTERVAL_P2  = 11 * 20;  // 2페이즈 주기
    public static final int ECHO_DELAY   = 4 * 20;   // 2페이즈 연속 시전 간격
    public static final float PHASE2_HP  = 0.50f;    // 2페이즈 진입 체력 비율
    public static final double RADIUS    = 5.0;      // 결계 반경
    public static final double RANGE     = 28.0;     // 교전으로 볼 거리
    public static final double CAST_MIN  = 6.0;      // 무게중심에서 결계까지
    public static final double CAST_MAX  = 9.0;
    public static final float DAMAGE     = 12.0f;    // 결계 밖 피해 (감쇄 전)
    public static final int BURN_SECONDS = 3;        // 결계 밖 추가 화상

    // «한 번 놓쳐도 안 죽지만 연달아 놓치면 죽는다» 세기를 노렸다. 실전 미검증 —
    // 너무 아프면 DAMAGE, 너무 정신없으면 INTERVAL_P2 부터 건드린다.

    private static final BossFightTracker TRACKER = new BossFightTracker(
        BOSS_ID, "이그니스", TEST_TAG,
        FIRST_DELAY, new int[] { INTERVAL_P1, INTERVAL_P2 }, new float[] { PHASE2_HP },
        RANGE, new BossFightTracker.Handler() {

            @Override
            public void cast(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                Vec3 center = pickCenter(level, near);

                if (!f.taught) {
                    f.taught = true;
                    for (ServerPlayer p : near) {
                        p.sendSystemMessage(Component.literal(
                            "§e◆ 업화의 결계 §7— §e노란 원 안§7으로 들어가라. §8(노랑은 언제나 '뭉쳐라'다)"));
                    }
                }

                // 소리의 출처가 곧 "누가 시전했나"다 — 보스 자리에서도 울린다(강철거인과 같은 규칙).
                level.playSound(null, f.boss.getX(), f.boss.getY(), f.boss.getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.9f, 0.7f);

                LivingEntity boss = f.boss;
                Telegraph.stack(level, center, RADIUS, () -> resolve(level, boss, center, near));

                // 2페이즈 연속 시전. ※ Telegraph 의 resolve 안에서 다시 Telegraph 를 부르면
                // ACTIVE 리스트를 순회하는 중에 add 가 일어나 ConcurrentModificationException 이
                // 난다(그쪽 try/catch 에 먹혀 «조용히 안 터지는» 형태가 된다). 그래서 연속 시전은
                // 반드시 이쪽 주기로 처리한다 — 다음 시전을 앞당기기만 하면 된다.
                if (f.phase >= 2) {
                    if (f.echo > 0) {
                        f.echo--;                       // 메아리였다 → 다음은 정상 주기
                    } else {
                        f.echo = 1;
                        f.next = ECHO_DELAY;            // 곧 한 번 더
                    }
                }
            }

            @Override
            public void onPhaseChange(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                level.playSound(null, f.boss.getX(), f.boss.getY(), f.boss.getZ(),
                    SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 1.4f);
                for (ServerPlayer p : near) {
                    p.sendSystemMessage(Component.literal(
                        "§6⚠ 이그니스가 불길을 끌어올린다 §7— 결계가 §e연달아§7 내려온다."));
                }
            }
        });

    // Y 는 무게중심 그대로 쓴다 — 지형을 따라가면 계단·경사에서 원이 땅에 묻힌다.
    private static Vec3 pickCenter(ServerLevel level, List<ServerPlayer> near) {
        Vec3 c = BossFightTracker.centroid(near);
        double a = level.random.nextDouble() * Math.PI * 2;
        double d = CAST_MIN + level.random.nextDouble() * (CAST_MAX - CAST_MIN);
        return new Vec3(c.x + Math.cos(a) * d, c.y, c.z + Math.sin(a) * d);
    }

    private static void resolve(ServerLevel level, LivingEntity boss, Vec3 center, List<ServerPlayer> near) {
        // 이그니스가 먼저 쓰러지면 불길도 멈춘다.
        if (!boss.isAlive()) return;

        List<ServerPlayer> in = Telegraph.inside(level, center, RADIUS);

        // near 기준으로 도는 이유는 BossFightTracker.nearby() 주석 참고 (Telegraph.outside() 는 월드 전체다).
        for (ServerPlayer p : near) {
            if (p.isSpectator() || !p.isAlive()) continue;
            if (p.level() != level) continue;          // 그 사이 차원을 옮겼을 수 있다
            if (in.contains(p)) continue;              // 결계 안 = 무사

            // 무적 프레임에 씹히면 "피했다/안 피했다"의 인과가 끊긴다. 예고형은 결정적이어야 배운다.
            p.invulnerableTime = 0;
            p.hurt(level.damageSources().mobAttack(boss), DAMAGE);
            p.igniteForSeconds(BURN_SECONDS);
            p.hurtMarked = true;
        }
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
