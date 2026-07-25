package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 작열탄(솔라리 추가)의 불바다 — 착탄 지점에 남는 화염 지대.
// 지대 안의 적을 주기적으로 태우고 발화시킨다. 몰려오는 잡몹 무리에 지르는 광역 지속 딜.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class FlameZoneManager {
    private FlameZoneManager() {}

    private static final int DAMAGE_INTERVAL = 10; // 0.5초마다

    private static final List<Zone> ACTIVE = new ArrayList<>();

    private static final class Zone {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 center;
        final double radius;
        final float perTick; // DAMAGE_INTERVAL 마다 주는 피해
        int ticksLeft;
        Zone(ServerLevel level, ServerPlayer caster, Vec3 center, double radius, float perTick, int ticks) {
            this.level = level; this.caster = caster; this.center = center;
            this.radius = radius; this.perTick = perTick; this.ticksLeft = ticks;
        }
    }

    public static void start(ServerLevel level, ServerPlayer caster, Vec3 center,
                             double radius, float perTick, int ticks) {
        ACTIVE.add(new Zone(level, caster, center, radius, perTick, ticks));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Zone> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Zone z = it.next();
            if (--z.ticksLeft <= 0) { it.remove(); continue; }

            if (z.ticksLeft % DAMAGE_INTERVAL == 0) {
                AABB box = new AABB(z.center.x - z.radius, z.center.y - 1, z.center.z - z.radius,
                                    z.center.x + z.radius, z.center.y + 2, z.center.z + z.radius);
                for (LivingEntity e : z.level.getEntitiesOfClass(LivingEntity.class, box,
                        en -> en != z.caster && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
                    if (e.distanceToSqr(z.center) > z.radius * z.radius) continue;
                    e.hurt(com.laststardust.relics.item.RelicSkills.relicSource(z.level, z.caster), z.perTick);
                    e.setRemainingFireTicks(40); // 발화 2초 (지대를 벗어나도 잠깐 탄다)
                }
            }
            render(z);
        }
    }

    private static void render(Zone z) {
        ServerLevel lv = z.level;
        // 지대 전체에 흩뿌리는 불꽃
        for (int i = 0; i < 6; i++) {
            double a = (z.ticksLeft * 0.3 + i) * 1.7;
            double r = z.radius * ((i % 3) / 3.0 + 0.2);
            double x = z.center.x + Math.cos(a) * r, zz = z.center.z + Math.sin(a) * r;
            lv.sendParticles(ParticleTypes.FLAME, x, z.center.y + 0.15, zz, 1, 0.05, 0.02, 0.05, 0.01);
            lv.sendParticles(ParticleTypes.SMALL_FLAME, x, z.center.y + 0.3, zz, 1, 0.05, 0.05, 0.05, 0.0);
        }
        if (z.ticksLeft % 6 == 0) {
            lv.sendParticles(ParticleTypes.LARGE_SMOKE, z.center.x, z.center.y + 0.5, z.center.z, 2, z.radius * 0.4, 0.2, z.radius * 0.4, 0.01);
        }
        if (z.ticksLeft % 20 == 0) {
            lv.playSound(null, z.center.x, z.center.y, z.center.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.9f);
        }
    }
}
