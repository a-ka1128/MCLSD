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
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 별빛 폭풍(활 궁극) — 조준 지점 상공에서 화살이 지속적으로 쏟아짐.
// 지속 중에는 회전하는 별빛 결계 + 낙하 궤적 + 주기 사운드, 종료 시 마무리 섬광.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class ArrowStormManager {
    private ArrowStormManager() {}

    // 대상 조준 시 흩어지는 반경(칸). 화살비의 단일 표적 화력을 정하는 **주 노브**다.
    // 착탄 확산(lsSplashR)이 1.5 이므로, 이 값이 그보다 크면 빗나가는 화살이 생기기 시작한다.
    //   0.25 → 사실상 완전 유도(예전 값, 90발 전부 명중 → 궁극 하나로 DPS 3배)
    //   3.0  → 뭉친 무리엔 잘 들어가고 단독 표적에는 절반 이하만 꽂힌다
    // 단일 표적 화력을 더 줄이려면 이 값을 올린다. 광역 성능은 거의 그대로다.
    private static final double AIM_SCATTER = 3.0;

    private static final List<Storm> ACTIVE = new ArrayList<>();

    private static final class Storm {
        final ServerLevel level;
        final ServerPlayer owner;
        final Vec3 center;
        final double radius;
        final int totalTicks;
        final float damage;   // 화살 1발이 명중 시 줄 피해(각성 반영값)
        int ticksLeft;
        int shotIndex;   // 여러 적에게 화살을 번갈아 배분하기 위한 카운터
        Storm(ServerLevel level, ServerPlayer owner, Vec3 center, double radius, int ticksLeft, float damage) {
            this.level = level; this.owner = owner; this.center = center;
            this.radius = radius; this.totalTicks = ticksLeft; this.ticksLeft = ticksLeft; this.damage = damage;
        }
    }

    public static void start(ServerLevel level, ServerPlayer owner, Vec3 center, double radius,
                             int durationTicks, float damage) {
        ACTIVE.add(new Storm(level, owner, center, radius, durationTicks, damage));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Storm> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Storm st = it.next();
            // 시전자가 죽거나 나가면 폭풍도 멈춘다
            if (st.owner.isRemoved() || !st.owner.isAlive()) { it.remove(); continue; }
            if (--st.ticksLeft <= 0) { finale(st); it.remove(); continue; }
            ambient(st);
            if (st.ticksLeft % 2 != 0) continue; // 2틱마다 3발

            // 지역 안의 적을 먼저 찾는다 — 있으면 그 머리 위로 떨어뜨려 명중을 보장.
            // (예전엔 반경 안 랜덤 좌표에 떨궈서 적이 조금만 움직여도 다 빗나갔음)
            List<LivingEntity> targets = st.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(st.center.x - st.radius, st.center.y - 4, st.center.z - st.radius,
                         st.center.x + st.radius, st.center.y + 6, st.center.z + st.radius),
                en -> en != st.owner && en.isAlive()
                      && !(en instanceof Player) && !(en instanceof AbstractVillager));

            for (int i = 0; i < 3; i++) {
                double x, z, y;
                if (!targets.isEmpty()) {
                    // 여러 마리면 번갈아 배분 (한 마리에 몰빵되지 않게)
                    LivingEntity t = targets.get(st.shotIndex++ % targets.size());
                    // 대상 위로 조준하되 **정확히 머리 위는 아니다.**
                    // 예전엔 ±0.25칸이라 사실상 완전 유도였고, 90발이 단일 표적에 전부 꽂혀
                    // 궁극 하나가 DPS 를 3배로 만들었다. 흩어뜨려서 명중률을 기하로 낮춘다 —
                    // 여러 마리가 뭉쳐 있을수록 잘 맞는 광역기가 되고, 한 마리만 있으면 샌다.
                    double ax = (st.level.getRandom().nextDouble() - 0.5) * 2 * AIM_SCATTER;
                    double az = (st.level.getRandom().nextDouble() - 0.5) * 2 * AIM_SCATTER;
                    x = t.getX() + ax;
                    z = t.getZ() + az;
                    y = spawnHeight(st, x, z, t.getY() + t.getBbHeight() * 0.5);
                } else {
                    double ang = st.level.getRandom().nextDouble() * Math.PI * 2;
                    double dist = st.level.getRandom().nextDouble() * st.radius;
                    x = st.center.x + Math.cos(ang) * dist;
                    z = st.center.z + Math.sin(ang) * dist;
                    y = st.center.y + 14.0;
                }
                // firedFromWeapon 은 null 이어야 함 (EMPTY 를 넘기면 1.21.1에서 IllegalArgumentException).
                // LsArrows.create 가 EMPTY→null 정규화까지 보장한다.
                Arrow arrow = LsArrows.create(st.level, st.owner, null);
                arrow.setPos(x, y, z);
                arrow.setDeltaMovement(0, -1.6, 0);
                arrow.setBaseDamage(st.damage / 2.6); // 크리(×1.5 기대) 포함해 명중 시 대략 damage
                arrow.setCritArrow(true);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                // 살짝 빗나가도 들어가게 착탄 소폭 확산 (RelicEventHandlers가 처리)
                arrow.getPersistentData().putFloat("lsSplash", st.damage * 0.375f);
                arrow.getPersistentData().putFloat("lsSplashR", 1.5f);
                st.level.addFreshEntity(arrow);
                // 낙하 궤적 — 화살 위쪽으로 꼬리를 그려 "별똥별" 느낌
                for (int k = 0; k < 4; k++) {
                    st.level.sendParticles(ParticleTypes.END_ROD, x, y + k * 1.2, z, 1, 0.04, 0.15, 0.04, 0.0);
                }
                st.level.sendParticles(GOLD_DUST, x, y - 0.5, z, 2, 0.05, 0.3, 0.05, 0.0);
            }
        }
    }

    // 대상 머리 위 어느 높이에서 떨어뜨릴지 — 천장이 있으면(동굴·실내) 막히지 않는 높이를 찾는다.
    private static final double[] SPAWN_HEIGHTS = {14.0, 10.0, 7.0, 4.0, 2.0};

    private static double spawnHeight(Storm st, double x, double z, double targetY) {
        for (double h : SPAWN_HEIGHTS) {
            Vec3 from = new Vec3(x, targetY + h, z);
            Vec3 to = new Vec3(x, targetY, z);
            HitResult hit = st.level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, st.owner));
            if (hit.getType() == HitResult.Type.MISS) return from.y;
        }
        return targetY + 1.5; // 전부 막혔으면 바로 위에서
    }

    private static final DustParticleOptions GOLD_DUST =
        new DustParticleOptions(new Vector3f(1.0f, 0.9f, 0.63f), 1.3f);
    private static final DustParticleOptions BLUE_DUST =
        new DustParticleOptions(new Vector3f(0.56f, 0.71f, 1.0f), 1.1f);

    // 지속 연출 — 회전하는 이중 결계 링 + 주기 사운드
    private static void ambient(Storm st) {
        ServerLevel lv = st.level;
        Vec3 c = st.center;
        double spin = (st.totalTicks - st.ticksLeft) * 0.22; // 시간에 따라 회전
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + spin;
            lv.sendParticles(ParticleTypes.END_ROD,
                c.x + Math.cos(a) * st.radius, c.y + 0.15, c.z + Math.sin(a) * st.radius, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 - spin * 1.6;
            lv.sendParticles(BLUE_DUST,
                c.x + Math.cos(a) * st.radius * 0.6, c.y + 0.4, c.z + Math.sin(a) * st.radius * 0.6, 1, 0, 0, 0, 0);
        }
        lv.sendParticles(GOLD_DUST, c.x, c.y + 0.3, c.z, 5, st.radius * 0.5, 0.2, st.radius * 0.5, 0.0);
        lv.sendParticles(ParticleTypes.ELECTRIC_SPARK,
            c.x, c.y + 0.2, c.z, 6, st.radius * 0.5, 0.1, st.radius * 0.5, 0.02);

        if (st.ticksLeft % 6 == 0) {
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.1f, 1.5f);
        }
        if (st.ticksLeft % 14 == 0) {
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f);
            lv.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.5f);
        }
    }

    // 종료 임팩트 — 마지막 한 방
    private static void finale(Storm st) {
        ServerLevel lv = st.level;
        double x = st.center.x, y = st.center.y + 0.6, z = st.center.z;
        lv.sendParticles(ParticleTypes.FLASH, x, y, z, 2, 0.2, 0.2, 0.2, 0);
        lv.sendParticles(ParticleTypes.END_ROD, x, y, z, 120, st.radius * 0.5, 0.6, st.radius * 0.5, 0.25);
        lv.sendParticles(ParticleTypes.FIREWORK, x, y, z, 90, st.radius * 0.5, 0.5, st.radius * 0.5, 0.3);
        lv.sendParticles(GOLD_DUST, x, y, z, 80, st.radius * 0.5, 0.4, st.radius * 0.5, 0.0);
        for (int i = 0; i < 48; i++) {
            double a = Math.PI * 2 * i / 48.0;
            double dx = Math.cos(a), dz = Math.sin(a);
            lv.sendParticles(ParticleTypes.END_ROD, x + dx * st.radius, st.center.y + 0.15, z + dz * st.radius,
                0, dx * 0.8, 0.05, dz * 0.8, 0.8);
        }
        lv.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.8f, 0.7f);
        lv.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.6f, 0.5f);
        Vec3 at = new Vec3(x, y, z);
        SoundScheduler.at(lv, at, SoundEvents.TRIDENT_THUNDER.value(), 1.6f, 1.2f, 3);
        SoundScheduler.at(lv, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5f, 0.6f, 8);
    }
}
