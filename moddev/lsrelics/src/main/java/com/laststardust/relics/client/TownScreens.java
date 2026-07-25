package com.laststardust.relics.client;

import com.laststardust.relics.town.TownView;

import net.minecraft.client.Minecraft;

// 서버가 보낸 현황을 화면으로 옮기는 얇은 다리.
// LSNetwork(공통 코드)가 Screen 클래스를 직접 참조하지 않게 한 단계 끼워둔다 —
// 전용 서버에는 클라 클래스가 없어 직접 참조하면 NoClassDefFound 가 난다.
public final class TownScreens {
    private TownScreens() {}

    public static void show(TownView view, boolean openHub) {
        Minecraft mc = Minecraft.getInstance();
        if (openHub) {
            // 서버가 "허브를 열어라"라고 했으면 지금 뭐가 떠 있든 허브로 간다.
            mc.setScreen(new TownHubScreen(view));
            return;
        }
        // 갱신 요청 — 열려 있는 화면만 새로 그린다(제출 직후 수치가 바로 바뀌어야 한다).
        if (mc.screen instanceof TownTrackScreen track) { track.setView(view); return; }
        if (mc.screen instanceof TownHubScreen hub) { hub.setView(view); return; }
        mc.setScreen(new TownHubScreen(view));
    }

    // 컨테이너 화면이 열린 직후 현황을 채워 넣는다 (메뉴는 아이템만 알지 수치는 모른다).
    public static void fill(TownView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TownTrackScreen track) track.setView(view);
    }
}
