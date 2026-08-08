package com.laststardust.relics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 하르모니아의 「엮음」 — 아군에게 걸리는 버프 넷과 매듭 지대.
 *
 * <p>── 왜 {@link CurseManager} 와 구조가 다른가 ──
 * 저주는 <b>몹</b>에게 걸리고 수가 많다. 그래서 엔티티 데이터에 만료 틱만 적고 청소를 안 한다.
 * 이쪽은 <b>플레이어</b>에게 걸리고 최대 8명이다. 대신 «속성 수정자»를 붙였다 떼야 해서
 * 만료를 실제로 지켜보는 주체가 필요하다 — 그래서 여기는 틱 루프를 둔다.
 * 8명 × 4버프면 초당 32번인데, 20틱에 한 번만 돌므로 부담이 없다.
 *
 * <p>── 회복을 하나도 안 넣었다 ──
 * 히기에이아가 서포트의 «회복» 절반을 갖고, 하르모니아는 «강화» 절반을 갖는다.
 * 여기에 힐이나 보호막을 넣는 순간 두 직업이 같은 자리를 놓고 싸운다
 * ({@code docs/CLASS-9-10.md} §2 설계 의도 1).
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class HarmonyManager {
    private HarmonyManager() {}

    // ── 수치 (docs/CLASS-9-10.md §2) ──
    public static final double AURA_RANGE   = 8.0;
    public static final float  AURA_SPEED   = 0.10f;   // 주변 아군 이동속도 +10%
    public static final float  KILL_ATK     = 0.05f;   // 처치 시 공격력 +5%
    public static final int    KILL_MAX     = 3;
    public static final int    KILL_TICKS   = 160;     // 8초
    public static final float  ANTHEM_ASPD  = 0.25f;   // R — 공격속도 +25%
    public static final int    ANTHEM_TICKS = 80;      // 4초
    public static final double KNOT_RANGE   = 6.0;
    public static final float  KNOT_DR      = 0.15f;   // C — 받는 피해 −15%
    public static final float  KNOT_CDR     = 0.02f;   // C — 초당 남은 쿨의 2% 를 당긴다
    public static final int    KNOT_TICKS   = 240;     // 12초
    public static final double CHORD_RANGE  = 24.0;
    public static final float  CHORD_ATK    = 0.30f;   // X — 공격력 +30%
    public static final float  CHORD_SPEED  = 0.25f;   // X — 이동속도 +25%
    public static final int    CHORD_TICKS  = 200;     // 10초
    public static final int    CHORD_IMMUNE = 60;      // X — 디버프 재부여 면역 3초

    private static ResourceLocation id(String s) {
        return ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, s);
    }

    private static final ResourceLocation M_AURA   = id("harmony_aura");
    private static final ResourceLocation M_KILL   = id("harmony_kill");
    private static final ResourceLocation M_ANTHEM = id("harmony_anthem");
    private static final ResourceLocation M_CHORD_A = id("harmony_chord_atk");
    private static final ResourceLocation M_CHORD_S = id("harmony_chord_spd");

    // ── 상태 ── (플레이어당 만료 틱. 값이 없거나 지났으면 «안 걸린 것»)
    private static final Map<UUID, Long> ANTHEM  = new HashMap<>();
    private static final Map<UUID, Long> CHORD   = new HashMap<>();
    private static final Map<UUID, Long> IMMUNE  = new HashMap<>();
    private static final Map<UUID, int[]> KILLS  = new HashMap<>();   // {중첩, 만료틱}

    /** 매듭 지대 — 여러 개가 겹칠 수 있다(하르모니아가 둘일 일은 없지만 재시전은 가능). */
    private record Knot(ServerLevel level, Vec3 center, long expire) {}
    private static final Map<UUID, Knot> KNOTS = new HashMap<>();

    // ══════════════════════════════════════════════════════════════════
    //  걸기 — 스킬이 부른다
    // ══════════════════════════════════════════════════════════════════

    public static void anthem(ServerPlayer p) { ANTHEM.put(p.getUUID(), now(p) + ANTHEM_TICKS); }

    public static void chord(ServerPlayer p) {
        CHORD.put(p.getUUID(), now(p) + CHORD_TICKS);
        IMMUNE.put(p.getUUID(), now(p) + CHORD_IMMUNE);
        cleanse(p);
    }

    public static void knot(ServerPlayer caster, ServerLevel level, Vec3 center) {
        KNOTS.put(caster.getUUID(), new Knot(level, center, level.getGameTime() + KNOT_TICKS));
    }

    /** 해로운 효과만 걷어낸다. 신속·재생까지 지우면 「버프를 걸었는데 버프가 사라진다」가 된다. */
    private static void cleanse(ServerPlayer p) {
        p.getActiveEffects().stream()
            .map(MobEffectInstance::getEffect)
            .filter(h -> !h.value().isBeneficial())
            .toList()
            .forEach(p::removeEffect);
    }

    private static long now(LivingEntity e) { return e.level().getGameTime(); }

    // ══════════════════════════════════════════════════════════════════
    //  처치 중첩 — 「아군이」 죽여도 쌓인다 (지휘관이므로)
    // ══════════════════════════════════════════════════════════════════

    /**
     * {@code RelicEventHandlers.onKill} 이 «모든» 처치에서 부른다.
     *
     * <p>⚠️ 그래서 <b>여기서 하르모니아가 있는지 먼저 봐야 한다.</b> 안 보면 하르모니아가
     * 서버에 없어도 모두가 처치할 때마다 공격력 +15% 를 공짜로 받는다.
     *
     * <p>중첩은 «그 하르모니아의 오라 안» 아군에게 간다. 죽인 사람 주변이 아니다 —
     * 지휘관에게서 퍼지는 버프이므로 기준점은 지휘관이어야 한다.
     */
    public static void onAllyKill(ServerPlayer killer) {
        if (!(killer.level() instanceof ServerLevel level)) return;

        for (ServerPlayer h : level.players()) {
            if (h.getMainHandItem().getItem() != LSRelics.HARMONIA.get()) continue;
            // 죽인 사람이 그 하르모니아의 오라 밖이면 이 지휘관과는 무관한 처치다
            if (killer.distanceToSqr(h.position()) > AURA_RANGE * AURA_RANGE) continue;
            for (ServerPlayer p : nearbyAllies(level, h.position(), AURA_RANGE, null)) {
                int[] k = KILLS.get(p.getUUID());
                int n = (k != null && k[1] > now(p)) ? k[0] : 0;
                KILLS.put(p.getUUID(),
                    new int[]{Math.min(KILL_MAX, n + 1), (int) (now(p) + KILL_TICKS)});
            }
            return;   // 하르모니아가 둘일 일은 없지만, 있어도 중첩이 두 배로 오르진 않게
        }
    }

    private static java.util.List<ServerPlayer> nearbyAllies(ServerLevel level, Vec3 c, double r,
                                                             ServerPlayer except) {
        java.util.List<ServerPlayer> out = new java.util.ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p == except || !p.isAlive()) continue;
            if (p.distanceToSqr(c.x, c.y, c.z) <= r * r) out.add(p);
        }
        return out;
    }

    // ══════════════════════════════════════════════════════════════════
    //  틱 — 속성 수정자를 붙이고 뗀다
    // ══════════════════════════════════════════════════════════════════
    private static int tick;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tick % 20 != 0) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            // 오라 — 하르모니아를 든 사람 주변
            java.util.Set<UUID> inAura = new java.util.HashSet<>();
            for (ServerPlayer h : level.players()) {
                if (h.getMainHandItem().getItem() != LSRelics.HARMONIA.get()) continue;
                for (ServerPlayer a : nearbyAllies(level, h.position(), AURA_RANGE, null)) {
                    inAura.add(a.getUUID());
                }
            }

            for (ServerPlayer p : level.players()) {
                long t = level.getGameTime();
                mod(p, Attributes.MOVEMENT_SPEED, M_AURA,
                    inAura.contains(p.getUUID()) ? AURA_SPEED : 0f);

                int[] k = KILLS.get(p.getUUID());
                float atk = (k != null && k[1] > t) ? KILL_ATK * k[0] : 0f;
                mod(p, Attributes.ATTACK_DAMAGE, M_KILL, atk);

                mod(p, Attributes.ATTACK_SPEED, M_ANTHEM,
                    after(ANTHEM, p, t) ? ANTHEM_ASPD : 0f);

                boolean ch = after(CHORD, p, t);
                mod(p, Attributes.ATTACK_DAMAGE, M_CHORD_A, ch ? CHORD_ATK : 0f);
                mod(p, Attributes.MOVEMENT_SPEED, M_CHORD_S, ch ? CHORD_SPEED : 0f);

                // 매듭 안이면 쿨다운을 당긴다
                if (inKnot(p) && p.getMainHandItem().has(net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
                    com.laststardust.relics.item.RelicSkills.hastenCooldowns(
                        p.getMainHandItem(), level, KNOT_CDR);
                }
            }
        }

        // 만료된 것 정리 — 안 하면 접속을 반복할수록 맵이 커진다
        long g = event.getServer().overworld().getGameTime();
        ANTHEM.entrySet().removeIf(e -> e.getValue() <= g);
        CHORD.entrySet().removeIf(e -> e.getValue() <= g);
        IMMUNE.entrySet().removeIf(e -> e.getValue() <= g);
        KILLS.entrySet().removeIf(e -> e.getValue()[1] <= g);
        KNOTS.entrySet().removeIf(e -> e.getValue().expire() <= g);
    }

    private static boolean after(Map<UUID, Long> m, ServerPlayer p, long t) {
        Long v = m.get(p.getUUID());
        return v != null && v > t;
    }

    /** 값이 0 이면 떼고, 아니면 그 값으로 갈아 끼운다. 매번 지우고 다시 다는 게 제일 안전하다. */
    private static void mod(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                            ResourceLocation key, float value) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        if (inst.getModifier(key) != null) inst.removeModifier(key);
        if (value != 0f) {
            inst.addTransientModifier(new AttributeModifier(
                key, value, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    public static boolean inKnot(ServerPlayer p) {
        long t = p.level().getGameTime();
        for (Knot k : KNOTS.values()) {
            if (k.expire() <= t || k.level() != p.level()) continue;
            if (p.distanceToSqr(k.center().x, k.center().y, k.center().z) <= KNOT_RANGE * KNOT_RANGE) {
                return true;
            }
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════
    //  이벤트
    // ══════════════════════════════════════════════════════════════════

    /** 매듭 안의 아군은 덜 아프다. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (inKnot(p)) event.setAmount(event.getAmount() * (1f - KNOT_DR));
    }

    /**
     * 만상의 화음 직후 3초는 해로운 효과가 «다시 안 붙는다».
     *
     * <p>해제만 하고 면역이 없으면 다음 틱에 그대로 다시 걸려서 궁극의 절반이 헛것이 된다 —
     * 공성처럼 디버프가 계속 날아오는 상황에서 특히 그렇다.
     */
    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (event.getEffectInstance().getEffect().value().isBeneficial()) return;
        if (after(IMMUNE, p, p.level().getGameTime())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** 서버가 내려갈 때 비운다 — 싱글에서 월드를 바꿔 열면 이전 상태가 남는다. */
    public static void reset() {
        ANTHEM.clear(); CHORD.clear(); IMMUNE.clear(); KILLS.clear(); KNOTS.clear();
    }
}
