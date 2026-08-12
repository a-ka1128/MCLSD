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

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private TownService() {}

    // 화면에 넘길 스냅샷을 만든다.
    public static TownView snapshot(MinecraftServer server) {
        TownData town = LSData.get(server).town();
        List<TownView.TrackView> tracks = new ArrayList<>();

        for (TownCatalog.Track t : TownCatalog.ALL) {
            int lv = town.level(t.key());
            TownCatalog.Level need = t.next(lv);
            if (need == null) {
                tracks.add(new TownView.TrackView(t.key(), lv, t.max(), "", "", 0, List.of(), false));
                continue;
            }
            List<TownView.ReqView> reqs = new ArrayList<>();
            for (TownCatalog.Req r : need.reqs()) {
                reqs.add(new TownView.ReqView(r.id().toString(), r.isTag(),
                    r.count(), town.depositCount(t.key(), r), r.isEssence()));
            }
            boolean ok = town.depositSatisfied(t.key(), need) && town.treasury() >= need.ducat();
            // 해석하지 않고 키를 넘긴다 — 번역은 클라의 언어로 해야 한다.
            tracks.add(new TownView.TrackView(
                t.key(), lv, t.max(),
                need.nameKey(), need.fxKey(),
                need.ducat(), reqs, ok));
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
        // 세계 쪽도 같이 맞춘다 — 「레벨은 3인데 건물은 2단계」가 남지 않게.
        // 여기 둔 이유는 위와 같다: 어느 경로로 레벨이 올랐든 이 함수를 지나간다.
        TownBuild.reconcile(server);
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

    // 안내 메시지용 이름. 태그는 아이템 이름을 쓰면 「참나무 원목」처럼 정반대로 읽히므로
    // 전용 키를 쓴다 (TownView.ReqView.displayName 과 같은 규칙).
    private static String reqName(TownCatalog.Req req) {
        if (req.isTag()) {
            return Component.translatable("lstown.req.tag." + req.id().getPath()).getString();
        }
        Item i = req.itemOrNull();
        return i == null ? req.id().toString() : Component.translatable(i.getDescriptionId()).getString();
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
        TownCatalog.Req missing = town.firstMissing(trackKey, need);
        if (missing != null) {
            player.sendSystemMessage(Component.translatable("lstown.msg.need_resource",
                reqName(missing), town.depositCount(trackKey, missing), missing.count()));
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
        town.addContribution(player.getGameProfile().getName(), need.totalCount());
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

        grantTownAdvancements(server, town, trackKey, lv + 1);
        return true;
    }

    // ── 도전과제 (2026-08-06) ──
    // 마을은 **서버 전체가 함께 올리는 것**이라 접속자 전원에게 준다.
    // 트랙 하나가 최대 단계에 닿으면 그 트랙의 것을, 넷이 전부 최대면 `town_all` 을 더 준다.
    //
    // 스크립트가 아니라 여기서 주는 이유: 마을 판정은 이 클래스 하나가 갖는다
    // (`ARCHITECTURE.md` 원칙 3 — 판정은 한 곳에서). 지급을 KubeJS 로 넘기면
    // 「완성됐는가」를 두 곳이 판단하게 되고, 그건 반드시 어긋난다.
    private static void grantTownAdvancements(MinecraftServer server, TownData town,
                                              String trackKey, int newLevel) {
        TownCatalog.Track track = TownCatalog.byKey(trackKey);
        if (track == null || track.next(newLevel) != null) return;   // 아직 최대가 아니다

        grantAll(server, "town_" + trackKey);

        for (TownCatalog.Track t : TownCatalog.ALL) {
            if (t.next(town.level(t.key())) != null) return;         // 아직 남은 트랙이 있다
        }
        grantAll(server, "town_all");
    }

    // `/advancement grant` 를 쓰지 않고 직접 준다 — 명령 문자열을 조립하면 오타가
    // 조용히 아무것도 안 하는 결과가 된다(KubeJS 쪽은 화이트리스트로 막았지만 여기는 자바다).
    // 없는 도전과제 id 면 `getAdvancement` 가 null 을 주므로 그때 로그를 남긴다.
    private static void grantAll(MinecraftServer server, String id) {
        var loc = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.laststardust.relics.LSRelics.MODID, id);
        var adv = server.getAdvancements().get(loc);
        if (adv == null) {
            LOG.warn("[LS] 도전과제가 없다: {} — data/lsrelics/advancement/ 를 볼 것", loc);
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.getAdvancements().award(adv, "granted");
        }
    }
}
