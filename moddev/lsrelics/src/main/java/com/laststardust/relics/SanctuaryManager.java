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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 수호의 성역(파나케이아 추가) — 지면에 깔리는 회복·경감 지대.
// 우리 게임의 핵심이 거점 방어라, 성벽 위에 깔아두고 버티는 스킬이 구조적으로 잘 맞는다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class SanctuaryManager {
    private SanctuaryManager() {}

    public static final float DAMAGE_REDUCTION = 0.20f; // 안에 있는 아군 받는 피해 -20%
    private static final int HEAL_INTERVAL = 20;        // 1초마다 회복·연소

    private static final List<Zone> ACTIVE = new ArrayList<>();

    private static final class Zone {
        final ServerLevel level;
        final Vec3 center;
        final double radius;
        final float healPerSecond;
        final float burnPerSecond;   // 지대 안 적이 초당 받는 피해
        final ServerPlayer caster;   // 피해 출처 (기여도 집계가 시전자에게 잡히도록)
        int ticksLeft;
        Zone(ServerLevel level, Vec3 center, double radius, float healPerSecond,
             float burnPerSecond, ServerPlayer caster, int ticks) {
            this.level = level; this.center = center; this.radius = radius;
            this.healPerSecond = healPerSecond; this.burnPerSecond = burnPerSecond;
            this.caster = caster; this.ticksLeft = ticks;
        }
    }

    public static void start(ServerLevel level, Vec3 center, double radius, float healPerSecond,
                             float burnPerSecond, ServerPlayer caster, int ticks) {
        ACTIVE.add(new Zone(level, center, radius, healPerSecond, burnPerSecond, caster, ticks));
        level.playSound(null, center.x, center.y, center.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.4f);
    }

    // ── 지대 안의 적을 태운다 ──
    //
    // 힐러는 조준선에 아군이 걸리면 힐이 우선이라(설계), 파티전에서는 평타가 거의 안 나간다.
    // 그래서 딜 기여를 평타가 아니라 **지대**에서 만든다 — 힐러가 자기 본업(성역을 잘 까는 것)을
    // 하는 것만으로 파티 화력에 보태진다. 역할과 보상이 어긋나지 않는다.
    //
    // ※ 단일 표적 측정(훈련 더미)에서는 작아 보이지만 **적이 많을수록 비례해서 커진다.**
    //   수성전처럼 몰려드는 상황이 이 스킬의 제자리다.
    private static void burn(Zone z) {
        if (z.burnPerSecond <= 0 || z.caster == null) return;
        var box = new net.minecraft.world.phys.AABB(
            z.center.x - z.radius, z.center.y - z.radius, z.center.z - z.radius,
            z.center.x + z.radius, z.center.y + z.radius, z.center.z + z.radius);
        var src = com.laststardust.relics.item.RelicSkills.relicSource(z.level, z.caster);
        for (net.minecraft.world.entity.LivingEntity e : z.level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, box,
                en -> en.isAlive() && !(en instanceof Player)
                      && !(en instanceof net.minecraft.world.entity.npc.AbstractVillager))) {
            if (e.distanceToSqr(z.center) > z.radius * z.radius) continue;
            // 다단히트라 상한을 둔다 — 설계 주기(1초)를 그대로 쓴다 (LsDamage 주석 참고)
            com.laststardust.relics.LsDamage.hitLimited(e, src, z.burnPerSecond,
                "sanctuary", z.level.getGameTime(), HEAL_INTERVAL, "성역");
            z.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.25, 0.35, 0.25, 0.01);
        }
    }

    // 이 플레이어가 성역 안에 있는가 (피해 경감 판정용).
    public static boolean isProtected(Player player) {
        for (Zone z : ACTIVE) {
            if (player.level() != z.level) continue;
            if (player.distanceToSqr(z.center) <= z.radius * z.radius) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Zone> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Zone z = it.next();
            if (--z.ticksLeft <= 0) { it.remove(); continue; }

            if (z.ticksLeft % HEAL_INTERVAL == 0) {
                for (ServerPlayer p : z.level.players()) {
                    if (!p.isAlive() || p.isSpectator()) continue;
                    if (p.distanceToSqr(z.center) > z.radius * z.radius) continue;
                    if (p.getHealth() >= p.getMaxHealth()) continue;
                    p.heal(z.healPerSecond);
                    // 성역은 깐 사람이 위협도를 진다(z.caster). 뿌리고 도망가는 걸 막는다.
                    ThreatManager.addHealThreat(z.level, z.caster, z.healPerSecond);
                    z.level.sendParticles(ParticleTypes.HEART,
                        p.getX(), p.getY() + p.getBbHeight() + 0.3, p.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
                }
                burn(z);
            }
            render(z);
        }
    }

    private static void render(Zone z) {
        ServerLevel lv = z.level;
        // 경계 링 — 어디까지가 성역인지 항상 보이게 (안에 서 있어야 효과를 받으니 중요)
        int n = 28;
        double spin = (z.ticksLeft % 200) * 0.03;
        DustParticleOptions soft = new DustParticleOptions(new Vector3f(0.80f, 1.0f, 0.85f), 1.1f);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + spin;
            double x = z.center.x + Math.cos(a) * z.radius;
            double zz = z.center.z + Math.sin(a) * z.radius;
            lv.sendParticles(soft, x, z.center.y + 0.15, zz, 1, 0.0, 0.02, 0.0, 0.0);
        }
        if (z.ticksLeft % 4 == 0) {
            lv.sendParticles(ParticleTypes.END_ROD,
                z.center.x, z.center.y + 0.4, z.center.z, 2, z.radius * 0.4, 0.3, z.radius * 0.4, 0.01);
        }
        if (z.ticksLeft % 40 == 0) {
            lv.playSound(null, z.center.x, z.center.y, z.center.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f, 1.7f);
        }
    }
}
