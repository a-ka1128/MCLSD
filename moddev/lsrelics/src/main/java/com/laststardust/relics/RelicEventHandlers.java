package com.laststardust.relics;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;


import com.laststardust.relics.item.RelicSkills;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 유물 패시브·특수효과 이벤트 모음.
//  · 유성 사격: 표식된 화살이 착탄 시 폭발
//  · 방벽의 수호자(방패 패시브): 근처 아군 받는 피해 -5%
//  · 바람의 발걸음(활 패시브): 처치 시 이동속도 상승
//
// ╔══════════════════════════════════════════════════════════════════════════╗
// ║ 화살 착탄 훅은 ProjectileImpactEvent 를 안 쓴다 (2026-08-04)               ║
// ╚══════════════════════════════════════════════════════════════════════════╝
//
// ── 확실한 사실 ──
// 8종 DPS 실측에서 유성 사격이 «18타 평균 9.9» 로 나왔다. 18타는 정확히 6시전 × 3발이고
// 9.9 는 화살 직격만의 값이다 — **폭발(5성 약 69)이 0회였다.** 시리우스가 8종 중
// 꼴찌(78.9)인 것과 무관하지 않다.
//
// ── 확실하지 않은 것 (내가 한때 단정했다가 물린 것) ──
// 원인을 «ProjectileImpactEvent 가 아예 안 불린다» 로 단정하고 그렇게 적었다. **근거가 약했다.**
//   · 훅 진입점에 로그를 박고 화살을 쐈더니 로그가 0 이었다 → 그렇게 결론냈다
//   · 그런데 그 뒤 **mixin 적용 후 최종 바이트코드를 뽑아 보니** `AbstractArrow.tick()` 안에
//     `EventHooks.onProjectileImpact` 호출이 **멀쩡히 남아 있다.**
//   · 게다가 그때 쓴 «손으로 소환한 화살» 시험이 못 믿을 물건이었다 — 같은 조건에서 6발을
//     쏘고 표적 체력이 100 → 100 이었다(한 발도 안 맞았다). 앞서 본 7/0/3 피해도 화살이
//     아니었을 수 있다.
// 두 증거가 충돌하고, 아직 못 갈랐다. **범인 모드도 못 찾았다** — 화살/투사체 mixin 을 가진
// 모드 9개를 훑었지만 착탄 경로를 취소하는 것은 없었다.
//
// ── 그래도 옮긴 이유 ──
// 원인이 무엇이든 **폭발이 0회였다는 사실은 실측이다.** 그리고 여기 쓰는 이벤트는
// «도는 게 증명된» 쪽이다 — 아래 `onStyxStrike` 가 같은 이벤트를 쓰고, 그 백어택 ×1.20 은
// 실측(정면 68.0 vs 풀딜 97.0 = ×1.43)으로 확인됐다. 나빠질 일이 없는 이동이다.
//
//     별빛 폭풍 확산 · 유성 사격 폭발  →  LivingIncomingDamageEvent  (맞았을 때 얹는다)
//     유물 화살의 무적 프레임 무시     →  LivingDamageEvent.Post     (박힌 뒤에 걷는다)
//     프로스트모 화살 경고            →  ExplorerGimmicks 의 보스 틱 (거기 주석 참조)
//
// ⚠️ **아직 도는 걸 못 봤다.** 시리우스로 `/dummy` 60초를 재서 «유성 사격 평균»이
//    9.9 → 50~70 대로 가는지 보면 갈린다(`docs/TEST-PLAN.md` 5-3 맨 위).
//
// **교훈 둘.** 「구현했다」와 「도는 걸 봤다」는 다르다 — 셋 다 정교한 주석까지 달려 있었다.
// 그리고 **못 믿을 계측으로 내린 결론을 «확인했다»고 적으면, 그 거짓 확신이 코드에 굳는다.**
// 위 문단을 지우지 말 것. 다음 사람이 같은 함정에 다시 들어가지 않게 하는 게 이 주석의 값이다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class RelicEventHandlers {
    private RelicEventHandlers() {}

    // ── 화살 착탄 효과 (별빛 폭풍 확산 · 유성 사격 폭발) ──
    //
    // **2026-08-04: ProjectileImpactEvent → LivingIncomingDamageEvent 로 옮겼다.**
    // 앞의 이벤트는 이 모드팩에서 한 번도 발화하지 않는다(위 머리말의 증거 참조).
    // 여기 쓰는 이벤트는 확실히 돈다 — 같은 파일의 `onSanctuaryProtect` 와 DummyManager 의
    // 집계가 이 계열로 멀쩡히 동작해 왔다.
    //
    // ── 바뀐 것: «빗나간 화살은 안 터진다» ──
    // 예전 이벤트는 블록에 박혀도 왔지만, 피해 이벤트는 **맞았을 때만** 온다.
    // 표적을 겨냥해 쏘는 스킬이라 실전에서 중요한 쪽은 남는다(한 발만 맞아도 주변까지 퍼진다).
    // 빗나간 자리의 광역까지 살리려면 화살을 매 틱 훑어야 하는데, 모든 화살에 그 비용을
    // 물리는 건 얻는 것에 비해 비싸다.
    //
    // ── 직격 대상만 처리가 다르다 ──
    // 지금 이 순간 그 대상은 `hurt()` 한가운데라 무적 창이 이미 서 있다. 거기에 `LsDamage.hit`
    // 을 또 부르면 **조용히 튕긴다.** 그래서 직격 대상은 이번 피해에 더하고(`setAmount`),
    // 주변만 별도 판정으로 때린다. 계측 이름표는 화살에 실린 `lsLabel` 이 그대로 따라온다.
    @SubscribeEvent
    public static void onArrowHitEffects(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.level() instanceof ServerLevel level)) return;

        float splash = arrow.getPersistentData().getFloat("lsSplash");
        if (splash > 0) {
            arrow.getPersistentData().putFloat("lsSplash", 0); // 1회만
            float r = arrow.getPersistentData().getFloat("lsSplashR");
            arrowAoe(level, arrow, event, r <= 0 ? 1.5f : r, splash, "별빛 폭풍");
            level.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(),
                6, 0.25, 0.15, 0.25, 0.03);
        }

        float boom = arrow.getPersistentData().getFloat("lsExplode");
        if (boom > 0) {
            arrow.getPersistentData().putFloat("lsExplode", 0); // 1회만
            float r = arrow.getPersistentData().getFloat("lsExplodeR");
            if (r <= 0) r = 2.5f;
            // 폭발도 크리 가능 (60% 확률 ×1.5)
            float finalDmg = level.getRandom().nextFloat() < 0.6f ? boom * 1.5f : boom;
            arrowAoe(level, arrow, event, r, finalDmg, "유성 사격");
            meteorFx(level, arrow.getX(), arrow.getY(), arrow.getZ(), r);
        }
    }

    // 직격 대상은 이번 피해에 더하고, 반경 안의 나머지는 따로 때린다.
    private static void arrowAoe(ServerLevel level, AbstractArrow arrow,
                                 LivingIncomingDamageEvent event, float radius, float dmg, String label) {
        LivingEntity direct = event.getEntity();
        event.setAmount(event.getAmount() + dmg);

        Entity owner = arrow.getOwner();
        double x = arrow.getX(), y = arrow.getY(), z = arrow.getZ();
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                en -> en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            if (e == direct) continue;                     // 위에서 이미 얹었다
            if (e.distanceToSqr(x, y, z) > radius * radius) continue;
            com.laststardust.relics.LsDamage.hit(e, owner instanceof Player p
                ? level.damageSources().playerAttack(p) : level.damageSources().generic(), dmg, label);
        }
    }

    // ── 유물 화살은 무적 프레임을 무시한다 ──
    //
    // 바닐라는 피해를 받으면 20틱 무적이 걸린다. 시리우스는 평타가 8틱 간격(초당 2.5발),
    // 속사 중엔 4틱(초당 5발)이라 **화살 절반 이상이 무적 창에 들어가 hurt() 가 거부되고,
    // 거부된 화살은 표적에서 튕겨 나왔다.** 실측 DPS 가 평타 이론치에도 못 미쳤고,
    // 속사는 단일 표적에서 사실상 아무 효과가 없었다(간격을 줄여도 무적 주기에 묶인다).
    //
    // ※ 화살비(별빛 폭풍)가 이 때문에 과해지는 건 **여기서 상한을 걸어 막지 않는다.**
    //   인위적 상한은 "왜 화살이 맞았는데 안 들어가지"를 다시 만든다 — 튕기는 화살은
    //   플레이어가 눈으로 보고 이상하게 느꼈던 바로 그 증상이다.
    //   대신 폭풍 쪽에서 **조준을 흩고 범위를 넓혀** 단일 표적 명중률을 기하로 낮춘다
    //   (ArrowStormManager.AIM_SCATTER). 광역기는 광역으로 강하고 단일기로는 약해야 맞다.
    //
    // ※ 유물 화살에만 적용한다. 바닐라 활·다른 모드 화살은 그대로 둔다 —
    //   전역으로 풀면 몹의 원거리 공격까지 무적을 무시해 수성전이 불가능해진다.
    //
    // **2026-08-04: ProjectileImpactEvent → LivingDamageEvent.Post 로 옮겼다.**
    // 방향이 뒤집혔다. 예전엔 «맞기 직전에 무적을 0 으로 만들어» 이번 화살을 통과시켰다.
    // 지금은 «박히고 난 직후에 0 으로 되돌려» **다음** 화살을 통과시킨다.
    // 결과는 같다 — 연사 중 화살이 무적 창에 막히지 않는다. 첫 발만 창을 만나는데,
    // 그 창은 어차피 직전 공격이 만든 것이라 원래도 못 뚫었다.
    //
    // Post 여야 하는 이유: `hurt()` 는 피해를 넣으면서 `invulnerableTime = 20` 을 세운다.
    // 그 전에 0 으로 만들어봐야 곧바로 덮인다. 실제로 박힌 뒤에 걷어야 남는다.
    @SubscribeEvent
    public static void onRelicArrowPierce(LivingDamageEvent.Post event) {
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)) return;
        if (!arrow.getPersistentData().getBoolean(LsArrows.TAG)) return;
        event.getEntity().invulnerableTime = 0;
    }

    // 유성 사격의 폭발 연출. 판정은 위 `onArrowHitEffects` 가 한다.
    private static void meteorFx(ServerLevel level, double x, double y, double z, float radius) {
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 2, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 5, 0.4, 0.4, 0.4, 0.0);
        level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 55, radius * 0.5, radius * 0.5, radius * 0.5, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 45, radius * 0.5, radius * 0.5, radius * 0.5, 0.12);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 30, radius * 0.4, radius * 0.4, radius * 0.4, 0.3);
        // 금빛 별먼지
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.89f, 0.63f), 1.4f);
        level.sendParticles(gold, x, y, z, 40, radius * 0.5, radius * 0.5, radius * 0.5, 0.0);
        // 바깥으로 퍼지는 충격파 링
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24.0;
            double dx = Math.cos(a), dz = Math.sin(a);
            level.sendParticles(ParticleTypes.END_ROD, x + dx * radius, y, z + dz * radius,
                0, dx * 0.45, 0.02, dz * 0.45, 0.45);
        }
        // ── 사운드 (폭발 + 별빛 잔향) ──
        level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.3f, 1.0f);
        level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.6f);
    }

    // ── 수호의 성역(파나케이아 추가): 지대 안의 아군은 받는 피해 -20% ──
    @SubscribeEvent
    public static void onSanctuaryProtect(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Player p) || victim.level().isClientSide) return;
        if (!SanctuaryManager.isProtected(p)) return;
        event.setAmount(event.getAmount() * (1.0f - SanctuaryManager.DAMAGE_REDUCTION));
    }

    // ── 수호 반격(이지스 추가): 반격 태세 중 받는 피해 −40% + 공격자·주변 반사 ──
    @SubscribeEvent
    public static void onGuardianParry(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer guard)) return;
        if (guard.level().isClientSide) return;
        ItemStack held = guard.getMainHandItem();
        if (held.getItem() != LSRelics.GUARDIAN.get()) return;
        var tag = held.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        long left = tag.getLong("parryUntil") - guard.level().getGameTime();
        if (left <= 0 || left > 60) return; // 태세 아님(또는 시간 되감김)

        event.setAmount(event.getAmount() * 0.6f); // 받는 피해 −40% (환경 피해에도 적용 — 반격 '태세'다)

        if (!(guard.level() instanceof ServerLevel sl)) return;

        // ── 반사는 "때린 놈"이 있을 때만 (2026-07-27) ──
        // 예전엔 모든 피해에 반사가 나갔다. 용암·불처럼 틱마다 들어오는 환경 피해는 공격자가
        // 없으니 반사가 통째로 주변 AoE 로 새고, 이펙트와 소리가 매 틱 도배됐다.
        // 반격은 공격을 받아넘기는 기술이지 용암을 받아넘기는 기술이 아니다.
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker instanceof Player) return;

        // ── 연타 제한 ──
        // 공세에서 몹 여럿이 같은 틱에 때리면 각각이 광역 반사를 한 번씩 터뜨려
        // 피해도 이펙트도 몹 수만큼 곱해졌다. 0.5초에 한 번으로 묶는다.
        long now = sl.getGameTime();
        long lastParry = guard.getPersistentData().getLong("lsParryBurst");
        if (lastParry != 0 && now - lastParry < 10) return;
        guard.getPersistentData().putLong("lsParryBurst", now);

        float power = tag.getFloat("parryPower");
        if (power <= 0) power = 8.0f;
        // 공격자에게 강하게, 주변 잡몹에 약하게 반사
        double cx = guard.getX(), cy = guard.getY() + 1.0, cz = guard.getZ();
        com.laststardust.relics.LsDamage.hit(attacker, sl.damageSources().thorns(guard), power * 1.5f, "반격");
        AABB box = new AABB(cx - 3.5, cy - 2, cz - 3.5, cx + 3.5, cy + 2, cz + 3.5);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != guard && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            com.laststardust.relics.LsDamage.hit(e, sl.damageSources().thorns(guard), power, "반격");
            e.knockback(0.5, cx - e.getX(), cz - e.getZ());
        }
        sl.sendParticles(ParticleTypes.CRIT, cx, cy, cz, 20, 1.5, 0.8, 1.5, 0.2);
        sl.sendParticles(ParticleTypes.WAX_OFF, cx, cy, cz, 16, 1.6, 0.6, 1.6, 0.3);
        sl.playSound(null, guard.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2f, 0.9f);
    }

    // ── 방벽의 수호자: 근처(8칸)에 방패를 든 아군이 있으면 받는 피해 -5% ──
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Player) || victim.level().isClientSide) return;
        for (Player guard : victim.level().players()) {
            if (guard.getMainHandItem().getItem() != LSRelics.GUARDIAN.get()) continue;
            if (guard.distanceToSqr(victim) > 64.0) continue; // 8칸
            event.setAmount(event.getAmount() * 0.95f);
            return;
        }
    }

    public static boolean isRelic(ItemStack stack) {
        Item i = stack.getItem();
        return i == LSRelics.GUARDIAN.get() || i == LSRelics.HUNTER.get()
            || i == LSRelics.SAGE.get() || i == LSRelics.PIONEER.get()
            || i == LSRelics.GUNNER.get() || i == LSRelics.HEALER.get()
            || i == LSRelics.ASSASSIN.get() || i == LSRelics.LANCER.get();
    }

    // ※ 예전에 있던 "대상 최대 체력 비례 평타 보너스"는 제거했다.
    //   보스가 너무 빨리 녹는 문제는 보스 체력을 직접 조절하는 쪽(/bossdiff)이 훨씬 정확하고,
    //   보너스를 유지하려면 평타/스킬 구분(스윙 플래그·투사체 표식·유물별 초당 타수 표)이
    //   전부 필요해서 신규 스킬을 넣을 때마다 사고가 났다. DPS는 표에 적힌 값 그대로가 낫다.

    // ── 툴팁: 각성 단계와 다음 해금 안내 ──
    // 별 표시는 클라이언트에서 그려지고, star 값은 custom_data라 자동으로 동기화된다.
    @SubscribeEvent
    public static void onRelicTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!isRelic(stack)) return;
        int s = RelicSkills.star(stack);

        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < 5; i++) stars.append(i < s ? "✦" : "✧");
        int pct = Math.round((RelicSkills.ascension(stack) - 1.0f) * 100);
        event.getToolTip().add(Component.literal(
            "§6" + stars + " §e각성 " + s + "성" + (pct > 0 ? " §7(피해 +" + pct + "%)" : "")));

        // 다음 계단이 무엇인지 항상 보이게 한다 — 각성의 목표가 뚜렷해야 성장이 체감된다.
        // 다음 한 계단만 보여준다. 남은 계단을 전부 나열하면 4줄이 되어 툴팁이 스킬 설명을 밀어낸다.
        if (s < 2) {
            event.getToolTip().add(Component.literal("§8  2성 — 이동기 해금 §7(V)"));
        } else if (s < 3) {
            event.getToolTip().add(Component.literal("§8  3성 — 추가 스킬 해금 §7(C)"));
        } else if (s < 4) {
            event.getToolTip().add(Component.literal("§8  4성 — 궁극기 해금 §7(X)"));
        } else if (s < 5) {
            event.getToolTip().add(Component.literal("§8  5성 — 모든 스킬이 최대 위력에 이른다"));
        } else {
            event.getToolTip().add(Component.literal("§8  모든 힘이 깨어났다"));
        }
    }

    // ── 각성이 평타도 올린다 ──
    // 각성 배율(ASCENSION)은 원래 스킬 피해에만 붙었다. 그런데 평타가 전체 딜의 70%라
    // 스킬만 오르면 "무기를 강화했는데 안 세진다"가 된다. 근접 유물은 여기서 공격력 속성에
    // 각성 배율을 직접 얹는다(툴팁 수치도 같이 올라간다).
    // 원거리 유물은 공격력 속성이 없고 좌클릭 투사체가 평타라 각 코드에서 ascension()을 곱한다.
    private static final ResourceLocation BASE_ATK_ID = ResourceLocation.withDefaultNamespace("base_attack_damage");
    private static final ResourceLocation ASCENSION_ATK_ID =
        ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "ascension_attack");

    @SubscribeEvent
    public static void onRelicAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!isRelic(stack)) return;
        // 각성 × 전역 위력. 1성에도 전역 배율이 걸리므로 예전처럼 "1성은 건너뛴다"가 아니다 —
        // 1성 평타가 네더라이트 검보다 약했던 게 바로 그 건너뛰기 때문이었다.
        float mult = RelicSkills.power(stack);
        if (mult <= 1.0f) return; // 배율이 1이면 더할 게 없다

        // 우리가 등록한 기본 공격력만 대상으로 한다. 어픽스·젬으로 붙은 공격력까지 곱하면
        // 각성과 어픽스가 서로 곱해져 후반에 폭주한다.
        double base = 0;
        for (ItemAttributeModifiers.Entry e : event.getDefaultModifiers().modifiers()) {
            if (!e.attribute().equals(Attributes.ATTACK_DAMAGE)) continue;
            if (!e.modifier().id().equals(BASE_ATK_ID)) continue;
            base = e.modifier().amount();
        }
        if (base <= 0) return; // 활·지팡이·마총·힐지팡이

        double total = 1.0 + base; // 플레이어 기본 공격력 1.0 포함한 실제 표시 공격력
        event.addModifier(Attributes.ATTACK_DAMAGE,
            new AttributeModifier(ASCENSION_ATK_ID, total * (mult - 1.0), AttributeModifier.Operation.ADD_VALUE),
            EquipmentSlotGroup.MAINHAND);
    }


    // ── 스틱스(단검) 근접 피해 보정 ──
    //   배후의 일격(패시브)   : 뒤에서 치면 +20%
    //     ※ 원래 +35%였는데, "백어택도 크리도 없을 때 44는 너무 낮다"는 판단으로
    //        기본값을 올리고 보너스를 깎아 격차를 좁혔다(2026-07-26 밸런스).
    //   무저갱(궁극)          : 방어 무시를 피해 +30%로 근사 — 이벤트 단계에선 방어 계산에
    //                           직접 개입하기 어렵고, 잡몹은 방어도가 낮아 이 근사로 충분하다
    //   망각의 안개(추가)     : 은신이 풀린 뒤 첫 공격 2배
    // 세 배수는 서로 곱해진다(뒤에서 기습하면 크게 터진다).
    //
    // ── 상단이 목표(60)를 넘는 것은 의도된 것이다 ──
    // 이 배수들은 근접 평타에만 붙고(아래 getDirectEntity 검사), 바닐라 크리(×1.5)와도
    // 곱해진다. 그래서 백어택+크리는 평타 한 방을 최대 ×1.80까지 올린다.
    // 실측: 백어택만 54.2 DPS(60초, 5성, 2026-07-26). 여기서 크리까지 붙으면 61~73이다.
    // 다른 유물이 54 목표인데 스틱스만 상단이 높은 이유 —
    //   낙하 중에만 크리가 터지므로 크리 상시 유지가 불가능하고, 실사용 구간은 54~64다.
    //   스틱스는 숙련도로 보상받는 무기로 두기로 했다(사용자 결정).
    // ※ 상단을 조이려면 CriticalHitEvent.setDamageMultiplier() 로 스틱스만 크리 배수를
    //    낮추는 게 맞다. BACKSTAB_BONUS 를 깎으면 기본값 대비 백어택 이득만 사라진다.
    private static final float BACKSTAB_BONUS = 1.20f;
    private static final float ABYSS_BONUS = 1.30f;
    private static final float AMBUSH_BONUS = 2.0f;

    @SubscribeEvent
    public static void onStyxStrike(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (attacker.getMainHandItem().getItem() != LSRelics.ASSASSIN.get()) return;
        // 근접 타격만 (그림자 도약의 직접 타격 포함, 투사체는 제외)
        if (event.getSource().getDirectEntity() != attacker) return;

        float mult = 1.0f;
        boolean back = isBehind(attacker, victim);
        if (back) mult *= BACKSTAB_BONUS;
        if (AbyssManager.isActive(attacker)) mult *= ABYSS_BONUS;
        boolean ambush = StealthManager.consumeAmbush(attacker);
        if (ambush) mult *= AMBUSH_BONUS;

        if (mult == 1.0f) return;
        event.setAmount(event.getAmount() * mult);
        if (victim.level() instanceof ServerLevel sl) {
            int n = ambush ? 20 : (back ? 12 : 6);
            sl.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6,
                victim.getZ(), n, 0.3, 0.3, 0.3, 0.25);
            if (ambush) {
                sl.sendParticles(ParticleTypes.SONIC_BOOM, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5,
                    victim.getZ(), 1, 0, 0, 0, 0);
                sl.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.8f);
            }
        }
    }

    // 공격자가 피격자의 등 뒤에 있는가 — 피격자의 몸통 방향(yBodyRot)으로 판정한다.
    private static boolean isBehind(Player attacker, LivingEntity victim) {
        double yaw = Math.toRadians(victim.yBodyRot);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw); // 피격자가 바라보는 정면 방향
        double dx = attacker.getX() - victim.getX(), dz = attacker.getZ() - victim.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) return false;
        double dot = (fx * dx + fz * dz) / len; // 정면=+1, 등 뒤=-1
        return dot < -0.35; // 대략 정면 ±110° 밖 = 뒤
    }

    // ── 연쇄 살상(스틱스 패시브): 처치 시 3초간 이속·공속 +15% (최대 3중첩) ──
    private static final ResourceLocation FRENZY_SPD =
        ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "frenzy_speed");
    private static final ResourceLocation FRENZY_ATK =
        ResourceLocation.fromNamespaceAndPath(LSRelics.MODID, "frenzy_atkspeed");

    @SubscribeEvent
    public static void onChainKill(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (killer.getMainHandItem().getItem() != LSRelics.ASSASSIN.get()) return;
        bumpFrenzy(killer, Attributes.MOVEMENT_SPEED, FRENZY_SPD);
        bumpFrenzy(killer, Attributes.ATTACK_SPEED, FRENZY_ATK);
        if (killer.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SOUL, killer.getX(), killer.getY() + 1.0, killer.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        }
    }

    // 처치할 때마다 +15%씩, 최대 3중첩(+45%). 처치가 이어지면 계속 갱신, 끊기면 3초 뒤 소멸.
    private static void bumpFrenzy(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id) {
        var inst = p.getAttribute(attr);
        if (inst == null) return;
        double cur = 0;
        var existing = inst.getModifier(id);
        if (existing != null) cur = existing.amount();
        double next = Math.min(0.45, cur + 0.15);
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, next, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        ChainFrenzy.refresh(p, id, 60); // 3초 뒤 제거 예약
    }

    // ── 거인 살해자(도끼 패시브): 최대 체력이 높은 적에게 주는 피해 +15% ──
    // 레이드 보스·거대 몹 특화. 잡몹(최대 체력 40 미만)에는 적용 안 됨.
    private static final float GIANT_HP = 40.0f;
    private static final float GIANT_BONUS = 1.15f;

    @SubscribeEvent
    public static void onGiantSlayer(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        if (victim.getMaxHealth() < GIANT_HP) return;
        if (!(event.getSource().getEntity() instanceof Player attacker)) return;
        if (attacker.getMainHandItem().getItem() != LSRelics.PIONEER.get()) return;
        event.setAmount(event.getAmount() * GIANT_BONUS);
        if (victim.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6,
                victim.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
        }
    }

    // ── 바람의 발걸음: 활로 처치 시 이동속도 추가 상승 ──
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (player.getMainHandItem().getItem() != LSRelics.HUNTER.get()) return;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 0, false, true)); // +20% 4초
    }
}
