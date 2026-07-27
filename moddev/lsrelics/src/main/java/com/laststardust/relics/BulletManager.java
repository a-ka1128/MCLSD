package com.laststardust.relics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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

// 태양탄 — 솔라리(마총)의 평타 탄환.
// 별빛 탄(BoltManager)과 달리 빠르고 관통한다: 첫 대상 100%, 이후 60%로 최대 3체.
// 총알이라 비행 연출은 최소한으로(궤적 잔상만) — 초당 1발이라 화면을 가릴 일은 없다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class BulletManager {
    private BulletManager() {}

    private static final double SPEED = 3.0;        // 블록/틱 (별빛 탄 1.6의 약 2배)
    private static final double SCOPE_SPEED = 5.0;  // 스코프 사격 — 원거리에서 편차 없이 맞도록
    private static final double HIT_RADIUS = 0.55;
    private static final int SUBSTEPS = 6;         // 빠른 만큼 잘게 쪼개야 통과하지 않는다
    private static final int MAX_PIERCE = 3;
    private static final float PIERCE_FALLOFF = 0.6f;

    // ── 패시브 "저격" ──
    // 발사 지점에서 멀리 맞을수록 아프다.
    // 추가 스킬 "산탄"이 정반대(가까울수록 세다)라 무기 하나가 거리축 양 끝을 담당하게 된다.
    //
    // ── 상한 거리를 50칸 -> 30칸으로 당겼다 (2026-07-27) ──
    // 예전엔 1칸당 0.8%, 50칸에서 +40% 였다. 그런데 공성은 성역 반경 24칸 안에서 벌어져서
    // **실전에서 상한에 닿을 일이 사실상 없었다** — 20칸에서 겨우 +16%다. 있으나 마나였다.
    // 30칸에서 +40% 가 되도록 기울기를 세운다. 구간이 짧아진 만큼 거리 한 칸의 값이 커져,
    // "한 발짝 물러선다"는 선택이 실제로 딜로 돌아온다.
    //   10칸 +13% · 20칸 +27% · 30칸 이상 +40%
    private static final double SNIPE_PER_BLOCK = 0.0133; // 1칸당 1.33% = 30칸에서 상한
    private static final float SNIPE_CAP = 0.40f;

    public static float snipeBonus(double distance) {
        return Math.min(SNIPE_CAP, (float) (distance * SNIPE_PER_BLOCK));
    }

    private static final List<Bullet> ACTIVE = new ArrayList<>();

    private static final class Bullet {
        final ServerLevel level;
        final ServerPlayer owner;
        final Vec3 dir;
        final float damage;
        final double speed;
        final Vec3 origin;
        final Set<Integer> hit = new HashSet<>();
        Vec3 pos;
        int ticksLeft;
        int pierced;
        Bullet(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 dir, float damage, int ticksLeft, double speed) {
            this.level = level; this.owner = owner; this.pos = pos;
            this.dir = dir; this.damage = damage; this.ticksLeft = ticksLeft; this.speed = speed;
            this.origin = pos;
        }
    }

    public static void fire(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 dir,
                            float damage, int ticks, boolean scoped) {
        ACTIVE.add(new Bullet(level, owner, start, dir.normalize(), damage, ticks,
            scoped ? SCOPE_SPEED : SPEED));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Bullet> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Bullet b = it.next();
            if (--b.ticksLeft <= 0) { it.remove(); continue; }

            boolean done = false;
            double step = b.speed / SUBSTEPS;
            for (int i = 0; i < SUBSTEPS && !done; i++) {
                Vec3 next = b.pos.add(b.dir.scale(step));

                HitResult clip = b.level.clip(new ClipContext(b.pos, next,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, b.owner));
                if (clip.getType() != HitResult.Type.MISS) {
                    blockImpact(b, clip.getLocation());
                    done = true;
                    break;
                }

                AABB box = new AABB(next.x - HIT_RADIUS, next.y - HIT_RADIUS, next.z - HIT_RADIUS,
                                    next.x + HIT_RADIUS, next.y + HIT_RADIUS, next.z + HIT_RADIUS);
                for (LivingEntity e : b.level.getEntitiesOfClass(LivingEntity.class, box,
                        en -> en != b.owner && en.isAlive()
                              && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
                    if (!b.hit.add(e.getId())) continue; // 같은 대상 중복 타격 방지
                    float dmg = b.pierced == 0 ? b.damage : b.damage * PIERCE_FALLOFF;
                    dmg *= 1.0f + snipeBonus(b.origin.distanceTo(next)); // 패시브 "저격"
                    // 무적 프레임 무시 — 파티에서 표적이 항상 무적이라 스코프 사격이 통째로
                    // 씹히고 있었다 (LsDamage 주석 참고)
                    com.laststardust.relics.LsDamage.hit(e,
                        com.laststardust.relics.item.RelicSkills.relicSource(b.level, b.owner), dmg);
                    hitFx(b.level, next);
                    if (++b.pierced >= MAX_PIERCE) { done = true; }
                    break;
                }
                if (done) break;
                b.pos = next;
            }
            if (done) { it.remove(); continue; }

            // 궤적 — 위치에만 짧게
            DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.35f), 0.7f);
            b.level.sendParticles(gold, b.pos.x, b.pos.y, b.pos.z, 2, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private static void hitFx(ServerLevel lv, Vec3 at) {
        lv.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, 0.18, 0.18, 0.18, 0.1);
        lv.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 5, 0.12, 0.12, 0.12, 0.02);
        lv.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7f, 1.4f);
    }

    private static void blockImpact(Bullet b, Vec3 at) {
        b.level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.08, 0.08, 0.08, 0.01);
        b.level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 5, 0.1, 0.1, 0.1, 0.05);
        b.level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.5f, 1.6f);
    }
}
