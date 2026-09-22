package com.laststardust.relics.blessing;

import java.util.ArrayList;
import java.util.List;

import com.laststardust.relics.data.BlessingCatalog;
import com.laststardust.relics.data.BlessingCatalog.Slot;
import com.laststardust.relics.data.BlessingData;
import com.laststardust.relics.data.LSData;
import com.laststardust.relics.network.BlessViewPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;

/** 제단 화면 열기/갱신의 단일 창구. {@code TownGui} 와 같은 자리다. */
public final class BlessGui {
    private BlessGui() {}

    public static void open(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                // translatable 을 그대로 넘긴다 — 여기서 getString() 을 부르면 서버 언어로 굳는다.
                return Component.translatable("lsblessing.gui.title");
            }
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new BlessMenu(id, inv, new SimpleContainer(2));
            }
        }, buf -> { });

        // 메뉴는 슬롯만 안다. 수치(성급·축복·비용)는 따로 실어 보낸다.
        sync(player, "");
    }

    public static void sync(ServerPlayer player, String spunSlot) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        PacketDistributor.sendToPlayer(player, new BlessViewPayload(snapshot(player, spunSlot)));
    }

    /**
     * @param spunSlot 방금 굴린 칸의 이름. 클라가 이걸 보고 <b>스핀 연출</b>을 시작한다.
     *                 결과는 이미 확정돼 있으므로 연출 도중에 창을 닫아도 안 날아간다.
     */
    public static BlessView snapshot(ServerPlayer player, String spunSlot) {
        MinecraftServer server = player.getServer();
        if (server == null) return BlessView.empty();

        LSData data = LSData.get(server);
        String name = player.getGameProfile().getName();
        int star = data.hero().star(name);
        boolean up = BlessingService.altarUpgraded(server);

        List<BlessView.SlotView> out = new ArrayList<>();
        for (Slot s : Slot.values()) {
            BlessingData.Roll r = data.blessing().get(name, s);
            out.add(new BlessView.SlotView(s.name(), s.unlockedAt(star),
                r == null ? "" : r.id(), r == null ? 0f : r.value()));
        }

        return new BlessView(star,
            BlessingService.altarUnlocked(server), up,
            BlessingCatalog.kindCost(star), BlessingCatalog.valueCost(star, up),
            out, spunSlot == null ? "" : spunSlot);
    }
}
