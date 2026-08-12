package com.laststardust.relics;

import com.laststardust.relics.data.LSData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

// 가호가 없는 사람에게 접속 직후 «프롤로그 → 선택 화면»을 보여준다.
//
// ── 왜 채팅으로는 부족한가 ──
// 여태 `ls_fate.js` 가 접속 때 «§6✦ 별의 가호를 아직 받지 않았습니다 — /fate» 한 줄을 보냈다.
// 첫 접속이면 그 줄이 모드팩 로딩 메시지 수십 줄에 묻힌다. **이 서버에서 제일 먼저 해야 하는
// 선택**이 제일 눈에 안 띄는 자리에 있었다.
//
// ── 「첫 소환」이 아니라 「가호 없음」으로 판정하는 이유 ──
// 첫 접속 플래그를 따로 두면 두 가지가 생긴다: (1) ESC 로 닫은 사람은 영영 못 본다,
// (2) 플래그를 세우는 곳과 가호를 주는 곳이 따로 살아서 언젠가 어긋난다.
// «가호가 없으면 연다» 는 조건 하나로 둘 다 사라진다 — 고르는 순간 저절로 멈추고,
// 닫은 사람에게는 다음 접속에 다시 뜬다. 상태가 늘지 않는다.
//
// ── ESC 를 막지 않는다 ──
// `Screen.shouldCloseOnEsc()` 로 강제할 수는 있다. 안 한다 — 화면이 무슨 이유로든 잘못되면
// 플레이어가 갇힌다. 접속마다 다시 뜨는 것으로 강제와 실질적으로 같고, 빠져나갈 구멍이 남는다.
//
// ── 왜 몇 틱 미루나 ──
// `PlayerLoggedInEvent` 는 클라가 아직 월드에 들어오는 중일 때 온다. 그 시점에 화면을 열라고
// 보내면 클라가 곧바로 자기 로딩 화면으로 덮어써서 **패킷은 갔는데 아무 일도 안 일어난다.**
// 서버 틱을 세어 늦춘다.
//
// ── 프롤로그 (2026-08-10) ──
// **처음 오는 사람에게만** 「별이 꺼진 밤」이 채팅에 천천히 흐르고, 끝나면 타이틀이 뜨고,
// 그다음에 선택 화면이 열린다. 순서가 중요해서 **한 타임라인에 몰아넣었다** —
// 프롤로그를 KubeJS 에, 화면 열기를 여기에 두면 둘이 서로를 모른 채 겹친다
// (화면이 3초에 뜨고 글은 20초 동안 그 뒤에서 흐른다).
//
// 두 번째부터는 프롤로그를 건너뛰고 예전처럼 2초 뒤 화면만 연다 — 죽고 다시 들어올 때마다
// 20초를 다시 보게 할 수는 없다.
@EventBusSubscriber(modid = LSRelics.MODID)
public final class FateAutoOpen {
    private FateAutoOpen() {}

    // 40틱 = 2초. 청크 로딩이 끝나고 화면이 안정된 뒤다.
    // 짧게 잡아 실패하면 «가끔 안 뜬다» 가 되는데, 그건 재현이 안 돼서 제일 고치기 어렵다.
    private static final int DELAY_TICKS = 40;

    /** 프롤로그를 본 적 있는가. ⚠️ 죽어도 남아야 하므로 `PlayerPersisted` 안에 적는다. */
    private static final String K_SEEN = "lsPrologueSeen";

    // ── 프롤로그 본문 ──
    // 문단 단위로 끊는다. 한 문단 안은 «읽는 속도»로, 문단 사이는 «숨 쉬는 속도»로 벌린다.
    // 마지막 한 줄만 흰색이다 — 앞이 전부 회색이라 거기서 눈이 멈춘다.
    private static final String[][] PROLOGUE = {
        {"§7하늘에는 본래 여러 개의 별이 있었다. 별들은 밤마다 세계를 내려다보았고, 세계는 그 빛 아래에서",
         "§7잠들었다. 어둠은 어디에나 있었지만, 어디에서도 주인이 아니었다."},
        {"§7첫 번째 별이 꺼졌을 때, 사람들은 구름이라 했다.",
         "§7세 번째 별이 꺼졌을 때, 학자들은 별의 순환이라 했다."},
        {"§7하늘에 하나만 남았을 때 더는 아무도 아무 말도 하지 않았다. 밤이 낮을 밀어내고",
         "§7있었다. 땅이 갈라진 자리마다 빛이 닿지 않는 검은 틈이 벌어졌고, 그 안에서 무언가가",
         "§7기어 나왔다. 별을 삼킨 것이 이번에는 세계를 삼키러 온 것이다."},
        {"§7그리고 마지막 밤. 그 하나마저 떨어졌다.",
         "§7별은 하늘을 가로질러 이 땅 어딘가에 부딪혀 부서졌고 — 세계는 어둠에 잠겼다."},
        {"§f하지만 부서진 별의 잔해는, 아직 빛나고 있다."},
    };

