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

// 일식(마총 궁극) — 2초 차징 후 전방으로 초대형 관통 광선.
//
// 조준 방향은 발사 순간에 확정한다. 차징 중에도 겨냥을 고칠 수 있어야
// "숨 참고 조준하다 쏜다"는 감각이 살고, 몰려오는 줄을 노리는 재미가 생긴다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class EclipseManager {
    private EclipseManager() {}

    private static final double RANGE = 60.0;      // 사거리 — 8종 중 최장
    private static final double RADIUS = 2.5;      // 광선 반경(판정)
    private static final int SUBSTEPS = 120;       // 광선 경로 표본 수

    private static final List<Eclipse> ACTIVE = new ArrayList<>();

    private static final class Eclipse {
        final ServerLevel level;
        final ServerPlayer caster;
        final float damage;
        final int totalTicks;
        int ticksLeft;
        Eclipse(ServerLevel level, ServerPlayer caster, float damage, int ticks) {
            this.level = level; this.caster = caster; this.damage = damage;
            this.totalTicks = ticks; this.ticksLeft = ticks;
        }
    }

    public static void start(ServerLevel level, ServerPlayer caster, float damage, int chargeTicks) {
        ACTIVE.add(new Eclipse(level, caster, damage, chargeTicks));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Eclipse> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Eclipse e = it.next();
            if (e.caster.isRemoved() || !e.caster.isAlive()) { it.remove(); continue; } // 죽으면 불발
            if (--e.ticksLeft <= 0) { fire(e); it.remove(); continue; }
            charging(e);
        }
    }

    // 차징 — 총구 앞에 빛이 모이고, 조준선이 점점 뚜렷해진다
    private static void charging(Eclipse e) {
        ServerLevel lv = e.level;
        double progress = 1.0 - (double) e.ticksLeft / e.totalTicks; // 0→1
        Vec3 eye = e.caster.getEyePosition();
        Vec3 look = e.caster.getViewVector(1.0f);
        Vec3 muzzle = eye.add(look.scale(1.2));

        // 총구로 빨려드는 빛 — 바깥에서 안으로
        double r = 2.2 * (1.0 - progress * 0.8);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8.0 + progress * 8.0;
            Vec3 side = look.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 up = side.cross(look).normalize();
            Vec3 p = muzzle.add(side.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
            lv.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z,
                0, (muzzle.x - p.x) * 0.3, (muzzle.y - p.y) * 0.3, (muzzle.z - p.z) * 0.3, 0.5);
        }
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.88f, 0.45f), 1.4f);
        lv.sendParticles(gold, muzzle.x, muzzle.y, muzzle.z, 4, 0.15, 0.15, 0.15, 0.0);

        // 조준선 예고 — 진행될수록 촘촘해져서 어디로 나갈지 보인다
        int dots = (int) (6 + progress * 26);
        for (int i = 1; i <= dots; i++) {
            Vec3 p = eye.add(look.scale(RANGE * i / (double) dots));
            lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }

        if (e.ticksLeft % 4 == 0) {
            lv.playSound(null, e.caster.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.8f, 0.7f + (float) progress * 1.1f);
        }
        if (e.ticksLeft % 10 == 0) {
            lv.playSound(null, e.caster.blockPosition(), SoundEvents.BEACON_AMBIENT,
                SoundSource.PLAYERS, 1.2f, 0.5f + (float) progress);
        }
    }

    // 발사 — 시선 방향 직선 관통
    private static void fire(Eclipse ec) {
        ServerLevel lv = ec.level;
        ServerPlayer p = ec.caster;
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0f);

        // 벽에 막히면 거기까지만 (지형을 관통하진 않는다)
        Vec3 far = eye.add(look.scale(RANGE));
        HitResult clip = lv.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = clip.getType() == HitResult.Type.MISS ? far : clip.getLocation();
        double reach = eye.distanceTo(end);

        // 경로상 전원 관통 — 같은 대상이 중복으로 맞지 않게 한 번만 담는다
        AABB box = new AABB(eye, end).inflate(RADIUS + 1.0);
        for (LivingEntity t : lv.getEntitiesOfClass(LivingEntity.class, box,
                en -> en != p && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
            Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
            Vec3 rel = c.subtract(eye);
            double along = rel.dot(look);
            if (along < 0 || along > reach) continue;            // 광선 뒤쪽이거나 사거리 밖
            if (rel.subtract(look.scale(along)).length() > RADIUS) continue; // 광선에서 너무 멀다
            com.laststardust.relics.LsDamage.hit(t, com.laststardust.relics.item.RelicSkills.relicSource(lv, p), ec.damage, "일식");
            lv.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        }

        // ── 연출: 굵은 백금색 광선 ──
        DustParticleOptions core = new DustParticleOptions(new Vector3f(1.0f, 0.97f, 0.85f), 2.6f);
        DustParticleOptions halo = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.35f), 1.8f);
        for (int i = 0; i <= SUBSTEPS; i++) {
            double d = reach * i / (double) SUBSTEPS;
            Vec3 at = eye.add(look.scale(d));
            lv.sendParticles(core, at.x, at.y, at.z, 2, 0.12, 0.12, 0.12, 0.0);
            lv.sendParticles(halo, at.x, at.y, at.z, 2, 0.45, 0.45, 0.45, 0.0);
            if (i % 3 == 0) lv.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.3, 0.3, 0.3, 0.01);
        }
        lv.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 3, 0, 0, 0, 0);
        lv.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 4, 0.5, 0.5, 0.5, 0.0);
        lv.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 90, 1.0, 1.0, 1.0, 0.3);

        // 총구 폭풍 + 반동
        Vec3 muzzle = eye.add(look.scale(1.2));
        lv.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 2, 0, 0, 0, 0);
        lv.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 40, 0.4, 0.4, 0.4, 0.06);
        p.setDeltaMovement(p.getDeltaMovement().add(-look.x * 0.45, 0.12, -look.z * 0.45));
        p.hurtMarked = true;

        // ── 사운드: 발사 3층 + 잔향 3층 ──
        Vec3 at = new Vec3(p.getX(), p.getY(), p.getZ());
        lv.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 0.8f);
        lv.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6f, 0.6f);
        lv.playSound(null, p.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.8f, 0.5f);
        SoundScheduler.at(lv, at, SoundEvents.TRIDENT_THUNDER.value(), 1.8f, 0.9f, 3);
        SoundScheduler.at(lv, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6f, 0.5f, 7);
        SoundScheduler.at(lv, at, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.6f, 13);
    }
}
