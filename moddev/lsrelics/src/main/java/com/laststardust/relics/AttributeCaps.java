package com.laststardust.relics;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

// 바닐라 속성 상한 해제.
//
// 최대 체력은 바닐라에서 1024로 하드 클램프된다:
//     MAX_HEALTH = new RangedAttribute("...", 20.0, 1.0, 1024.0)
// 그런데 5성 각성 + 전투 스킬트리를 두른 4인 파티는 수백 DPS를 내기 때문에,
// 1024짜리 보스는 몇 초 만에 녹는다. /bossdiff 로 체력 배율을 아무리 올려도
// setBaseValue 단계에서 1024로 잘려 아무 효과가 없다.
//
// 그래서 상한 자체를 올린다. RangedAttribute.maxValue 는 private final 이라
// META-INF/accesstransformer.cfg 로 접근을 열어뒀다.
//
// 상한을 "없애는" 게 아니라 넉넉한 값으로 "올리는" 것이라, 잘못된 값이 들어왔을 때의
// 안전장치(NaN·무한대 방지)는 그대로 남는다.
@EventBusSubscriber(modid = LSRelics.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class AttributeCaps {
    private AttributeCaps() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final double MAX_HEALTH_CAP = 1_000_000.0;

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> raise(Attributes.MAX_HEALTH, "max_health", MAX_HEALTH_CAP));
    }

    private static void raise(Holder<Attribute> holder, String name, double newMax) {
        if (!(holder.value() instanceof RangedAttribute ra)) {
            LOG.warn("[LSRelics] {} 이(가) RangedAttribute 가 아니라 상한을 올리지 못했다", name);
            return;
        }
        double old = ra.getMaxValue();
        if (old >= newMax) return; // 다른 모드가 이미 더 크게 올려놨다면 건드리지 않는다
        ra.maxValue = newMax;
        LOG.info("[LSRelics] 속성 상한 조정: {} {} -> {}", name, old, newMax);
    }
}
