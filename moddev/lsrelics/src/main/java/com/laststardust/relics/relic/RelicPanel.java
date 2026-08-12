package com.laststardust.relics.relic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.laststardust.relics.network.RelicViewPayload;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * <h2>유물 제단 화면을 «쌓아 올리는» 자리</h2>
 *
 * <p>KubeJS 가 항목을 하나씩 채우고 마지막에 {@link #show}를 부른다.
 * 왜 이렇게 하는지는 {@link RelicView} 머리말에 있다 — <b>수치가 KubeJS 에 살기 때문</b>이다.
 *
 * <p>── 왜 «한 번에 넘기지» 않고 쌓나 ──
 * Rhino 에서 자바로 중첩 구조(레코드 목록)를 그대로 넘기는 깔끔한 길이 없다.
 * 문자열 하나로 말아 넘기는 방법은 있지만 그건 <b>안 쓴다</b> —
 * 「파싱이 실패하면 조용히 빈 화면이 된다」가 이 저장소가 이미 겪은 실패다
 * ({@code TownViewPayload} 주석). 항목마다 인수 타입이 정해진 호출로 쌓으면
 * 어긋날 자리가 없다.
 *
 * <p>── 왜 사람마다 따로 두나 ──
 * 여럿이 동시에 제단을 만질 수 있다. 하나를 공유하면 <b>남이 쌓던 화면이 내게 뜬다.</b>
 *
 * <p>⚠️ 다 쌓고 {@link #show} 를 부르지 않으면 아무 일도 안 일어난다. 쌓다 만 것은
 * {@link #begin} 이 다음에 덮어쓴다 — 새 판을 시작할 때마다 비우므로 찌꺼기가 안 남는다.
 */
public final class RelicPanel {
    private RelicPanel() {}

    private static final Map<String, Draft> DRAFTS = new HashMap<>();

    private static final class Draft {
        String title = "", subtitle = "", kind = "", lore = "", echo = "", footer = "";
        int star = 0, maxStar = 5;
        boolean owned = false;
        final List<RelicView.Row> rows = new ArrayList<>();
        final List<RelicView.Ladder> ladder = new ArrayList<>();
    }

    private static Draft draft(String user) {
        return DRAFTS.computeIfAbsent(user, k -> new Draft());
    }

    /** 새 판을 연다. 이전에 쌓다 만 것은 여기서 버려진다. */
    public static void begin(String user, String title, String subtitle, String kind) {
        Draft d = new Draft();
        d.title = title == null ? "" : title;
        d.subtitle = subtitle == null ? "" : subtitle;
        d.kind = kind == null ? "" : kind;
        DRAFTS.put(user, d);
    }

    public static void stars(String user, int star, int maxStar, boolean owned) {
        Draft d = draft(user);
        d.star = star;
        d.maxStar = maxStar <= 0 ? 5 : maxStar;
        d.owned = owned;
    }

    /** 수치 한 줄. {@code color} 는 0xRRGGBB, 0 이면 화면 기본색. */
    public static void row(String user, String label, String value, int color) {
        draft(user).rows.add(new RelicView.Row(
            label == null ? "" : label, value == null ? "" : value, color));
    }

    public static void ladder(String user, int star, String desc, int cost, boolean reached) {
        draft(user).ladder.add(new RelicView.Ladder(star, desc == null ? "" : desc, cost, reached));
    }

    public static void text(String user, String lore, String echo) {
        Draft d = draft(user);
        d.lore = lore == null ? "" : lore;
        d.echo = echo == null ? "" : echo;
    }

    /** 화면 아래에 한 줄. 「별의 파편 2/3 — 대공세를 격퇴하면 얻는다」 같은 안내. */
    public static void footer(String user, String footer) {
        draft(user).footer = footer == null ? "" : footer;
    }

    /** 쌓은 것을 그 사람에게 보낸다. 보내고 나면 초안은 버린다. */
    public static boolean show(MinecraftServer server, String user) {
        if (server == null || user == null) return false;
        Draft d = DRAFTS.remove(user);
        if (d == null) return false;
        ServerPlayer p = server.getPlayerList().getPlayerByName(user);
        if (p == null) return false;
        // 컨테이너가 열려 있으면 닫는다 — 안 닫으면 다음 창 열기가 꼬인다(TownGui 와 같은 이유).
        p.closeContainer();
        PacketDistributor.sendToPlayer(p, new RelicViewPayload(new RelicView(
            d.title, d.subtitle, d.kind, d.star, d.maxStar,
            List.copyOf(d.rows), List.copyOf(d.ladder),
            d.lore, d.echo, d.owned, d.footer)));
        return true;
    }

    /** 서버가 내려갈 때 비운다 — 싱글에서 월드를 바꿔 열면 이전 초안이 남는다. */
    public static void reset() {
        DRAFTS.clear();
    }
}
