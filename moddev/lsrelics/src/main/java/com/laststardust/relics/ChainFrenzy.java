package com.laststardust.relics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.core.Holder;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// 연쇄 살상(스틱스 패시브)의 버프 만료 관리.
// 처치가 이어지는 동안은 refresh()가 계속 시간을 늘려 유지되고, 처치가 끊기면
// 예약 시간이 지나 붙여둔 이속·공속 모디파이어를 제거한다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class ChainFrenzy {
    private ChainFrenzy() {}

    private static final List<Timer> ACTIVE = new ArrayList<>();

    private static final class Timer {
        final ServerPlayer player;
        final ResourceLocation id;
        final Holder<Attribute> attr;
        int ticksLeft;
        Timer(ServerPlayer player, Holder<Attribute> attr, ResourceLocation id, int ticks) {
            this.player = player; this.attr = attr; this.id = id; this.ticksLeft = ticks;
        }
    }

    // 만료 타이머를 예약/갱신한다. (모디파이어 자체는 RelicEventHandlers 가 붙인다)
    public static void refresh(ServerPlayer player, ResourceLocation id, int ticks) {
        for (Timer t : ACTIVE) {
            if (t.player == player && t.id.equals(id)) { t.ticksLeft = ticks; return; }
        }
        Holder<Attribute> attr = attrFor(player, id);
        if (attr != null) ACTIVE.add(new Timer(player, attr, id, ticks));
    }

    private static Holder<Attribute> attrFor(ServerPlayer p, ResourceLocation id) {
        // id 로 어떤 속성인지 되찾는다 — 붙일 때와 같은 규칙
        var move = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (move != null && move.getModifier(id) != null) return net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
        return net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Timer> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Timer t = it.next();
            if (t.player.isRemoved() || !t.player.isAlive()) { clear(t); it.remove(); continue; }
            if (--t.ticksLeft <= 0) { clear(t); it.remove(); }
        }
    }

    private static void clear(Timer t) {
        AttributeInstance inst = t.player.getAttribute(t.attr);
        if (inst != null) inst.removeModifier(t.id);
    }
}
