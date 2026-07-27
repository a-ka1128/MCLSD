package com.laststardust.relics.item;

import com.laststardust.relics.ArrowStormManager;
import com.laststardust.relics.BleedManager;
import com.laststardust.relics.BoltManager;
import com.laststardust.relics.LsDamage;
import com.laststardust.relics.ChargeManager;
import com.laststardust.relics.EclipseManager;
import com.laststardust.relics.FissureManager;
import com.laststardust.relics.GravityWellManager;
import com.laststardust.relics.BabylonManager;
import com.laststardust.relics.JavelinManager;
import com.laststardust.relics.SanctuaryManager;
import com.laststardust.relics.SoundScheduler;
import com.laststardust.relics.StealthManager;
import com.laststardust.relics.AbyssManager;
import com.laststardust.relics.SupernovaManager;
import com.laststardust.relics.TitanManager;
import com.laststardust.relics.TauntManager;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

import java.util.List;

// 유물 궁극기 공통 로직 + 스킬별 화려한 연출.
// 전방 스킬은 시전자 눈에서 목표점까지 "선(빔) 전체"에 피해 → 바로 앞 적도 맞음(점블랭크 문제 해결).
// 쿨다운은 아이템 쿨다운(핫바 표시). 모든 실제 효과·파티클은 서버에서만.
public final class RelicSkills {
    private RelicSkills() {}

    // 테마 색상 (packed RGB)
    private static final int GOLD = 0xFFE4A0;
    private static final int BLUE = 0x8FB6FF;
    private static final int VOID = 0x9A5CFF;
    private static final int DARK = 0x4A2C7A;

