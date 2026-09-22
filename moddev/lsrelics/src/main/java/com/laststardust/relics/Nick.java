package com.laststardust.relics;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * <h2>부르는 이름 — {@code /닉네임}</h2>
 *
 * <p>비행선의 린케우스가 첫 대화에서 묻는다: 「너를 뭐라고 불러야 할지 모르겠군.」
 * 마인크래프트 계정 이름은 세계관 밖의 물건이라, 이 서버에서 서로를 부를 이름을 따로 받는다.
 *
 * <p>── 계정 이름은 <b>절대</b> 안 바뀐다 ──
 * 가호·유물·각성·금고가 전부 {@code getGameProfile().getName()} 을 열쇠로 쓴다
 * ({@code LSData} 의 모든 섹션). 여기서 그걸 건드리면 <b>별명을 바꾼 순간 그 사람의
 * 모든 진행이 사라진다.</b> 그래서 이 파일은 «보이는 이름»만 만진다.
 *
 * <p>── 어디에 저장하나 ──
 * {@code LSData} 가 아니라 <b>플레이어의 {@code PlayerPersisted}</b> 다. 이유가 둘이다:
 * <ul>
 *   <li>별명은 «그 사람의 것»이지 서버 진행 상태가 아니다.</li>
 *   <li><b>오픈 전 청소 때 함께 지워져야 한다.</b> {@code playerdata/} 를 지우면 같이 사라지는데,
 *       {@code laststardust.dat} 에 뒀으면 제단·성역과 <b>같은 파일에 묶여</b> 따로 못 지운다
 *       (그 얽힘을 {@code /relic altar} 좌표판을 만들며 한 번 겪었다).</li>
 * </ul>
 * 죽어도 남아야 하므로 {@code getPersistentData()} 가 아니라 그 안의
 * {@code PlayerPersisted} 하위 태그에 적는다 — 리스폰이 그 태그만 옮긴다
 * ({@code FateAutoOpen} 의 「봤다」 표식과 같은 자리, 같은 이유).
 *
 * <p>── 어디까지 바뀌나 (솔직하게) ──
 * <ul>
 *   <li>✅ 채팅·사망 메시지·명령 출력 — {@link PlayerEvent.NameFormat}</li>
 *   <li>✅ 탭 목록 — {@link PlayerEvent.TabListNameFormat}</li>
 *   <li>❌ <b>머리 위 이름표</b> — 그건 클라이언트가 계정 이름으로 직접 그린다.
 *       서버가 바꿀 수 있는 값이 아니다(팀 접두사로 «덧붙이는» 것만 된다).</li>
 * </ul>
 */
@EventBusSubscriber(modid = LSRelics.MODID)
public final class Nick {
    private Nick() {}

    /** ⚠️ 죽어도 남아야 하므로 {@code PlayerPersisted} 안에 적는다. */
    private static final String KEY = "lsNick";

    public static final int MIN_LEN = 2;
    public static final int MAX_LEN = 16;

    private static CompoundTag persisted(ServerPlayer p) {
        return p.getPersistentData().getCompound(ServerPlayer.PERSISTED_NBT_TAG);
    }

    /** 정한 별명, 없으면 빈 문자열. */
    public static String get(ServerPlayer p) {
        return p == null ? "" : persisted(p).getString(KEY);
    }

    public static boolean has(ServerPlayer p) {
        return !get(p).isEmpty();
    }

    /** 별명이 있으면 별명, 없으면 계정 이름. <b>표시용으로만 쓴다 — 저장 열쇠로 쓰면 안 된다.</b> */
    public static String display(ServerPlayer p) {
        if (p == null) return "";
        String n = get(p);
        return n.isEmpty() ? p.getGameProfile().getName() : n;
    }

    /** 접속 중인 사람 중 이 계정의 표시 이름. KubeJS 가 부른다. */
    public static String displayOf(MinecraftServer server, String username) {
        if (server == null || username == null) return username == null ? "" : username;
        ServerPlayer p = server.getPlayerList().getPlayerByName(username);
        return p == null ? username : display(p);
    }