    private static final int START_TICKS = 60;   // 3초 — 로딩 메시지가 지나가길 기다린다
    private static final int LINE_TICKS = 32;    // 한 줄 읽는 시간
    private static final int PARA_TICKS = 46;    // 문단 사이의 숨
    private static final int TITLE_GAP = 70;     // 마지막 줄 → 타이틀
    private static final int SCREEN_GAP = 90;    // 타이틀 → 선택 화면

    // ⚠️ 타이틀은 **4배 크기로 그려지고 줄바꿈도 축소도 안 한다.** 길면 양옆이 잘린다.
    //    처음엔 「당신은 별의 의지를 이을 별의 잔해입니다.」(20자)였는데 잘릴 자리라 나눴다.
    //
    // 부제에서 `/fate` 안내를 뺀 이유: 바로 뒤에 **선택 화면이 자동으로 열린다.**
    // 열릴 것을 굳이 알려주면 연출이 설명서가 된다.
    // (화면이 어떤 이유로든 안 열려도 `ls_fate.js` 가 접속 때 보내는 안내 줄이 남아 있다.)
    private static final String TITLE = "§f별의 잔해";
    private static final String SUBTITLE = "§7당신은 별의 의지를 이을 자입니다";

    // ── 대본 ──
    // (틱, 할 일) 목록. 프롤로그를 안 보는 사람은 마지막 «화면 열기» 하나짜리 대본을 쓴다.
    private interface Beat { void run(ServerPlayer p); }

    private record Cue(int at, Beat beat) {}

    /**
     * <b>처음 오는 사람의 가호 선택을 «성역에 도착한 뒤»로 미룬다.</b>
     *
     * <p>비행선 NPC 온보딩({@link Onboarding})이 생기면서 순서가 어긋났다. 접속하자마자
     * 화면이 열리면 플레이어는 <b>NPC 를 만나기도 전에</b> 가호를 고르고, 그러면
     * NPC 의 「거기서 별이 자네를 볼 거다」가 이미 지난 일을 말하는 대사가 된다.
     * 도착 뒤에 여는 {@link Onboarding} 의 {@code openLater} 도 «이미 가호가 있음»으로
     * 조용히 건너뛴다 — 연출이 통째로 죽어 있었다.
     *
     * <p>그래서 첫 접속에는 <b>프롤로그와 타이틀까지만</b> 틀고 화면은 열지 않는다.
     * 화면은 성역에 도착하고 3초 뒤 {@link Onboarding} 이 연다.
     *
     * <p>⚠️ <b>두 번째 접속부터는 그냥 연다</b>({@code PLAIN}) — 이게 탈출구다.
     * NPC 가 없거나 대화가 막혀도 재접속 한 번이면 가호를 고를 수 있다.
     * 이 구멍이 없으면 「대화가 안 되는데 가호도 못 고르는」 상태에 갇힌다.
     *
     * <p>비행선 온보딩을 접으면 이 값을 {@code false} 로 되돌린다 — 예전처럼 접속 즉시 연다.
     */
    private static final boolean DEFER_TO_ONBOARDING = true;

    private static final List<Cue> WITH_PROLOGUE = buildProlouge(true);
    /** 프롤로그·타이틀까지만. 가호 화면은 {@link Onboarding} 이 성역에서 연다. */
    private static final List<Cue> PROLOGUE_ONLY = buildProlouge(false);
    private static final List<Cue> PLAIN = List.of(new Cue(DELAY_TICKS, FateAutoOpen::openScreen));

    private static List<Cue> buildProlouge(boolean openScreenAtEnd) {
        List<Cue> out = new ArrayList<>();
        int t = START_TICKS;
        // 모드팩 로딩 줄과 붙어 시작하면 프롤로그가 그 일부로 읽힌다. 빈 줄로 떼어낸다.
        final int t0 = t;
        out.add(new Cue(t0, p -> { say(p, ""); say(p, ""); }));
        t += 10;
        for (String[] para : PROLOGUE) {
            for (String line : para) {
                final String l = line;
                out.add(new Cue(t, p -> say(p, l)));
                t += LINE_TICKS;
            }
            final int gap = t;
            out.add(new Cue(gap, p -> say(p, "")));
            t += PARA_TICKS;
        }
        t += TITLE_GAP;
        out.add(new Cue(t, FateAutoOpen::showTitle));
        if (openScreenAtEnd) {
            t += SCREEN_GAP;
            out.add(new Cue(t, FateAutoOpen::openScreen));
        }
        return List.copyOf(out);
    }

