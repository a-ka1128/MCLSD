package com.laststardust.relics.client;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.item.RelicActions;
import com.laststardust.relics.network.RelicInputPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 클라: 좌클릭(공격키) 홀드를 폴링해 평타 패킷으로 넘긴다.
//
// 여기 남은 건 평타 하나뿐이다. 스킬은 전부 전용 키(RelicKeybinds — R/V/C/X)로 옮겼다:
//   · 웅크림+좌클릭 = 궁극  → 절벽에서 웅크린 채 때리면 60초 궁극기가 헛발로 나갔다
//   · 웅크림+우클릭 = 추가  → 웅크리고 상자를 열면 상자 대신 스킬이 나갔다
//   · 쉬프트 두 번  = 이동  → 절벽을 내려다보려 톡톡 치면 밖으로 돌진했다
@EventBusSubscriber(modid = LSRelics.MODID, value = Dist.CLIENT)
public final class RelicInputHandler {
    private RelicInputHandler() {}

    private static boolean wasAttackDown = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) {
            wasAttackDown = false;
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof RelicActions relic)) {
            wasAttackDown = false;
            return;
        }
        boolean down = mc.options.keyAttack.isDown();
        if (relic.firesOnLeftClick() && down) {
            PacketDistributor.sendToServer(new RelicInputPayload(RelicInputPayload.ATTACK));
        }
        wasAttackDown = down;
    }
}
