package com.laststardust.relics.client;

import net.minecraft.client.Minecraft;

// 서버가 보낸 fate_open 패킷을 실제 화면 열기로 옮기는 얇은 다리.
// LSNetwork(공통 코드)가 Screen 클래스를 직접 참조하지 않게 한 단계 끼워둔다.
public final class FateScreenOpener {
    private FateScreenOpener() {}

    public static void open(String current) {
        Minecraft.getInstance().setScreen(new FateSelectScreen(current));
    }
}
