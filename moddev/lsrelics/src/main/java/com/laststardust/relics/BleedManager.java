package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 출혈(게볼그) — 바닐라에 출혈 효과가 없어 직접 구현한 지속 피해.
// 붉은 피 파티클과 함께 주기적으로 소량 피해를 준다. 같은 대상에 다시 걸면 시간만 갱신한다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class BleedManager {
    private BleedManager() {}

    private static final int INTERVAL = 10; // 0.5초마다

    private static final List<Bleed> ACTIVE = new ArrayList<>();

    private static final class Bleed {
        final ServerLevel level;
        final LivingEntity victim;
        final ServerPlayer source;
        final float perTick; // INTERVAL 마다 주는 피해
        int ticksLeft;
        Bleed(ServerLevel level, LivingEntity victim, ServerPlayer source, float perTick, int ticks) {
            this.level = level; this.victim = victim; this.source = source;
            this.perTick = perTick; this.ticksLeft = ticks;
        }
    }

    // perSecond: 초당 총 출혈 피해. durationTicks 동안 지속.
    public static void apply(ServerLevel level, LivingEntity victim, ServerPlayer source,
                             float perSecond, int durationTicks) {
        for (Bleed b : ACTIVE) {
            if (b.victim == victim) { b.ticksLeft = Math.max(b.ticksLeft, durationTicks); return; }
        }
        ACTIVE.add(new Bleed(level, victim, source, perSecond * INTERVAL / 20.0f, durationTicks));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Bleed> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Bleed b = it.next();
            if (b.victim.isRemoved() || !b.victim.isAlive()) { it.remove(); continue; }
            if (b.ticksLeft % INTERVAL == 0) {
                com.laststardust.relics.LsDamage.hit(b.victim, com.laststardust.relics.item.RelicSkills.relicSource(b.level, b.source), b.perTick, "출혈");
                Vec3 c = b.victim.position();
                b.level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    c.x, c.y + b.victim.getBbHeight() * 0.5, c.z, 3, 0.2, 0.2, 0.2, 0.0);
            }
            if (--b.ticksLeft <= 0) it.remove();
        }
    }
}
