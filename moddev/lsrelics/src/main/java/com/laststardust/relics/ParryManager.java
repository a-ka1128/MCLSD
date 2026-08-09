package com.laststardust.relics;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 네메시스의 「흘리기」 — 패링 창과 그에 딸린 상태들.
 *
 * <p>── 이지스의 「수호 반격」과 무엇이 다른가 ──
 * 이지스는 <b>태세</b>다. 켜두면 3초간 자동으로 −40% 와 반사가 붙고 타이밍이 필요 없다.
 * 이쪽은 <b>순간</b>이다. 창이 {@link #WINDOW} 틱뿐이라 맞는 박자에 눌러야 하고,
 * 빗나가면 <b>그대로 맞는다.</b> 에레보스가 「등 뒤로 돌아야 숫자가 나오는」 딜러인 것처럼
 * 이쪽은 숙련형 탱커다({@code docs/CLASS-11.md} §2).
 *
 * <p>── 그래도 「못 해도 탱커」다 ──
 * 두 번째 탱커를 넣는 이유가 「아틀라스가 없을 때 대신 설 사람」인데, 그 대체재가 숙련을
 * 요구하면 초보가 잡았을 때 전선이 그대로 무너진다. 그래서 바닥을 깔았다:
 * 방어력 +5 / 방어 강도 +3 상시({@code LSRelics.nemesisAttrs}) · 체력 40칸 ·
 * 타이밍이 필요 없는 R「강철 발」(기세 0 이어도 −20%).
 *
 * <p>── 상태를 어디에 두나 ──
 * 창·태세처럼 <b>무기에 딸린 것</b>은 스택의 {@code custom_data} 에 만료 틱을 적는다
 * (이지스 {@code parryUntil} 과 같은 방식). 기세는 <b>사람에게</b> 붙으므로 엔티티 데이터에
 * 적는다({@link CurseManager} 와 같은 이유 — 만료 틱만 보면 청소 루프가 필요 없다).
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class ParryManager {
    private ParryManager() {}

    // ── 수치 (docs/CLASS-11.md §2·§3) ──
    /** 패링 창. 0.4초. 「순간」이라는 정체성이 이 숫자 하나에 걸려 있다. */
    public static final int WINDOW = 8;
    /** 한 번 패링에 성공하면 이만큼은 창이 안 열린다. 자세(−25%)는 그대로 유지된다. */
    public static final int PARRY_CD = 24;          // 1.2초
    /** 쥐고 있는 동안 앞에서 오는 피해를 이만큼 깎는다. 타이밍을 못 맞춰도 받는 몫. */
    public static final float BLOCK_DR = 0.25f;

    public static final int MOMENTUM_MAX = 5;
    public static final float MOMENTUM_PER = 0.06f; // 중첩당 주는 피해 +6% (최대 +30%)
    public static final int MOMENTUM_TICKS = 200;   // 10초

    // ── R「강철 발」 — 기세를 먹고 그만큼 단단해진다 (2026-08-09, 유저 결정) ──
    // 기본 −20% 에 중첩당 −5%, 5중첩이면 −45%. 기세가 0 이어도 −20% 는 나오므로
    // 「못 해도 탱커」의 바닥은 유지된다.
    public static final float STANCE_BASE = 0.20f;
    public static final float STANCE_PER = 0.05f;
    /** C「불굴」 — 중첩당 −7%, 5중첩이면 −35%. 기세가 0 이면 아무 일도 안 난다. */
    public static final float RESOLVE_PER = 0.07f;
    public static final int RESOLVE_TICKS = 120;    // 6초
    public static final float SUNDER_ALLY_DR = 0.25f;   // X 이후 아군 받는 피해 −25%
    public static final double SUNDER_ALLY_RANGE = 8.0; // 「내 뒤에 서라」 — 하르모니아(24칸)와 갈린다

    // ── 스택 키 ── (무기에 딸린 상태)
    public static final String K_PARRY  = "parryWindow";
    public static final String K_STANCE = "stanceUntil";
    public static final String K_RESOLVE = "resolveUntil"; // 불굴
    /** 자세·불굴의 «세기»는 그때 먹은 기세로 정해지므로 만료와 «같이» 적어둔다. */
    private static final String K_STANCE_DR = "stanceDr";
    private static final String K_RESOLVE_DR = "resolveDr";
    // ── 엔티티 키 ── (사람에게 붙는 상태)
    private static final String K_MOM      = "lsMomentum";
    private static final String K_MOM_END  = "lsMomentumUntil";
    private static final String K_SUNDER   = "lsSunderUntil";

    private static long now(LivingEntity e) { return e.level().getGameTime(); }

    // ══════════════════════════════════════════════════════════════════
    //  기세 — 패링에 성공할 때만 쌓인다
    // ══════════════════════════════════════════════════════════════════

    public static int momentum(Player p) {
        CompoundTag d = p.getPersistentData();
        if (d.getLong(K_MOM_END) <= now(p)) return 0;
        return d.getInt(K_MOM);
    }

    /** 주는 피해 배수. 기세가 없으면 1.0. */
    public static float momentumMult(Player p) {
        return 1.0f + MOMENTUM_PER * momentum(p);
    }

    /** 기세를 전부 쓰고 그 수를 돌려준다. 스킬이 «얼마나 세게» 나갈지는 이 값이 정한다. */
    public static int consumeMomentum(ServerPlayer p) {
        int n = momentum(p);
        CompoundTag d = p.getPersistentData();
        d.putInt(K_MOM, 0);
        d.putLong(K_MOM_END, 0);
        return n;
    }

    public static void addMomentum(ServerPlayer p) {
        int n = Math.min(MOMENTUM_MAX, momentum(p) + 1);
        CompoundTag d = p.getPersistentData();
        d.putInt(K_MOM, n);
        d.putLong(K_MOM_END, now(p) + MOMENTUM_TICKS);
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + p.getBbHeight() * 0.6, p.getZ(),
                n * 3, 0.35, 0.3, 0.35, 0.05);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  무기에 딸린 상태 — 만료 틱만 적는다
    // ══════════════════════════════════════════════════════════════════

    public static void arm(ItemStack stack, ServerLevel level, String key, int ticks) {
        CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        t.putLong(key, level.getGameTime() + ticks);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    /**
     * 만료 틱이 살아 있나. <b>{@code max} 로 상한을 두는 이유</b>: 시간이 되감기면
     * (백업 복원·`/time set`) 아득히 먼 만료 틱이 남아 상태가 영구히 켜진다.
     * 이지스 {@code onGuardianParry} 가 같은 이유로 {@code left > 60} 을 걸러낸다.
     */
    public static boolean active(ItemStack stack, ServerLevel level, String key, int max) {
        long left = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
            .copyTag().getLong(key) - level.getGameTime();
        return left > 0 && left <= max;
    }

    /** 만료와 «세기»를 같이 적는다. 세기는 시전 시점의 기세로 정해져 그 뒤엔 안 변한다. */
    public static void armWith(ItemStack stack, ServerLevel level, String key, String drKey,
                               int ticks, float dr) {
        CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        t.putLong(key, level.getGameTime() + ticks);
        t.putFloat(drKey, dr);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    private static float drOf(ItemStack stack, String drKey) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getFloat(drKey);
    }

    /** X「일도양단」 직후 아군을 감싸는 창. 시전자에게 적는다. */
    public static void markSunder(ServerPlayer caster, int ticks) {
        caster.getPersistentData().putLong(K_SUNDER, now(caster) + ticks);
    }

    // ══════════════════════════════════════════════════════════════════
    //  ① 패링 — 맞는 순간에 창이 열려 있으면 무효화하고 되돌려준다
    // ══════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onParry(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer guard)) return;
        if (!(guard.level() instanceof ServerLevel sl)) return;
        ItemStack held = guard.getMainHandItem();
        if (held.getItem() != LSRelics.NEMESIS.get()) return;

        // ── 방패처럼 «쥐고 있는가» ──
        // 창의 시작점을 따로 저장하지 않는다. 바닐라가 이미 세고 있다(getTicksUsingItem).
        // 놓았다 다시 쥐면 0 부터 다시 세므로 «다시 노린다»가 공짜로 성립한다.
        boolean guarding = guard.isUsingItem() && guard.getUseItem() == held;
        boolean front = guarding && facing(guard, event);

        boolean perfect = front
            && guard.getTicksUsingItem() <= WINDOW
            && !active(held, sl, K_PARRY, PARRY_CD);   // 한 번 성공하면 잠깐 못 연다

        if (!perfect) {
            float mult = 1f;
            // 못 맞춰도 «막고는 있다» — 이게 「못 해도 탱커」의 첫 바닥이다.
            // 방패와 같이 앞에서 오는 것만 막는다.
            if (front) mult *= (1f - BLOCK_DR);
            // 강철 발·불굴은 «자세»가 아니라 «상태»라 방향을 안 본다. 셋은 곱해진다.
            if (active(held, sl, K_STANCE, 60)) mult *= (1f - drOf(held, K_STANCE_DR));
            if (active(held, sl, K_RESOLVE, RESOLVE_TICKS)) mult *= (1f - drOf(held, K_RESOLVE_DR));
            if (mult < 1f) event.setAmount(event.getAmount() * mult);
            return;
        }
        // 연속 무효화 방지 — 한 번의 자세로 무리 전체를 지우면 타이밍이 의미를 잃는다.
        // (광역으로 되받는 건 C「역린」의 몫이다.)
        arm(held, sl, K_PARRY, PARRY_CD);

        // ── 성공 ──
        event.setCanceled(true);
        addMomentum(guard);
        sl.sendParticles(ParticleTypes.CRIT, guard.getX(), guard.getY() + 1.1, guard.getZ(),
            24, 0.4, 0.35, 0.4, 0.25);
        sl.playSound(null, guard.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.4f, 0.6f);
        sl.playSound(null, guard.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.8f);

        // ── 반격 ──
        // ⚠️ 때린 «놈»이 있고 그게 플레이어가 아닐 때만. 용암·낙하는 받아넘길 대상이 아니고,
        //    PvP 에서 서로 패링하면 반격이 서로의 창을 다시 열어 핑퐁이 된다.
        //    이지스 onGuardianParry 가 같은 이유로 같은 검사를 한다.
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker instanceof Player) return;

        var src = com.laststardust.relics.item.RelicSkills.relicSource(sl, guard);
        LsDamage.hit(attacker, src, com.laststardust.relics.item.RelicSkills.dmg(held, 6.0f), "흘리기");
        stagger(sl, attacker);

    }

    /**
     * 앞에서 오는 공격인가. <b>바닐라 방패와 같은 계산을 쓴다</b>
     * ({@code LivingEntity.isDamageSourceBlocked}) — 손맛이 방패와 어긋나면
     * 「왜 이건 막히고 저건 안 막히지」가 되고, 그건 배울 수 없는 규칙이다.
     *
     * <p>피해 위치를 모르면(질식·독처럼 «어디서»가 없는 것) 막을 수 없는 것으로 본다.
     */
    private static boolean facing(ServerPlayer guard, LivingIncomingDamageEvent event) {
        var from = event.getSource().getSourcePosition();
        if (from == null) return false;
        var look = guard.getViewVector(1.0f);
        var to = from.vectorTo(guard.position()).normalize();
        to = new net.minecraft.world.phys.Vec3(to.x, 0.0, to.z);
        return to.dot(look) < 0.0;
    }

    /**
     * 「경직」 — 마크에 그런 상태가 없어서 최고 등급 둔화 + 이동 정지로 근사한다.
     * 헤카테 「재의 결계」({@code RelicSkills.ashWard})가 이미 같은 수법을 쓴다.
     */
    public static void stagger(ServerLevel level, LivingEntity e) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 5, false, true));
        e.setDeltaMovement(0, e.getDeltaMovement().y, 0);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT,
            e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
    }

    // ══════════════════════════════════════════════════════════════════
    //  ①-B 강철 발이 «끝날 때» 밀어낸다
    // ══════════════════════════════════════════════════════════════════

    /**
     * 「3초를 버틴 대가」라 버티고 «난 뒤»에 나와야 한다. 시전 순간에 터뜨리면 그냥 광역
     * 넉백 스킬이고, 뿌리내리는 3초가 순수 손해가 된다.
     *
     * <p>이 모드엔 지연 실행이 없다({@code SoundScheduler} 는 소리 전용). 그래서 스택에
     * 만료 틱을 적어두고 여기서 지나갔는지만 본다 — 무기를 바꾸거나 죽으면 그대로 사라지는데,
     * 그건 「자리를 지키지 못했다」와 같은 뜻이라 오히려 앞뒤가 맞는다.
     */
    private static final String K_BURST = "stanceBurst";

    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long t = level.getGameTime();
            for (ServerPlayer p : level.players()) {
                ItemStack held = p.getMainHandItem();
                if (held.getItem() != LSRelics.NEMESIS.get()) continue;
                CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                long at = tag.getLong(K_BURST);
                if (at == 0 || at > t) continue;
                tag.putLong(K_BURST, 0);
                held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                com.laststardust.relics.item.RelicSkills.stanceBurst(level, p, held);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  ② 일도양단 직후 — 시전자 주변 8칸 아군이 덜 아프다
    // ══════════════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onSunderGuard(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!(victim.level() instanceof ServerLevel sl)) return;
        long t = sl.getGameTime();
        for (ServerPlayer caster : sl.players()) {
            long left = caster.getPersistentData().getLong(K_SUNDER) - t;
            if (left <= 0 || left > 200) continue;              // 되감김 방어
            if (victim.distanceToSqr(caster) > SUNDER_ALLY_RANGE * SUNDER_ALLY_RANGE) continue;
            event.setAmount(event.getAmount() * (1f - SUNDER_ALLY_DR));
            return;                                             // 둘이 겹쳐도 한 번만
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  ③ 기세 — 네메시스가 «주는» 피해에 곱한다
    // ══════════════════════════════════════════════════════════════════

    /**
     * 스틱스의 {@code onStyxStrike} 와 같은 자리·같은 방식이다.
     *
     * <p>속성(`ATTACK_DAMAGE` 수정자)으로 안 하는 이유: 그 속성을 보는 건 <b>근접 평타뿐</b>이고,
     * 스킬 피해는 전부 {@code LsDamage} 고정 수치라 통째로 새어나간다. 부활 쇠약이 정확히
     * 그렇게 새다가 2026-07-30 에 이 방식으로 옮겨졌다({@code TODO.md} A절).
     */
    @SubscribeEvent
    public static void onMomentumStrike(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (attacker.getMainHandItem().getItem() != LSRelics.NEMESIS.get()) return;
        float mult = momentumMult(attacker);
        if (mult <= 1.0f) return;
        event.setAmount(event.getAmount() * mult);
    }
}
