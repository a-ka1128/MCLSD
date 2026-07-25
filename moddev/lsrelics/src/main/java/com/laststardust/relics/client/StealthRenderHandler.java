package com.laststardust.relics.client;

import com.laststardust.relics.LSRelics;

import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

// 망각의 안개(스틱스) — "진짜" 은신.
// 바닐라 투명화는 몸만 지우고 착용 방어구와 손에 든 아이템은 그대로 그린다.
// 갑옷 입은 암살자는 사실상 다 보이고, 스틱스는 쌍단검이라 양손에 무기가 2개다
// — 그래서 투명화가 걸려도 "안 걸린 것 같은" 그림이 나왔다.
// 스틱스를 든 채 투명 상태인 플레이어는 렌더를 통째로 건너뛰어 장비까지 완전히 지운다.
//
// 서버가 따로 보낼 게 없다: isInvisible() 은 엔티티 데이터 플래그로, 손에 든 아이템은
// 장비 패킷으로 이미 모든 클라이언트에 동기화된다 — ScopeZoomHandler 와 같은 방식이다.
// 1인칭 손 렌더는 이 이벤트를 타지 않으므로 자기 무기는 계속 보인다(의도된 동작).
@EventBusSubscriber(modid = LSRelics.MODID, value = Dist.CLIENT)
public final class StealthRenderHandler {
    private StealthRenderHandler() {}

    private static boolean holdingStyx(Player player) {
        return player.getMainHandItem().getItem() == LSRelics.ASSASSIN.get()
            || player.getOffhandItem().getItem() == LSRelics.ASSASSIN.get();
    }

    public static boolean isFullyHidden(Player player) {
        return player.isInvisible() && holdingStyx(player);
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (isFullyHidden(event.getEntity())) event.setCanceled(true);
    }
}
