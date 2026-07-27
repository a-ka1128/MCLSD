package com.laststardust.relics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 과잉 치유(파나케이아 패시브) — 넘친 회복량을 흡수 보호막으로 바꾼다.
//
// 바닐라 흡수 효과(MobEffects.ABSORPTION)는 레벨당 4씩 고정이라 "넘친 만큼"을 표현할 수 없어서
// 흡수량을 직접 다룬다. 대신 우리가 준 몫만 정확히 기억해 뒀다가 만료 시 그만큼만 회수한다 —
// 그러지 않으면 황금 사과 같은 다른 출처의 흡수까지 같이 날려버린다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class ShieldManager {
    private ShieldManager() {}

    private static final Map<UUID, Shield> ACTIVE = new HashMap<>();

    private static final class Shield {
        float granted;   // 우리가 부여한 흡수량
        int ticksLeft;
        Shield(float granted, int ticksLeft) { this.granted = granted; this.ticksLeft = ticksLeft; }
    }

    // 보호막을 더한다. cap은 "우리가 준 몫"의 상한이며 다른 출처의 흡수와는 별개로 센다.
    public static void add(Player target, float amount, float cap, int ticks) {
        if (amount <= 0) return;
        Shield s = ACTIVE.get(target.getUUID());
        float already = s == null ? 0 : s.granted;
        float room = cap - already;
        if (room <= 0) { // 이미 가득 — 지속시간만 갱신
            if (s != null) s.ticksLeft = ticks;
            return;
        }
        float give = Math.min(room, amount);
        target.setAbsorptionAmount(target.getAbsorptionAmount() + give);
        if (s == null) ACTIVE.put(target.getUUID(), new Shield(give, ticks));
        else { s.granted += give; s.ticksLeft = ticks; }
    }

    public static float grantedTo(Player target) {
        Shield s = ACTIVE.get(target.getUUID());
        return s == null ? 0 : s.granted;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Map.Entry<UUID, Shield>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Shield> en = it.next();
            Shield s = en.getValue();
            if (--s.ticksLeft > 0) continue;

            // 만료 — 우리가 준 몫만 회수 (남은 흡수가 그보다 적으면 이미 깎인 것이므로 그만큼만)
            ServerPlayer p = findPlayer(event.getServer(), en.getKey());
            if (p != null) {
                float back = Math.min(s.granted, p.getAbsorptionAmount());
                if (back > 0) p.setAbsorptionAmount(p.getAbsorptionAmount() - back);
            }
            it.remove();
        }
    }

    private static ServerPlayer findPlayer(net.minecraft.server.MinecraftServer server, UUID id) {
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }
}
