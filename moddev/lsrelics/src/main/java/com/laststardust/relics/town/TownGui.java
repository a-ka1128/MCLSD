package com.laststardust.relics.town;

import com.laststardust.relics.data.LSData;
import com.laststardust.relics.data.TownCatalog;
import com.laststardust.relics.network.TownViewPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

// 마을 화면 열기/갱신의 단일 창구.
public final class TownGui {
    private TownGui() {}

    // 허브 — 일반 Screen 이라 메뉴 없이 패킷만 보내면 클라가 연다.
    // 다만 트랙 창(컨테이너)이 열려 있었다면 서버 쪽 메뉴도 반드시 닫아야 한다.
    // 안 닫으면 플레이어가 컨테이너를 연 상태로 남아 다음 창 열기가 꼬인다.
    public static void openHub(ServerPlayer player) {
        if (player.getServer() == null) return;
        player.closeContainer();
        PacketDistributor.sendToPlayer(player,
            new TownViewPayload(TownService.snapshot(player.getServer()), true));
    }

    // 트랙 창 — 진짜 컨테이너라 서버가 메뉴를 열어줘야 한다.
    public static void openTrack(ServerPlayer player, String trackKey) {
        TownCatalog.Track track = TownCatalog.byKey(trackKey);
        if (track == null || player.getServer() == null) return;
        var town = LSData.get(player.getServer()).town();

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                // translatable 을 그대로 넘긴다 — 클라가 자기 언어로 해석한다.
                // 여기서 getString() 을 부르면 서버 언어(en_us)로 굳어버린다.
                return Component.literal(track.icon() + " ")
                    .append(Component.translatable(track.nameKey()));
            }
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return TownMenu.server(id, inv, town, trackKey);
            }
        }, buf -> {
            buf.writeUtf(trackKey);
            // 요구 아이템도 함께 — 클라가 같은 판정을 해야 아이템이 들어갔다 튕기지 않는다.
            var need = track.next(town.level(trackKey));
            var item = need == null ? null : need.itemOrNull();
            buf.writeUtf(item == null ? "" :
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
        });

        // 메뉴는 아이템만 안다. 수치(필요량·Ducat·단계)는 따로 실어 보낸다.
        sync(player);
    }

    // 현황을 다시 보낸다. 열려 있는 화면이 알아서 갱신한다.
    public static void sync(ServerPlayer player) {
        if (player.getServer() == null) return;
        PacketDistributor.sendToPlayer(player,
            new TownViewPayload(TownService.snapshot(player.getServer()), false));
    }

    public static void syncAll(net.minecraft.server.MinecraftServer server) {
        TownView view = TownService.snapshot(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new TownViewPayload(view, false));
        }
    }
}
