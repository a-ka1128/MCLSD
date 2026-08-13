package com.laststardust.relics.shop;

import java.util.ArrayList;

import com.laststardust.relics.data.LSData;
import com.laststardust.relics.network.ShopViewPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 상점의 «판정»이 전부 여기 있다 — 열기·값·구매.
 *
 * <p>마을({@code TownService})·축복({@code BlessingService})과 같은 구조다.
 * 규칙을 화면 쪽에 두면 화면을 안 거치고 패킷만 보내는 길이 열린다.
 */
public final class ShopService {
    private ShopService() {}

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /** 지금 이 사람에게 보일 화면 내용. */
    public static ShopView snapshot(ServerPlayer p) {
        if (p == null || p.getServer() == null) return ShopView.empty();
        String name = p.getGameProfile().getName();
        int bal = LSData.get(p.getServer()).wallet().get(name);
        var rows = new ArrayList<ShopView.Row>();
        var list = ShopCatalog.resolve();
        for (int i = 0; i < list.size(); i++) {
            var e = list.get(i);
            rows.add(new ShopView.Row(i, e.id(), e.count(), e.price(), e.group(), bal >= e.price()));
        }
        return new ShopView(bal, rows);
    }

    public static void open(ServerPlayer p) {
        if (p == null || p.getServer() == null) return;
        // 컨테이너가 열려 있으면 닫는다 — 안 닫으면 다음 창 열기가 꼬인다(TownGui 와 같은 이유).
        p.closeContainer();
        PacketDistributor.sendToPlayer(p, new ShopViewPayload(snapshot(p)));
    }

    /** 화면을 다시 그린다. 산 직후 잔액이 바로 바뀌어야 한다. */
    public static void sync(ServerPlayer p) {
        if (p == null || p.getServer() == null) return;
        PacketDistributor.sendToPlayer(p, new ShopViewPayload(snapshot(p)));
    }

    /**
     * 산다.
     *
     * <p>⚠️ <b>번호를 서버가 다시 푼다.</b> 클라가 보낸 것은 «몇 번째»뿐이고,
     * 무엇을 얼마에 주는지는 여기서 카탈로그를 보고 정한다 — 아이템과 값을 클라가
     * 실어 보내게 두면 1 Ducat 짜리 다이아 요청을 막을 방법이 없다.
     *
     * @return 실패 사유, 성공이면 {@code null}
     */
    public static String buy(ServerPlayer p, int index) {
        if (p == null) return "플레이어를 찾을 수 없다.";
        MinecraftServer server = p.getServer();
        if (server == null) return "서버를 찾을 수 없다.";

        var e = ShopCatalog.byIndex(index);
        if (e == null) return "그런 물건이 없습니다.";
        var stack = e.stack();
        if (stack.isEmpty()) return "지금은 살 수 없는 물건입니다.";

        String name = p.getGameProfile().getName();
        var data = LSData.get(server);
        var wallet = data.wallet();
        if (wallet.get(name) < e.price()) {
            return "Ducat 이 모자랍니다: " + wallet.get(name) + " / " + e.price();
        }

        // ⚠️ 넣을 자리부터 본다. 돈을 먼저 빼고 인벤이 꽉 차 바닥에 흘리면
        //    「샀는데 사라졌다」가 된다 — 되돌릴 방법이 없는 종류다.
        if (!p.getInventory().add(stack.copy())) {
            return "가방이 가득 찼습니다.";
        }
        wallet.spend(name, e.price());
        data.dirty();

        p.sendSystemMessage(Component.literal(
            "§a구입: §r" + stack.getHoverName().getString() + " §7×" + e.count()
            + " §8(−" + e.price() + " Ducat · 남은 " + wallet.get(name) + ")"));
        LOG.info("[상점] {} 구입 {} x{} (-{} Ducat)", name, e.id(), e.count(), e.price());
        sync(p);
        return null;
    }
}