    /**
     * 별명을 정한다.
     *
     * @return 실패 사유, 성공이면 {@code null} — 부른 쪽이 그대로 띄운다.
     *         <b>조용히 실패하지 않는다.</b> 일반 유저가 쓰는 명령이라 「왜 안 되는지」가 안 보이면
     *         같은 걸 계속 다시 친다.
     */
    public static String set(ServerPlayer p, String raw) {
        if (p == null) return "플레이어를 찾을 수 없다.";
        MinecraftServer server = p.getServer();
        if (server == null) return "서버를 찾을 수 없다.";

        String name = raw == null ? "" : raw.trim();

        // §k(마법 글자)·§l 같은 서식 코드를 그대로 두면 이름이 화면에서 튀거나 아예 안 읽힌다.
        // 색을 «쓰게» 해 달라는 요청이 나올 수 있는데, 그건 그때 허용 목록으로 여는 편이 낫다.
        if (name.indexOf('§') >= 0) return "§ 서식 코드는 쓸 수 없습니다.";
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < ' ' || c == 127) return "이름에 쓸 수 없는 글자가 있습니다.";
        }
        if (name.indexOf(' ') >= 0) return "이름에 띄어쓰기는 넣을 수 없습니다.";

        // 길이는 «글자 수»로 센다. 한글은 UTF-8 로 3바이트라 바이트로 세면
        // 「다섯 글자인데 너무 길다」는 말을 듣게 된다.
        int len = name.codePointCount(0, name.length());
        if (len < MIN_LEN || len > MAX_LEN) {
            return "이름은 " + MIN_LEN + "~" + MAX_LEN + "글자여야 합니다. §7(지금 " + len + "글자)";
        }

        // ── 겹침 검사 ──
        // ⚠️ **접속 중인 사람만 본다.** 접속 안 한 사람의 별명은 각자 playerdata 안에 있어서
        //    싸게 훑을 방법이 없다. 여덟 명짜리 서버라 실제로 겹칠 일이 거의 없고,
        //    겹치더라도 이름이 같을 뿐 진행은 계정 이름으로 갈리므로 망가지지는 않는다.
        String self = p.getGameProfile().getName();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            String on = other.getGameProfile().getName();
            if (on.equals(self)) continue;
            if (on.equalsIgnoreCase(name)) return "다른 사람의 계정 이름과 같습니다.";
            if (get(other).equalsIgnoreCase(name)) return "이미 " + name + " 라고 불리는 사람이 있습니다.";
        }

        CompoundTag root = p.getPersistentData();
        CompoundTag tag = root.getCompound(ServerPlayer.PERSISTED_NBT_TAG);
        tag.putString(KEY, name);
        root.put(ServerPlayer.PERSISTED_NBT_TAG, tag);

        refresh(p);
        return null;
    }

    /**
     * 바뀐 이름을 «지금» 보이게 한다.
     *
     * <p>{@link PlayerEvent.NameFormat} 은 표시 이름을 <b>다시 계산할 때만</b> 불린다
     * (결과가 캐시된다). 이걸 안 비우면 <b>재접속해야 별명이 보인다</b> —
     * 「명령은 됐다는데 아무것도 안 바뀐다」로 읽히는 종류다.
     */
    private static void refresh(ServerPlayer p) {
        p.refreshDisplayName();
        MinecraftServer server = p.getServer();
        if (server == null) return;
        // 탭 목록은 따로 밀어 줘야 한다 — 캐시를 비운다고 남의 화면이 갱신되지는 않는다.
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(p)));
    }

    // ── 이름이 실제로 쓰이는 두 자리 ──

    /**
     * 채팅·사망 메시지·명령 출력에 쓰이는 이름.
     *
     * <p>✅ <b>칭호와 저절로 합쳐진다.</b> {@code Player.getDisplayName()} 은 이 이벤트로
     * 기본 이름을 정한 <b>뒤에</b> {@code PlayerTeam.formatNameForTeam} 으로 팀 접두사를 씌운다.
     * 칭호({@code ls_title.js})가 바로 그 팀 접두사라, 여기서는 아무것도 안 해도
     * 「[별을 이은 자] 린케우스」가 된다 — 칭호를 여기 끼워 넣으면 <b>두 번 붙는다.</b>
     */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        String n = get(p);
        if (!n.isEmpty()) event.setDisplayname(Component.literal(n));
    }

    /**
     * 탭 목록의 이름.
     *
     * <p>⚠️ <b>여기는 팀 접두사가 «저절로» 안 붙는다.</b> 클라이언트는 탭 항목에
     * 표시 이름이 <b>안 정해져 있을 때만</b> 팀 서식을 입힌다. 그냥 별명만 넣으면
     * 탭에서만 <b>칭호가 사라진다</b> — 채팅에는 멀쩡히 있어서 더 헷갈린다.
     * 그래서 여기서는 손으로 씌운다.
     */
    @SubscribeEvent
    public static void onTabName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        String n = get(p);
        if (n.isEmpty()) return;
        event.setDisplayName(PlayerTeam.formatNameForTeam(p.getTeam(), Component.literal(n)));
    }
}
