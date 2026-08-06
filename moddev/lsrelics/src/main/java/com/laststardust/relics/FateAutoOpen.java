package com.laststardust.relics;

import com.laststardust.relics.data.LSData;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

// 가호가 없는 사람에게 접속 직후 선택 화면을 띄운다.
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
@EventBusSubscriber(modid = LSRelics.MODID)
public final class FateAutoOpen {
    private FateAutoOpen() {}

    // 40틱 = 2초. 청크 로딩이 끝나고 화면이 안정된 뒤다.
    // 짧게 잡아 실패하면 «가끔 안 뜬다» 가 되는데, 그건 재현이 안 돼서 제일 고치기 어렵다.
    private static final int DELAY_TICKS = 40;

    private static final class Pending {
        final ServerPlayer player;
        int ticksLeft;

        Pending(ServerPlayer player, int ticks) {
            this.player = player;
            this.ticksLeft = ticks;
        }
    }

    // 접속 순간에 여럿이 들어올 수 있다(서버 재시작 직후). 리스트로 둔다.
    private static final List<Pending> PENDING = new ArrayList<>();

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getServer() == null) return;
        // 여기서 한 번 거른다. 2초 뒤에 또 본다 — 그 사이에 `/fate choose` 가 끝날 수 있어서다
        // (다른 사람이 대신 골라줄 수는 없지만, OP 가 `/fate set` 을 쓸 수는 있다).
        if (!LSData.get(player.getServer()).hero().fate(name(player)).isEmpty()) return;
        PENDING.add(new Pending(player, DELAY_TICKS));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // 2초 안에 나가는 경우가 있다. 안 지우면 사라진 플레이어에게 패킷을 쏜다.
        PENDING.removeIf(p -> p.player == event.getEntity());
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        PENDING.removeIf(p -> {
            if (--p.ticksLeft > 0) return false;
            ServerPlayer sp = p.player;
            if (sp.getServer() == null || sp.hasDisconnected()) return true;
            // 다시 확인한다 — 기다리는 2초 사이에 가호가 생겼으면 열 이유가 없다.
            if (!LSData.get(sp.getServer()).hero().fate(name(sp)).isEmpty()) return true;
            LSCommands.openFateScreen(sp);
            return true;
        });
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }
}
