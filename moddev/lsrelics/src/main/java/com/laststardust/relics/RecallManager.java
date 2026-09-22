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

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 네메시스 V「참격 인계」 — 대검을 던지고 <b>그 대검을 잡으며 날아간다.</b>
 *
 * <p>{@link JavelinManager}(게볼그 투창)와 같은 수법이다 — 손에 든 모델을 그대로 띄워
 * ({@code Display.ItemDisplay}) 날린다. 파티클이 아니라 진짜 검이 날아간다.
 * 다른 점은 <b>돌아오지 않는다</b>는 것이다. 검이 멈춘 자리로 <b>사람이 간다.</b>
 *
 * <p>── 벽 너머로는 못 간다 ──
 * 날아가다 블록에 막히면 거기서 멈추고, 시전자는 그 앞까지만 간다. 투창과 같은 판정이고
 * 이동기로서 자연스러운 제약이라 그대로 뒀다 — 안 그러면 벽을 통과하는 순간이동이 된다.
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class RecallManager {
    private RecallManager() {}

    private static final double SPEED = 1.6;      // 블록/틱
    private static final int SUBSTEPS = 4;        // 관통 방지
    private static final double HIT_RADIUS = 0.9; // 대검이라 투창(0.6)보다 넓다
    private static final int MAX_TICKS = 20;      // 보험 — 어떤 이유로든 안 멈추면 여기서 끝낸다

    /** 도착 직후 이 시간 동안은 낙하 피해를 안 받는다 — 「검을 잡고 착지한다」가 자해면 이상하다. */
    private static final int LAND_GRACE = 30;
    private static final String K_GRACE = "lsRecallGrace";

    private static final List<Blade> ACTIVE = new ArrayList<>();

    private static final class Blade {
        final ServerLevel level;
        final ServerPlayer owner;
        final float damage;
        final double maxRange;
        final Set<Integer> hit = new HashSet<>();
        Display.ItemDisplay display;
        Vec3 dir;
        Vec3 pos;
        double traveled;
        int ticks;
        Blade(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 dir, float damage, double maxRange) {
            this.level = level; this.owner = owner; this.pos = pos;
            this.dir = dir.normalize(); this.damage = damage; this.maxRange = maxRange;
        }
    }

    public static void throwBlade(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 dir,
                                  float damage, double maxRange) {
        Blade b = new Blade(level, owner, start, dir, damage, maxRange);
        Display.ItemDisplay disp = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        disp.setItemStack(new ItemStack(LSRelics.NEMESIS.get()));
        // NONE = 모델의 display 변환을 안 탄다. FIXED 로 두면 모델 자체의 회전이 덧씌워져
        // 우리가 준 방향이 틀어진다 (JavelinManager 가 같은 이유로 NONE 을 쓴다).
        disp.setItemTransform(ItemDisplayContext.NONE);
        disp.setPos(start.x, start.y, start.z);
        b.display = disp;
        orient(b);
        level.addFreshEntity(disp);
        ACTIVE.add(b);
        level.playSound(null, owner.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    /**
     * 칼날(모델 길이축 +Y)이 진행 방향을 향하게 눕힌다 — 검이 «머리부터» 날아간다.
     * 축 회전(스핀)은 주지 않는다. 대검이 뱅글뱅글 돌면 던졌다기보다 굴린 것처럼 보인다.
     * 중심 보정 −0.5 는 모델 한가운데(모델좌표 y≈8 = 0.5블록)를 엔티티 좌표에 맞추는 값이다.
     */
    private static void orient(Blade b) {
        if (b.display == null) return;
        Vector3f d = new Vector3f((float) b.dir.x, (float) b.dir.y, (float) b.dir.z).normalize();
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), d);
        b.display.setTransformation(new Transformation(
            new Vector3f(0, -0.5f, 0), q, new Vector3f(1, 1, 1), new Quaternionf()));
        b.display.setPos(b.pos.x, b.pos.y, b.pos.z);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Blade> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Blade b = it.next();
            if (b.owner.isRemoved() || !b.owner.isAlive()) { discard(b); it.remove(); continue; }

            boolean stop = ++b.ticks >= MAX_TICKS;
            double step = SPEED / SUBSTEPS;
            for (int i = 0; i < SUBSTEPS && !stop; i++) {
                Vec3 next = b.pos.add(b.dir.scale(step));
                HitResult clip = b.level.clip(new ClipContext(b.pos, next,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b.owner));
                if (clip.getType() != HitResult.Type.MISS) { stop = true; break; }
                b.pos = next;
                b.traveled += step;
                hitAlong(b);
                if (b.traveled >= b.maxRange) { stop = true; }
            }

            orient(b);
            DustParticleOptions steel = new DustParticleOptions(new Vector3f(0.62f, 0.70f, 0.85f), 1.1f);
            b.level.sendParticles(steel, b.pos.x, b.pos.y, b.pos.z, 2, 0.06, 0.06, 0.06, 0.0);

            if (stop) { land(b); discard(b); it.remove(); }
        }
    }

    /** 검이 멈춘 자리로 사람을 던진다. 순간이동이 아니라 «날아가는» 것이라 속도를 준다. */
    private static void land(Blade b) {
        Vec3 to = b.pos.subtract(b.owner.position());
        double d = to.length();
        if (d < 0.1) return;
        Vec3 v = to.normalize().scale(Math.min(2.2, 0.55 + d * 0.16));
        b.owner.setDeltaMovement(v.x, Math.max(0.30, v.y + 0.28), v.z);
        b.owner.hurtMarked = true;
        b.owner.resetFallDistance();
        b.owner.getPersistentData().putLong(K_GRACE, b.level.getGameTime() + LAND_GRACE);
        b.level.sendParticles(ParticleTypes.SWEEP_ATTACK, b.pos.x, b.pos.y, b.pos.z, 3, 0.3, 0.3, 0.3, 0.0);
        b.level.playSound(null, b.owner.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.9f, 1.1f);
    }

    private static void discard(Blade b) {
        if (b.display != null && b.display.isAlive()) b.display.discard();
    }

    private static void hitAlong(Blade b) {
        AABB box = new AABB(b.pos.x - HIT_RADIUS, b.pos.y - HIT_RADIUS, b.pos.z - HIT_RADIUS,
                            b.pos.x + HIT_RADIUS, b.pos.y + HIT_RADIUS, b.pos.z + HIT_RADIUS);
        for (LivingEntity e : b.level.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != b.owner && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            if (!b.hit.add(e.getId())) continue;
            LsDamage.hit(e, com.laststardust.relics.item.RelicSkills.relicSource(b.level, b.owner),
                b.damage, "참격 인계");
            b.level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
        }
    }

    /** 착지 유예 — 검을 잡고 날아간 직후의 낙하 피해를 지운다. */
    @SubscribeEvent
    public static void onLandingGrace(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!e.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) return;
        long left = p.getPersistentData().getLong(K_GRACE) - p.level().getGameTime();
        if (left > 0 && left <= LAND_GRACE) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        for (Blade b : ACTIVE) discard(b);
        ACTIVE.clear();
    }
}
