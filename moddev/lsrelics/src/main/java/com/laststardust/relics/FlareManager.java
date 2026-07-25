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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 작열탄 — 솔라리스가 **던지는** 소이탄. 날아가서 부딪히면 터진다.
//
// ── 왜 따로 만들었나 ──
// 예전엔 조준 지점에서 곧바로 터졌다(aimPoint 히트스캔). 맞기는 잘 맞지만
// "내가 뭔가를 던졌다"는 감각이 전혀 없어서, 32칸 밖이 갑자기 폭발하는 것처럼 보였다.
// 총알(BulletManager)은 직선이라 그것대로 안 맞는다 — 던진 물건은 포물선을 그려야 한다.
//
// 그래서 중력을 받는 투척체로 둔다. 결과적으로 **거리 조절이 실력이 되고**,
// 날아가는 0.5~1초가 적에게도 피할 틈을 준다(즉발 광역이 아니게 된다).
@EventBusSubscriber(modid = LSRelics.MODID)
public final class FlareManager {
    private FlareManager() {}

    private static final double SPEED = 1.35;      // 블록/틱 — 던진 물건다운 속도
    private static final double GRAVITY = 0.045;   // 틱당 하강 가속 (포물선의 정도)
    private static final double HIT_RADIUS = 0.5;
    private static final int SUBSTEPS = 4;         // 빠른 구간에서 벽을 통과하지 않게 잘게 나눈다
    private static final int MAX_TICKS = 70;       // 3.5초 — 허공에 던져도 언젠간 사라진다

    private static final List<Flare> ACTIVE = new ArrayList<>();

    private static final class Flare {
        final ServerLevel level;
        final ServerPlayer owner;
        final float burstDamage;   // 착탄 즉시 광역 피해
        final float zoneDamage;    // 불바다 지대 초당 피해
        final double radius;
        Vec3 pos;
        Vec3 vel;
        int ticksLeft = MAX_TICKS;
        int spin;

        Flare(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 vel,
              float burstDamage, float zoneDamage, double radius) {
            this.level = level; this.owner = owner; this.pos = pos; this.vel = vel;
            this.burstDamage = burstDamage; this.zoneDamage = zoneDamage; this.radius = radius;
        }
    }

    public static void throwFlare(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 dir,
                                  float burstDamage, float zoneDamage, double radius) {
        ACTIVE.add(new Flare(level, owner, start, dir.normalize().scale(SPEED),
            burstDamage, zoneDamage, radius));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Flare> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Flare f = it.next();
            if (--f.ticksLeft <= 0) { burst(f, f.pos); it.remove(); continue; }

            boolean done = false;
            Vec3 stepVel = f.vel.scale(1.0 / SUBSTEPS);
            for (int i = 0; i < SUBSTEPS && !done; i++) {
                Vec3 next = f.pos.add(stepVel);

                // 지형 충돌
                HitResult clip = f.level.clip(new ClipContext(f.pos, next,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, f.owner));
                if (clip.getType() != HitResult.Type.MISS) {
                    burst(f, clip.getLocation());
                    done = true;
                    break;
                }

                // 적 직격 — 맞으면 그 자리에서 바로 터진다
                AABB box = new AABB(next.x - HIT_RADIUS, next.y - HIT_RADIUS, next.z - HIT_RADIUS,
                                    next.x + HIT_RADIUS, next.y + HIT_RADIUS, next.z + HIT_RADIUS);
                for (LivingEntity e : f.level.getEntitiesOfClass(LivingEntity.class, box,
                        en -> en != f.owner && en.isAlive()
                              && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
                    burst(f, next);
                    done = true;
                    break;
                }
                if (done) break;
                f.pos = next;
            }
            if (done) { it.remove(); continue; }

            f.vel = f.vel.subtract(0, GRAVITY, 0);   // 중력 — 이게 포물선을 만든다
            trail(f);
        }
    }

    // 비행 궤적 — 불티가 흩날리며 회전하는 느낌
    private static void trail(Flare f) {
        f.spin++;
        ServerLevel l = f.level;
        l.sendParticles(ParticleTypes.FLAME, f.pos.x, f.pos.y, f.pos.z, 3, 0.06, 0.06, 0.06, 0.01);
        l.sendParticles(ParticleTypes.SMOKE, f.pos.x, f.pos.y, f.pos.z, 1, 0.05, 0.05, 0.05, 0.0);
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.72f, 0.25f), 1.1f);
        l.sendParticles(gold, f.pos.x, f.pos.y, f.pos.z, 2, 0.08, 0.08, 0.08, 0.0);
        // 날아가는 소리 — 2틱마다 짧게, 어디쯤 오는지 귀로도 알 수 있게
        if (f.spin % 2 == 0) {
            l.playSound(null, net.minecraft.core.BlockPos.containing(f.pos),
                SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.35f, 1.6f);
        }
    }

    // ── 착탄 ──
    // 피해·불바다·연출은 예전 즉발 버전과 같다. 달라진 건 "여기까지 날아왔다"는 것뿐.
    private static void burst(Flare f, Vec3 at) {
        ServerLevel l = f.level;
        double r = f.radius;

        AABB box = new AABB(at.x - r, at.y - r, at.z - r, at.x + r, at.y + r, at.z + r);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != f.owner && en.isAlive()
                      && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            if (e.distanceToSqr(at) > r * r) continue;
            e.hurt(com.laststardust.relics.item.RelicSkills.relicSource(l, f.owner), f.burstDamage);
            e.setRemainingFireTicks(60);
        }
        FlameZoneManager.start(l, f.owner, at, r, f.zoneDamage, 60);

        l.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 2, 0, 0, 0, 0);
        l.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.3, 0.3, 0.3, 0.0);
        l.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 60, r * 0.5, 0.3, r * 0.5, 0.05);
        l.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 12, r * 0.4, 0.2, r * 0.4, 0.0);
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.35f), 1.6f);
        l.sendParticles(gold, at.x, at.y, at.z, 40, r * 0.5, r * 0.3, r * 0.5, 0.0);

        var pos = net.minecraft.core.BlockPos.containing(at);
        l.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 1.1f);
        l.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2f, 0.8f);
        SoundScheduler.at(l, at, SoundEvents.FIRE_AMBIENT, 1.0f, 0.8f, 3);
        SoundScheduler.at(l, at, SoundEvents.BLAZE_SHOOT, 0.9f, 0.9f, 6);
    }
}
