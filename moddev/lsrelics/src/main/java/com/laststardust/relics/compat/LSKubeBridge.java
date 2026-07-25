package com.laststardust.relics.compat;

import com.laststardust.relics.data.LSData;
import com.laststardust.relics.data.TownCatalog;
import com.laststardust.relics.town.TownGui;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// KubeJS 스크립트 ↔ 모드 데이터의 다리. **이관 기간 동안만 존재한다.**
//
// ── 왜 필요한가 ──
// 마을·금고는 모드(LSData)로 옮겼지만 공성·관문·봉화·현상금·구출은 아직 스크립트다.
// 그 스크립트들이 금고에 보상을 넣고 비용을 확인해야 하는데, 옛 persistentData 키를 그대로 두면
// **모드의 금고와 스크립트의 금고가 서로 다른 두 개**가 되어 공성 보상이 영영 도착하지 않는다.
//
// 그래서 남은 스크립트는 이 바인딩을 통해 **모드가 가진 하나의 금고**를 본다:
//     LS.treasury(server)            // 잔액
//     LS.addTreasury(server, 50)     // 적립
//     LS.spendTreasury(server, 200)  // 지출 (부족하면 false)
//     LS.townLevel(server, 'ramparts')
//
// ── 수명 ──
// 남은 시스템을 전부 모드로 옮기면 이 파일과 build.gradle 의 KubeJS compileOnly 를 함께 지운다.
// 그때까지는 "데이터의 단일 소유자는 모드"라는 원칙을 지키게 해 주는 장치다.
public class LSKubeBridge implements KubeJSPlugin {

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("LS", new Api());
    }

    // Rhino 가 public 메서드를 그대로 노출한다.
    public static class Api {

        public int treasury(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).town().treasury();
        }

        public void addTreasury(MinecraftServer server, int amount) {
            if (server == null || amount == 0) return;
            LSData data = LSData.get(server);
            data.town().addTreasury(amount);
            data.dirty();
            TownGui.syncAll(server);
        }

        // 부족하면 아무것도 하지 않고 false. 호출부가 "확인 후 차감"을 두 번 하지 않아도 되게.
        public boolean spendTreasury(MinecraftServer server, int amount) {
            if (server == null) return false;
            LSData data = LSData.get(server);
            if (!data.town().spend(amount)) return false;
            data.dirty();
            TownGui.syncAll(server);
            return true;
        }

        public int townLevel(MinecraftServer server, String track) {
            if (server == null || TownCatalog.byKey(track) == null) return 0;
            return LSData.get(server).town().level(track);
        }

        public void addContribution(MinecraftServer server, String name, int points) {
            if (server == null || name == null || points <= 0) return;
            LSData data = LSData.get(server);
            data.town().addContribution(name, points);
            data.dirty();
        }

        public boolean townFlag(MinecraftServer server, String key) {
            return server != null && LSData.get(server).town().flag(key);
        }

        // ── 성역 좌표 — 이제 모드가 유일한 소유자다 ──
        // 2026-07-25 이전에는 KubeJS persistentData(`ls_sanc_*`)와 LSData 가 **각자 한 벌씩**
        // 들고 있었다. 스크립트가 좌표를 정해도 모드는 몰랐고, 그걸 메꾸려고 ls_towneffect.js 가
        // 2초마다 밀어 넣었는데 그 코드가 예외로 죽어 성공 0건 — 귀환석이 몇 시간 동안 먹통이었다.
        // 두 벌이 있는 한 "한쪽만 갱신됨"은 시간 문제였다. 지금은 여기가 유일한 저장소고,
        // 스크립트는 아래 getter 로 읽는다(자기 사본을 두지 않는다).
        //
        // 명령(`/sanctuary`)은 아직 ls_siege.js 에 있다 — info 가 위협도·노드처럼 스크립트가
        // 소유한 값을 함께 출력하기 때문이다. 그쪽까지 이관되면 이 통로도 사라진다.
        public void setSanctuary(MinecraftServer server, int x, int y, int z) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.setSanctuary(x, y, z);
            data.dirty();
        }

        public boolean hasSanctuary(MinecraftServer server) {
            return server != null && LSData.get(server).hasSanctuary();
        }

        // 좌표는 셋을 따로 낸다. int[] 나 BlockPos 로 내면 스크립트 호출부(`c.x`)를 전부
        // 고쳐야 하는데, 그 대량 수정이야말로 이번에 없애려는 종류의 실수를 부른다.
        // 미지정일 때는 0 을 준다 — 호출 전에 hasSanctuary() 로 걸러야 한다.
        public int sanctuaryX(MinecraftServer server) { return sanc(server, 0); }
        public int sanctuaryY(MinecraftServer server) { return sanc(server, 1); }
        public int sanctuaryZ(MinecraftServer server) { return sanc(server, 2); }

        // ── 관문 진행도 (0~4) ──
        // 6개 스크립트가 각자 persistentData 에서 읽고 있었다. 성역 좌표와 같은 구조라
        // 같은 사고가 나기 전에 옮긴다. 상한은 LSData 가 걸므로 호출부는 신경 쓰지 않아도 된다.
        public int progress(MinecraftServer server) {
            return server == null ? 0 : LSData.get(server).progress();
        }

        public void setProgress(MinecraftServer server, int n) {
            if (server == null) return;
            LSData data = LSData.get(server);
            data.setProgress(n);
            data.dirty();
        }

        private int sanc(MinecraftServer server, int axis) {
            if (server == null) return 0;
            LSData data = LSData.get(server);
            if (!data.hasSanctuary()) return 0;
            var p = data.sanctuary();
            return axis == 0 ? p.getX() : axis == 1 ? p.getY() : p.getZ();
        }

        // 화면을 열어둔 사람에게 갱신을 밀어준다 (스크립트가 금고를 바꾼 직후 등)
        public void syncTown(ServerPlayer player) {
            if (player != null) TownGui.sync(player);
        }
    }
}
