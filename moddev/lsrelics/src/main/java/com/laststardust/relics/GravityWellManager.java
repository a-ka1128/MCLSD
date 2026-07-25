package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 중력 붕괴 — 한 번의 충격이 아니라 지속되는 중력장.
// 지속시간 동안 매 틱 적을 중심으로 끌어당겨 실제로 "빨려드는" 느낌을 만든다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class GravityWellManager {
    private GravityWellManager() {}

    private static final double PULL = 0.75;       // 틱당 끌어당기는 힘
    private static final double HOLD_RADIUS = 0.8; // 중심에 이만큼 붙으면 그만 당김

    private static final List<Well> ACTIVE = new ArrayList<>();

    private static final class Well {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 center;
        final double radius;
        int ticksLeft;
        Well(ServerLevel level, ServerPlayer caster, Vec3 center, double radius, int ticksLeft) {
            this.level = level; this.caster = caster; this.center = center;
            this.radius = radius; this.ticksLeft = ticksLeft;
        }
    }

    public static void start(ServerLevel level, ServerPlayer caster, Vec3 center, double radius, int ticks) {
        ACTIVE.add(new Well(level, caster, center, radius, ticks));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Well> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Well w = it.next();
            // 시전자가 죽거나 나가면 중력장 해제
            if (w.caster.isRemoved() || !w.caster.isAlive()) { it.remove(); continue; }
            if (--w.ticksLeft <= 0) { it.remove(); continue; }
            ServerLevel lv = w.level;
            Vec3 c = w.center;
            double r = w.radius;

            AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
            for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, box,
                    en -> en != w.caster && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
                Vec3 to = new Vec3(c.x - e.getX(), c.y + 0.25 - e.getY(), c.z - e.getZ());
                double dist = to.length();
                if (dist > r || dist < HOLD_RADIUS) continue;
                Vec3 dir = to.normalize();
                // 매 틱 속도를 덮어써서 AI 저항을 이김
                e.setDeltaMovement(dir.x * PULL, dir.y * PULL * 0.4 + 0.04, dir.z * PULL);
                e.hasImpulse = true;
                e.fallDistance = 0.0f;
                // 빠져나가지 못하게 둔화 유지
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 2, false, false));
            }

            // 소용돌이 연출 (안으로 감기는 입자)
            for (int i = 0; i < 14; i++) {
                double a = lv.getRandom().nextDouble() * Math.PI * 2;
                double d = r * (0.35 + lv.getRandom().nextDouble() * 0.65);
                double px = c.x + Math.cos(a) * d, pz = c.z + Math.sin(a) * d;
                double vx = (c.x - px) * 0.3, vz = (c.z - pz) * 0.3;
                lv.sendParticles(ParticleTypes.REVERSE_PORTAL, px, c.y + 0.4 + lv.getRandom().nextDouble(), pz,
                    0, vx, 0.05, vz, 0.7);
            }
            if (w.ticksLeft % 8 == 0) {
                lv.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8f, 1.4f);
            }
        }
    }
}
