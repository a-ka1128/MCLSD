package com.laststardust.relics.client;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.item.SolarMusket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

// 조준이 완성되면 1인칭 총 모델을 감춘다.
// 총열이 39칸짜리라 3배 줌 상태에서는 화면 절반을 가려버린다 — 실제 조준경을 들여다보는
// 상황이니 총신이 보이지 않는 편이 자연스럽고 시야도 확보된다.
// 진입 중(0.5초)에는 그대로 보여줘서 "겨누는 동작"이 눈에 남게 한다.
@EventBusSubscriber(modid = LSRelics.MODID, value = Dist.CLIENT)
public final class ScopeRenderHandler {
    private ScopeRenderHandler() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack stack = event.getItemStack();
        if (stack.getItem() != LSRelics.GUNNER.get()) return;
        // 주손에 들고 완전히 조준한 상태에서만 숨긴다
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!SolarMusket.isScoped(player, player.getMainHandItem())) return;
        event.setCanceled(true);
    }
}
