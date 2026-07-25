package com.laststardust.relics.client;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.network.TownOpenPayload;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 마을 관리 화면 키바인드 (기본 N — 게임 설정 › 조작 › Last Stardust 에서 바꿀 수 있다).
//
// ※ M 은 쓰면 안 된다 — Xaero's World Map 의 기본키다. 같은 키를 쓰면 둘 다 열린다.
//   이 모드팩에서 이미 잡혀 있는 키: M(Xaero 월드맵) · Y(Xaero 미니맵 설정) · G(Curios) · F(손 교체) · Q(버리기) · E(인벤)
//
// 화면 자체는 서버가 열어준다: 키를 누르면 신호만 보내고, 서버가 현황을 담아 되돌려주면 그때 열린다.
// 클라가 직접 열면 금고·성벽 같은 값을 알 수 없어 빈 화면이 먼저 뜬 뒤 채워지는 모양이 된다.
@EventBusSubscriber(modid = LSRelics.MODID, value = Dist.CLIENT)
public final class TownKeybind {
    private TownKeybind() {}

    public static final String CATEGORY = "key.categories." + LSRelics.MODID;

    public static final KeyMapping OPEN_TOWN = new KeyMapping(
        "key." + LSRelics.MODID + ".town",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_N,
        CATEGORY);

    // 키 등록(RegisterKeyMappingsEvent)은 MOD 버스라 이 클래스가 아니라 LSRelicsClient 에서 받는다.
    // 중첩 @EventBusSubscriber 도 동작하지만, 버스가 둘로 갈리면 나중에 읽는 사람이 헷갈린다.

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // consumeClick() 은 눌린 횟수를 큐에서 하나씩 꺼낸다 — 누르고 있어도 한 번만 발동한다.
        // while 로 비워야 키를 연타했을 때 눌림이 다음 틱으로 밀리지 않는다.
        boolean fire = false;
        while (OPEN_TOWN.consumeClick()) fire = true;
        if (fire) PacketDistributor.sendToServer(TownOpenPayload.INSTANCE);
    }
}
