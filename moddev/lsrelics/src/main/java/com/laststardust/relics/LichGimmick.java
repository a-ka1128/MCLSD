package com.laststardust.relics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// ── 최종 보스 기믹: 리치의 「별을 삼키는 자」 ──
//
// 관문 넷이 어휘를 가르치고(T1 빨강 · T2 노랑 · T3 보라 · T4 조합) 마지막에 - 파랑 - 이 온다.
// 파랑은 이미 이그니스에서 한 번 봤다: «치지 마라». 거기서는 12% 확률로 잠깐 스쳐 갔지만
// 여기서는 그게 전투의 뼈대가 된다.
//
// ── 왜 이 보스에 무적을 «만들어» 붙이나 ──
// 강철거인·이그니스의 파랑은 모드가 이미 가진 규칙을 - 보이게 - 만든 것이었다.
// BOMD 리치는 피해 경로를 전혀 안 건드려서(hurt 오버라이드 없음) 그런 규칙이 없다.
// 그래서 여기서는 우리가 만든다 — 최종 보스에 «때리는 것만으로는 안 되는 구간»이 없으면
// 어휘를 넷이나 가르쳐놓고 마지막을 그냥 두들기기로 끝내는 셈이 된다.
//
// ── 규칙 ──
//   주기적으로 리치가 별빛을 삼킨다(8초). 그동안:
//     · 파랑  = 리치는 무적이다. 때려도 0이다.
//     · 노랑  = 시전 시점에 고정된 자리. 파티 - 절반 이상 - 이 그 안에 있으면 삼킴이 풀린다.
//   3초를 채우면 조기 해제(연출 + 잠깐의 빈틈), 못 채우면 리치가 최대 체력의 5% 를 회복한다.
//
// 노랑 자리는 - 리치를 따라가지 않는다 - . 보스에게서 떨어진 곳에 굳어 있어야
// «보스를 두고 자리를 비운다»는 선택이 생긴다. 붙어 다니면 그냥 서 있으면 되는 기믹이다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class LichGimmick {
    private LichGimmick() {}

    public static final ResourceLocation BOSS_ID =
        ResourceLocation.fromNamespaceAndPath("bosses_of_mass_destruction", "lich");

    public static final String TEST_TAG = "ls_gimmick_lich";

    // ── 조절 손잡이 ──
    public static final int FIRST_DELAY  = 20 * 20;  // 첫 삼킴까지 (연출을 볼 시간)
    public static final int INTERVAL_P1  = 30 * 20;
    public static final int INTERVAL_P2  = 22 * 20;
    public static final float PHASE2_HP  = 0.40f;
    public static final int SWALLOW_TICKS = 8 * 20;  // 무적 지속
    public static final int HOLD_NEEDED   = 3 * 20;  // 이만큼 지키면 조기 해제
    public static final double RANGE      = 32.0;
    public static final double RING_RADIUS = 4.5;
    public static final double RING_MIN    = 7.0;    // 리치에게서 이만큼 떨어진 곳
    public static final double RING_MAX    = 11.0;
    public static final float HEAL_FRAC    = 0.05f;  // 못 막으면 회복하는 비율

    // 삼킴 중인 리치 -> 끝나는 서버 틱. 이 지도에 있는 동안 피해가 취소된다.
    // 지도에 남아 무적이 굳는 사고를 막으려고 - 값이 마감 시각 - 이다. 지우는 걸 잊어도 저절로 풀린다.
    private static final Map<UUID, Long> SWALLOW = new HashMap<>();
    private static final Map<UUID, Vec3> RING = new HashMap<>();
    private static final Map<UUID, Integer> HELD = new HashMap<>();

    public static boolean isSwallowing(LivingEntity e) {
        Long until = SWALLOW.get(e.getUUID());
        return until != null && e.level().getServer() != null
            && e.level().getServer().getTickCount() < until;
    }

    private static final BossFightTracker TRACKER = new BossFightTracker(
        BOSS_ID, "리치", TEST_TAG,
        FIRST_DELAY, new int[] { INTERVAL_P1, INTERVAL_P2 }, new float[] { PHASE2_HP },
        RANGE, new BossFightTracker.Handler() {

            @Override
            public void cast(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                UUID id = f.boss.getUUID();
                long now = level.getServer().getTickCount();
                if (SWALLOW.containsKey(id) && now < SWALLOW.get(id)) return;   // 이미 삼키는 중

                SWALLOW.put(id, now + SWALLOW_TICKS);
                HELD.put(id, 0);

                double a = level.random.nextDouble() * Math.PI * 2;
                double d = RING_MIN + level.random.nextDouble() * (RING_MAX - RING_MIN);
                Vec3 b = f.boss.position();
                RING.put(id, new Vec3(b.x + Math.cos(a) * d, b.y, b.z + Math.sin(a) * d));

                if (!f.taught) {
                    f.taught = true;
                    for (ServerPlayer p : near) {
                        p.sendSystemMessage(Component.literal(
                            "§9◆ 별을 삼키는 자 §7— §9파랑§7이면 때려도 0이다. §e노란 자리§7로 §e절반 이상§7이 모여야 풀린다."));
                    }
                }
                Telegraph.announce(level, f.boss.position(), RANGE, Telegraph.Kind.HOLD, true);
                level.playSound(null, b.x, b.y, b.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.4f, 0.5f);
            }

            @Override
            public void onTick(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                UUID id = f.boss.getUUID();
                Long until = SWALLOW.get(id);
                if (until == null) return;
                long now = level.getServer().getTickCount();

                if (now >= until) {                       // 시간이 다 됐다 = 못 막았다
                    end(level, f.boss, near, false);
                    return;
                }

                Vec3 ring = RING.get(id);
                if (ring == null) return;

                // 파티 절반 이상이 안에 있어야 «지키는 중»이다. 한 명이 대신 서면 안 되는 이유:
                // 그러면 나머지는 계속 때리면 되고, 파랑이 «치지 마라»가 아니라 «한 명만 빠져라»가 된다.
                int inside = 0;
                for (ServerPlayer p : Telegraph.inside(level, ring, RING_RADIUS)) {
                    if (near.contains(p) && Telegraph.hittable(p, level)) inside++;
                }
                int need = Math.max(1, (near.size() + 1) / 2);
                int held = HELD.getOrDefault(id, 0) + (inside >= need ? 1 : 0);
                HELD.put(id, held);

                if (held >= HOLD_NEEDED) { end(level, f.boss, near, true); return; }

                if (now % 2 != 0) return;   // 2틱마다면 충분하다
                Telegraph.aura(level, f.boss.position(), Telegraph.Kind.HOLD, 2.0, 3.4, 5);
                Telegraph.band(level, ring, Telegraph.Kind.STACK, 0f, 360f,
                    RING_RADIUS - 0.2, RING_RADIUS + 0.2, 26);
            }

            @Override
            public void onPhaseChange(ServerLevel level, BossFightTracker.Fight f, List<ServerPlayer> near) {
                level.playSound(null, f.boss.getX(), f.boss.getY(), f.boss.getZ(),
                    SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 0.6f);
                for (ServerPlayer p : near) {
                    p.sendSystemMessage(Component.literal(
                        "§5⚠ 리치가 더 깊이 삼킨다 §7— 삼킴이 §d더 자주§7 온다."));
                }
            }
        });

    private static void end(ServerLevel level, LivingEntity boss, List<ServerPlayer> near, boolean broken) {
        UUID id = boss.getUUID();
        SWALLOW.remove(id);
        RING.remove(id);
        HELD.remove(id);
        Vec3 at = boss.position();
        Telegraph.announce(level, at, RANGE, Telegraph.Kind.HOLD, false);

        if (broken) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH, at.x, at.y + 1.5, at.z, 3, 0, 0, 0, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 1.2f, 1.2f);
            for (ServerPlayer p : near) {
                p.sendSystemMessage(Component.literal("§a✦ 별빛을 되찾았다 §7— 리치의 삼킴이 풀렸다."));
            }
            return;
        }

        // 못 막으면 회복한다. 벌이 «시간 낭비»뿐이면 그냥 기다리는 게 최적해가 된다.
        float heal = boss.getMaxHealth() * HEAL_FRAC;
        boss.heal(heal);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.2f, 0.7f);
        for (ServerPlayer p : near) {
            p.sendSystemMessage(Component.literal(
                String.format("§c✦ 리치가 별을 삼켰다 §7— 체력 §c+%.0f§7 회복.", heal)));
        }
    }

    // ── 무적 집행 ──
    // 리치는 hurt() 를 오버라이드하지 않으므로 이 이벤트가 정상적으로 열린다
    // (강철거인·이그니스는 그 반대라서 여기서 못 잡는다 — 그쪽은 표시만 한다).
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (SWALLOW.isEmpty()) return;
        if (!isSwallowing(victim)) return;
        event.setCanceled(true);
        if (victim.level() instanceof ServerLevel sl && sl.getServer().getTickCount() % 4 == 0) {
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                victim.getX(), victim.getY() + 1.2, victim.getZ(), 3, 0.4, 0.6, 0.4, 0.01);
        }
    }

    // ── 이벤트 위임 (BossFightTracker 주석 참고: 구독은 기믹이 한다) ──

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) { TRACKER.onJoin(event); }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) { TRACKER.tick(); }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRACKER.stop();
        SWALLOW.clear();
        RING.clear();
        HELD.clear();
    }

    // ── 시험용 ──

    public static boolean summon(ServerPlayer p) { return TRACKER.summon(p); }
    public static boolean forceNear(ServerPlayer p) { return TRACKER.forceNear(p); }
    public static int clearTest() { return TRACKER.clearTest(); }
    public static int tracked() { return TRACKER.tracked(); }
    public static List<Component> status() { return TRACKER.status(); }
}
