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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 백 개의 창(게볼그 궁극) — "게이트 오브 바빌론" 연출.
// 시전자 등 뒤 허공에 황금 파문(게이트)이 여러 개 열리고, 그 안에서 창이 사출되어
// 주변 적에게 꽂힌다. 5초간 쉬지 않고 쏟아진다.
//
// 창은 히트스캔이다 — 게이트에서 대상까지 즉발 금빛 창격을 그린다. 실제 투사체를 수십 개
// 날리면 서버 부담이 크고, 애니메이션의 "순식간에 꽂히는" 느낌과도 히트스캔이 더 맞다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class BabylonManager {
    private BabylonManager() {}

    private static final double RADIUS = 14.0;      // 창이 닿는 범위
    private static final int FIRE_INTERVAL = 4;      // 4틱마다 일제 사격
    private static final int SPEARS_PER_VOLLEY = 3;
    private static final int GATE_COUNT = 6;         // 동시에 떠 있는 게이트 수

    private static final List<Babylon> ACTIVE = new ArrayList<>();

    private static final class Babylon {
        final ServerLevel level;
        final ServerPlayer caster;
        final float perSpear;
        int ticksLeft;
        final int total;
        long seed;
        Babylon(ServerLevel level, ServerPlayer caster, float perSpear, int ticks) {
            this.level = level; this.caster = caster; this.perSpear = perSpear;
            this.ticksLeft = ticks; this.total = ticks; this.seed = ticks * 2654435761L;
        }
        // Math.random()이 워크플로 환경에서 막혀 있어도 안전하도록 자체 난수
        double rnd() { seed = seed * 6364136223846793005L + 1442695040888963407L; return ((seed >>> 11) & 0xFFFFFFFFFFFFFL) / (double) (1L << 52); }
    }

    public static void start(ServerLevel level, ServerPlayer caster, float perSpear, int ticks) {
        ACTIVE.removeIf(b -> b.caster == caster);
        ACTIVE.add(new Babylon(level, caster, perSpear, ticks));
        // ── 개막 연출: 하늘을 찢으며 게이트가 열린다 ──
        Vec3 c = caster.position().add(0, 1.4, 0);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 6, 0.8, 0.8, 0.8, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 0.4, 0.4, 0.4, 0);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 1.2, 1.2, 1.2, 0.25);
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.35f), 2.2f);
        level.sendParticles(gold, c.x, c.y, c.z, 100, 1.4, 1.2, 1.4, 0.05);
        // 바깥으로 퍼지는 황금 충격 링
        for (int i = 0; i < 40; i++) {
            double a = Math.PI * 2 * i / 40.0;
            double dx = Math.cos(a), dz = Math.sin(a);
            level.sendParticles(ParticleTypes.END_ROD, c.x + dx * 2, c.y - 0.8, c.z + dz * 2,
                0, dx * 0.8, 0.1, dz * 0.8, 0.8);
        }
        // ── 개막 사운드: 즉시 3층 + 시간차 잔향 3층 (크게) ──
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 0.7f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.6f, 0.8f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.2f, 0.5f);
        SoundScheduler.at(level, caster.position(), SoundEvents.TRIDENT_THUNDER.value(), 2.4f, 1.0f, 4);
        SoundScheduler.at(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 0.5f, 8);
        SoundScheduler.at(level, caster.position(), SoundEvents.WARDEN_SONIC_BOOM, 2.0f, 1.2f, 14);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Babylon> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Babylon b = it.next();
            ServerPlayer p = b.caster;
            if (p.isRemoved() || !p.isAlive()) { it.remove(); continue; }
            if (--b.ticksLeft <= 0) { it.remove(); continue; }
            if (!(p.level() instanceof ServerLevel lv)) continue;

            renderGates(b, lv, p);
            if (b.ticksLeft % FIRE_INTERVAL == 0) volley(b, lv, p);
            // 지속 저음 울림 — 창의 폭풍이 계속되고 있다는 압박감
            if (b.ticksLeft % 12 == 0) {
                lv.playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6f, 0.7f);
            }
        }
    }

    // 등 뒤·머리 위로 떠 있는 황금 파문(게이트)들
    private static void renderGates(Babylon b, ServerLevel lv, ServerPlayer p) {
        Vec3 back = p.getViewVector(1.0f).scale(-1); // 등 뒤 방향
        Vec3 base = p.position().add(0, 1.8, 0).add(back.scale(1.2));
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.85f, 0.35f), 1.2f);
        for (int g = 0; g < GATE_COUNT; g++) {
            double ang = g * (Math.PI * 2 / GATE_COUNT) + b.ticksLeft * 0.02;
            double gx = base.x + Math.cos(ang) * 1.8 + back.x * (g % 2);
            double gy = base.y + Math.sin(ang) * 1.0 + (g % 3) * 0.4;
            double gz = base.z + Math.sin(ang) * 1.8 + back.z * (g % 2);
            // 게이트 = 작은 링
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * 2 * i / 6.0 + b.ticksLeft * 0.1;
                lv.sendParticles(gold, gx + Math.cos(a) * 0.35, gy + Math.sin(a) * 0.35, gz, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void volley(Babylon b, ServerLevel lv, ServerPlayer p) {
        AABB box = p.getBoundingBox().inflate(RADIUS);
        List<LivingEntity> targets = lv.getEntitiesOfClass(LivingEntity.class, box,
            en -> en != p && en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager));
        if (targets.isEmpty()) return;

        Vec3 back = p.getViewVector(1.0f).scale(-1);
        Vec3 gateBase = p.position().add(0, 1.8, 0).add(back.scale(1.2));

        for (int s = 0; s < SPEARS_PER_VOLLEY; s++) {
            LivingEntity t = targets.get((int) (b.rnd() * targets.size()));
            if (t == null) continue;
            // 게이트에서 대상 몸통으로 즉발 금빛 창
            Vec3 origin = gateBase.add(new Vec3((b.rnd() - 0.5) * 3, (b.rnd() - 0.5) * 1.5, (b.rnd() - 0.5) * 3));
            Vec3 hit = t.position().add(0, t.getBbHeight() * 0.5, 0);
            spearBeam(lv, origin, hit);

            // 다단히트라 상한을 둔다. 창은 4틱마다 나가는데 무적이 20틱이라 다섯 번 중 네 번이
            // 버려지고 있었다 — 무적을 통째로 풀면 반대로 5배가 되어 과잉이 된다(별빛 폭풍이
            // 그렇게 터졌다). 8틱(초당 2.5회)으로 끊어 설계 의도에 가깝게 되돌린다.
            com.laststardust.relics.LsDamage.hitLimited(t,
                com.laststardust.relics.item.RelicSkills.relicSource(lv, p), b.perSpear,
                "babylon", lv.getGameTime(), 8, "백 개의 창");
            // 창이 꽂히는 임팩트 — 폭발·섬광
            lv.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 12, 0.25, 0.25, 0.25, 0.2);
            lv.sendParticles(ParticleTypes.FLASH, hit.x, hit.y, hit.z, 1, 0, 0, 0, 0);
            lv.playSound(null, hit.x, hit.y, hit.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        // 발사음 — 게이트에서 창이 쏟아지는 소리 (크게)
        lv.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.4f, 1.1f);
    }

    // 게이트→대상 금빛 창격 (짧은 잔상)
    private static void spearBeam(ServerLevel lv, Vec3 from, Vec3 to) {
        Vec3 seg = to.subtract(from);
        double len = seg.length();
        int steps = Math.max(3, (int) (len * 1.5));
        Vec3 unit = seg.scale(1.0 / steps);
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.3f), 1.0f);
        Vec3 at = from;
        for (int i = 0; i <= steps; i++) {
            lv.sendParticles(gold, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            if (i % 2 == 0) lv.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
            at = at.add(unit);
        }
        lv.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
    }
}
