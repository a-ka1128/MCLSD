package com.laststardust.relics.client;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.item.SolarMusket;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

// 솔라리 스코프 — 시야각 축소(줌).
// 서버는 아무것도 보낼 필요가 없다: 아이템 사용 상태(isUsingItem)와 남은 사용 틱은
// 이미 클라이언트가 알고 있어서, 서버와 완전히 같은 판정을 로컬에서 계산할 수 있다.
@EventBusSubscriber(modid = LSRelics.MODID, value = Dist.CLIENT)
public final class ScopeZoomHandler {
    private ScopeZoomHandler() {}

    private static final float ZOOM = 0.35f; // 최대 줌에서의 FOV 배율 (약 3배 확대)

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (!(event.getPlayer() instanceof LocalPlayer player)) return;
        ItemStack used = player.getUseItem();
        if (used.getItem() != LSRelics.GUNNER.get()) return;
        if (!player.isUsingItem()) return;

        // 진입 0.5초 동안 부드럽게 줌인 — 서버의 피해 판정과 같은 진행도를 쓴다
        float p = SolarMusket.scopeProgress(player);
        event.setNewFovModifier(event.getFovModifier() * (1.0f + (ZOOM - 1.0f) * p));
    }
}
