package com.laststardust.relics;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 케이론의 「상처 입은 치유자」 — <b>때리면 아군이 낫는다. 나만 빼고.</b>
 *
 * <p>── 이 파일이 존재하는 이유 ──
 * 이 직업의 정체성은 스킬이 아니라 <b>평타</b>에 있다. 적을 때릴 때마다 반경 12칸에서
 * 가장 다친 아군이 회복된다. 그 판정을 스킬 파일이 아니라 여기 한 곳에 모은다 —
 * 스틱스 {@code onStyxStrike} · 네메시스 {@code onMomentumStrike} 과 같은 자리다.
 *
 * <p>── 왜 «피해 비례»가 아니라 «타격당 고정»인가 ──
 * 크리(×1.5) · 축복 · 전역 배율이 전부 피해에 곱해진다. 비례로 두면 <b>회복이 같이 폭주한다.</b>
 * 피해는 보스 체력을 올려 보정할 수 있지만 회복은 그럴 수 없다 —
 * {@code RelicSkills.healScale} 주석이 같은 이유로 회복 배율을 1.0→2.5 로만 올려뒀다.
 *
 * <p>── 자기 자신은 회복 대상이 아니다 ──
 * 케이론은 신화에서 <b>자기 상처만은 끝내 못 고친 치유자</b>다. 그게 그대로 기믹이 됐다:
 * 앞에 서야 힐이 나오는데, 앞에 서면 자기가 맞고, 자기는 못 낫는다.
 * <b>유일한 예외가 C「가르침」</b>({@link #teachingActive})이다 — 가르친 것이 스승에게 돌아온다.
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class ChironManager {
    private ChironManager() {}

    // ── 수치 (docs/CLASSES.md 「케이론」 §2·§4) ──
    /** 평타 한 대당 아군 회복. {@code healScale} 이 곱해져 5성 2.2 가 된다. */
    public static final float HEAL_PER_HIT = 0.9f;
    /** 회복 대상을 찾는 반경. */
    public static final double HEAL_RANGE = 12.0;

    /** C「가르침」 — 아군의 평타에도 붙는 전이 회복. <b>본인의 절반</b>이다(폭주 방지). */
    public static final float TEACH_FACTOR = 0.5f;
    public static final int TEACH_TICKS = 160;      // 8초
    public static final double TEACH_RANGE = 12.0;

    /** V「바람 걸음」 — 6초. 지나가면서 반경 4칸 아군을 회복시킨다. */
    public static final int WIND_TICKS = 120;
    public static final double WIND_RANGE = 4.0;
    private static final int WIND_PULSE = 10;       // 0.5초마다 한 번
    private static final float WIND_HEAL = 0.6f;    // 한 번당 (healScale 적용 · 5성 1.5)

    /** X「펠리온의 밤」 — 10초. ⚠️ 시전자 본인은 이 효과를 안 받는다. */
    public static final int NIGHT_TICKS = 200;
    public static final double NIGHT_RANGE = 12.0;

    // ── 엔티티 키 ── (사람에게 붙는 상태)
    private static final String K_TEACH = "lsChironTeachUntil";
    private static final String K_NIGHT = "lsChironNightUntil";
    private static final String K_WIND = "lsChironWindUntil";

    // ══════════════════════════════════════════════════════════════════
    //  상태
    // ══════════════════════════════════════════════════════════════════

    public static void markTeaching(ServerPlayer caster, int ticks) {
        caster.getPersistentData().putLong(K_TEACH, caster.level().getGameTime() + ticks);
    }

    public static void markNight(ServerPlayer caster, int ticks) {
        caster.getPersistentData().putLong(K_NIGHT, caster.level().getGameTime() + ticks);
    }

    public static void markWind(ServerPlayer caster, int ticks) {
        caster.getPersistentData().putLong(K_WIND, caster.level().getGameTime() + ticks);
    }

    /**
     * 남은 틱. <b>{@code max} 로 상한을 두는 이유</b>: 시간이 되감기면(백업 복원 · {@code /time set})
     * 아득히 먼 만료 틱이 남아 상태가 영구히 켜진다. {@code ParryManager.active} 와 같은 방어다.
     */
    private static long leftOf(Player p, String key, int max) {
        long left = p.getPersistentData().getLong(key) - p.level().getGameTime();
        return (left > 0 && left <= max) ? left : 0L;
    }

    public static long teachingLeft(ServerPlayer p) { return leftOf(p, K_TEACH, TEACH_TICKS); }
    public static long nightLeft(ServerPlayer p) { return leftOf(p, K_NIGHT, NIGHT_TICKS); }
    public static long windLeft(ServerPlayer p) { return leftOf(p, K_WIND, WIND_TICKS); }

    /** 지금 「가르침」 창이 열려 있나 — 이 동안만 시전자도 회복 대상이 된다. */
    public static boolean teachingActive(ServerPlayer p) { return teachingLeft(p) > 0; }

    // ══════════════════════════════════════════════════════════════════
    //  ① 상처 입은 치유자 — 때리면 아군이 낫는다
    // ══════════════════════════════════════════════════════════════════

    /**
     * {@code LivingDamageEvent.Post} 를 쓰는 이유: 「실제로 박혔는가」를 봐야 하기 때문이다.
     * 무적 프레임에 씹힌 헛방으로 회복이 나가면 안 된다.
     *
     * <p>회복량은 피해와 무관하므로 여기서 {@code getNewDamage()} 를 읽지 않는다 —
     * 「몇 번 때렸나」만 본다.
     */
    @SubscribeEvent
    public static void onChironStrike(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        // 아군 오사·자해로 회복이 나가면 힐 무한 루프가 된다.
        if (victim instanceof Player) return;

        ItemStack held = attacker.getMainHandItem();
        if (held.getItem() == LSRelics.CHIRON.get()) {
            transfer(level, attacker, attacker, held, HEAL_PER_HIT);
            return;
        }

        // ── C「가르침」: 주변 아군의 평타에도 전이 회복이 붙는다 ──
        // 명단을 시전 시점에 고정하지 않고 매 타격마다 거리를 본다 — 고정하면 8초 동안
        // 20칸 밖에서도 회복이 나가는 구멍이 된다.
        for (ServerPlayer teacher : level.players()) {
            if (teacher == attacker) continue;
            if (!teachingActive(teacher)) continue;
            if (teacher.distanceToSqr(attacker) > TEACH_RANGE * TEACH_RANGE) continue;
            ItemStack staff = teacher.getMainHandItem();
            if (staff.getItem() != LSRelics.CHIRON.get()) continue;   // 도중에 무기를 바꾸면 끊긴다
            // 회복의 «출처»는 여전히 케이론이다 — 축복·위협도가 케이론 몫으로 잡혀야 앞뒤가 맞는다.
            transfer(level, teacher, attacker, staff, HEAL_PER_HIT * TEACH_FACTOR);
            break;   // 두 케이론이 겹쳐도 한 대에 한 번만
        }
    }

    /**
     * 회복을 실제로 넣는다.
     *
     * @param caster 회복의 «주인» — 축복 배율과 성급을 이 사람의 무기에서 읽는다
     * @param origin 이번 타격을 «친» 사람 — 회복 대상을 이 사람 주변에서 찾는다
     */
    private static void transfer(ServerLevel level, ServerPlayer caster, ServerPlayer origin,
                                 ItemStack staff, float base) {
        float amount = base * com.laststardust.relics.item.RelicSkills.healScale(staff);
        if (amount <= 0) return;

        // ⚠️ 자기 자신은 대상이 아니다 — 「가르침」 창이 열려 있을 때만 풀린다.
        Player target = com.laststardust.relics.item.RelicSkills.weakestAlly(
            level, origin, HEAL_RANGE, teachingActive(caster) ? null : caster);
        if (target == null) return;

        float missing = Math.max(0, target.getMaxHealth() - target.getHealth());
        float healed = Math.min(missing, amount);
        if (healed <= 0) return;

        final Player t = target;
        final float h = healed;
        // 별의 축복 「치유 증폭」의 «주는» 절반 — PanaceaStaff.heal 과 같은 이유.
        com.laststardust.relics.blessing.BlessingEffects.healingBy(caster, () -> t.heal(h));

        // ⚠️ 위협도를 «일부러» 안 올린다.
        // 이 회복은 «준 피해»에서 나온 것이고, 그 피해가 이미 ThreatManager 에 올라갔다.
        // 여기서 또 올리면 두 번 세는 셈이고, 근접이라 그대로 두면 탱커보다 어그로를 끈다
        // (docs/CLASSES.md 「케이론」 §4). R·C·X 의 회복은 별도 행동이므로 정상적으로 위협도를 낸다.

        level.sendParticles(ParticleTypes.HEART,
            t.getX(), t.getY() + t.getBbHeight() * 0.75, t.getZ(), 1, 0.25, 0.25, 0.25, 0.0);
    }

    // ══════════════════════════════════════════════════════════════════
    //  ② 펠리온의 밤 — 아군이 죽지 않는다. 케이론만 빼고
    // ══════════════════════════════════════════════════════════════════

    /**
     * 「죽지 않는다」는 불사의 토템과 같은 자리를 건드린다. 이 피해로 죽을 때 <b>체력 1</b>을 남긴다.
     *
     * <p>⚠️ 우선순위를 낮게(=늦게) 두어 {@code ShieldManager} 보호막이나 다른 경감이
     * <b>먼저 걸린 뒤</b>에 판단하게 한다. 먼저 끼어들면 「보호막으로 막을 수 있었던 피해」에도
     * 궁극이 소모된 것처럼 보인다.
     *
     * <p>⚠️ <b>시전자 본인은 제외한다.</b> 여기서 자기를 포함시키면 10초 완전 무적이 되고,
     * 이 스킬의 유일한 대가가 사라진다({@code docs/CLASSES.md 「케이론」} §3).
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
    public static void onPelionNight(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (event.getAmount() < victim.getHealth()) return;    // 이 피해로는 안 죽는다

        // 허공(VOID)은 제외한다. 체력 1 로 버텨봐야 다음 틱에 또 맞고, 창이 끝나는 순간
        // 전원이 즉사한다 — 살린 게 아니라 죽음을 미룬 것이다.
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        for (ServerPlayer caster : level.players()) {
            if (nightLeft(caster) <= 0) continue;
            if (!caster.isAlive()) continue;          // 케이론이 죽으면 즉시 끝난다
            if (caster == victim) continue;           // ⚠️ 시전자 본인은 안 받는다
            if (victim.distanceToSqr(caster) > NIGHT_RANGE * NIGHT_RANGE) continue;

            event.setAmount(Math.max(0.0f, victim.getHealth() - 1.0f));
            level.sendParticles(ParticleTypes.END_ROD,
                victim.getX(), victim.getY() + 1.0, victim.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
            level.playSound(null, victim.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 0.5f, 1.6f);
            return;                                   // 둘이 겹쳐도 한 번만
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  ③ 유지 파티클 — 켜져 있는 게 보여야 한다
    // ══════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        // ── V「바람 걸음」 — 지나가면서 근처 아군을 회복시킨다 ──
        // 원안의 「자취를 밟으면 회복」을 뺀 이유: 실전에서 아군은 남의 발자국을 따라 걷지 않는다.
        // 화면에는 예쁜데 효과가 0 인 스킬이 된다. 지금은 «내가 지나가면» 그때그때 낫는다.
        if (event.getServer().getTickCount() % WIND_PULSE == 0) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                for (ServerPlayer p : level.players()) {
                    if (windLeft(p) <= 0) continue;
                    ItemStack staff = p.getMainHandItem();
                    if (staff.getItem() != LSRelics.CHIRON.get()) continue;   // 무기를 바꾸면 끊긴다
                    float amount = WIND_HEAL * com.laststardust.relics.item.RelicSkills.healScale(staff);
                    for (ServerPlayer ally : level.players()) {
                        if (!ally.isAlive() || ally.isSpectator()) continue;
                        if (ally == p && !teachingActive(p)) continue;   // 자기 자신은 제외 (패시브와 같은 규칙)
                        if (ally.distanceToSqr(p) > WIND_RANGE * WIND_RANGE) continue;
                        if (ally.getHealth() >= ally.getMaxHealth()) continue;
                        final ServerPlayer t = ally;
                        final float h = Math.min(amount, t.getMaxHealth() - t.getHealth());
                        com.laststardust.relics.blessing.BlessingEffects.healingBy(p, () -> t.heal(h));
                        com.laststardust.relics.ThreatManager.addHealThreat(level, p, h);
                        level.sendParticles(ParticleTypes.HEART,
                            t.getX(), t.getY() + t.getBbHeight() * 0.75, t.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
                    }
                    level.sendParticles(ParticleTypes.CLOUD,
                        p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.3, 0.05, 0.3, 0.01);
                }
            }
        }

        if (event.getServer().getTickCount() % 5 != 0) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (ServerPlayer p : level.players()) {
                if (teachingLeft(p) > 0) {
                    Vec3 c = p.position().add(0, 1.2, 0);
                    level.sendParticles(ParticleTypes.WAX_ON, c.x, c.y, c.z, 2, 0.5, 0.4, 0.5, 0.0);
                }
                if (nightLeft(p) > 0) {
                    for (int i = 0; i < 6; i++) {
                        double a = Math.PI * 2 * i / 6 + level.getGameTime() * 0.05;
                        level.sendParticles(ParticleTypes.END_ROD,
                            p.getX() + Math.cos(a) * NIGHT_RANGE, p.getY() + 0.2,
                            p.getZ() + Math.sin(a) * NIGHT_RANGE, 1, 0, 0, 0, 0.0);
                    }
                }
            }
        }
    }
}