    // ─────────────────────────────── 스킬: 별지기의 지팡이 "소멸" ───────────────────────────────
    public static void annihilate(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdAnnihilate", "소멸", 120, 1)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        // 연출도 벽에서 멈춘다 — 판정(beamHurt)과 같은 사거리를 써야 눈과 실제가 어긋나지 않는다.
        Vec3 end = eye.add(look.scale(beamReach(sl, player, eye, look, 11.0)));

        ring(sl, eye.x, eye.y, eye.z, 0.7, 16, ParticleTypes.ENCHANT, 0.0);
        // 별똥별 빔 — 흰 END_ROD + 금/청 별먼지 + 폭죽 반짝임
        beamParticles(sl, eye, end, 0.35, ParticleTypes.END_ROD, 0.0);
        beamDust(sl, eye, end, 0.5, GOLD, 1.2f);
        beamDust(sl, eye, end, 0.5, BLUE, 1.0f);
        beamParticles(sl, eye, end, 0.8, ParticleTypes.FIREWORK, 0.02);
        // 착탄: 폭발 섬광 + 별먼지 구체 + 용의 숨결 잔류 + 이중 링
        double r = 5.0;
        sl.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 2, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.FIREWORK, end.x, end.y, end.z, 60, r * 0.4, r * 0.4, r * 0.4, 0.15);
        sl.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 90, r * 0.45, r * 0.45, r * 0.45, 0.05);
        sl.sendParticles(ParticleTypes.DRAGON_BREATH, end.x, end.y, end.z, 40, r * 0.4, 0.2, r * 0.4, 0.0);
        dustBurst(sl, end, r * 0.5, 50, GOLD, 1.4f);
        shockRing(sl, end.x, end.y - 0.5, end.z, r, 40, ParticleTypes.END_ROD, 0.35);
        shockRing(sl, end.x, end.y - 0.5, end.z, r * 1.4, 46, ParticleTypes.ELECTRIC_SPARK, 0.5);

        beamHurt(sl, player, eye, look, 11.0, r, dmg(stack, 14.0f), 0.3, false);

        // 6초 쿨로 자주 쓰는 스킬이라 볼륨을 낮게 유지 (귀 아픔 방지)
        play(level, player, SoundEvents.WITHER_SHOOT, 0.45f, 1.5f);
        play(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 0.8f);
        play(level, player, SoundEvents.ILLUSIONER_CAST_SPELL, 0.5f, 1.4f);
    }

    // ─────────────────────────────── 지팡이 좌클릭 마법 평타 "별빛 탄" ───────────────────────────────
    // 실제로 날아가는 투사체(BoltManager). 히트스캔 빔은 경로 전체를 그려 시야를 가려서 교체.
    public static void magicBolt(ServerLevel level, Player player, ItemStack stack) {
        if (!shotReady(level, stack, 8)) return; // 초당 2.5발
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        if (player instanceof ServerPlayer sp) {
            // 4.56 × 초당 2.5발 = DPS 11.4 (원거리 하이브리드)
            BoltManager.fire(level, sp, eye.add(look.scale(0.5)), look, dmg(stack, 5.32f), 22);
        }
        // 총구 섬광만 짧게
        level.sendParticles(ParticleTypes.END_ROD,
            eye.x + look.x * 0.7, eye.y + look.y * 0.7, eye.z + look.z * 0.7, 5, 0.05, 0.05, 0.05, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 1.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f, 2.0f);
    }

    // ─────────────────────────────── 스킬: 시리우스 "유성 사격" (추가·3성) ───────────────────────────────
    // C 키. 화살 3발 → 착탄 시 각각 폭발. 화살·폭발 모두 크리 가능. 쿨 10초.
    public static void meteorShot(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdMeteor", "유성 사격", 200, 3)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);

        int n = 3;
        // ── 확산각과 거리의 관계 (2026-07-26) ──
        // 인접 화살 간격 = 거리 × tan(각). 몹 폭이 0.6칸이라 간격이 0.4칸을 넘으면 곁 화살이 빗나간다.
        //   6도 → 3발 다 맞는 거리 3.8m 까지   (실측: 근접 56.6 vs 15m 45.4 — 20% 절벽)
        //   4도 → 5.7m 까지
        //   2도 → 11.4m 까지                   ← 지금
        // 2도를 골랐다. 실전 사거리(10~20m) 안에서 유성이 거리와 무관하게 3발 다 꽂히므로
        // "붙어야 세다"는 왜곡이 사라진다. 사냥꾼이 거리를 두는 정석 플레이가 손해를 보지 않는 게
        // 이 유물의 정체성에 맞고, 밸런스도 배율 하나로 맞출 수 있게 된다.
        double spreadDeg = 2.0;
        for (int i = 0; i < n; i++) {
            double offRad = Math.toRadians((i - (n - 1) / 2.0) * spreadDeg);
            Vec3 dir = rotateYaw(look, offRad);
            Arrow arrow = com.laststardust.relics.LsArrows.create(sl, player, stack);
            arrow.setPos(eye.x, eye.y - 0.1, eye.z);
            arrow.shoot(dir.x, dir.y, dir.z, 3.2f, 0.4f);
            arrow.setBaseDamage(1.32); // 명중 ~4.0
            if (sl.getRandom().nextFloat() < 0.6f) arrow.setCritArrow(true); // 화살 크리
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.getPersistentData().putFloat("lsExplode", dmg(stack, 6.6f));  // 폭발 피해
            arrow.getPersistentData().putFloat("lsExplodeR", 2.5f); // 폭발 반경
            sl.sendParticles(ParticleTypes.END_ROD, arrow.getX(), arrow.getY(), arrow.getZ(), 6, 0.05, 0.05, 0.05, 0.02);
            sl.addFreshEntity(arrow);
        }
        sl.sendParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
        dustBurst(sl, eye, 0.5, 25, GOLD, 1.2f);
        play(level, player, SoundEvents.ARROW_SHOOT, 1.3f, 0.8f);
        play(level, player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.9f, 1.3f);
    }

    // ─────────────────────────────── 스킬: 시리우스 "속사" (기본) ───────────────────────────────
    // 우클릭. 몇 초간 좌클릭 연사 속도 급상승(폭딜 윈도우). 쿨 12초.
    public static void rapidFire(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdHaste", "속사", 240, 1)) return;
        long now = sl.getGameTime();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putLong("hasteUntil", now + 80); // 4초
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        sl.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getEyeY(), player.getZ(), 30, 0.4, 0.4, 0.4, 0.12);
        dustBurst(sl, player.getEyePosition(), 0.6, 25, GOLD, 1.2f);
        ring(sl, player.getX(), player.getY() + 0.1, player.getZ(), 1.0, 20, ParticleTypes.ELECTRIC_SPARK, 0.2);
        play(level, player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.7f);
        play(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.9f);
    }

    // 좌클릭 연사가 속사(가속) 상태인가?
    public static boolean isHasted(ServerLevel level, ItemStack stack) {
        long now = level.getGameTime();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return now < tag.getLong("hasteUntil");
    }

    // ─────────────────────────────── 스킬: 별을 벼린 도끼 "균열 붕괴" ───────────────────────────────
    public static void riftCollapse(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdRift", "균열 붕괴", 160, 1)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(beamReach(sl, player, eye, look, 6.0)));

        ring(sl, eye.x, eye.y, eye.z, 0.8, 14, ParticleTypes.SCULK_SOUL, 0.0);
        // 공허 균열 빔 — 역차원문 + 스컬크 + 보라 먼지
        beamParticles(sl, eye, end, 0.3, ParticleTypes.REVERSE_PORTAL, 0.02);
        beamParticles(sl, eye, end, 0.5, ParticleTypes.SCULK_CHARGE_POP, 0.0);
        beamDust(sl, eye, end, 0.4, VOID, 1.4f);
        beamDust(sl, eye, end, 0.6, DARK, 1.1f);
        // 착탄: 소닉붐 + 공허 붕괴 + 바닥 균열 링
        double r = 4.8;
        sl.sendParticles(ParticleTypes.SONIC_BOOM, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, end.x, end.y, end.z, 80, r * 0.4, r * 0.4, r * 0.4, 0.25);
        sl.sendParticles(ParticleTypes.SCULK_SOUL, end.x, end.y, end.z, 40, r * 0.4, 0.4, r * 0.4, 0.02);
        sl.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 6, r * 0.3, r * 0.3, r * 0.3, 0.0);
        dustBurst(sl, end, r * 0.5, 60, VOID, 1.6f);
        shockRing(sl, end.x, end.y - 0.6, end.z, r, 42, ParticleTypes.SCULK_CHARGE_POP, 0.3);
        shockRing(sl, end.x, end.y - 0.6, end.z, r * 1.5, 50, ParticleTypes.REVERSE_PORTAL, 0.55);

        beamHurt(sl, player, eye, look, 6.0, r, dmg(stack, 9.6f), 0.6, true);

        play(level, player, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.3f, 0.8f);
        play(level, player, SoundEvents.WARDEN_SONIC_BOOM, 0.9f, 1.2f);
        play(level, player, SoundEvents.GLASS_BREAK, 1.0f, 0.5f);
    }

    // ─────────────────────────────── 스킬: 타이탄 브레이커 "대지 쪼개기" (추가·3성) ───────────────────────────────
    // C 키. 전방 부채꼴을 내려찍어 즉발 피해 + 그 자리에 5초간 균열 지대(초당 피해·둔화). 쿨 12초.
    public static void earthSplitter(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdSplit", "대지 쪼개기", 240, 3)) return;
        Vec3 look = player.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 origin = new Vec3(player.getX(), player.getY(), player.getZ());
        double length = 7.0, halfAngle = 45.0;
        double cosHalf = Math.cos(Math.toRadians(halfAngle));

        // 즉발 피해 (부채꼴 안)
        AABB box = new AABB(origin.x - length, origin.y - 3, origin.z - length,
                            origin.x + length, origin.y + 3, origin.z + length);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != player && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            Vec3 to = new Vec3(e.getX() - origin.x, 0, e.getZ() - origin.z);
            double dist = to.length();
            if (dist > length) continue;
            if (dist > 0.01 && to.normalize().dot(flat) < cosHalf) continue;
            LsDamage.hit(e, relicSource(sl, player), dmg(stack, 7.68f));
            e.knockback(0.4, origin.x - e.getX(), origin.z - e.getZ());
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true));
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(),
                12, 0.3, 0.3, 0.3, 0.1);
        }
        // 지속 균열 지대 (5초, 초당 2뎀) — 총합이 균열 붕괴와 비슷하게
        if (player instanceof ServerPlayer sp) {
            FissureManager.start(sl, sp, origin, flat, length, halfAngle, dmgTick(stack, 1.92f), 100);
        }

        // ── 연출: 앞으로 뻗어나가는 균열 ──
        for (int step = 1; step <= 14; step++) {
            double d = length * step / 14.0;
            double spread = Math.tan(Math.toRadians(halfAngle)) * d;
            for (int k = 0; k < 5; k++) {
                double off = (sl.getRandom().nextDouble() - 0.5) * 2 * spread;
                Vec3 side = new Vec3(-flat.z, 0, flat.x).scale(off);
                double px = origin.x + flat.x * d + side.x;
                double pz = origin.z + flat.z * d + side.z;
                sl.sendParticles(ParticleTypes.SCULK_CHARGE_POP, px, origin.y + 0.15, pz, 1, 0.05, 0.05, 0.05, 0.0);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, px, origin.y + 0.1, pz, 0, 0, 0.6, 0, 0.8);
            }
        }
        dustBurst(sl, origin.add(flat.scale(2.0)).add(0, 0.4, 0), 2.0, 60, VOID, 1.6f);
        shockRing(sl, origin.x, origin.y + 0.1, origin.z, 3.0, 36, ParticleTypes.SCULK_SOUL, 0.5);
        sl.sendParticles(ParticleTypes.EXPLOSION, origin.x + flat.x * 2, origin.y + 0.3, origin.z + flat.z * 2,
            4, 0.6, 0.2, 0.6, 0.0);

        // 볼륨 낮게 — 스컬크 절규는 길고 날카로워서 제거
        play(level, player, SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
        play(level, player, SoundEvents.SCULK_BLOCK_BREAK, 0.7f, 0.5f);
        play(level, player, SoundEvents.GENERIC_EXPLODE.value(), 0.45f, 0.6f);
    }

    // ─────────────────────────────── 궁극: 타이탄 브레이커 "타이탄 강림" (궁극·4성) ───────────────────────────────
    // X 키. 12초간 거대화 — 공격력·사거리·넉백·체력 증가, 넉백 면역. 쿨 60초.
    public static void titanAscension(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!ready(level, player, stack, "cdTitan", "타이탄 강림", 1200, 4)) return;
        TitanManager.start(level, player, 200); // 10초 (12→10 하향, TitanManager 주석 참고)
    }

    // ─────────────────────────────── 스킬: 별빛의 방벽 "수호의 파동" ───────────────────────────────
    public static void guardPulse(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdPulse", "수호의 파동", 160, 1)) return;
        double cx = player.getX(), cy = player.getY(), cz = player.getZ();
        {
            double r = 6.0;
            // 중앙 임팩트
            sl.sendParticles(ParticleTypes.SONIC_BOOM, cx, cy + 1.0, cz, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.FLASH, cx, cy + 1.0, cz, 1, 0, 0, 0, 0);
            // 금빛 보호 돔
            dome(sl, cx, cy, cz, r, 120, ParticleTypes.END_ROD);
            dustBurst(sl, new Vec3(cx, cy + 1.0, cz), r * 0.7, 70, GOLD, 1.5f);
            // 3중 충격파 링 (확장 연출)
            shockRing(sl, cx, cy + 0.1, cz, r * 0.5, 30, ParticleTypes.END_ROD, 0.3);
            shockRing(sl, cx, cy + 0.1, cz, r * 0.85, 44, ParticleTypes.ELECTRIC_SPARK, 0.45);
            shockRing(sl, cx, cy + 0.1, cz, r * 1.2, 56, ParticleTypes.WAX_OFF, 0.6);
            // 솟아오르는 수호의 빛
            for (int i = 0; i < 30; i++) {
                double a = Math.PI * 2 * i / 30.0;
                double px = cx + Math.cos(a) * r * 0.9, pz = cz + Math.sin(a) * r * 0.9;
                sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, px, cy + 0.2, pz, 0, 0, 0.6, 0, 0.4);
            }
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, cx, cy + 1.0, cz, 40, r * 0.4, 0.6, r * 0.4, 0.3);

            hurtAround(sl, player, cx, cy + 1, cz, r, dmg(stack, 6.6f), 0.2); // 딜 낮게·넉백 최소(몹 붙잡아두기)
            // 도발 — 8칸 내 적들이 4초간 시전자를 노림.
            // (쿨 8초라 8초로 두면 어그로가 영구 고정돼 다른 유물이 위협을 못 느낌)
            TauntManager.taunt(sl, player, 8.0, 80);
        }
        // 탱커 생존 버프: 저항II(평타 40%↓) 6초 + 흡수III(6칸 보호막) 6초
        // 둘 다 쿨(8초)보다 짧게 둔다 — 지속이 쿨보다 길면 버프가 상시 유지돼 스킬을 쓰는 의미가 없어진다.
        // (흡수는 맞으면 먼저 깎이지만, 안 맞고 있으면 그대로 겹쳐 쌓이므로 지속도 함께 잘라야 한다.)
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, 2, false, true));

        // 사운드 — 짐승 울부짖음 대신 금속·성물 계열로 통일
        play(level, player, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.4f);
        play(level, player, SoundEvents.SHIELD_BLOCK, 1.3f, 0.6f);
        play(level, player, SoundEvents.ANVIL_LAND, 0.7f, 1.6f);
        play(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.8f);
    }

    // ─────────────────────────────── 이동기: 솔라리 "산탄" (이동·V·2성) ───────────────────────────────
    // C 키. 전방 원뿔에 산탄 → 강한 넉백 + 시전자는 뒤로 도약. 쿨 11초.
    //
    // 마총은 조준 중 이동속도가 −80%라 근접에 물리면 빠져나올 방법이 없다. 이 스킬이 그 해답이라
    // "딜"보다 "적을 밀어내고 나는 빠진다"가 본체다. 그래서 사거리를 8칸으로 묶고,
    // 거리에 따라 피해가 줄게 했다 — 붙어야 세다.
    private static final double BUCK_RANGE = 8.0;
    private static final double BUCK_HALF_ANGLE = 30.0;

    // ─────────────────────────────── 궁극(연사 모드): 솔라리스 "탄막 집중" ───────────────────────────────
    // 남은 연사탄을 전부 태워 눈앞 원뿔에 쏟아붓는다. 탄 1발당 피해가 붙는다.
    //
    // ── 왜 일식과 따로 두는가 ──
    // 쿨은 일식과 **공유한다**(cdEclipse). 궁극기 충전은 하나뿐이고 형태만 갈린다.
    // 쿨이 따로면 "연사 켜고 궁 두 번"이 항상 정답이 되어 선택이 사라진다.
    //   일식      = 60칸 한 줄 관통 (장거리)
    //   탄막 집중 = 16칸 원뿔 광역 (근중거리)
    // 역할이 갈리므로 "지금 어느 쪽이 필요한가"가 판단으로 남는다.
    //
    // ── 대가 ──
    // 남은 탄을 전부 먹고 연사 모드가 끝난다(호출부 SolarMusket.ultimate 가 처리).
    // 그래서 공짜 추가딜이 아니라 "지금 터뜨릴까, 계속 쏠까"의 선택이 된다.
    // 재장전 직후(30발)에 쓰면 최대, 다 쏘고 쓰면 헛방이다.
    //
    // 발당 6.5 의 근거: 탄 1발을 연사로 쏘면 4.5 다. 즉시·광역으로 터뜨리는 값이
    // 계속 쏘는 것보다 1.44배 — 타이밍을 맞춘 보상이다.
    private static final double BARRAGE_RANGE = 16.0;
    private static final double BARRAGE_HALF_ANGLE = 25.0;
    // 6.5 -> 5.0 (2026-07-27). 연사 중 남은 탄을 즉시 피해로 바꾸는 궁이라, 6.5 에서는
    // 30발 = 585 가 한 번에 들어가 60초 실측의 19% 를 혼자 먹었다.
    // 발당으로 보면 연사(3.4)의 1.47배 — 5.0 에서도 "쏘느니 누르는" 이득은 그대로다.
    // 이 비율이 1.0 밑으로 내려가면 궁이 함정이 되므로 더 내릴 때는 연사와 같이 봐야 한다.
    private static final float BARRAGE_PER_AMMO = 5.0f;

    public static boolean solarBarrage(ServerLevel sl, ServerPlayer player, ItemStack stack, int ammo) {
        if (!ready(sl, player, stack, "cdEclipse", "탄막 집중", 1200, 4)) return false;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        float base = dmg(stack, BARRAGE_PER_AMMO * ammo);
        double cosLimit = Math.cos(Math.toRadians(BARRAGE_HALF_ANGLE));

        AABB box = new AABB(eye.x - BARRAGE_RANGE, eye.y - BARRAGE_RANGE, eye.z - BARRAGE_RANGE,
                            eye.x + BARRAGE_RANGE, eye.y + BARRAGE_RANGE, eye.z + BARRAGE_RANGE);
        int hit = 0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != player && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double dist = to.length();
            if (dist > BARRAGE_RANGE || dist < 1.0E-4) continue;
            if (to.normalize().dot(look) < cosLimit) continue;
            // 산탄(-60%)보다 완만하게 — 이쪽은 "집중 사격"이라 거리를 덜 탄다
            float falloff = (float) (1.0 - (dist / BARRAGE_RANGE) * 0.3);
            LsDamage.hit(e, relicSource(sl, player), base * falloff, "탄막");
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                18, 0.35, 0.35, 0.35, 0.12);
            hit++;
        }

        // ── 연출: 원뿔을 채우는 예광탄 다발 ──
        for (int i = 0; i < 90; i++) {
            double yaw = Math.toRadians((sl.getRandom().nextDouble() * 2 - 1) * BARRAGE_HALF_ANGLE);
            Vec3 dir = rotateYaw(look, yaw)
                .add(0, (sl.getRandom().nextDouble() - 0.5) * 0.42, 0).normalize();
            double d = 1.0 + sl.getRandom().nextDouble() * (BARRAGE_RANGE - 1.0);
            Vec3 at = eye.add(dir.scale(d));
            sl.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (i % 3 == 0) sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.01);
        }
        Vec3 muzzle = eye.add(look.scale(1.2));
        sl.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 3, 0, 0, 0, 0);
        dustBurst(sl, muzzle, 0.8, 30, GOLD, 1.6f);
        play(sl, player, SoundEvents.FIREWORK_ROCKET_BLAST, 1.4f, 0.7f);
        play(sl, player, SoundEvents.BLAZE_SHOOT, 1.2f, 0.6f);
        player.displayClientMessage(Component.literal(
            "§6☀ §c탄막 집중 §7— 탄 " + ammo + "발 · 명중 " + hit), true);
        return true;
    }

    public static void buckshot(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdBuckshot", "산탄", 220, 2)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        float base = dmg(stack, 16.56f);
        double cosLimit = Math.cos(Math.toRadians(BUCK_HALF_ANGLE));

        AABB box = new AABB(eye.x - BUCK_RANGE, eye.y - BUCK_RANGE, eye.z - BUCK_RANGE,
                            eye.x + BUCK_RANGE, eye.y + BUCK_RANGE, eye.z + BUCK_RANGE);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != player && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double dist = to.length();
            if (dist > BUCK_RANGE || dist < 1.0E-4) continue;
            if (to.normalize().dot(look) < cosLimit) continue; // 원뿔 밖

            // 점블랭크 100% → 최대 사거리 40%
            float falloff = (float) (1.0 - (dist / BUCK_RANGE) * 0.6);
            LsDamage.hit(e, relicSource(sl, player), base * falloff, "산탄");
            e.knockback(1.5, player.getX() - e.getX(), player.getZ() - e.getZ()); // 평타(0.4)의 약 4배
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                12, 0.3, 0.3, 0.3, 0.1);
        }

        // 시전자는 반대로 뒤로 도약 (반동)
        player.setDeltaMovement(-look.x * 0.9, 0.42, -look.z * 0.9);
        player.hurtMarked = true; // 클라에 속도 동기화
        player.resetFallDistance(); // 도약 착지로 낙하 피해를 입지 않게

        // ── 연출: 원뿔로 퍼지는 산탄 궤적 + 총구 화염 ──
        for (int i = 0; i < 40; i++) {
            double yaw = Math.toRadians((sl.getRandom().nextDouble() * 2 - 1) * BUCK_HALF_ANGLE);
            Vec3 dir = rotateYaw(look, yaw).add(0, (sl.getRandom().nextDouble() - 0.5) * 0.5, 0).normalize();
            double d = 1.0 + sl.getRandom().nextDouble() * (BUCK_RANGE - 1.0);
            Vec3 at = eye.add(dir.scale(d));
            sl.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        Vec3 muzzle = eye.add(look.scale(1.0));
        sl.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 2, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 25, 0.3, 0.3, 0.3, 0.05);
        dustBurst(sl, muzzle, 1.2, 30, GOLD, 1.4f);
        play(level, player, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 1.2f);
        play(level, player, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 0.8f);
        SoundScheduler.at(sl, muzzle, SoundEvents.FIRE_EXTINGUISH, 0.8f, 1.4f, 3);
    }

    // ─────────────────────────────── 스킬: 파나케이아 "심판의 빛" (기본·1성) ───────────────────────────────
    // 우클릭. 전방 직선에 신성 피해 → 그 피해의 80%를 가장 위태로운 아군에게 회복. 쿨 8초.
    //
    // 힐러의 최대 약점은 "아무도 안 다쳤을 때 할 일이 없다"는 것이다. 공격이 곧 회복이면
    // 항상 쓸모가 있고, 딜을 하는 동안에도 파티에 기여하게 된다.
    private static final float JUDGE_DMG = 12.0f;
    private static final float JUDGE_HEAL_RATIO = 0.8f;

    public static void judgmentLight(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdJudge", "심판의 빛", 160, 1)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double reach = beamReach(sl, player, eye, look, 12.0);   // 벽·구조물에서 멈춘다
        Vec3 end = eye.add(look.scale(reach));

        int hit = countBeamTargets(sl, player, eye, look, reach, 1.5);
        beamHurt(sl, player, eye, look, reach, 1.5, dmg(stack, JUDGE_DMG), 0.2, false);

        // 회복량은 적중 수와 무관하게 고정 — 여러 마리를 꿰뚫었다고 힐이 폭주하면 안 된다.
        if (hit > 0) {
            Player target = weakestAlly(sl, player, 16.0);
            if (target != null) {
                float amount = JUDGE_DMG * JUDGE_HEAL_RATIO * healScale(stack);
                target.heal(amount);
                com.laststardust.relics.ThreatManager.addHealThreat(sl, player, amount);
                Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
                sl.sendParticles(ParticleTypes.HEART, to.x, to.y, to.z, 3, 0.3, 0.3, 0.3, 0.0);
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, to.x, to.y, to.z, 10, 0.4, 0.5, 0.4, 0.0);
                playAt(sl, to, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
            }
        }

        // ── 연출: 하얀 심판의 빛줄기 ──
        beamParticles(sl, eye, end, 0.3, ParticleTypes.END_ROD, 0.0);
        beamDust(sl, eye, end, 0.45, 0xDCFFE0, 1.2f);
        sl.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        shockRing(sl, end.x, end.y, end.z, 1.6, 20, ParticleTypes.END_ROD, 0.25);
        play(level, player, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
        play(level, player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 1.4f);
        SoundScheduler.at(sl, end, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.7f, 3);
        SoundScheduler.at(sl, end, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.4f, 7);
    }

    // ─────────────────────────────── 스킬: 파나케이아 "수호의 성역" (추가·3성) ───────────────────────────────
    // C 키. 발밑에 8초 지대 — 초당 1.5 회복 + 받는 피해 20% 경감. 쿨 20초.
    public static void sanctuary(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdSanctuary", "수호의 성역", 400, 3)) return;
        Vec3 center = new Vec3(player.getX(), player.getY(), player.getZ());
        // 회복 + **지대 안 적 연소**. 힐 우선 설계 때문에 힐러의 평타가 파티전에서 거의 안 나가서,
        // 딜 기여를 이쪽으로 옮겼다 (SanctuaryManager.burn 주석 참고)
        SanctuaryManager.start(sl, center, 4.0, 1.5f * healScale(stack),
            dmgTick(stack, 6.0f), player instanceof ServerPlayer sp2 ? sp2 : null, 160);

        dustBurst(sl, center.add(0, 1, 0), 2.0, 60, 0xDCFFE0, 1.5f);
        dome(sl, center.x, center.y, center.z, 4.0, 90, ParticleTypes.END_ROD);
        shockRing(sl, center.x, center.y + 0.1, center.z, 4.0, 40, ParticleTypes.HAPPY_VILLAGER, 0.2);
        sl.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1.0, center.z, 1, 0, 0, 0, 0);
        play(level, player, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.5f);
        play(level, player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
        SoundScheduler.at(sl, center, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.6f, 4);
    }

    // ─────────────────────────────── 궁극: 파나케이아 "소생" (궁극·4성) ───────────────────────────────
    // X 키. 반경 20 내에서 죽은 아군을 사망 지점에서 되살린다(최대 2명). 쿨 90초.
    //
    // 8종 통틀어 유일하게 죽음을 되돌리는 스킬. 3~6인 co-op에서 한 명이 죽으면 리스폰 지점에서
    // 다시 뛰어오는 시간이 길어 사실상 그 전투에서 이탈하는데, 이걸 되돌리는 건 어떤 딜 궁극보다 크다.
    private static final int REVIVE_MAX = 2;
    private static final double REVIVE_RANGE = 20.0;

    // 소생 마무리 — 쓰러진 자리로 옮기고 절반 체력으로 세운다.
    //
    // 두 경로가 공유한다: ①이미 리스폰해 있던 사람(즉시) ②사망 화면에 있다가 본인이 리스폰을
    // 누른 사람(ReviveManager.onRespawn). 한 곳에 모아둬야 두 경로가 서로 다른 상태로 갈리지 않는다.
    public static void finishRevive(ServerLevel level, ServerPlayer revived, Vec3 where) {
        if (!revived.isAlive()) return;
        revived.teleportTo(level, where.x, where.y, where.z, revived.getYRot(), revived.getXRot());
        revived.setHealth(revived.getMaxHealth() * 0.5f); // 절반 체력으로 일어난다
        // 일어나자마자 다시 죽지 않도록 짧은 보호
        revived.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 2, false, true));
        revived.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1, false, true));
        revived.setInvulnerable(false);
        // 되살아난 사람에게 죽음의 대가(ls_revive.js 의 '별빛 쇠약')까지 씌우면
        // 소생이 그냥 '순간이동'이 된다. 이 궁극기가 지운다.
        var srv = level.getServer();
        String n = revived.getName().getString();
        srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(),
            "attribute " + n + " minecraft:generic.max_health modifier remove last_stardust:frailty_health");
        srv.getCommands().performPrefixedCommand(srv.createCommandSourceStack(),
            "attribute " + n + " minecraft:generic.attack_damage modifier remove last_stardust:frailty_attack");
        // 떨어뜨린 자기 아이템 중 아직 바닥에 남은 것을 걷어 돌려준다 (ReviveManager 주석 참고)
        int back = com.laststardust.relics.ReviveManager.reclaimDrops(revived);
        // 그리고 시체를 치운다 — 아이템을 돌려줬는데 시체에도 같은 게 남아 있으면 복사가 된다.
        // 죽은 자리와 되살아난 자리가 같으므로 where 주변만 보면 되고, 반경은 넉넉히 8칸.
        com.laststardust.relics.ReviveManager.removeCorpses(level, revived.getUUID(), where, 8.0);
        if (back > 0) {
            revived.displayClientMessage(Component.literal(
                "§7소지품 §e" + back + "§7묶음을 되찾았다."), true);
        }
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
            where.x, where.y + 1, where.z, 60, 0.5, 0.8, 0.5, 0.3);
        playAt(level, where, SoundEvents.TOTEM_USE, 1.4f, 1.1f);
    }

    public static void resurrect(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!ready(level, player, stack, "cdRevive", "소생", 1800, 4)) return;

        // 최근 30초 안에 이 근처에서 쓰러진 아군. 사망 화면에 그대로 떠 있든, 벌써 리스폰을
        // 눌러 스폰 지점에 가 있든 둘 다 대상이다 — 예전엔 전자만 찾아서 사실상 못 쓰는
        // 스킬이었다(죽으면 다들 리스폰을 바로 눌러버린다).
        List<ServerPlayer> fallen = com.laststardust.relics.ReviveManager
            .candidates(level, player, REVIVE_RANGE, REVIVE_MAX);

        // ※ 연출은 대상이 있을 때만. 예전엔 이 위에서 먼저 터뜨렸는데, 되살릴 사람이 없어도
        //   거대한 빛기둥과 토템 소리가 나서 **성공한 줄 알고 착각하게 만들었다.**
        //   쿨도 돌려주는 마당에 연출까지 나가면 "썼는데 아무 일도 안 났다"로만 남는다.
        if (fallen.isEmpty()) {
            // 헛방이면 90초 쿨을 돌려준다 — 되살릴 사람이 없는데 쿨만 날리면 두 번 손해다.
            clearCooldown(stack, "cdRevive");
            com.laststardust.relics.CooldownDisplay.yieldFor(player, 40);
            player.displayClientMessage(Component.literal("§7최근 30초 안에 이 근처에서 쓰러진 아군이 없다."), true);
            play(level, player, SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.7f);   // 실패는 실패답게
            return;
        }

        Vec3 c = player.position();
        dustBurst(level, c.add(0, 1.2, 0), 2.5, 90, 0xDCFFE0, 2.0f);
        dome(level, c.x, c.y, c.z, 6.0, 140, ParticleTypes.END_ROD);
        play(level, player, SoundEvents.TOTEM_USE, 1.6f, 0.9f);
        play(level, player, SoundEvents.BEACON_POWER_SELECT, 1.5f, 0.7f);
        SoundScheduler.at(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6f, 0.6f, 4);

        for (ServerPlayer dead : fallen) {
            var rec = com.laststardust.relics.ReviveManager.record(dead.getUUID());
            final Vec3 where = rec != null ? rec.pos : dead.position(); // 쓰러진 그 자리
            com.laststardust.relics.ReviveManager.consume(dead.getUUID()); // 같은 죽음으로 두 번 못 살린다

            if (dead.isDeadOrDying()) {
                // 아직 사망 화면 — **서버가 대신 리스폰시키지 않는다.**
                // 예약만 걸고, 본인이 리스폰을 누르면 그때 쓰러진 자리로 끌어온다.
                // (강제 리스폰은 클라를 사망 상태에 남겨 "되살아났는데 아무에게도 안 보이는"
                //  동기화 파손을 만들었다 — ReviveManager.reserve 주석 참고)
                com.laststardust.relics.ReviveManager.reserve(dead.getUUID(), where, level.getGameTime());
                dead.sendSystemMessage(Component.literal(
                    "§d✦ §f" + player.getName().getString() + "§7이(가) 당신을 되살리려 한다."));
                dead.sendSystemMessage(Component.literal(
                    "§e   리스폰을 누르면 쓰러진 자리에서 일어난다."));
            } else {
                // 이미 리스폰을 눌러 스폰 지점에 서 있다 — 쓰러진 자리로 되돌리기만 하면 된다
                com.laststardust.relics.ReviveManager.later(15, () -> finishRevive(level, dead, where));
                dead.displayClientMessage(Component.literal(
                    "§a✦ " + player.getName().getString() + "§7이(가) 당신을 되살렸다."), false);
            }

            // 쓰러진 자리 연출은 두 경우 모두 — "여기서 일어난다"는 표시가 된다
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, where.x, where.y + 1, where.z, 80, 0.5, 0.8, 0.5, 0.3);
            level.sendParticles(ParticleTypes.FLASH, where.x, where.y + 1, where.z, 3, 0, 0, 0, 0);
            playAt(level, where, SoundEvents.TOTEM_USE, 1.8f, 1.0f);
        }
        com.laststardust.relics.CooldownDisplay.yieldFor(player, 40);
        player.displayClientMessage(Component.literal("§a✦ 아군 " + fallen.size() + "명을 되살렸다."), true);
    }

    // ─────────────────────────────── 스킬: 게볼그 "투창" (기본·1성) ───────────────────────────────
    // R 키. 명중 + 출혈, 최대 거리에서
    // 되돌아오며 다시 벤다. 쿨 8초. 기본 슬롯이라 상시 원거리 견제 무브다.
    public static void javelinThrow(ServerLevel sl, ServerPlayer player, ItemStack stack, float charge) {
        if (!ready(sl, player, stack, "cdJavelin", "투창", 160, 1)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        float power = 1.0f + 0.5f * charge; // 데미지 배수 1.0~1.5
        // 눈이 아니라 오른손 위치에서 창이 나간다 (손에서 던지는 느낌)
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 hand = eye.add(look.scale(0.6)).add(right.scale(0.35)).add(0, -0.3, 0);
        JavelinManager.throwSpear(sl, player, hand, look,
            dmg(stack, 8.9f) * power, dmgTick(stack, 1.78f), 60, charge); // 출혈 초당 2 × 3초

        dustBurst(sl, eye, 0.5, 12 + (int) (charge * 18), GOLD, 1.2f);
        sl.sendParticles(ParticleTypes.FLASH, eye.x + look.x, eye.y + look.y, eye.z + look.z, 1, 0, 0, 0, 0);
        // 완충일수록 던지는 소리가 묵직하다
        play(sl, player, SoundEvents.TRIDENT_THROW.value(), 1.0f, 1.3f - charge * 0.4f);
        if (charge >= 1.0f) play(sl, player, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.0f, 0.9f);
        SoundScheduler.at(sl, eye, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.7f, 1.2f, 3);
    }

    // ─────────────────────────────── 이동기: 게볼그 "질풍 돌진" (이동·V·2성) ───────────────────────────────
    // C 키. 전방으로 창을 앞세워 돌진하며 경로상 적을 관통한다. 쿨 9초.
    // 근접 진입기 — 원거리(투창)와 돌진(질풍)으로 한 무기가 거리 조절을 다 한다.
    public static void gustDash(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdDash", "질풍 돌진", 180, 2)) return;
        // 바라보는 방향 그대로 돌진한다 (위를 보면 솟구치고, 정면이면 앞으로) — 사거리도 늘렸다
        Vec3 look = player.getViewVector(1.0f).normalize();
        double p = 2.4;
        player.setDeltaMovement(look.x * p, look.y * p + 0.1, look.z * p);
        player.hurtMarked = true;
        player.resetFallDistance();
        if (player instanceof ServerPlayer sp) ChargeManager.start(sl, sp, 20, dmg(stack, 8.01f));

        // ── 연출: 앞으로 뻗는 질풍 ──
        Vec3 eye = player.getEyePosition();
        dustBurst(sl, eye, 0.7, 30, GOLD, 1.3f);
        beamParticles(sl, eye, eye.add(look.scale(5.0)), 0.4, ParticleTypes.CRIT, 0.0);
        shockRing(sl, player.getX(), player.getY() + 0.1, player.getZ(), 2.0, 24, ParticleTypes.CLOUD, 0.4);
        play(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 1.1f);
        play(level, player, SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.0f, 1.2f);
        sl.sendParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
        SoundScheduler.at(sl, player.position(), SoundEvents.TRIDENT_RIPTIDE_2.value(), 0.9f, 1.3f, 3);
        SoundScheduler.at(sl, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.5f, 6);
    }

    // ─────────────────────────────── 궁극: 게볼그 "백 개의 창" (궁극·4성) ───────────────────────────────
    // X 키. 등 뒤 허공에 황금 게이트가 열려 5초간 창을 쏟아낸다(게이트 오브 바빌론).
    // 반경 14 내 모든 적을 무차별로 꿰뚫는 광역 지속 궁극 — 초신성(단발 폭발)·별빛 폭풍(화살비)과
    // 결이 다른 "쏟아지는 창의 폭풍"이다.
    public static void hundredSpears(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!ready(level, player, stack, "cdBabylon", "백 개의 창", 1200, 4)) return;
        // ── 발당 피해를 올린 이유 (2026-07-26) ──
        // 타수는 손대지 않는다(표적별 8틱 상한 유지 — 단일 표적에 17발). 대신 발당을 올린다.
        //   1.3 → 2.0 : 5성 발당 3.9 → 6.0, 단일 표적 궁극 총합 66 → **102**
        // 실측에서 백창이 게볼그 전체 피해의 **3.9%** 밖에 안 됐다. 7초간 창 105발이 쏟아지는
        // 연출인데 일식(108)의 61%, 초신성(84)의 79% 라 궁극기로서 존재감이 없었다.
        // 102 면 다른 궁극기와 같은 층위이면서, 7초에 걸쳐 들어가는 만큼 즉발보다 조금 높다.
        BabylonManager.start(level, player, dmgTick(stack, 1.78f), 140); // 창 1발 2.0 · 7초
        // 시전자 발밑에서 솟구치는 금빛 기둥 (개막 임팩트 보강)
        Vec3 c = player.position();
        for (int i = 0; i < 30; i++) {
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + i * 0.4, c.z, 2, 0.3, 0.1, 0.3, 0.02);
        }
        dustBurst(level, c.add(0, 1, 0), 2.0, 60, GOLD, 1.8f);
    }

    // ─────────────────────────────── 이동기: 스틱스 "그림자 도약" (이동·V·2성) ───────────────────────────────
    // 우클릭. 조준한 적의 뒤로 순간이동하며 즉시 타격. 대상이 없으면 전방으로 짧게 대시.
    // 뒤로 잡으면 곧바로 패시브 "배후의 일격"(+20%) 각이 나와, 도약→평타가 자동으로 폭딜이 된다.
    private static final float LEAP_DMG = 11.13f;
    private static final double LEAP_RANGE = 12.0;

    public static void shadowLeap(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdLeap", "그림자 도약", 140, 2)) return;

        LivingEntity target = aimedEnemy(sl, player, LEAP_RANGE);
        Vec3 from = player.position();

        if (target != null) {
            // 대상 몸통 뒤쪽 1.2칸 지점으로 순간이동
            float bodyRad = (float) Math.toRadians(target.yBodyRot);
            Vec3 behind = new Vec3(Math.sin(bodyRad), 0, -Math.cos(bodyRad)).scale(1.2);
            Vec3 dest = target.position().add(behind);
            player.teleportTo(dest.x, dest.y, dest.z);
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                target.position().add(0, target.getBbHeight() * 0.5, 0));
            if (player instanceof ServerPlayer sp) sp.connection.teleport(dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());

            LsDamage.hit(target, relicSource(sl, player), dmg(stack, LEAP_DMG));
            sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                12, 0.3, 0.3, 0.3, 0.2);
        } else {
            // 대상 없음 — 전방 짧은 대시(회피기)
            Vec3 look = player.getViewVector(1.0f);
            player.setDeltaMovement(look.x * 1.4, 0.25, look.z * 1.4);
            player.hurtMarked = true;
        }

        // ── 연출: 사라진 자리와 나타난 자리에 어둠 ──
        trailPoof(sl, from);
        trailPoof(sl, player.position());
        sl.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        play(level, player, SoundEvents.ENDERMAN_TELEPORT, 0.9f, 1.4f);
        play(level, player, SoundEvents.SOUL_ESCAPE.value(), 0.7f, 1.2f);
        SoundScheduler.at(sl, player.position(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.8f, 1.6f, 3);
        SoundScheduler.at(sl, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.5f, 7);
    }

    private static void trailPoof(ServerLevel lv, Vec3 at) {
        lv.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.0, at.z, 20, 0.3, 0.5, 0.3, 0.02);
        lv.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 25, 0.3, 0.5, 0.3, 0.3);
        DustParticleOptions dark = new DustParticleOptions(new Vector3f(0.4f, 0.2f, 0.6f), 1.4f);
        lv.sendParticles(dark, at.x, at.y + 1.0, at.z, 15, 0.3, 0.5, 0.3, 0.0);
    }

    // ─────────────────────────────── 스킬: 스틱스 "망각의 안개" (추가·3성) ───────────────────────────────
    // C 키. 3초 은신(몹이 타겟 해제) + 은신이 풀린 뒤 첫 공격 2배. 쿨 15초.
    // 은신 중 공격하면 은신이 풀리며 그 일격이 강화된다 — "숨었다 튀어나오는" 폭딜 셋업.
    public static void oblivionMist(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdMist", "망각의 안개", 300, 3)) return;
        if (!(player instanceof ServerPlayer sp)) return;
        StealthManager.start(sl, sp, 60); // 3초

        Vec3 c = player.position();
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 1.0, c.z, 40, 0.6, 0.8, 0.6, 0.02);
        sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 1.0, c.z, 30, 0.6, 0.8, 0.6, 0.03);
        dustBurst(sl, c.add(0, 1, 0), 1.5, 30, 0x6A3CAA, 1.4f);
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
        play(level, player, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.7f);
        play(level, player, SoundEvents.SOUL_ESCAPE.value(), 0.9f, 0.9f);
        SoundScheduler.at(sl, c, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.7f, 1.5f, 4);
    }

    // ─────────────────────────────── 궁극: 스틱스 "무저갱" (궁극·4성) ───────────────────────────────
    // X 키. 8초간 이속+40%·공속+50%·모든 공격 방어 무시. 쿨 60초.
    public static void abyss(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!ready(level, player, stack, "cdAbyss", "무저갱", 1200, 4)) return;
        AbyssManager.start(level, player, 160); // 8초
    }

    // ─────────────────────────────── 궁극: 솔라리 "일식" (궁극·4성) ───────────────────────────────
    // X 키. 2초 차징 후 전방 60칸 초대형 관통 광선. 쿨 60초.
    // 8종 궁극 중 유일한 "직선 관통" — 초신성(광역)·별빛 폭풍(지대)과 역할이 겹치지 않는다.
    // 차징 중 몸이 굳어(둔화 IV) 겨냥만 할 수 있다 — 쏘기 전 2초가 곧 리스크다.
    public static void eclipse(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!ready(level, player, stack, "cdEclipse", "일식", 1200, 4)) return;
        // 일식은 **솔라리스(마총)** 궁극기다 — 셀레스티아가 아니다(SolarMusket.ultimate).
        // 36.0 → 39.6 → 36.43. 마지막은 솔라리스 일괄 ×0.92(2026-07-26 실측 58.9 / 목표 54).
        EclipseManager.start(level, player, dmg(stack, 36.43f), 40); // 2초 차징
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
        Vec3 eye = player.getEyePosition();
        dustBurst(level, eye, 1.0, 40, GOLD, 1.6f);
        level.sendParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
        play(level, player, SoundEvents.CROSSBOW_LOADING_START.value(), 1.4f, 0.6f);
        play(level, player, SoundEvents.BEACON_ACTIVATE, 1.3f, 1.4f);
    }

    // ─────────────────────────────── 이동기: 에테르 이지스 "이지스 돌진" (이동·V·2성) ───────────────────────────────
    // C 키. 앞으로 돌진 → 경로(반경1.5) 적 10뎀, 착지(반경3) 적 넉백+둔화. 쿨 10초.
    // 실제 경로 판정은 ChargeManager가 매 틱 추적한다.
    public static void aegisCharge(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdCharge", "이지스 돌진", 200, 2)) return;
        Vec3 look = player.getViewVector(1.0f);
        // 돌진 (수평 + 살짝 위로)
        player.setDeltaMovement(look.x * 1.7, 0.35, look.z * 1.7);
        player.hurtMarked = true; // 클라에 속도 동기화
        if (player instanceof ServerPlayer sp) ChargeManager.start(sl, sp, 16, dmg(stack, 11.0f));

        // 시전 연출
        Vec3 eye = player.getEyePosition();
        dustBurst(sl, eye, 0.8, 30, GOLD, 1.3f);
        shockRing(sl, player.getX(), player.getY() + 0.1, player.getZ(), 2.0, 24, ParticleTypes.ELECTRIC_SPARK, 0.4);
        sl.sendParticles(ParticleTypes.FLASH, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
        play(level, player, SoundEvents.SHIELD_BLOCK, 1.2f, 0.7f);
        play(level, player, SoundEvents.RAVAGER_STEP, 1.0f, 1.2f);
        SoundScheduler.at(sl, player.position(), SoundEvents.ANVIL_LAND, 0.8f, 0.7f, 3);
    }

    // ─────────────────────────────── 궁극: 에테르 이지스 "불멸의 맹세" (궁극·4성) ───────────────────────────────
    // X 키. 5초 무적 + 광역 도발 + 근처 아군 보호막. 쿨 60초.
    public static void oathOfImmortality(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!ready(level, player, stack, "cdOath", "불멸의 맹세", 1200, 4)) return;
        // 무적 (저항 V = 100% 경감) 5초
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 3, false, true));
        // 광역 도발 (16칸, 8초)
        TauntManager.taunt(level, player, 16.0, 160);
        // 근처 아군 보호막
        for (Player ally : level.players()) {
            if (ally == player) continue;
            if (ally.distanceToSqr(player) > 144.0) continue; // 12칸
            ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1, false, true));
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, false, true));
        }
        // ── 연출: 금빛 성역 돔 + 하늘로 뻗는 맹세의 기둥 ──
        double cx = player.getX(), cy = player.getY(), cz = player.getZ();
        Vec3 c = new Vec3(cx, cy + 1, cz);
        level.sendParticles(ParticleTypes.FLASH, cx, cy + 1, cz, 3, 0, 0, 0, 0);
        dome(level, cx, cy, cz, 8.0, 200, ParticleTypes.END_ROD);
        dome(level, cx, cy, cz, 6.5, 120, ParticleTypes.TOTEM_OF_UNDYING);
        dustBurst(level, c, 3.0, 120, GOLD, 2.0f);
        dustBurst(level, c.add(0, 1.5, 0), 2.0, 60, BLUE, 1.4f);
        for (int i = 0; i < 40; i++) { // 하늘 기둥 (약 14칸)
            level.sendParticles(ParticleTypes.END_ROD, cx, cy + i * 0.35, cz, 2, 0.35, 0.1, 0.35, 0.02);
        }
        shockRing(level, cx, cy + 0.1, cz, 4.0, 44, ParticleTypes.WAX_OFF, 0.4);
        shockRing(level, cx, cy + 0.1, cz, 6.0, 60, ParticleTypes.TOTEM_OF_UNDYING, 0.6);
        shockRing(level, cx, cy + 0.1, cz, 10.0, 80, ParticleTypes.ELECTRIC_SPARK, 0.8);

        // ── 사운드: 즉시 3층 + 시간차 3층 (짐승 포효 대신 성물·금속 계열) ──
        play(level, player, SoundEvents.TOTEM_USE, 1.5f, 0.8f);
        play(level, player, SoundEvents.BEACON_ACTIVATE, 1.6f, 0.6f);
        play(level, player, SoundEvents.ANVIL_LAND, 0.9f, 0.5f);
        SoundScheduler.at(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.8f, 0.5f, 3);
        SoundScheduler.at(level, c, SoundEvents.TRIDENT_THUNDER.value(), 1.5f, 1.5f, 6);
        SoundScheduler.at(level, c, SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.7f, 11);
        SoundScheduler.at(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.5f, 18);
    }

    // ─────────────────────────────── 궁극: 시리우스 "별빛 폭풍" (궁극·4성) ───────────────────────────────
    // X 키. 조준한 위치에 광역 화살비 강하(3초). 쿨 45초.
    public static void starfallStorm(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!ready(level, player, stack, "cdStorm", "별빛 폭풍", 900, 4)) return;
        // 조준점 = 시선 방향 최대 40칸 (블록 충돌 지점)
        // 겨눈 적의 발밑에 떨어진다 (엔티티를 무시하면 적 뒤 땅에 깔린다 — aimPoint 주석 참고)
        Vec3 eye = player.getEyePosition();
        Vec3 center = aimPoint(level, player, 40.0);
        // 반경을 넓혀 "광역기"로 세운다 — 좁으면 결국 단일 표적에 몰려 단일 딜러가 된다.
        double r = 8.0;
        ArrowStormManager.start(level, player, center, r, 60, dmgTick(stack, 4.4f)); // 반경8, 3초

        // ── 시전 연출: 조준 지점에서 하늘로 치솟는 별빛 기둥 + 다중 링 ──
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1, center.z, 3, 0.2, 0.2, 0.2, 0);
        for (int i = 0; i < 44; i++) { // 하늘 기둥 (약 15칸)
            double h = i * 0.35;
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + h, center.z, 2, 0.3, 0.1, 0.3, 0.02);
        }
        dustBurst(level, center.add(0, 1.0, 0), r * 0.5, 90, GOLD, 1.7f);
        dustBurst(level, center.add(0, 2.5, 0), r * 0.35, 50, BLUE, 1.3f);
        level.sendParticles(ParticleTypes.FIREWORK, center.x, center.y + 1.0, center.z, 70, r * 0.4, 0.8, r * 0.4, 0.2);
        shockRing(level, center.x, center.y + 0.1, center.z, r * 0.6, 36, ParticleTypes.FIREWORK, 0.3);
        shockRing(level, center.x, center.y + 0.1, center.z, r, 52, ParticleTypes.END_ROD, 0.45);
        shockRing(level, center.x, center.y + 0.1, center.z, r * 1.35, 64, ParticleTypes.ELECTRIC_SPARK, 0.6);
        // 시전자 → 조준 지점으로 이어지는 별빛 궤적
        beamDust(level, eye, center.add(0, 1.0, 0), 0.7, GOLD, 1.0f);

        // ── 사운드: 시전자와 조준 지점 양쪽에서 (원거리 시전이어도 들리게) ──
        play(level, player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.7f);
        play(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.6f);
        play(level, player, SoundEvents.EVOKER_CAST_SPELL, 1.2f, 0.8f);
        playAt(level, center, SoundEvents.TRIDENT_THUNDER.value(), 2.0f, 1.4f);
        playAt(level, center, SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.6f);
        playAt(level, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.8f, 0.5f);
    }

    // ─────────────────────────────── 스킬: 셀레스티아 "중력 붕괴" (추가·3성) ───────────────────────────────
    // C 키. 조준 지점으로 적을 끌어당기고 9뎀 + 둔화. 쿨 12초.
    public static void gravityCollapse(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdGravity", "중력 붕괴", 240, 3)) return;
        Vec3 center = aimPoint(sl, player, 24.0);
        double r = 6.0;
        AABB box = new AABB(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != player && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            if (e.distanceToSqr(center.x, center.y, center.z) > r * r) continue;
            // 초기 강한 흡입 (이후 GravityWellManager가 계속 끌어당김)
            Vec3 pull = new Vec3(center.x - e.getX(), center.y + 0.3 - e.getY(), center.z - e.getZ()).normalize().scale(1.4);
            e.setDeltaMovement(pull.x, pull.y * 0.5 + 0.1, pull.z);
            e.hasImpulse = true;
            e.fallDistance = 0.0f;
            LsDamage.hit(e, relicSource(sl, player), dmg(stack, 10.5f));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2, false, true));
        }
        // 연출 — 안으로 빨려드는 소용돌이
        for (int i = 0; i < 60; i++) {
            double a = sl.getRandom().nextDouble() * Math.PI * 2;
            double d = r * (0.4 + sl.getRandom().nextDouble() * 0.6);
            double px = center.x + Math.cos(a) * d, pz = center.z + Math.sin(a) * d;
            double vx = (center.x - px) * 0.25, vz = (center.z - pz) * 0.25;
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, px, center.y + 0.6, pz, 0, vx, 0.05, vz, 0.6);
        }
        // 지속 중력장 (2초) — 단발 임펄스는 AI 저항에 바로 풀리므로 매 틱 끌어당긴다
        if (player instanceof ServerPlayer sp) GravityWellManager.start(sl, sp, center, r, 40);
        sl.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 1.0, center.z, 1, 0, 0, 0, 0);
        dustBurst(sl, center.add(0, 1.0, 0), r * 0.35, 50, VOID, 1.4f);
        play(level, player, SoundEvents.WARDEN_SONIC_BOOM, 0.9f, 1.4f);
        play(level, player, SoundEvents.ILLUSIONER_CAST_SPELL, 1.1f, 0.7f);
        SoundScheduler.at(sl, center, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f, 3);
        SoundScheduler.at(sl, center, SoundEvents.WARDEN_HEARTBEAT, 0.9f, 1.2f, 7);
    }

    // ─────────────────────────────── 궁극: 셀레스티아 "초신성" (궁극·4성) ───────────────────────────────
    // X 키. 조준 지역에 1.5초 차징 후 대형 폭발. 쿨 60초.
    public static void supernova(ServerLevel level, ServerPlayer player, ItemStack stack) {
        if (!ready(level, player, stack, "cdSupernova", "초신성", 1200, 4)) return;
        Vec3 center = aimPoint(level, player, 32.0);
        // 28.0 → 34.0 → 37.06 → 39.65. 백창과 같은 피해 수준으로 맞춘 뒤 셀레스티아 배율을 얹었다.
        SupernovaManager.start(level, player, center, 7.0, dmg(stack, 39.65f), 30); // 반경7·34뎀·1.5초 차징

        // ── 시전 연출: 시전자 → 조준 지점 별빛 궤적 + 지정 지점 개시 링 ──
        Vec3 eye = player.getEyePosition();
        Vec3 mark = center.add(0, 1.0, 0);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y + 1, center.z, 2, 0.2, 0.2, 0.2, 0);
        beamDust(level, eye, mark, 0.6, GOLD, 1.1f);
        beamParticles(level, eye, mark, 1.2, ParticleTypes.END_ROD, 0.0);
        dustBurst(level, mark, 1.6, 60, GOLD, 1.5f);
        shockRing(level, center.x, center.y + 0.1, center.z, 7.0, 64, ParticleTypes.END_ROD, 0.5);
        shockRing(level, center.x, center.y + 0.1, center.z, 4.5, 44, ParticleTypes.ELECTRIC_SPARK, 0.4);

        // ── 사운드: 시전자 + 조준 지점, 시간차로 "차징 시작" 을 알림 ──
        play(level, player, SoundEvents.ILLUSIONER_CAST_SPELL, 1.3f, 0.6f);
        play(level, player, SoundEvents.EVOKER_PREPARE_SUMMON, 1.2f, 1.3f);
        playAt(level, mark, SoundEvents.BEACON_POWER_SELECT, 1.8f, 0.5f);
        SoundScheduler.at(level, mark, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6f, 0.6f, 4);
        SoundScheduler.at(level, mark, SoundEvents.END_PORTAL_FRAME_FILL, 1.6f, 0.7f, 9);
    }

    // 패시브 "별빛 충전" — 마법 평타 적중 시 해금된 스킬 중 랜덤 1개의 쿨 5% 감소(최대 0.6초).
    //
    // 대상은 각성 단계로 정해진다. 잠긴 스킬까지 후보에 넣으면 1성에서는 셋 중 둘이 못 쓰는
    // 스킬이라 패시브 가치의 2/3이 그냥 버려진다.
    //   1성 → 소멸만 · 3성 → 소멸·중력 붕괴 · 4성 → 셋 전부
    private static final String[] SAGE_KEYS = { "cdAnnihilate", "cdGravity", "cdSupernova" };
    private static final int[] SAGE_CDS = { 120, 240, 1200 };
    private static final int[] SAGE_STARS = { 1, 3, 4 }; // 각 스킬의 해금 성급

    public static void starCharge(ServerLevel level, ItemStack stack) {
        int s = star(stack);
        int pool = 0;
        while (pool < SAGE_STARS.length && SAGE_STARS[pool] <= s) pool++;
        if (pool <= 0) return;
        int i = level.getRandom().nextInt(pool);
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        long end = tag.getLong(SAGE_KEYS[i]);
        long now = level.getGameTime();
        if (end <= now) return; // 이미 준비된 스킬
        // 2026-07-26 상향: 5% → 8%, 상한 0.6초 → 1.0초.
        // 셀레스티아의 정체성이 "때릴수록 스킬이 빨리 돌아온다" 인데, 상한 12틱이 낮아서
        // 긴 쿨 스킬(초신성 1200틱)은 사실상 5%가 아니라 1% 씩만 줄고 있었다.
        // 평타를 계속 맞히는 플레이가 실제로 보상받게 한다.
        long cut = Math.min(Math.round(SAGE_CDS[i] * 0.08), 20); // 8%, 상한 1.0초
        tag.putLong(SAGE_KEYS[i], Math.max(now, end - cut));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ─────────────────────────────── 이동기: 타이탄 "지축 밟기" (이동·V·2성) ───────────────────────────────
    // 전방으로 짧고 묵직하게 돌진 → 착지 지점 주변을 넉백. 딜 없는 진입기. 쿨 6초.
    public static void earthStomp(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdStomp", "지축 밟기", 120, 2)) return;
        Vec3 look = player.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        player.setDeltaMovement(flat.x * 1.5, 0.45, flat.z * 1.5); // 짧지만 높이 솟구쳐 내려찍는 느낌
        player.hurtMarked = true;
        player.resetFallDistance();

        // 발밑에서 퍼지는 충격 (딜 없이 넉백만)
        double cx = player.getX(), cy = player.getY(), cz = player.getZ();
        AABB box = new AABB(cx - 3.5, cy - 2, cz - 3.5, cx + 3.5, cy + 2, cz + 3.5);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != player && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            e.knockback(1.0, cx - e.getX(), cz - e.getZ());
        }
        for (int ring = 1; ring <= 3; ring++)
            shockRing(sl, cx, cy + 0.1, cz, ring * 1.2, 24, ParticleTypes.CLOUD, 0.4);
        dustBurst(sl, new Vec3(cx, cy + 0.3, cz), 2.0, 40, GOLD, 1.4f);
        play(level, player, SoundEvents.RAVAGER_STEP, 1.2f, 0.7f);
        play(level, player, SoundEvents.ANVIL_LAND, 0.7f, 0.6f);
        sl.sendParticles(ParticleTypes.FLASH, cx, cy + 0.5, cz, 1, 0, 0, 0, 0);
        SoundScheduler.at(sl, new Vec3(cx, cy, cz), SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.6f, 2);
        SoundScheduler.at(sl, new Vec3(cx, cy, cz), SoundEvents.RAVAGER_STEP, 0.7f, 0.9f, 5);
    }

    // ─────────────────────────────── 이동기: 파나케이아 "천사의 발걸음" (이동·V·2성) ───────────────────────────────
    // 3초간 이동속도 +30% + 저항 I. 아군을 쫓아가거나 위험에서 빠지는 서포터 기동기. 쿨 8초.
    public static void angelStep(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdAngelStep", "천사의 발걸음", 160, 2)) return;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 2, false, true)); // +30% (레벨3)
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, false, true)); // 저항 I
        Vec3 c = player.position().add(0, 0.2, 0);
        sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 16, 0.3, 0.2, 0.3, 0.02);
        dustBurst(sl, c.add(0, 0.6, 0), 0.6, 18, 0xDCFFE0, 1.1f);
        play(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.7f);
        play(level, player, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.5f);
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.8, c.z, 1, 0, 0, 0, 0);
        SoundScheduler.at(sl, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f, 4);
    }

    // ─────────────────────────────── 이동기: 시리우스 "회피 도약" (이동·V·2성) ───────────────────────────────
    // 뒤(이동 입력이 있으면 그 방향)로 빠르게 도약하는 카이팅 회피. 딜 없음. 쿨 6초.
    public static void evadeLeap(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdEvade", "회피 도약", 120, 2)) return;
        // 기본은 바라보는 반대(뒤)로, 이동 중이면 그 방향으로 — 궁수답게 거리를 벌린다
        Vec3 look = player.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        Vec3 dir = flat.scale(-1); // 뒤로
        double f = player.zza, s = player.xxa; // 전후·좌우 입력
        if (f != 0 || s != 0) {
            Vec3 right = new Vec3(-flat.z, 0, flat.x);
            dir = flat.scale(f).add(right.scale(-s));
            if (dir.lengthSqr() < 1.0E-4) dir = flat.scale(-1);
            dir = dir.normalize();
        }
        player.setDeltaMovement(dir.x * 1.5, 0.42, dir.z * 1.5);
        player.hurtMarked = true;
        player.resetFallDistance();

        Vec3 c = player.position().add(0, 0.2, 0);
        sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 20, 0.3, 0.1, 0.3, 0.05);
        dustBurst(sl, c.add(0, 0.6, 0), 0.6, 18, BLUE, 1.0f);
        play(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
        play(level, player, SoundEvents.ENDER_DRAGON_FLAP, 0.7f, 1.4f);
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.8, c.z, 1, 0, 0, 0, 0);
        SoundScheduler.at(sl, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1.8f, 3);
    }

    // 순간이동 착지점 보정 — 원하는 자리에 플레이어 몸이 들어갈 수 있는지 실제로 검사하고,
    // 막혀 있으면 위아래로 훑어 설 수 있는 가장 가까운 자리를 돌려준다. 없으면 null.
    //
    // 위를 먼저 보는 이유: 지면을 겨눴을 때 반 칸 파묻히는 게 가장 흔한 실패라, 살짝 올리면 대개 해결된다.
    // 아래로도 보는 건 천장 아래를 겨눈 경우다. 범위를 좁게 잡아(±3) 엉뚱한 곳으로 튀지 않게 한다.
    private static final int[] BLINK_DY = {0, 1, 2, 3, -1, -2, -3};

    private static Vec3 safeSpot(ServerLevel sl, Player p, Vec3 want) {
        for (int dy : BLINK_DY) {
            Vec3 c = new Vec3(want.x, want.y + dy, want.z);
            // 현재 히트박스를 후보 위치로 옮겨 충돌을 본다 — 키(웅크림 포함)가 자동으로 반영된다
            if (sl.noCollision(p, p.getBoundingBox().move(c.subtract(p.position())))) return c;
        }
        return null;
    }

    // ─────────────────────────────── 이동기: 셀레스티아 "성간 도약" (이동·V·2성) ───────────────────────────────
    // 조준한 지점으로 순간이동하는 마법 블링크. 딜 없음. 쿨 7초.
    public static void blink(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        Vec3 from = player.position();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        // 시선 방향 최대 12칸, 벽에 막히면 그 직전까지
        var hit = sl.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(12.0)),
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getLocation();
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            target = target.subtract(look.scale(0.8)); // 벽에 끼지 않게 살짝 앞
        }
        // 발 높이 기준으로 착지 (눈높이만큼 내림)
        double dy = player.getEyeY() - player.getY();
        Vec3 want = new Vec3(target.x, target.y - dy + 0.1, target.z);

        // ── 착지점 보정 (2026-07-27) ──
        // 땅을 내려다보고 쓰면 충돌 지점이 지표면이라, 거기서 눈높이(1.62)만큼 내리면
        // 발이 지면 아래로 들어가 블록에 파묻혔다. 설 수 있는 자리인지 실제로 검사한다.
        Vec3 dest = safeSpot(sl, player, want);
        if (dest == null) {
            // 쿨타임을 먹기 전에 돌아간다 — 못 간 주제에 7초를 물리면 억울하다.
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§7그곳엔 설 자리가 없다."), true);
            return;
        }
        if (!ready(sl, player, stack, "cdBlink", "성간 도약", 140, 2)) return;
        player.teleportTo(dest.x, dest.y, dest.z);
        if (player instanceof ServerPlayer sp)
            sp.connection.teleport(dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
        player.resetFallDistance();

        // 사라진 자리·나타난 자리 별빛
        for (Vec3 p : new Vec3[]{from.add(0, 1, 0), player.position().add(0, 1, 0)}) {
            sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 30, 0.3, 0.6, 0.3, 0.05);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 25, 0.3, 0.6, 0.3, 0.3);
            dustBurst(sl, p, 0.7, 20, BLUE, 1.2f);
        }
        play(level, player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
        play(level, player, SoundEvents.ENDERMAN_TELEPORT, 0.7f, 1.3f);
        sl.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        SoundScheduler.at(sl, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.6f, 3);
        SoundScheduler.at(sl, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 2.0f, 7);
    }

    // ─────────────────────────────── 스킬: 이지스 "수호 반격" (추가·3성) ───────────────────────────────
    // 쉬프트+우클릭. 3초간 반격 태세 — 받는 피해 −40%, 맞을 때마다 공격자와 주변에 반사 피해. 쿨 14초.
    // 방어(도발·불멸의 맹세)와 달리 "맞으면서 되받아치는" 능동 반격이라 결이 겹치지 않는다.
    // 반사 판정은 RelicEventHandlers.onGuardianParry 가 custom_data("parryUntil")를 읽어 처리한다.
    public static void guardParry(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdParry", "수호 반격", 280, 3)) return;
        long now = sl.getGameTime();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putLong("parryUntil", now + 60); // 3초
        tag.putFloat("parryPower", dmgTick(stack, 8.8f)); // 반사 기본 피해(각성 반영)
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        Vec3 c = player.position().add(0, 1, 0);
        dome(sl, player.getX(), player.getY(), player.getZ(), 2.5, 60, ParticleTypes.END_ROD);
        dustBurst(sl, c, 1.2, 40, GOLD, 1.5f);
        shockRing(sl, player.getX(), player.getY() + 0.1, player.getZ(), 2.5, 32, ParticleTypes.WAX_OFF, 0.3);
        play(level, player, SoundEvents.SHIELD_BLOCK, 1.4f, 0.7f);
        play(level, player, SoundEvents.BEACON_ACTIVATE, 1.2f, 1.2f);
        SoundScheduler.at(sl, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f, 4);
    }


    // ─────────────────────────────── 스킬: 게볼그 "꿰뚫기" (추가·3성) ───────────────────────────────
    // 쉬프트+우클릭. 전방 7칸 직선을 관통하는 강력한 찌르기. 쿨 8초.
    public static void pierceThrust(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdPierce", "꿰뚫기", 160, 3)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double reach = beamReach(sl, player, eye, look, 7.0);
        beamHurt(sl, player, eye, look, reach, 1.2, dmg(stack, 10.68f), 0.4, false);

        Vec3 end = eye.add(look.scale(reach));
        beamParticles(sl, eye, end, 0.25, ParticleTypes.CRIT, 0.0);
        beamDust(sl, eye, end, 0.35, GOLD, 1.3f);
        sl.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, end.x, end.y, end.z, 2, 0.2, 0.2, 0.2, 0.0);
        shockRing(sl, end.x, end.y, end.z, 1.2, 16, ParticleTypes.CRIT, 0.3);
        play(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 0.7f);
        play(level, player, SoundEvents.TRIDENT_HIT, 1.0f, 1.0f);
        SoundScheduler.at(sl, end, SoundEvents.TRIDENT_THUNDER.value(), 0.8f, 1.2f, 3);
    }

    // ─────────────────────────────── 스킬: 스틱스 "급소 가르기" (기본·1성) ───────────────────────────────
    // 우클릭. 짧은 쿨의 근접 일격 + 3초 출혈. 백어택이면 패시브로 크게 터진다. 쿨 4초.
    // 그림자 도약(진입)으로 뒤를 잡고 → 급소 가르기로 딜, 이 사이클이 스틱스의 기본기다.
    public static void viciousStrike(Level level, Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (!ready(sl, player, stack, "cdVicious", "급소 가르기", 80, 1)) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double reach = 4.0;
        // 전방 좁은 부채꼴 근접 베기
        AABB box = new AABB(eye, eye.add(look.scale(reach))).inflate(1.2);
        boolean hitAny = false;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != player && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            Vec3 rel = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double along = rel.dot(look);
            if (along < 0 || along > reach) continue;
            if (rel.subtract(look.scale(along)).length() > 1.5) continue;
            LsDamage.hit(e, relicSource(sl, player), dmg(stack, 8.66f)); // 백어택이면 패시브 +20%가 자동으로 얹힘
            if (player instanceof ServerPlayer sp) BleedManager.apply(sl, e, sp, dmgTick(stack, 1.856f), 60);
            sl.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 10, 0.3, 0.3, 0.3, 0.15);
            hitAny = true;
        }
        // ── 연출: 그림자 칼날 ──
        Vec3 slash = eye.add(look.scale(2.0));
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, slash.x, slash.y, slash.z, 2, 0.3, 0.3, 0.3, 0.0);
        DustParticleOptions dark = new DustParticleOptions(new Vector3f(0.4f, 0.2f, 0.6f), 1.3f);
        sl.sendParticles(dark, slash.x, slash.y, slash.z, 16, 0.4, 0.3, 0.4, 0.02);
        if (hitAny) sl.sendParticles(ParticleTypes.FLASH, slash.x, slash.y, slash.z, 1, 0, 0, 0, 0);
        play(level, player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.4f);
        play(level, player, SoundEvents.SOUL_ESCAPE.value(), 0.6f, 1.5f);
    }

    // 시선 방향으로 **블록에 막히기 전까지의** 실제 사거리.
    //
    // 빔 계열(소멸·균열 붕괴·심판의 빛·꿰뚫기)은 원래 거리만 재고 지형을 보지 않아서
    // 벽·구조물을 그대로 뚫고 반대편 몹을 때렸다. 판정과 연출 양쪽이 이걸 거쳐야
    // "빛이 벽에서 멈춘다"가 눈과 실제 판정에서 같아진다.
    private static double beamReach(ServerLevel level, Player player, Vec3 eye, Vec3 look, double maxReach) {
        Vec3 far = eye.add(look.scale(maxReach));
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, far,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return maxReach;
        return Math.min(maxReach, hit.getLocation().distanceTo(eye));
    }

    // 시선 방향 조준 지점.
    //
    // ── 엔티티를 먼저 본다 ──
    // 예전엔 블록만 봤다. 그래서 **몹을 정확히 겨누고 스킬을 써도 조준점이 그 몹을 지나쳐
    // 뒤쪽 땅에 잡혔다** — 장판·화살비가 적 뒤에 깔리는 게 이것 때문이다.
    // 블록까지의 구간 안에 적이 있으면 그 발밑을 조준점으로 삼는다.
    //
    // ※ 관통 빔(EclipseManager)과 이동기(성간 도약)는 이 함수를 쓰지 않는다.
    //   전자는 엔티티에서 끊으면 관통이 사라지고, 후자는 적 뒤로 가는 게 의도다.
    private static Vec3 aimPoint(ServerLevel level, Player player, double maxDist) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 far = eye.add(look.scale(maxDist));

        var blockHit = level.clip(new net.minecraft.world.level.ClipContext(eye, far,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 end = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
            ? far : blockHit.getLocation();

        var eh = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
            level, player, eye, end, new AABB(eye, end).inflate(1.0),
            en -> en.isAlive() && en != player
                  && !(en instanceof Player) && !(en instanceof AbstractVillager));
        // 발밑을 준다 — 대부분 지면에 까는 장판·화살비라 몸통 중앙을 주면 공중에 뜬다.
        if (eh != null) return eh.getEntity().position();
        return end;
    }

    // ─────────────────────────────── 각성 · 스킬 피해 계산 ───────────────────────────────
    // 스킬 피해 = 기본값 × 각성 배율 + 무기 공격력 × 0.3 (하이브리드)
    //  · 주 성장축은 각성 배율 — 4종 모두 동일하게 적용돼 예외가 없다.
    //  · 무기 공격력은 30%만 반영 — 젬·마법부여로 공격력이 올라도 스킬이 폭주하지 않는다.
    //  · 활·지팡이는 공격력 속성이 없어 뒷항이 자연히 0이 된다(별도 처리 불필요).
    // ── 스킬 기본치를 정하는 기준 ──
    // 후반 배율 스택이 크다: 각성 ×1.8 · puffish 전투 스킬트리 melee_damage ×1.75
    // (우리 스킬은 전부 playerAttack 소스라 puffish DamageKind가 MELEE로 분류한다)
    // · Apotheosis 어픽스 공격력 · ApothicAttributes 크리 → 최소 3.15배, 실제론 그 이상.
    // 따라서 기본치는 "1성에서 일반 몹(20HP) 2타" 수준으로 잡는다. 1성부터 원콤이면
    // 각성해도 트래시 상대 체감이 없고 5성에서 과해진다. 3성쯤 원콤이 되는 게 성장 계단이다.
    private static final float WEAPON_SCALE = 0.3f;
    // 각성 배율 — 단계당 +0.5 등차, 최종 3.0배.
    // 이전 곡선(최대 1.8배)은 단계당 +13~20%라 강화가 체감되지 않았다.
    // 등차라서 체감 증가폭은 앞이 크다(+50% / +33% / +25% / +20%) — 첫 각성이 가장 짜릿하고,
    // 뒤로 갈수록 배율보다 스킬 해금(2성 이동기 · 3성 추가 · 4성 궁극)이 성장의 주인공이 된다.
    // 5성은 새 스킬이 없는 대신 배율·체력이 최대가 되는 '완성' 단계다.
    // 최대 체력도 같은 폭으로 20→60까지 오른다(ls_ascend.js) — 보스 공격력 상승과 짝을 맞추기 위해.
    private static final float[] ASCENSION = {1.0f, 1.5f, 2.0f, 2.5f, 3.0f}; // 1성~5성

    // 각성 단계(1~5). 아직 각성 시스템이 없으면 1성.
    public static int star(ItemStack stack) {
        int s = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("star");
        return s < 1 ? 1 : Math.min(s, ASCENSION.length);
    }

    // 각성 배율 (평타 스케일링에서도 쓰므로 공개)
    public static float ascension(ItemStack stack) {
        return ASCENSION[star(stack) - 1];
    }

    // ── 회복 전용 배율 ──
    // 피해는 보스 체력을 올려 보정할 수 있지만, 수성전 잡몹의 공격력은 그렇게 못 한다.
    // 회복까지 3배가 되면 파티가 잘 죽지 않아 수성전의 긴장이 사라진다.
    // 그래서 회복은 더 완만하게 1.0 → 2.5배로만 오른다.
    //   배율은 딱 떨어지지 않지만 실제 회복량은 깔끔하다 — 평타 기준 4 / 5.5 / 7 / 8.5 / 10 HPS.
    private static final float[] HEAL_SCALE = {1.0f, 1.375f, 1.75f, 2.125f, 2.5f};

    public static float healScale(ItemStack stack) {
        return HEAL_SCALE[star(stack) - 1];
    }

    // 유물 계열에 맞는 피해 소스.
    // puffish_attributes의 DamageKind는 is_magic·IS_PROJECTILE 태그가 없는 피해를 전부 melee로
    // 분류한다. 그대로 두면 지팡이·활 스킬 딜이 스킬트리의 "근접 피해" 노드로 올라가서,
    // 마법사가 딜을 올리려면 근접 노드를 찍어야 하는 상황이 된다.
    // 계열별 전용 피해 타입을 만들어 각자 자기 노드로 스케일링되게 한다.
    private static ResourceKey<DamageType> dt(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("lsrelics", path));
    }
    private static final ResourceKey<DamageType> RELIC_MAGIC = dt("relic_magic");   // neoforge:is_magic
    private static final ResourceKey<DamageType> RELIC_RANGED = dt("relic_ranged"); // minecraft:is_projectile

    public static DamageSource relicSource(ServerLevel level, Player player) {
        net.minecraft.world.item.Item held = player.getMainHandItem().getItem();
        ResourceKey<DamageType> key = null;
        if (held == com.laststardust.relics.LSRelics.SAGE.get()
            || held == com.laststardust.relics.LSRelics.HEALER.get()) {
            key = RELIC_MAGIC;
        } else if (held == com.laststardust.relics.LSRelics.HUNTER.get()
            || held == com.laststardust.relics.LSRelics.GUNNER.get()) {
            key = RELIC_RANGED;
        }
        // 근접 4종(이지스·타이탄·스틱스·게볼그)은 바닐라 근접 피해 그대로
        if (key == null) return level.damageSources().playerAttack(player);
        return new DamageSource(level.registryAccess()
            .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), player);
    }

    // 아이템에 붙은 공격력(ADD_VALUE 합). 접두사·젬으로 붙은 것도 함께 잡힌다.
    private static double weaponAttack(ItemStack stack) {
        ItemAttributeModifiers mods =
            stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double sum = 0;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (!e.attribute().equals(Attributes.ATTACK_DAMAGE)) continue;
            if (e.modifier().operation() != AttributeModifier.Operation.ADD_VALUE) continue;
            EquipmentSlotGroup g = e.slot();
            if (g != EquipmentSlotGroup.MAINHAND && g != EquipmentSlotGroup.HAND && g != EquipmentSlotGroup.ANY) continue;
            sum += e.modifier().amount();
        }
        return sum;
    }

    // 스킬 최종 피해. 단발성 스킬이 이 함수를 통과한다.
    public static float dmg(ItemStack stack, float base) {
        return base * ASCENSION[star(stack) - 1] + (float) (weaponAttack(stack) * WEAPON_SCALE);
    }

    // 다단히트·지속피해용. 무기 공격력 항을 빼고 각성 배율만 적용한다.
    // 장판이 5초간 매 초, 화살비가 3초간 90발씩 때리는데 매 타격마다 무기 보너스를 더하면
    // 그 항이 타격 수만큼 곱해져 단발 스킬의 몇 배가 되어버린다. (접두사·젬으로 공격력이 붙으면 더 심해짐)
    public static float dmgTick(ItemStack stack, float base) {
        return base * ASCENSION[star(stack) - 1];
    }

    // ─────────────────────────────── 공통 프리미티브 ───────────────────────────────
    // 스킬별 개별 쿨다운(custom_data 키). 무기당 스킬이 3개라 아이템 쿨다운(하나뿐)은 못 씀.
    // minStar: 이 스킬이 해금되는 각성 단계 (기본 1성 · 이동기 2성 · 추가 3성 · 궁극 4성).
    private static boolean ready(ServerLevel level, Player player, ItemStack stack,
                                 String key, String name, int cooldownTicks, int minStar) {
        int s = star(stack);
        if (s < minStar) {
            // 채팅으로 띄운다 — 액션바는 쿨다운 표시에 금방 덮여서 놓치기 쉽다.
            // 다만 우클릭 연타로 도배되지 않게 3초에 한 번만 알린다.
            if (player instanceof ServerPlayer sp) {
                CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                long now = level.getGameTime();
                long quiet = t.getLong("lockMsg") - now;
                if (quiet <= 0 || quiet > 60) {
                    t.putLong("lockMsg", now + 60);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
                    sp.sendSystemMessage(Component.literal(
                        "§c✘ §e" + name + "§7은(는) §e" + minStar + "성 각성§7이 필요하다. §8(현재 " + s + "성)"));
                    sp.sendSystemMessage(Component.literal("§8   §7각성 조건 확인 — §e/ascend"));
                    level.playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(),
                        SoundSource.PLAYERS, 0.5f, 0.7f);
                }
            }
            return false;
        }
        long now = level.getGameTime();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        long left = tag.getLong(key) - now;
        // 남은 쿨이 정상 범위(0 < left <= 쿨)일 때만 대기. 그보다 크면 월드 시간 되감김으로 생긴
        // 비정상 값이므로 무시하고 사용 허용(그대로 두면 스킬이 영영 안 나감).
        if (left > 0 && left <= cooldownTicks) return false;
        tag.putLong(key, now + cooldownTicks);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal(String.format("§b✦ %s §f발동!", name)), true);
        }
        return true;
    }

    // 스킬 남은 쿨(틱). 0이면 준비됨. (액션바 표시용)
    // total보다 큰 값은 월드 시간 불일치로 생긴 비정상 데이터 → 준비됨으로 취급.
    public static long cooldownLeft(ServerLevel level, ItemStack stack, String key, int total) {
        long end = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong(key);
        long left = end - level.getGameTime();
        if (left <= 0 || left > total) return 0;
        return left;
    }

    // 쿨다운 즉시 해제 — 스킬이 헛방으로 끝났을 때 쿨을 되돌린다(필중이 대상 없이 나가는 경우 등).
    public static void clearCooldown(ItemStack stack, String key) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(key);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // 아이템 데이터(custom_data)에 키별 쿨다운 저장 → ItemCooldowns를 안 써 우클릭/발사를 막지 않음.
    private static boolean dataCooldown(ServerLevel level, ItemStack stack, String key, int cooldownTicks) {
        long now = level.getGameTime();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        long end = tag.getLong(key);
        long left = end - now;
        // 남은 쿨이 정상 범위(0 < left <= 쿨)일 때만 대기. left가 쿨보다 크면 월드 시간 불일치로 생긴
        // 비정상 값이므로 무시하고 사용 허용(그대로 두면 영영 준비가 안 됨).
        if (left > 0 && left <= cooldownTicks) return false;
        tag.putLong(key, now + cooldownTicks);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }

    // 연사 속도 제한 (활 좌클릭 평타 — 매 틱 호출돼도 이 간격마다만 발사)
    public static boolean shotReady(ServerLevel level, ItemStack stack, int cooldownTicks) {
        return dataCooldown(level, stack, "shotReady", cooldownTicks);
    }

    private static void play(Level level, Player player, SoundEvent sound, float vol, float pitch) {
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, vol, pitch);
    }

    // 임의 좌표에서 재생 (원거리 조준 스킬은 착탄 지점에서도 소리가 나야 함)
    private static void playAt(Level level, Vec3 at, SoundEvent sound, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, vol, pitch);
    }

    // 빔(선분) 전체에 피해 — 캡슐 판정(선분과의 거리 <= radius). 점블랭크~원거리 모두 명중.
    private static void beamHurt(ServerLevel level, Player player, Vec3 eye, Vec3 look,
                                 double reach, double radius, float dmg, double knockback, boolean markWeak) {
        reach = beamReach(level, player, eye, look, reach);   // 벽 뒤는 때리지 않는다
        Vec3 end = eye.add(look.scale(reach));
        AABB box = new AABB(eye.x, eye.y, eye.z, end.x, end.y, end.z).inflate(radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != player && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            Vec3 center = e.position().add(0, e.getBbHeight() * 0.5, 0);
            if (distToSegment(center, eye, end) <= radius + e.getBbWidth() * 0.5) {
                com.laststardust.relics.LsDamage.hit(e, relicSource(level, player), dmg);
                if (knockback > 0) e.knockback(knockback, player.getX() - e.getX(), player.getZ() - e.getZ());
                if (markWeak) e.getPersistentData().putLong("lsWeakUntil", level.getGameTime() + 100); // 약점 노출 5초
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                    12, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    private static void hurtAround(ServerLevel level, Player player, double x, double y, double z,
                                   double radius, float dmg, double knockback) {
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != player && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            if (e.distanceToSqr(x, y, z) > radius * radius) continue;
            com.laststardust.relics.LsDamage.hit(e, relicSource(level, player), dmg);
            if (knockback > 0) e.knockback(knockback, player.getX() - e.getX(), player.getZ() - e.getZ());
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                10, 0.3, 0.3, 0.3, 0.1);
        }
    }

    // 빔 경로에 적이 몇이나 있는지 (피해를 주기 전에 세어야 "맞았는지" 판정이 된다)
    private static int countBeamTargets(ServerLevel level, Player player, Vec3 eye, Vec3 look,
                                        double reach, double radius) {
        reach = beamReach(level, player, eye, look, reach);   // 벽 뒤는 세지 않는다
        Vec3 end = eye.add(look.scale(reach));
        AABB box = new AABB(eye, end).inflate(radius + 1.0);
        int n = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != player && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            Vec3 rel = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double along = rel.dot(look);
            if (along < 0 || along > reach) continue;
            if (rel.subtract(look.scale(along)).length() > radius) continue;
            n++;
        }
        return n;
    }

    // 조준선 위의 가장 가까운 적(몹). 그림자 도약이 뒤로 붙을 대상을 고른다.
    private static LivingEntity aimedEnemy(ServerLevel level, Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 far = eye.add(look.scale(range));
        AABB box = new AABB(eye, far).inflate(1.0);
        LivingEntity best = null;
        double bestAlong = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != player && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            Vec3 rel = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
            double along = rel.dot(look);
            if (along < 0 || along > range) continue;
            if (rel.subtract(look.scale(along)).length() > 1.5) continue; // 조준 관대
            if (along < bestAlong) { bestAlong = along; best = e; }
        }
        return best;
    }

    // 체력 비율이 가장 낮은 아군(시전자 포함). 절대 체력이 아니라 비율로 봐야
    // 최대 체력이 제각각인 파티에서 "가장 위태로운 사람"이 제대로 잡힌다.
    private static Player weakestAlly(ServerLevel level, Player caster, double range) {
        Player best = null;
        float bestRatio = Float.MAX_VALUE;
        for (Player p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (p != caster && p.distanceToSqr(caster) > range * range) continue;
            if (p.getHealth() >= p.getMaxHealth()) continue;
            float ratio = p.getHealth() / p.getMaxHealth();
            if (ratio < bestRatio) { bestRatio = ratio; best = p; }
        }
        return best;
    }

    // Y축(요) 기준 벡터 회전 — 부채꼴 발사에 사용
    private static Vec3 rotateYaw(Vec3 v, double ang) {
        double cos = Math.cos(ang), sin = Math.sin(ang);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        if (len2 < 1.0e-6) return p.distanceTo(a);
        double t = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len2));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    // 빔을 따라 파티클 분사 (step 간격)
    private static void beamParticles(ServerLevel level, Vec3 from, Vec3 to, double step,
                                      ParticleOptions p, double speed) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        Vec3 dir = d.scale(1.0 / Math.max(len, 1.0e-6));
        for (double s = 0; s <= len; s += step) {
            Vec3 pt = from.add(dir.scale(s));
            level.sendParticles(p, pt.x, pt.y, pt.z, 1, 0.03, 0.03, 0.03, speed);
        }
    }

    private static DustParticleOptions dustOpt(int color, float scale) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        return new DustParticleOptions(new Vector3f(r, g, b), scale);
    }

    private static void beamDust(ServerLevel level, Vec3 from, Vec3 to, double step, int color, float scale) {
        beamParticles(level, from, to, step, dustOpt(color, scale), 0.0);
    }

    private static void dustBurst(ServerLevel level, Vec3 c, double spread, int count, int color, float scale) {
        level.sendParticles(dustOpt(color, scale), c.x, c.y, c.z, count, spread, spread, spread, 0.0);
    }

    // 수평 링 (반경 r, n개 점)
    private static void ring(ServerLevel level, double cx, double cy, double cz,
                             double r, int n, ParticleOptions p, double speed) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.sendParticles(p, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r, 1, 0, 0, 0, speed);
        }
    }

    // 바깥으로 퍼지는 충격파 링 (count=0 + 방사 속도 → 방향성 파티클)
    private static void shockRing(ServerLevel level, double cx, double cy, double cz,
                                  double r, int n, ParticleOptions p, double speed) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double dx = Math.cos(a), dz = Math.sin(a);
            level.sendParticles(p, cx + dx * r, cy, cz + dz * r, 0, dx * speed, 0.02, dz * speed, speed);
        }
    }

    // 반구 돔 (피보나치 분포)
    private static void dome(ServerLevel level, double cx, double cy, double cz, double r, int n, ParticleOptions p) {
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int i = 0; i < n; i++) {
            double y = (double) i / n; // 0..1 (하단→상단 반구)
            double rad = Math.sqrt(1.0 - y * y);
            double theta = golden * i;
            double px = cx + Math.cos(theta) * rad * r;
            double pz = cz + Math.sin(theta) * rad * r;
            double py = cy + y * r;
            level.sendParticles(p, px, py, pz, 1, 0, 0, 0, 0.0);
        }
    }
}
