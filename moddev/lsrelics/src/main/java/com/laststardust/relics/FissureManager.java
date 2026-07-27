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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 대지 쪼개기(도끼 추가 스킬) — 갈라진 땅이 일정 시간 남아 지속 피해 + 둔화를 준다.
// 즉발 피해는 시전 시점에 RelicSkills가 처리하고, 여기서는 남은 균열 지대만 관리한다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class FissureManager {
    private FissureManager() {}

    private static final int DAMAGE_INTERVAL = 20; // 1초마다
    private static final DustParticleOptions VOID_DUST =
        new DustParticleOptions(new Vector3f(0.60f, 0.36f, 1.0f), 1.4f);

    private static final List<Fissure> ACTIVE = new ArrayList<>();

    private static final class Fissure {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 origin;
        final Vec3 dir;      // 부채꼴 중심 방향(수평)
        final double length; // 균열 길이
        final double halfAngleCos;
        final float tickDamage;
        int ticksLeft;
        Fissure(ServerLevel level, ServerPlayer caster, Vec3 origin, Vec3 dir,
                double length, double halfAngleDeg, float tickDamage, int ticksLeft) {
            this.level = level; this.caster = caster; this.origin = origin;
            this.dir = dir; this.length = length;
            this.halfAngleCos = Math.cos(Math.toRadians(halfAngleDeg));
            this.tickDamage = tickDamage; this.ticksLeft = ticksLeft;
        }
    }

    public static void start(ServerLevel level, ServerPlayer caster, Vec3 origin, Vec3 dir,
                             double length, double halfAngleDeg, float tickDamage, int ticks) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0e-6) flat = new Vec3(0, 0, 1);
        ACTIVE.add(new Fissure(level, caster, origin, flat.normalize(), length, halfAngleDeg, tickDamage, ticks));
    }

    // 대상이 부채꼴 안에 있는가 (수평 기준)
    private static boolean inCone(Fissure f, LivingEntity e) {
        Vec3 to = new Vec3(e.getX() - f.origin.x, 0, e.getZ() - f.origin.z);
        double dist = to.length();
        if (dist > f.length || dist < 0.01) return dist <= f.length;
        if (Math.abs(e.getY() - f.origin.y) > 3.0) return false; // 위아래로 너무 멀면 제외
        return to.normalize().dot(f.dir) >= f.halfAngleCos;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Fissure> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fissure f = it.next();
            // 시전자가 죽거나 나가면 균열 지대 소멸
            if (f.caster.isRemoved() || !f.caster.isAlive()) { it.remove(); continue; }
            if (--f.ticksLeft <= 0) { it.remove(); continue; }
            ServerLevel lv = f.level;

            // 갈라진 틈 연출 — 부채꼴을 따라 솟는 공허 입자
            for (int i = 0; i < 6; i++) {
                double t = lv.getRandom().nextDouble();
                double ang = Math.toRadians((lv.getRandom().nextDouble() - 0.5) * 2
                    * Math.toDegrees(Math.acos(f.halfAngleCos)));
                double cos = Math.cos(ang), sin = Math.sin(ang);
                Vec3 d = new Vec3(f.dir.x * cos - f.dir.z * sin, 0, f.dir.x * sin + f.dir.z * cos);
                double dist = t * f.length;
                double px = f.origin.x + d.x * dist, pz = f.origin.z + d.z * dist;
                lv.sendParticles(ParticleTypes.SCULK_CHARGE_POP, px, f.origin.y + 0.1, pz, 1, 0.1, 0.05, 0.1, 0.0);
                lv.sendParticles(VOID_DUST, px, f.origin.y + 0.2, pz, 1, 0.1, 0.1, 0.1, 0.0);
                if (lv.getRandom().nextInt(3) == 0) {
                    lv.sendParticles(ParticleTypes.REVERSE_PORTAL, px, f.origin.y + 0.1, pz, 0, 0, 0.35, 0, 0.5);
                }
            }

            if (f.ticksLeft % DAMAGE_INTERVAL != 0) continue;

            AABB box = new AABB(f.origin.x - f.length, f.origin.y - 3, f.origin.z - f.length,
                                f.origin.x + f.length, f.origin.y + 3, f.origin.z + f.length);
            boolean hit = false;
            for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, box,
                    en -> en != f.caster && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
                if (!inCone(f, e)) continue;
                // 다단히트 — 설계 주기(1초)를 그대로 상한으로 쓴다. 파티에서 남이 때린 직후면
                // 그 초의 장판 피해가 통째로 사라지던 것만 고쳐지고, 그 이상 늘지는 않는다.
                com.laststardust.relics.LsDamage.hitLimited(e,
                    com.laststardust.relics.item.RelicSkills.relicSource(lv, f.caster), f.tickDamage,
                    "fissure", lv.getGameTime(), DAMAGE_INTERVAL, "균열 지대");
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, true));
                lv.sendParticles(ParticleTypes.SCULK_SOUL, e.getX(), e.getY() + 0.2, e.getZ(), 6, 0.2, 0.1, 0.2, 0.02);
                hit = true;
            }
            if (hit) {
                lv.playSound(null, f.origin.x, f.origin.y, f.origin.z,
                    SoundEvents.SCULK_BLOCK_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
            }
        }
    }
}
