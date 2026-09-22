package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 바닥에 남아 초당 피해를 주는 구형 지대 — 「장판딜」.
 *
 * <p>── 왜 새로 만들었나 ──
 * 비슷한 것이 이미 둘 있었지만 둘 다 못 쓴다.
 * <ul>
 *   <li>{@link SanctuaryManager} 는 아군 회복·경감이 한 몸이라, 이걸 빌려 쓰면 헤카테의 밤
 *       안에 선 아군이 파나케이아의 성역 경감(−20%)을 공짜로 받는다. 남의 유물 정체성이
 *       조용히 새어나온다.</li>
 *   <li>{@link FissureManager} 는 부채꼴 전용이고 도끼의 균열 연출에 묶여 있다.</li>
 * </ul>
 * 그래서 «원 모양 + 초당 피해» 만 하는 것을 따로 둔다. 헤카테의 밤 · 결속의 매듭 ·
 * 만상의 화음 셋이 이걸 공유한다.
 *
 * <p>── {@code key} 를 반드시 스킬마다 다르게 줄 것 ──
 * {@link LsDamage#hitLimited} 의 도장을 같은 이름으로 찍으면 서로의 피해를 지운다
 * ({@code LsDamage} 머리말: 백창과 장판이 그렇게 간섭했다). 지대가 겹칠 수 있는 이상
 * — 하르모니아의 매듭과 화음은 실제로 겹치라고 만든 것이다 — 이건 선택이 아니다.
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class DamageZoneManager {
    private DamageZoneManager() {}

    /** 피해 주기. 넘기는 값이 «초당» 이므로 이 값과 한 몸이다. */
    private static final int INTERVAL = 20;

    private static final List<Zone> ACTIVE = new ArrayList<>();

    private static final class Zone {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 center;
        final double radius;
        final float damagePerSecond;
        final String key;            // hitLimited 도장 — 스킬마다 달라야 한다
        final String label;          // /dummy 계측용 이름표
        final DustParticleOptions dust;
        final ParticleOptions ambient;
        int ticksLeft;

        Zone(ServerLevel level, ServerPlayer caster, Vec3 center, double radius, float dps,
             int ticks, String key, String label, DustParticleOptions dust, ParticleOptions ambient) {
            this.level = level; this.caster = caster; this.center = center; this.radius = radius;
            this.damagePerSecond = dps; this.ticksLeft = ticks;
            this.key = key; this.label = label; this.dust = dust; this.ambient = ambient;
        }
    }

    /** 0xRRGGBB → 파티클 색. 스킬 쪽 색 상수(TEAL·ROSE)를 그대로 넘길 수 있게 한다. */
    public static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(
            ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), scale);
    }

    public static void start(ServerLevel level, ServerPlayer caster, Vec3 center, double radius,
                             float damagePerSecond, int ticks, String key, String label,
                             int rgb, ParticleOptions ambient) {
        ACTIVE.add(new Zone(level, caster, center, radius, damagePerSecond, ticks,
            key, label, dust(rgb, 1.3f), ambient));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Zone> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Zone z = it.next();
            // 시전자가 죽거나 나가면 지대도 사라진다 (FissureManager 와 같은 규칙).
            // 깔아두고 로그아웃해서 남의 몹을 대신 잡아주는 걸 막는다.
            if (z.caster.isRemoved() || !z.caster.isAlive()) { it.remove(); continue; }
            if (--z.ticksLeft <= 0) { it.remove(); continue; }

            render(z);
            if (z.ticksLeft % INTERVAL != 0) continue;

            AABB box = new AABB(z.center.x - z.radius, z.center.y - z.radius, z.center.z - z.radius,
                                z.center.x + z.radius, z.center.y + z.radius, z.center.z + z.radius);
            var src = com.laststardust.relics.item.RelicSkills.relicSource(z.level, z.caster);
            for (LivingEntity e : z.level.getEntitiesOfClass(LivingEntity.class, box,
                    en -> en.isAlive() && !(en instanceof Player) && !(en instanceof AbstractVillager))) {
                if (e.distanceToSqr(z.center) > z.radius * z.radius) continue;
                // 상한 간격은 설계 주기(1초)를 그대로 쓴다 — 파티가 같이 때려도 그 초의 장판
                // 피해가 통째로 사라지지 않게 하는 것이 목적이지, 그 이상 늘리려는 게 아니다.
                LsDamage.hitLimited(e, src, z.damagePerSecond, z.key,
                    z.level.getGameTime(), INTERVAL, z.label);
                z.level.sendParticles(z.ambient,
                    e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.25, 0.35, 0.25, 0.01);
            }
        }
    }

    /**
     * 경계선을 계속 보여준다.
     *
     * <p>장판은 «어디까지가 안인가» 가 안 보이면 그냥 안 밟는다 — 아군 버프 지대(매듭)와 겹칠
     * 때는 더 그렇다. 다만 매 틱 원을 다 그리면 파티클이 폭발하므로, 링을 천천히 돌리면서
     * 몇 점씩만 찍는다. 눈에는 이어진 원으로 보이고 비용은 1/8 이다.
     */
    private static void render(Zone z) {
        if (z.ticksLeft % 2 != 0) return;
        int n = 8;
        double spin = (z.ticksLeft % 160) * 0.04;
        for (int i = 0; i < n; i++) {
            double a = spin + Math.PI * 2 * i / n;
            double x = z.center.x + Math.cos(a) * z.radius;
            double zz = z.center.z + Math.sin(a) * z.radius;
            z.level.sendParticles(z.dust, x, z.center.y + 0.2, zz, 1, 0.02, 0.08, 0.02, 0.0);
        }
        // 안쪽에도 드문드문 — 테두리만 있으면 «빈 원» 으로 보인다
        if (z.ticksLeft % 8 == 0) {
            var rnd = z.level.getRandom();
            for (int i = 0; i < 4; i++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(rnd.nextDouble()) * z.radius;   // 균일 분포 (안 하면 가운데로 몰린다)
                z.level.sendParticles(ParticleTypes.SMOKE,
                    z.center.x + Math.cos(a) * r, z.center.y + 0.1, z.center.z + Math.sin(a) * r,
                    1, 0.05, 0.02, 0.05, 0.0);
            }
        }
    }

    /** 서버가 내려갈 때 비운다 — 싱글에서 월드를 바꿔 열면 이전 레벨 참조가 남는다. */
    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
