package com.laststardust.relics.client;

import com.laststardust.relics.relic.RelicView;

import net.minecraft.client.Minecraft;

/**
 * 서버가 보낸 유물 현황을 화면으로 옮기는 얇은 다리.
 *
 * <p>⚠️ {@code LSNetwork}(공통 코드)가 {@code Screen} 클래스를 <b>직접 참조하지 않게</b>
 * 한 단계 끼워 둔다 — 전용 서버에는 클라 클래스가 없어 직접 참조하면
 * {@code NoClassDefFoundError} 가 난다. {@code TownScreens} 와 같은 이유, 같은 모양이다.
 */
public final class RelicScreens {
    private RelicScreens() {}

    public static void show(RelicView view) {
        Minecraft.getInstance().setScreen(new RelicAltarScreen(view));
    }
}
