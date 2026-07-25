package com.laststardust.relics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 이지스 돌진 — 돌진은 여러 틱에 걸쳐 이동하므로 경로를 매 틱 추적한다.
//  · 경로(반경 1.5): 지나가며 적에게 10뎀 기준(각성·무기 보정은 RelicSkills.dmg가 적용, 적당 1회)
//  · 착지(반경 3.0): 넉백 + 강한 둔화
@EventBusSubscriber(modid = LSRelics.MODID)
public final class ChargeManager {
    private ChargeManager() {}

    private static final double PATH_RADIUS = 1.5;
    private static final double LAND_RADIUS = 3.0;

    private static final List<Charge> ACTIVE = new ArrayList<>();

    private static final class Charge {
        final ServerLevel level;
        final ServerPlayer player;
        final Set<UUID> hit = new HashSet<>();
        final float damage;
        int ticksLeft;
        int elapsed;
        Charge(ServerLevel level, ServerPlayer player, int ticksLeft, float damage) {
            this.level = level; this.player = player; this.ticksLeft = ticksLeft; this.damage = damage;
        }
    }

    public static void start(ServerLevel level, ServerPlayer player, int ticks, float damage) {
        ACTIVE.add(new Charge(level, player, ticks, damage));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Charge> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Charge c = it.next();
            ServerPlayer p = c.player;
            if (!p.isAlive() || p.isRemoved()) { it.remove(); continue; }
            c.elapsed++;

            // 경로 피해 (반경 1.5, 같은 적은 1회만)
            AABB box = p.getBoundingBox().inflate(PATH_RADIUS);
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box,
                    en -> en != p && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
                if (!c.hit.add(e.getUUID())) continue;
                e.hurt(com.laststardust.relics.item.RelicSkills.relicSource(c.level, p), c.damage);
                c.level.sendParticles(ParticleTypes.CRIT,
                    e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
            }
            // 돌진 잔상
            c.level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 4, 0.2, 0.1, 0.2, 0.01);
            c.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.6, p.getZ(), 3, 0.3, 0.2, 0.3, 0.02);

            // 종료: 시간 만료 or 착지(초반 4틱은 무시 — 발사 직후 바닥 판정 방지)
            boolean landed = (--c.ticksLeft <= 0) || (c.elapsed > 4 && p.onGround());
            if (landed) { land(c); it.remove(); }
        }
    }

    // 착지 충격 — 반경 3칸 넉백 + 둔화
    private static void land(Charge c) {
        ServerPlayer p = c.player;
        ServerLevel lv = c.level;
        double x = p.getX(), y = p.getY(), z = p.getZ();
        AABB box = new AABB(x - LAND_RADIUS, y - LAND_RADIUS, z - LAND_RADIUS,
                            x + LAND_RADIUS, y + LAND_RADIUS, z + LAND_RADIUS);
        for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != p && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            if (e.distanceToSqr(x, y, z) > LAND_RADIUS * LAND_RADIUS) continue;
            e.knockback(1.3, x - e.getX(), z - e.getZ()); // 착지점 바깥으로
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, true)); // 3초 강한 둔화
        }
        // 착지 연출
        lv.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.2, z, 2, 0.3, 0.1, 0.3, 0.0);
        lv.sendParticles(ParticleTypes.CLOUD, x, y + 0.2, z, 30, 1.2, 0.2, 1.2, 0.06);
        for (int i = 0; i < 28; i++) {
            double a = Math.PI * 2 * i / 28.0;
            double dx = Math.cos(a), dz = Math.sin(a);
            lv.sendParticles(ParticleTypes.END_ROD, x + dx * LAND_RADIUS, y + 0.15, z + dz * LAND_RADIUS,
                0, dx * 0.4, 0.05, dz * 0.4, 0.4);
        }
        lv.playSound(null, x, y, z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.3f, 0.6f);
        lv.playSound(null, x, y, z, SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.2f, 0.8f);
    }
}
