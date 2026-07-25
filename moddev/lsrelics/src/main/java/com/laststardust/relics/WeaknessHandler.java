package com.laststardust.relics;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 약점 노출(도끼 균열 붕괴) — 표식된 적이 받는 피해 +10%.
// 표식은 beamHurt가 대상 persistentData "lsWeakUntil"에 만료 틱을 기록.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class WeaknessHandler {
    private WeaknessHandler() {}

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        long until = target.getPersistentData().getLong("lsWeakUntil");
        if (until > target.level().getGameTime()) {
            event.setAmount(event.getAmount() * 1.10f); // +10%
        }
    }
}
