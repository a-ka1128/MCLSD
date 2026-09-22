package com.laststardust.relics.client;

import com.laststardust.relics.blessing.BlessView;

import net.minecraft.client.Minecraft;

// 서버가 보낸 제단 현황을 화면으로 옮기는 얇은 다리.
// LSNetwork(공통 코드)가 Screen 클래스를 직접 참조하지 않게 한 단계 끼워둔다 —
// 전용 서버에는 클라 클래스가 없어 직접 참조하면 NoClassDefFound 가 난다. (TownScreens 와 같은 이유)
public final class BlessScreens {
    private BlessScreens() {}

    public static void update(BlessView view) {
        // 화면이 열려 있든 아니든 **캐시는 항상 갱신한다** — 툴팁은 창을 안 열어도 보인다.
        BlessCache.set(view);
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BlessScreen s) s.setView(view);
    }
}
