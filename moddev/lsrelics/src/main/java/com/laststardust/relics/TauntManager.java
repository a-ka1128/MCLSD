package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 도발(어그로 유인) — 몇 초간 지정 적들이 시전자를 계속 노리게 유지.
// setTarget은 한 틱 뒤 AI가 재평가할 수 있어, 지속시간 동안 매 틱 재적용.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class TauntManager {
    private TauntManager() {}

    private static final List<Taunt> ACTIVE = new ArrayList<>();

    private static final class Taunt {
        final Player player;
        final List<Mob> mobs;
        int ticksLeft;
        Taunt(Player player, List<Mob> mobs, int ticksLeft) {
            this.player = player;
            this.mobs = mobs;
            this.ticksLeft = ticksLeft;
        }
    }

    // 반경 내 적대 몹을 시전자에게 도발. durationTicks 동안 유지.
    public static void taunt(ServerLevel level, Player player, double radius, int durationTicks) {
        AABB box = player.getBoundingBox().inflate(radius);
        List<Mob> mobs = new ArrayList<>();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive())) {
            mob.setTarget(player);
            mobs.add(mob);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                mob.getX(), mob.getEyeY() + 0.6, mob.getZ(), 5, 0.25, 0.25, 0.25, 0.0);
        }
        if (!mobs.isEmpty()) ACTIVE.add(new Taunt(player, mobs, durationTicks));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Taunt> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Taunt t = it.next();
            if (--t.ticksLeft <= 0 || !t.player.isAlive() || t.player.isRemoved()) {
                it.remove();
                continue;
            }
            for (Mob mob : t.mobs) {
                // 살아있고 시야권(40칸) 내면 계속 시전자를 노리게
                if (mob.isAlive() && mob.distanceToSqr(t.player) < 1600.0) {
                    mob.setTarget(t.player);
                }
            }
        }
    }
}
