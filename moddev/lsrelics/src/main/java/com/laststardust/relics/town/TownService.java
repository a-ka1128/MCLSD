package com.laststardust.relics.town;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.laststardust.relics.data.LSData;
import com.laststardust.relics.data.TownCatalog;
import com.laststardust.relics.data.TownData;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;

// 마을 규칙의 단일 창구. 화면·명령·패킷이 전부 여기를 거친다.
//
// KubeJS 시절엔 판정이 스크립트에, 표시가 모드에 나뉘어 있어 둘이 어긋나면
// "버튼은 켜졌는데 눌러도 안 되는" 상태가 났다. 이제 한 곳에서만 판정한다.
public final class TownService {
    private TownService() {}

    // 화면에 넘길 스냅샷을 만든다.
    public static TownView snapshot(MinecraftServer server) {
        TownData town = LSData.get(server).town();
        List<TownView.TrackView> tracks = new ArrayList<>();

        for (TownCatalog.Track t : TownCatalog.ALL) {
            int lv = town.level(t.key());
            TownCatalog.Level need = t.next(lv);
            if (need == null) {
                tracks.add(new TownView.TrackView(t.key(), lv, t.max(), "", "", 0, "", 0, 0, false, false));
                continue;
            }
            int have = town.depositCount(t.key(), need);
            boolean ok = have >= need.count() && town.treasury() >= need.ducat();
            // 해석하지 않고 키를 넘긴다 — 번역은 클라의 언어로 해야 한다.
            tracks.add(new TownView.TrackView(
                t.key(), lv, t.max(),
                need.nameKey(), need.fxKey(),
                need.ducat(), need.item().toString(), need.count(), have,
                need.isEssence(), ok));
        }

        List<TownView.Contributor> board = new ArrayList<>();
        for (Map.Entry<String, Integer> e : town.topContributors(6)) {
            board.add(new TownView.Contributor(e.getKey(), e.getValue()));
        }

        // 성벽은 아직 KubeJS(ls_siege) 소유다. 옮기기 전까지는 0 으로 두고 화면이 알아서 감춘다.
        return new TownView(town.treasury(), 0, 0, 0, tracks, board);
    }

    // 단계 완성 즉시 지급되는 것 — 지금은 공방 Lv4 귀환석뿐.
    // 접속 중인 전원에게 한 개씩. 못 받은 사람(오프라인)은 접속 시 채워준다(TownEffects).
    private static void grantOnUpgrade(MinecraftServer server, String track, int newLevel) {
        if (!"workshop".equals(track) || newLevel != 4) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            giveHearthstone(p);
        }
    }

    // 지금 레벨이 마땅히 받았어야 할 것들을 맞춰준다.
    //
    // ── 왜 별도로 있나 ──
    // `/town level` 같은 관리자 조정은 upgrade() 를 거치지 않아 지급 훅을 그냥 지나친다.
    // 그러면 "레벨은 4인데 귀환석이 없는" 상태가 되어, 정작 검증할 때 효과가 죽은 건지
    // 지급이 안 된 건지 갈리지 않는다. 어느 경로로 레벨이 올랐든 같은 상태에 도착해야 한다.
    // (여러 번 불려도 안전하다 — giveHearthstone 이 이미 가진 사람은 건너뛴다)
    public static void reconcile(MinecraftServer server) {
        TownData town = LSData.get(server).town();
        for (TownCatalog.Track t : TownCatalog.ALL) {
            grantOnUpgrade(server, t.key(), town.level(t.key()));
        }
    }

    // 이미 갖고 있으면 주지 않는다 — 쿨다운이 아이템에 붙어 있어서 여러 개는 의미가 없고,
    // 접속할 때마다 쌓이면 인벤토리만 지저분해진다.
    public static void giveHearthstone(ServerPlayer p) {
        var item = com.laststardust.relics.LSRelics.HEARTHSTONE.get();
        for (var st : p.getInventory().items) {
            if (st.is(item)) return;
        }
        var stack = new net.minecraft.world.item.ItemStack(item);
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        p.sendSystemMessage(Component.translatable("lstown.msg.hearth_granted"));
    }

    private static String itemName(TownCatalog.Level need) {
        Item i = need.itemOrNull();
        return i == null ? need.item().toString() : Component.translatable(i.getDescriptionId()).getString();
    }

    // 완성 시도. 실패해도 아무것도 소모하지 않는다.
    public static boolean upgrade(ServerPlayer player, String trackKey) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        TownCatalog.Track track = TownCatalog.byKey(trackKey);
        if (track == null) return false;

        LSData data = LSData.get(server);
        TownData town = data.town();
        int lv = town.level(trackKey);
        TownCatalog.Level need = track.next(lv);
        if (need == null) {
            player.sendSystemMessage(Component.translatable("lstown.msg.max"));
            return false;
        }
        int have = town.depositCount(trackKey, need);
        if (have < need.count()) {
            player.sendSystemMessage(Component.translatable("lstown.msg.need_resource",
                itemName(need), have, need.count()));
            return false;
        }
        if (town.treasury() < need.ducat()) {
            player.sendSystemMessage(Component.translatable("lstown.msg.need_ducat",
                town.treasury(), need.ducat()));
            return false;
        }

        // 집행 — 여기서부터는 실패하지 않는다
        town.spend(need.ducat());
        grantOnUpgrade(server, trackKey, lv + 1);
        town.consumeDeposit(trackKey, need);
        town.setLevel(trackKey, lv + 1);
        if (need.flag() != null) town.setFlag(need.flag(), true);
        town.addContribution(player.getGameProfile().getName(), need.count());
        data.dirty();

        String levelName = Component.translatable(need.nameKey()).getString();
        String trackName = Component.translatable(track.nameKey()).getString();
        server.getPlayerList().broadcastSystemMessage(
            Component.translatable("lstown.msg.upgraded", track.icon() + " " + trackName, lv + 1, levelName), false);
        server.getPlayerList().broadcastSystemMessage(
            Component.literal("§7   → " + Component.translatable(need.fxKey()).getString()), false);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.0f, 1.0f);
            // 마지막 단계(별빛 등대)는 승리 축포를 한 겹 더 — 세상을 되찾은 상징물이라
            if ("lighthouse".equals(need.flag())) {
                p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            }
        }
        return true;
    }
}