    private static void say(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    private static void showTitle(ServerPlayer p) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(SUBTITLE)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(TITLE)));
        if (p.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 0.7f, 1.4f);
        }
    }

    private static void openScreen(ServerPlayer p) {
        // 여기서도 다시 본다 — 대본이 도는 20여 초 사이에 OP 가 `/fate set` 을 했을 수 있다.
        if (p.getServer() == null || !LSData.get(p.getServer()).hero().fate(name(p)).isEmpty()) return;
        LSCommands.openFateScreen(p);
    }

    private static final class Pending {
        final ServerPlayer player;
        final List<Cue> script;
        int tick = 0;
        int next = 0;

        Pending(ServerPlayer player, List<Cue> script) {
            this.player = player;
            this.script = script;
        }
    }

    // 접속 순간에 여럿이 들어올 수 있다(서버 재시작 직후). 리스트로 둔다.
    private static final List<Pending> PENDING = new ArrayList<>();

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getServer() == null) return;
        // 여기서 한 번 거른다. 뒤에서 또 본다 — 그 사이에 `/fate choose` 가 끝날 수 있어서다
        // (다른 사람이 대신 골라줄 수는 없지만, OP 가 `/fate set` 을 쓸 수는 있다).
        if (!LSData.get(player.getServer()).hero().fate(name(player)).isEmpty()) return;

        boolean first = !persisted(player).getBoolean(K_SEEN);
        if (first) markSeen(player);
        // 첫 접속 → 프롤로그. 화면을 여기서 여느냐 성역에서 여느냐는 DEFER_TO_ONBOARDING 이 가른다.
        // 두 번째부터는 언제나 그냥 연다 — 온보딩이 막혔을 때의 탈출구다.
        PENDING.add(new Pending(player,
            first ? (DEFER_TO_ONBOARDING ? PROLOGUE_ONLY : WITH_PROLOGUE) : PLAIN));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // 대본이 도는 중에 나가는 경우가 있다. 안 지우면 사라진 플레이어에게 패킷을 쏜다.
        PENDING.removeIf(p -> p.player == event.getEntity());
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        PENDING.removeIf(p -> {
            ServerPlayer sp = p.player;
            if (sp.getServer() == null || sp.hasDisconnected()) return true;
            p.tick++;
            while (p.next < p.script.size() && p.script.get(p.next).at() <= p.tick) {
                p.script.get(p.next).beat().run(sp);
                p.next++;
            }
            return p.next >= p.script.size();
        });
    }

    // ── 「봤다」 표식 ──
    // ⚠️ 그냥 `getPersistentData()` 에 적으면 **죽으면 사라진다** — 리스폰은 새 ServerPlayer 를
    //    만들고 `PlayerPersisted` 하위 태그만 옮기기 때문이다. 가호를 고르기 전에 죽는 일이
    //    충분히 있을 수 있고, 그때마다 20초짜리 프롤로그를 다시 보게 할 수는 없다.
    private static CompoundTag persisted(ServerPlayer player) {
        return player.getPersistentData().getCompound(ServerPlayer.PERSISTED_NBT_TAG);
    }

    private static void markSeen(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag tag = root.getCompound(ServerPlayer.PERSISTED_NBT_TAG);
        tag.putBoolean(K_SEEN, true);
        root.put(ServerPlayer.PERSISTED_NBT_TAG, tag);
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    /**
     * 프롤로그를 <b>지금 다시 튼다</b> — {@code /lsrelic prologue}.
     *
     * <p>연출은 한 번 보고 끝나는 물건이라 «다시 보기»가 없으면 박자를 못 고친다.
     * 재접속으로는 안 되는 게, 「봤다」 표식이 남아 있으면 건너뛰기 때문이다.
     *
     * <p>표식도 같이 지운다 — 이걸 안 지우면 «명령으로는 보이는데 진짜 첫 접속에서는
     * 안 보이는» 상태가 되어, 정작 확인하려던 것을 확인하지 못한다.
     *
     * <p>⚠️ 이미 가호가 있으면 마지막의 «선택 화면 열기»만 조용히 건너뛴다
     * ({@link #openScreen} 이 스스로 확인한다). 글과 타이틀은 그대로 나오므로
     * 박자를 보는 데는 지장이 없다.
     */
    /**
     * {@code ticks} 뒤에 <b>가호 화면만</b> 연다. 프롤로그는 안 튼다.
     *
     * <p>비행선 NPC 온보딩({@link Onboarding})이 성역으로 보낸 직후에 쓴다 —
     * 그 시점엔 프롤로그를 이미 봤고, 필요한 건 「이제 고르라」 한 걸음뿐이다.
     * 여기 있는 대본 장치를 그대로 쓰는 이유는 <b>화면을 여는 길이 하나여야</b>
     * 「명령으로 열면 잠긴 목록이 보이는데 자동으로 열면 안 보이는」 어긋남이 안 생기기 때문이다.
     */
    public static void openLater(ServerPlayer player, int ticks) {
        if (player == null) return;
        PENDING.removeIf(p -> p.player == player);
        PENDING.add(new Pending(player, List.of(new Cue(ticks, FateAutoOpen::openScreen))));
    }

    public static void replay(ServerPlayer player) {
        PENDING.removeIf(p -> p.player == player);          // 돌고 있던 대본은 버린다
        CompoundTag root = player.getPersistentData();
        CompoundTag tag = root.getCompound(ServerPlayer.PERSISTED_NBT_TAG);
        tag.remove(K_SEEN);
        root.put(ServerPlayer.PERSISTED_NBT_TAG, tag);
        PENDING.add(new Pending(player, WITH_PROLOGUE));
    }
}
