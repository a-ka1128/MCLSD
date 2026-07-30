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

// ── T3 관문 기믹: 건틀렛의 「분쇄 파문」 ──
// 어휘의 세 번째 수업 = 보라(흩어져라). 빨강·노랑은 원이 «어디로 갈지»를 알려주지만
// 보라는 서로에게서 떨어지라고 한다 — 갈 곳을 안 주므로 마지막에 가르친다.
//
// 판정이 «원 안이냐»가 아니라 «몇 명이 같이 있냐»인 이유: 원 안이면 피해로 만들면 그건 그냥
// 빨강이고 어휘가 하나 줄어든다. 그래서 원은 플레이어마다 하나씩 발밑에 뜨고, 그 안에 둘 이상
// 남아 있으면 그 안 전원이 맞는다. 뭉쳐 있으면 원이 겹쳐 겹친 수만큼 맞는다.
//   ※ 1인 시험에서는 원만 뜨고 피해가 없다 — 버그가 아니라 정의상 그렇다.
//
// 2페이즈(50%)는 보라와 함께 보스 발밑에 빨강이 온다. 새 어휘가 아니라 이미 배운 둘을
// 동시에 요구하는 «조합»의 첫 적용점이다 (docs/RESEARCH.md 「2. 전투·보스전」).
@EventBusSubscriber(modid = LSRelics.MODID)
public final class GauntletGimmick {
    private GauntletGimmick() {}

    public static final ResourceLocation BOSS_ID =
        ResourceLocation.fromNamespaceAndPath("bosses_of_mass_destruction", "gauntlet");

    public static final String TEST_TAG = "ls_gimmick_gauntlet";

    // ── 조절 손잡이 ──
    public static final int FIRST_DELAY = 9 * 20;   // 교전 시작 후 첫 시전까지
    public static final int INTERVAL_P1 = 15 * 20;  // 1페이즈 주기
    public static final int INTERVAL_P2 = 12 * 20;  // 2페이즈 주기
    public static final float PHASE2_HP = 0.50f;    // 2페이즈 진입 체력 비율
    public static final double RADIUS   = 4.0;      // 개인 원 반경 = 이만큼은 떨어져야 한다
    public static final double RANGE    = 26.0;     // 교전으로 볼 거리
    public static final float DAMAGE    = 7.0f;     // 원 하나당 피해 (감쇄 전)
    public static final double CORE_RADIUS = 6.0;   // 2페이즈 보스 발밑 빨강 반경
    public static final float CORE_DAMAGE  = 9.0f;

    // 붙어 있으면 원이 겹쳐 두 번 맞는다(7+7). 실전 미검증 — RADIUS 를 올리면 흩어져야 하는
    // 거리가 늘어나는데, 좁은 아레나에서는 이쪽이 먼저 문제가 된다.

    private static final BossFightTracker TRACKER = new BossFightTracker(
        BOSS_ID, "건틀렛", TEST_TAG,
        FIRST_DELAY, new int[] { INTERVAL_P1, INTERVAL_P2 }, new float[] { PHASE2_HP },
        RANGE, new BossFightTracker.Handler() {

            @Override
            public void cast(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                if (!f.taught) {
                    f.taught = true;
                    for (ServerPlayer p : near) {
                        p.sendSystemMessage(Component.literal(
                            "§d◆ 분쇄 파문 §7— §d보라 원§7이 겹치지 않게 §d서로 떨어져라§7. §8(보라는 언제나 '흩어져라'다)"));
                    }
                }

                level.playSound(null, f.boss.getX(), f.boss.getY(), f.boss.getZ(),
                    SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 1.0f, 0.5f);

                LivingEntity boss = f.boss;

                // 자리는 «지금» 위치로 고정된다 — 1.5초 동안 어디로 움직이든 판정은 그 자리 기준이다.
                for (ServerPlayer target : near) {
                    Vec3 center = target.position();
                    Telegraph.spread(level, center, RADIUS, () -> resolveRing(level, boss, center));
                }

                if (f.phase >= 2) {
                    Vec3 core = f.boss.position();
                    Telegraph.danger(level, core, CORE_RADIUS, () -> resolveCore(level, boss, core, near));
                }
            }

            @Override
            public void onPhaseChange(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                level.playSound(null, f.boss.getX(), f.boss.getY(), f.boss.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.9f, 1.2f);
                for (ServerPlayer p : near) {
                    p.sendSystemMessage(Component.literal(
                        "§c⚠ 건틀렛이 주먹을 땅에 박는다 §7— 이제 §d흩어지면서§7 §c발밑도§7 피해야 한다."));
                }
            }
        });

    // 혼자면 아무 일도 없다 — 이게 보라의 정의다.
    private static void resolveRing(ServerLevel level, LivingEntity boss, Vec3 center) {
        if (!boss.isAlive()) return;
        List<ServerPlayer> in = Telegraph.inside(level, center, RADIUS);
        if (in.size() < 2) return;
        for (ServerPlayer p : in) {
            if (!Telegraph.hittable(p, level)) continue;
            p.invulnerableTime = 0;
            p.hurt(level.damageSources().mobAttack(boss), DAMAGE);
            p.hurtMarked = true;
        }
    }

    // near 로 한 번 더 거르는 이유는 BossFightTracker.nearby() 주석 참고.
    private static void resolveCore(ServerLevel level, LivingEntity boss, Vec3 center, List<ServerPlayer> near) {
        if (!boss.isAlive()) return;
        List<ServerPlayer> in = Telegraph.inside(level, center, CORE_RADIUS);
        for (ServerPlayer p : in) {
            if (!Telegraph.hittable(p, level)) continue;
            if (!near.contains(p)) continue;
            p.invulnerableTime = 0;
            p.hurt(level.damageSources().mobAttack(boss), CORE_DAMAGE);
            p.push(0.0, 0.42, 0.0);
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
