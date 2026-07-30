package com.laststardust.relics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.mojang.math.Transformation;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.laststardust.relics.LSRelics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 투창(게볼그 추가) — 창을 던져 명중 + 출혈, 최대 거리에서 되돌아오며 다시 벤다.
// 나갈 때·돌아올 때 각각 한 번씩 맞는다(같은 대상은 편도당 1회). 되돌아오는 게 게볼그의 낭만.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class JavelinManager {
    private JavelinManager() {}

    private static final double HIT_RADIUS = 1.2;
    private static final int SUBSTEPS = 4;

    private static final List<Javelin> ACTIVE = new ArrayList<>();

    private static final class Javelin {
        final ServerLevel level;
        final ServerPlayer owner;
        final float damage;
        final float bleedPerSec;
        final int bleedTicks;
        final double speed;
        final double maxRange;
        Vec3 pos;
        Vec3 dir;
        boolean returning;
        double traveled;
        Display.ItemDisplay display; // 실제로 보이는 창 모델
        final Set<Integer> hitOut = new HashSet<>();
        final Set<Integer> hitBack = new HashSet<>();
        Javelin(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 dir, float damage,
                float bleedPerSec, int bleedTicks, double speed, double maxRange) {
            this.level = level; this.owner = owner; this.pos = pos;
            this.dir = dir; this.damage = damage; this.bleedPerSec = bleedPerSec; this.bleedTicks = bleedTicks;
            this.speed = speed; this.maxRange = maxRange;
        }
    }

    // 속도 2.0 · 사거리 20칸.
    // 예전엔 charge(0~1) 를 받아 속도 1.2~2.0 / 사거리 10~20 으로 나눴는데, 차징 조작이
    // 사라지면서 호출부가 항상 1.0 을 넘기게 됐다. 쓰이지 않는 축은 지우고 상수로 접었다.
    private static final double SPEAR_SPEED = 2.0;
    private static final double SPEAR_RANGE = 20.0;

    public static void throwSpear(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 dir,
                                  float damage, float bleedPerSec, int bleedTicks) {
        Javelin j = new Javelin(level, owner, start, dir.normalize(), damage, bleedPerSec, bleedTicks,
                                SPEAR_SPEED, SPEAR_RANGE);
        // 손에 든 게볼그 모델을 그대로 띄운다 — 파티클이 아니라 진짜 창이 날아간다
        Display.ItemDisplay disp = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        disp.setItemStack(new ItemStack(LSRelics.LANCER.get()));
        // NONE 컨텍스트 = 모델의 display 변환을 안 탄다 → 순수 모델 축(길이축 Y)만 남아
        // 우리가 준 회전이 그대로 적용된다. (FIXED 는 모델의 fixed 회전 90°가 덧씌워져 방향이 틀어졌다)
        disp.setItemTransform(ItemDisplayContext.NONE);
        disp.setPos(start.x, start.y, start.z);
        j.display = disp;
        orient(j);
        level.addFreshEntity(disp);
        ACTIVE.add(j);
    }

    // 창을 비행 방향으로 눕힌다. 게볼그 모델의 길이축은 Y라 모델 Y축을 진행 방향으로 돌린다.
    private static void orient(Javelin j) {
        if (j.display == null) return;
        Vector3f dir = new Vector3f((float) j.dir.x, (float) j.dir.y, (float) j.dir.z).normalize();
        // 모델 길이축 +Y(창 머리) 를 진행 방향으로 돌린다 → 머리가 앞장선다.
        // 축 회전(스핀)은 주지 않는다 — 창은 곧게 날아가야지 뱅글뱅글 돌면 안 된다.
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir);
        // 모델 중심(모델좌표 y≈10 → 0.6블록)을 엔티티 위치로 당겨 창 한가운데가 좌표에 오게 한다
        j.display.setTransformation(new Transformation(
            new Vector3f(0, -0.6f, 0), q, new Vector3f(1, 1, 1), new Quaternionf()));
        j.display.setPos(j.pos.x, j.pos.y, j.pos.z);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Javelin> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Javelin j = it.next();
            if (j.owner.isRemoved() || !j.owner.isAlive()) { discard(j); it.remove(); continue; }

            double step = j.speed / SUBSTEPS;
            for (int i = 0; i < SUBSTEPS; i++) {
                if (j.returning) {
                    // 던진 사람에게 되돌아온다 (움직이는 목표 추적)
                    Vec3 hand = j.owner.getEyePosition().add(0, -0.2, 0);
                    j.dir = hand.subtract(j.pos).normalize();
                    if (j.pos.distanceTo(hand) < 1.2) { discard(j); it.remove(); return; }
                } else {
                    j.traveled += step;
                    // 벽에 박히거나 최대 거리에 닿으면 회수 시작
                    HitResult clip = j.level.clip(new ClipContext(j.pos, j.pos.add(j.dir.scale(step)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, j.owner));
                    if (clip.getType() != HitResult.Type.MISS || j.traveled >= j.maxRange) {
                        j.returning = true;
                        j.level.playSound(null, j.pos.x, j.pos.y, j.pos.z,
                            SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.8f, 1.0f);
                    }
                }
                j.pos = j.pos.add(j.dir.scale(step));
                hitAlong(j);
            }

            // 모델을 새 위치·자세로 갱신 (창끝이 진행 방향을 곧게 향한다)
            orient(j);
            // 옅은 금빛 잔상만 (모델이 주역이라 파티클은 최소)
            DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.4f), 0.9f);
            j.level.sendParticles(gold, j.pos.x, j.pos.y, j.pos.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    private static void discard(Javelin j) {
        if (j.display != null && j.display.isAlive()) j.display.discard();
    }

    private static void hitAlong(Javelin j) {
        AABB box = new AABB(j.pos.x - HIT_RADIUS, j.pos.y - HIT_RADIUS, j.pos.z - HIT_RADIUS,
                            j.pos.x + HIT_RADIUS, j.pos.y + HIT_RADIUS, j.pos.z + HIT_RADIUS);
        Set<Integer> seen = j.returning ? j.hitBack : j.hitOut;
        for (LivingEntity e : j.level.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != j.owner && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            if (!seen.add(e.getId())) continue;
            com.laststardust.relics.LsDamage.hit(e, com.laststardust.relics.item.RelicSkills.relicSource(j.level, j.owner), j.damage, "투창");
            BleedManager.apply(j.level, e, j.owner, j.bleedPerSec, j.bleedTicks);
            j.level.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                8, 0.3, 0.3, 0.3, 0.1);
        }
    }
}
