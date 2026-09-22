package com.laststardust.relics.client;

import com.laststardust.relics.shop.ShopView;

import net.minecraft.client.Minecraft;

/**
 * 상점 화면으로 옮기는 얇은 다리.
 *
 * <p>⚠️ 공통 코드가 {@code Screen} 을 직접 참조하지 않게 한 단계 끼워 둔다 —
 * 전용 서버에는 클라 클래스가 없어 {@code NoClassDefFoundError} 가 난다
 * ({@code TownScreens}·{@code RelicScreens} 와 같은 이유).
 */
public final class ShopScreens {
    private ShopScreens() {}

    public static void show(ShopView view) {
        Minecraft mc = Minecraft.getInstance();
        // 이미 열려 있으면 갱신만 — 산 직후 창이 깜빡이며 다시 뜨면 목록 위치가 튄다.
        if (mc.screen instanceof ShopScreen s) s.setView(view);
        else mc.setScreen(new ShopScreen(view));
    }
}
