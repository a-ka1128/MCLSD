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

    /**
     * <b>손에 든 유물의 «실제» 수치</b>를 붙인다.
     *
     * <p>여기만 자바가 직접 채운다. 나머지 항목은 KubeJS 가 채우는데(수치가 거기 살아서),
     * <b>이 값들은 반대로 자바에만 있다</b> — 각성 배율표({@code ASCENSION})·전역 배율
     * ({@code GLOBAL_POWER})·무기 어픽스 보정은 전부 {@code RelicSkills} 안이고,
     * 공격력·공격속도는 아이템 속성이다. KubeJS 로 옮기면 이번엔 그쪽이 사본이 된다.
     *
     * <p>⚠️ <b>플레이어의 «살아 있는» 속성을 읽는다</b> — 아이템에 적힌 기본값이 아니라.
     * 그래야 인챈트·젬·어픽스·가호 패시브가 이미 섞인 «지금 진짜 때리는 값»이 나온다.
     * 아이템 정의만 읽으면 화면의 숫자와 실제로 들어가는 피해가 갈린다.
     *
     * <p>⚠️ 주손에 유물이 없으면 <b>아무 줄도 안 붙인다.</b> 0 을 찍으면
     * 「유물이 약하다」로 읽힌다 — 안 든 것과 약한 것은 다르다.
     */
    public static void stats(ServerPlayer p) {
        if (p == null) return;
        var stack = p.getMainHandItem();
        if (stack.isEmpty()) return;
        String user = p.getGameProfile().getName();

        double atk = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        double spd = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        row(user, "§8── 지금 든 무기 ──", "", 0x6B7280);
        row(user, "공격력", fmt(atk), 0xFFD98A);
        row(user, "공격 속도", fmt(spd) + "/초", 0xC7CDD6);
        // 초당 피해는 «평타만» 이다. 스킬·투사체는 각자 계산식이 달라 한 줄로 못 줄인다 —
        // 합쳐서 한 숫자로 내면 그게 곧 거짓말이 된다.
        row(user, "평타 초당 피해", fmt(atk * spd), 0xE08A8A);

        float asc = com.laststardust.relics.item.RelicSkills.ascension(stack);
        row(user, "각성 배율", "×" + fmt(asc), 0xFFD98A);
        row(user, "전역 배율", "×" + fmt(com.laststardust.relics.item.RelicSkills.GLOBAL_POWER), 0x9AA4B2);
        row(user, "스킬 총배율", "×" + fmt(com.laststardust.relics.item.RelicSkills.power(stack)), 0xC08AE0);
    }

    /** 소수 첫째 자리까지, 딱 떨어지면 정수로. 「4.0」보다 「4」가 표로 읽기 좋다. */
    private static String fmt(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
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
