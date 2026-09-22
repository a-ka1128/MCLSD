package com.laststardust.relics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 헤카테의 「저주」 — 적에게 쌓이는 약화 중첩과 그에 딸린 두 상태.
 *
 * <p>── 왜 정적 맵이 아니라 엔티티 데이터인가 ──
 * {@code WeaknessHandler} 가 이미 같은 방식이다({@code lsWeakUntil}). 만료 틱을 엔티티에 적어두면
 * <b>청소 루프가 필요 없고</b>, 청크가 내려갔다 올라와도 값이 따라온다. 정적 맵으로 하면
 * 공성 한 판에 수백 개가 쌓여 매 틱 훑어야 한다({@code BlessingEffects} 가 그렇게 하고 있는데,
 * 그쪽은 «플레이어» 상태라 수가 적어서 감당되는 것이다).
 *
 * <p>── 세 가지 상태 ──
 * <ul>
 *   <li><b>저주</b> — 중첩당 받는 피해 +3%. 평타·재의 채찍·연좌가 쌓는다</li>
 *   <li><b>약화</b> — 그 적이 «주는» 피해 감소. 재의 채찍(−25%)·헤카테의 밤(−20%)</li>
 *   <li><b>회복 차단</b> — 헤카테의 밤 동안 그 적은 회복이 통째로 막힌다</li>
 * </ul>
 * 셋 다 만료 틱만 보므로 «걸어두고 잊는» 구조다.
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class CurseManager {
    private CurseManager() {}

    // ── 수치 (docs/CLASSES.md 「헤카테·하르모니아」 §1) ──
    public static final float PER_STACK   = 0.03f;  // 중첩당 받는 피해 +3%
    public static final int   MAX_STACKS  = 5;
    public static final int   BOSS_STACKS = 8;      // 보스에겐 상한이 높다 — 헤카테의 정체성
    public static final int   DURATION    = 160;    // 8초

    /**
     * 보스 판정 — 최대 체력으로 가른다.
     *
     * <p>이 모드에 보스 여부를 묻는 공용 함수가 없다. 보스바를 보는 방법은 몹 구현마다 달라서
     * 모드 29종을 다 맞출 수 없고, 태그로 관리하면 새 보스가 들어올 때마다 빠뜨린다.
     * 최대 체력은 {@code RelicEventHandlers.GIANT_HP} 가 이미 쓰는 잣대다.
     *
     * <p>기준을 200 으로 둔 이유: 잡몹은 {@code globalHp 180%} 를 타도 40 안팎이고
     * 정예도 100 을 크게 안 넘는다. 제단 보스는 절대 체력이 수천이라 넉넉히 갈린다.
     */
    public static final float BOSS_HP = 200.0f;

    public static boolean isBoss(LivingEntity e) {
        return e.getMaxHealth() >= BOSS_HP;
    }

    public static int cap(LivingEntity e) {
        return isBoss(e) ? BOSS_STACKS : MAX_STACKS;
    }

    // ── 저장 키 ──
    private static final String K_STACK   = "lsCurse";
    private static final String K_UNTIL   = "lsCurseUntil";
    private static final String K_ATK     = "lsAtkDown";
    private static final String K_ATK_END = "lsAtkDownUntil";
    private static final String K_NOHEAL  = "lsNoHealUntil";

    private static long now(LivingEntity e) { return e.level().getGameTime(); }

    // ══════════════════════════════════════════════════════════════════
    //  저주 중첩
    // ══════════════════════════════════════════════════════════════════

    /** 지금 걸려 있는 중첩. 만료됐으면 0. */
    public static int stacks(LivingEntity e) {
        CompoundTag d = e.getPersistentData();
        if (d.getLong(K_UNTIL) <= now(e)) return 0;
        return d.getInt(K_STACK);
    }

    /** 중첩을 더한다. 상한을 넘지 않고, 지속시간은 항상 새로 채워진다. */
    public static void add(LivingEntity e, int n) {
        if (n <= 0 || !e.isAlive()) return;
        int cur = stacks(e);
        set(e, Math.min(cap(e), cur + n));
    }

    /** 중첩을 «그 값으로» 맞춘다 — 연좌(복사)와 헤카테의 밤(최대)이 쓴다. */
    public static void set(LivingEntity e, int n) {
        if (!e.isAlive()) return;
        CompoundTag d = e.getPersistentData();
        int before = stacks(e);
        int after = Math.max(0, Math.min(cap(e), n));
        d.putInt(K_STACK, after);
        d.putLong(K_UNTIL, now(e) + DURATION);
        if (after > before) mark(e, after);
    }

    /**
     * 중첩이 «오를 때»만 표식을 띄운다.
     *
     * <p>매 틱 도는 표시 루프를 두지 않는 이유: 공성에서 저주 걸린 몹이 수십 마리면 그게
     * 그대로 파티클 폭탄이 된다. 대신 «바뀌는 순간»에만 터뜨리면 공짜고, 실제로 알고 싶은
     * 것도 「지금 몇 개인가」보다 「걸렸나 / 늘었나」다. 정확한 숫자는 액션바가 따로 띄운다
     * ({@code HecateScythe.hudStatus}).
     *
     * <p>파티클을 중첩 수만큼 띄운다 — 세지 않아도 «많이 걸렸다»가 눈에 들어온다.
     */
    private static void mark(LivingEntity e, int stacks) {
        if (!(e.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        double y = e.getY() + e.getBbHeight() + 0.35;
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
            e.getX(), y, e.getZ(), stacks * 2, 0.28, 0.12, 0.28, 0.005);
        if (stacks >= cap(e)) {
            // 상한에 닿으면 한 겹 더 — 연좌를 쓸 때가 됐다는 신호
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                e.getX(), y, e.getZ(), 6, 0.3, 0.15, 0.3, 0.01);
        }
    }

    /** 이 적이 받는 피해 배수. 저주가 없으면 1.0. */
    public static float incomingMultiplier(LivingEntity e) {
        return 1.0f + PER_STACK * stacks(e);
    }

    // ══════════════════════════════════════════════════════════════════
    //  약화 (그 적이 «주는» 피해 감소)
    //
    //  속성 수정자(AttributeModifier)를 안 쓴다. 그건 스스로 만료되지 않아서 떼어낼 시점을
    //  따로 관리해야 하는데, 몹은 죽거나 청크와 함께 사라져 그 시점을 놓치기 쉽다.
    //  피해 이벤트에서 만료 틱만 보면 «걸어두고 잊는» 게 된다.
    // ══════════════════════════════════════════════════════════════════

    public static void weaken(LivingEntity e, float pct, int ticks) {
        if (pct <= 0 || !e.isAlive()) return;
        CompoundTag d = e.getPersistentData();
        // 이미 더 센 약화가 걸려 있으면 유지한다 — 궁극(20%) 이 채찍(25%) 을 덮어쓰면 손해다
        float cur = d.getLong(K_ATK_END) > now(e) ? d.getFloat(K_ATK) : 0f;
        d.putFloat(K_ATK, Math.max(cur, Math.min(0.9f, pct)));
        d.putLong(K_ATK_END, Math.max(d.getLong(K_ATK_END), now(e) + ticks));
    }

    public static float outgoingMultiplier(LivingEntity e) {
        CompoundTag d = e.getPersistentData();
        if (d.getLong(K_ATK_END) <= now(e)) return 1.0f;
        return 1.0f - d.getFloat(K_ATK);
    }

    // ══════════════════════════════════════════════════════════════════
    //  회복 차단
    // ══════════════════════════════════════════════════════════════════

    public static void blockHeal(LivingEntity e, int ticks) {
        if (!e.isAlive()) return;
        CompoundTag d = e.getPersistentData();
        d.putLong(K_NOHEAL, Math.max(d.getLong(K_NOHEAL), now(e) + ticks));
    }

    public static boolean healBlocked(LivingEntity e) {
        return e.getPersistentData().getLong(K_NOHEAL) > now(e);
    }

    // ══════════════════════════════════════════════════════════════════
    //  이벤트
    // ══════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        float amount = event.getAmount();

        // ① 저주가 걸린 «적»이 맞으면 더 아프다. 파티 전원의 피해에 적용된다 —
        //    이게 헤카테가 «지원»인 이유다. 자기 딜이 아니라 남의 딜을 키운다.
        if (!(victim instanceof Player)) {
            amount *= incomingMultiplier(victim);
        }

        // ② 약화된 적이 «때리면» 덜 아프다. 플레이어를 때릴 때만 의미가 있는 게 아니라
        //    누구를 때리든 적용된다 — 규칙이 대상에 따라 갈리면 설명이 두 줄이 된다.
        if (event.getSource().getEntity() instanceof LivingEntity foe && !(foe instanceof Player)) {
            amount *= outgoingMultiplier(foe);
        }

        event.setAmount(amount);
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || e instanceof Player) return;
        if (healBlocked(e)) event.setCanceled(true);
    }
}
