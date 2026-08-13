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

    /**
     * 판매액 중 <b>마을 금고로 가는 몫</b>(퍼센트).
     *
     * <p>개인 수입이 곧 마을 발전이 되게 하는 손잡이다 — 설계의 「이중 성장」이
     * 한 화면 안에서 눈에 보인다. 20 이면 장식처럼 느껴지고 50 이면 세금으로 읽힌다.
     * ⚠️ 화면에도 그대로 띄운다. 안 보이면 «떼인» 것으로만 느껴진다.
     */
    public static final int TOWN_CUT = 30;

    /** 지금 이 사람에게 보일 화면 내용. */
    public static ShopView snapshot(ServerPlayer p) {
        if (p == null || p.getServer() == null) return ShopView.empty();
        String name = p.getGameProfile().getName();
        var data = LSData.get(p.getServer());
        int bal = data.wallet().get(name);

        var buy = new ArrayList<ShopView.Row>();
        var list = ShopCatalog.resolve();
        for (int i = 0; i < list.size(); i++) {
            var e = list.get(i);
            buy.add(new ShopView.Row(i, e.id(), e.count(), e.price(), e.group(), bal >= e.price()));
        }

        var sell = new ArrayList<ShopView.SellRow>();
        var slist = ShopCatalog.resolveSell();
        for (int i = 0; i < slist.size(); i++) {
            var e = slist.get(i);
            sell.add(new ShopView.SellRow(i, e.id(), e.unit(), count(p, e.item()), e.group()));
        }
        return new ShopView(bal, data.town().treasury(), TOWN_CUT, buy, sell);
    }

    /** 가방에 든 개수. 화면에 «가진 만큼»을 보여주고, 팔 때도 이 수를 기준으로 한다. */
    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        if (item == null) return 0;
        int n = 0;
        for (var st : p.getInventory().items) if (st.is(item)) n += st.getCount();
        for (var st : p.getInventory().offhand) if (st.is(item)) n += st.getCount();
        return n;
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

    /**
     * 가진 만큼 전부 판다.
     *
     * <p>⚠️ <b>개수를 클라가 안 보낸다.</b> 서버가 가방을 세서 그만큼만 가져간다 —
     * 개수를 실어 보내게 두면 «안 가진 만큼» 팔 수 있다.
     *
     * <p>⚠️ <b>아이템을 먼저 걷고 돈을 준다.</b> 순서가 반대면 걷다가 실패했을 때
     * 돈만 들어간다 — 되돌릴 방법이 없다(사는 쪽에서 «자리부터 본다»와 같은 이유).
     *
     * @return 실패 사유, 성공이면 {@code null}
     */
    public static String sell(ServerPlayer p, int index) {
        if (p == null) return "플레이어를 찾을 수 없다.";
        MinecraftServer server = p.getServer();
        if (server == null) return "서버를 찾을 수 없다.";

        var e = ShopCatalog.sellByIndex(index);
        if (e == null) return "그건 사들이지 않습니다.";
        var item = e.item();
        if (item == null) return "지금은 사들일 수 없는 물건입니다.";

        int have = count(p, item);
        if (have <= 0) return "가방에 없습니다.";

        // 실제로 걷은 만큼만 값을 친다. 「세었더니 10인데 8만 걷혔다」가 생겨도 어긋나지 않는다.
        int taken = 0;
        for (var st : p.getInventory().items) {
            if (taken >= have) break;
            if (!st.is(item)) continue;
            int t = Math.min(st.getCount(), have - taken);
            st.shrink(t);
            taken += t;
        }
        for (var st : p.getInventory().offhand) {
            if (taken >= have) break;
            if (!st.is(item)) continue;
            int t = Math.min(st.getCount(), have - taken);
            st.shrink(t);
            taken += t;
        }
        if (taken <= 0) return "가방에 없습니다.";

        int total = e.unit() * taken;
        int town = total * TOWN_CUT / 100;
        int mine = total - town;   // 나머지 — 나눗셈에서 버려진 몫이 사라지지 않게

        String name = p.getGameProfile().getName();
        var data = LSData.get(server);
        data.wallet().add(name, mine);
        data.town().addTreasury(town);
        data.dirty();

        p.sendSystemMessage(Component.literal(
            "§a판매: §r" + item.getDescription().getString() + " §7×" + taken
            + " §8→ §e+" + mine + " Ducat §8· 마을 금고 +" + town));
        LOG.info("[상점] {} 판매 {} x{} (개인 +{} · 금고 +{})", name, e.id(), taken, mine, town);
        sync(p);
        return null;
    }
}
