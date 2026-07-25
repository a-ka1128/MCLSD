package com.laststardust.relics;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

// 클라이언트 전용 설정. (좌클릭 입력은 client/RelicInputHandler, 마을 키는 client/TownKeybind 가 담당)
@EventBusSubscriber(modid = LSRelics.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LSRelicsClient {
    private LSRelicsClient() {}

    // MenuType ↔ Screen 연결. 이게 없으면 서버가 메뉴를 열어도 클라가 뭘 그릴지 몰라
    // **아무 일도 일어나지 않는다** — 에러조차 안 나서 원인을 찾기 어렵다.
    @SubscribeEvent
    public static void onRegisterScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.laststardust.relics.town.TownMenu.TYPE.get(),
            com.laststardust.relics.client.TownTrackScreen::new);
    }

    // 키바인드 등록. 키 자체와 눌림 처리는 TownKeybind / RelicKeybinds 에 있고, 등록만 여기서 받는다
    // (RegisterKeyMappingsEvent 는 MOD 버스인데 그 클래스들은 게임 버스에 붙어 있어서).
    @SubscribeEvent
    public static void onRegisterKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(com.laststardust.relics.client.TownKeybind.OPEN_TOWN);
        for (net.minecraft.client.KeyMapping k : com.laststardust.relics.client.RelicKeybinds.all()) {
            event.register(k);
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 활 모델의 당김 상태 예측(현재는 즉발 연사라 거의 쓰이지 않지만 모델 호환용)
            ItemProperties.register(LSRelics.HUNTER.get(),
                ResourceLocation.withDefaultNamespace("pull"),
                (stack, level, entity, seed) -> {
                    if (entity == null || entity.getUseItem() != stack) return 0.0F;
                    return (float) (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F;
                });
            ItemProperties.register(LSRelics.HUNTER.get(),
                ResourceLocation.withDefaultNamespace("pulling"),
                (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
        });
    }
}
