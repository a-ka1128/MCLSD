package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.laststardust.relics.item.RelicSkills;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 별빛 탄 — 실제로 날아가는 마법 투사체.
// (히트스캔 빔은 경로 전체에 파티클을 그려 시야를 가렸음 → 현재 위치에만 작게 표시)
@EventBusSubscriber(modid = LSRelics.MODID)
public final class BoltManager {
    private BoltManager() {}

    private static final double SPEED = 1.6;      // 블록/틱
    private static final double HIT_RADIUS = 0.6;
    private static final int SUBSTEPS = 4;        // 틱당 분할 이동(관통 방지)

    private static final List<Bolt> ACTIVE = new ArrayList<>();

    private static final class Bolt {
        final ServerLevel level;
        final ServerPlayer owner;
        final Vec3 dir;
        final float damage;
        final Vector3f color;
        Vec3 pos;
        int ticksLeft;
        Bolt(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 dir, float damage, int ticksLeft, Vector3f color) {
            this.level = level; this.owner = owner; this.pos = pos;
            this.dir = dir; this.damage = damage; this.ticksLeft = ticksLeft; this.color = color;
        }
    }

    private static final Vector3f STAR_BLUE = new Vector3f(0.56f, 0.71f, 1.0f);

    public static void fire(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 dir, float damage, int ticks) {
        fire(level, owner, start, dir, damage, ticks, STAR_BLUE);
    }

    public static void fire(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 dir,
                            float damage, int ticks, Vector3f color) {
        ACTIVE.add(new Bolt(level, owner, start, dir.normalize(), damage, ticks, color));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Bolt> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Bolt b = it.next();
            if (--b.ticksLeft <= 0) { it.remove(); continue; }

            boolean done = false;
            double step = SPEED / SUBSTEPS;
            for (int i = 0; i < SUBSTEPS; i++) {
                Vec3 next = b.pos.add(b.dir.scale(step));
                // 블록 충돌
                HitResult clip = b.level.clip(new ClipContext(b.pos, next,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b.owner));
                if (clip.getType() != HitResult.Type.MISS) {
                    impact(b, clip.getLocation(), null);
                    done = true;
                    break;
                }
                // 엔티티 충돌
                AABB box = new AABB(next.x - HIT_RADIUS, next.y - HIT_RADIUS, next.z - HIT_RADIUS,
                                    next.x + HIT_RADIUS, next.y + HIT_RADIUS, next.z + HIT_RADIUS);
                LivingEntity target = null;
                for (LivingEntity e : b.level.getEntitiesOfClass(LivingEntity.class, box,
                        en -> en != b.owner && en.isAlive()
                              && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
                    target = e;
                    break;
                }
                if (target != null) {
                    impact(b, next, target);
                    done = true;
                    break;
                }
                b.pos = next;
            }
            if (done) { it.remove(); continue; }

            // 비행 중 — 현재 위치에만 작게 (시야 방해 최소화)
            b.level.sendParticles(ParticleTypes.END_ROD, b.pos.x, b.pos.y, b.pos.z, 2, 0.03, 0.03, 0.03, 0.0);
            DustParticleOptions tint = new DustParticleOptions(b.color, 0.9f);
            b.level.sendParticles(tint, b.pos.x, b.pos.y, b.pos.z, 1, 0.04, 0.04, 0.04, 0.0);
        }
    }

    private static void impact(Bolt b, Vec3 at, LivingEntity target) {
        ServerLevel lv = b.level;
        if (target != null) {
            // 무적 프레임 무시 (LsDamage 주석 참고) — 법사·힐러 평타가 파티에서 씹히던 원인
            com.laststardust.relics.LsDamage.hit(target,
                com.laststardust.relics.item.RelicSkills.relicSource(lv, b.owner), b.damage);
            lv.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.1);
            // 패시브 별빛 충전
            ItemStack held = b.owner.getMainHandItem();
            if (held.getItem() == LSRelics.SAGE.get()) RelicSkills.starCharge(lv, held);
        }
        lv.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 18, 0.18, 0.18, 0.18, 0.06);
        lv.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.05);
        lv.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.2f, 1.8f);
        lv.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.5f);
    }
}
