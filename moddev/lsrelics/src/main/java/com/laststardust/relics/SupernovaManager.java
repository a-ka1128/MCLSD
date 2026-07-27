package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 초신성(지팡이 궁극) — 조준 지역에 차징 후 대형 폭발.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class SupernovaManager {
    private SupernovaManager() {}

    private static final List<Nova> ACTIVE = new ArrayList<>();

    private static final class Nova {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 center;
        final double radius;
        final float damage;
        final int totalTicks;
        int ticksLeft;
        Nova(ServerLevel level, ServerPlayer caster, Vec3 center, double radius, float damage, int ticks) {
            this.level = level; this.caster = caster; this.center = center;
            this.radius = radius; this.damage = damage; this.totalTicks = ticks; this.ticksLeft = ticks;
        }
    }

    public static void start(ServerLevel level, ServerPlayer caster, Vec3 center,
                             double radius, float damage, int chargeTicks) {
        ACTIVE.add(new Nova(level, caster, center, radius, damage, chargeTicks));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Nova> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Nova n = it.next();
            // 시전자가 죽거나 나가면 차징 중단 (폭발 없이 소멸)
            if (n.caster.isRemoved() || !n.caster.isAlive()) { it.remove(); continue; }
            if (--n.ticksLeft <= 0) { detonate(n); it.remove(); continue; }
            charging(n);
        }
    }

    // 차징 연출 — 점점 조여드는 별빛 구체
    private static void charging(Nova n) {
        double progress = 1.0 - (double) n.ticksLeft / n.totalTicks; // 0→1
        double r = n.radius * (1.0 - progress * 0.75);               // 바깥에서 안으로 수축
        ServerLevel lv = n.level;
        for (int i = 0; i < 10; i++) {
            double a = lv.getRandom().nextDouble() * Math.PI * 2;
            double yy = (lv.getRandom().nextDouble() - 0.5) * r;
            double px = n.center.x + Math.cos(a) * r;
            double pz = n.center.z + Math.sin(a) * r;
            lv.sendParticles(ParticleTypes.END_ROD, px, n.center.y + 1.0 + yy, pz, 1, 0, 0, 0, 0);
        }
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.6f), 1.3f);
        lv.sendParticles(gold, n.center.x, n.center.y + 1.0, n.center.z, 6, r * 0.4, 0.5, r * 0.4, 0.0);
        // 안쪽으로 빨려드는 별먼지 + 위험 표시 링(수축)
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8.0 + progress * 6.0;
            double px = n.center.x + Math.cos(a) * r, pz = n.center.z + Math.sin(a) * r;
            lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, px, n.center.y + 0.15, pz,
                0, (n.center.x - px) * 0.3, 0.05, (n.center.z - pz) * 0.3, 0.6);
        }
        // 차징 사운드 — 음이 점점 올라가며 조여드는 느낌 + 저음 심박
        if (n.ticksLeft % 4 == 0) {
            lv.playSound(null, n.center.x, n.center.y, n.center.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 0.6f + (float) progress);
        }
        if (n.ticksLeft % 8 == 0) {
            lv.playSound(null, n.center.x, n.center.y, n.center.z,
                SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.5f + (float) progress * 0.8f);
        }
    }

    // 폭발
    private static void detonate(Nova n) {
        ServerLevel lv = n.level;
        double x = n.center.x, y = n.center.y + 1.0, z = n.center.z;
        AABB box = new AABB(x - n.radius, y - n.radius, z - n.radius, x + n.radius, y + n.radius, z + n.radius);
        for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != n.caster && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            if (e.distanceToSqr(x, y, z) > n.radius * n.radius) continue;
            com.laststardust.relics.LsDamage.hit(e, com.laststardust.relics.item.RelicSkills.relicSource(lv, n.caster), n.damage, "초신성");
            e.knockback(1.0, x - e.getX(), z - e.getZ());
        }
        // 연출
        lv.sendParticles(ParticleTypes.FLASH, x, y, z, 4, 0.2, 0.2, 0.2, 0.0);
        lv.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 3, 1.0, 0.5, 1.0, 0.0);
        lv.sendParticles(ParticleTypes.END_ROD, x, y, z, 220, n.radius * 0.5, n.radius * 0.5, n.radius * 0.5, 0.35);
        lv.sendParticles(ParticleTypes.FIREWORK, x, y, z, 160, n.radius * 0.5, n.radius * 0.5, n.radius * 0.5, 0.4);
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.55f), 2.0f);
        lv.sendParticles(gold, x, y, z, 140, n.radius * 0.5, n.radius * 0.5, n.radius * 0.5, 0.1);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48.0;
            double dx = Math.cos(a), dz = Math.sin(a);
            lv.sendParticles(ParticleTypes.END_ROD, x + dx * n.radius, n.center.y + 0.2, z + dz * n.radius,
                0, dx * 0.9, 0.05, dz * 0.9, 0.9);
        }
        // ── 사운드: 폭발 순간 3층 + 시간차로 울리는 잔향 4층 ──
        Vec3 at = new Vec3(x, y, z);
        lv.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f, 0.5f);
        lv.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.8f, 0.6f);
        lv.playSound(null, x, y, z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.4f, 1.1f);
        SoundScheduler.at(lv, at, SoundEvents.TRIDENT_THUNDER.value(), 2.0f, 0.8f, 2);
        SoundScheduler.at(lv, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.8f, 0.5f, 5);
        SoundScheduler.at(lv, at, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 1.4f, 9);
        SoundScheduler.at(lv, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.5f, 14);
    }
}
