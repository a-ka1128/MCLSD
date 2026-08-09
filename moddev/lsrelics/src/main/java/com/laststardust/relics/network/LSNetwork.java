package com.laststardust.relics.network;

import com.laststardust.relics.FateCatalog;
import com.laststardust.relics.LSRelics;
import com.laststardust.relics.item.RelicActions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// 패킷 등록 + 서버측 처리.
//   relic_input (클라→서버) 유물 평타 / R·V·C·X 스킬
//   fate_choose (클라→서버) 가호 선택 화면에서 고른 가호
//   fate_open   (서버→클라) 가호 선택 화면을 열어라
//   town_open   (클라→서버) 마을 관리 화면을 열어달라 (키바인드)
//   town_action (클라→서버) 마을 화면에서 누른 버튼
//   town_view   (서버→클라) 마을 현황 — 허브를 열거나 갱신
@EventBusSubscriber(modid = LSRelics.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class LSNetwork {
    private LSNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar("1");
        reg.playToServer(RelicInputPayload.TYPE, RelicInputPayload.STREAM_CODEC, LSNetwork::handle);
        reg.playToServer(FateChoosePayload.TYPE, FateChoosePayload.STREAM_CODEC, LSNetwork::handleChoose);
        reg.playToClient(FateOpenPayload.TYPE, FateOpenPayload.STREAM_CODEC, LSNetwork::handleOpen);
        reg.playToServer(TownOpenPayload.TYPE, TownOpenPayload.STREAM_CODEC, LSNetwork::handleTownOpen);
        reg.playToServer(TownActionPayload.TYPE, TownActionPayload.STREAM_CODEC, LSNetwork::handleTownAction);
        reg.playToClient(TownViewPayload.TYPE, TownViewPayload.STREAM_CODEC, LSNetwork::handleTownView);
        reg.playToServer(BlessActionPayload.TYPE, BlessActionPayload.STREAM_CODEC, LSNetwork::handleBlessAction);
        reg.playToClient(BlessViewPayload.TYPE, BlessViewPayload.STREAM_CODEC, LSNetwork::handleBlessView);
        reg.playToClient(RelicAnimPayload.TYPE, RelicAnimPayload.STREAM_CODEC, LSNetwork::handleAnim);
    }

    // ── 플레이어 자세 ──
    // 애니메이션은 클라에만 있다(PlayerAnimator 는 클라 라이브러리). 서버는 «지금 시작하라»만 보낸다.
    private static void handleAnim(RelicAnimPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 라이브러리가 없는 클라도 이 패킷을 받는다 — 없으면 자세만 없고 나머지는 그대로 돈다.
            if (!net.neoforged.fml.ModList.get().isLoaded("playeranimator")) return;
            if (payload.anim() == RelicAnimPayload.SUNDER) {
                com.laststardust.relics.client.anim.RelicAnimations.playSunder(payload.entityId());
            }
        });
    }

    // ── 별의 제단 ──
    // 규칙(해금·성급·중복금지·비용)은 전부 BlessingService 한 곳에 있다. 마을과 같은 구조다.

    private static void handleBlessAction(BlessActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof com.laststardust.relics.blessing.BlessMenu menu)) return;

            // 클라가 보낸 문자열은 enum 과 대조해 거른다 — 그대로 믿으면 잠긴 칸도 굴릴 수 있다.
            com.laststardust.relics.data.BlessingCatalog.Slot slot;
            try {
                slot = com.laststardust.relics.data.BlessingCatalog.Slot.valueOf(payload.slot());
            } catch (IllegalArgumentException e) { return; }

            // 올려둔 장비가 «그 칸»을 여는 게 맞는지도 서버가 다시 본다. 화면만 믿으면
            // 유물을 올려놓고 상의칸을 굴리는 패킷을 손으로 보낼 수 있다.
            if (!menu.visibleSlots().contains(slot)) return;

            var action = switch (payload.action()) {
                case "kind"  -> com.laststardust.relics.blessing.BlessingService.Action.REROLL_KIND;
                case "value" -> com.laststardust.relics.blessing.BlessingService.Action.REROLL_VALUE;
                case "bless" -> com.laststardust.relics.blessing.BlessingService.Action.BLESS;
                default -> null;
            };
            if (action == null) return;

            var res = com.laststardust.relics.blessing.BlessingService.apply(
                player, slot, action, menu.work());
            // 성공하면 apply 안에서 이미 현황을 보냈다(스핀 포함). 실패했을 때만 여기서 갱신한다 —
            // 재료칸이 줄었을 수 있고, 이유는 이미 채팅으로 갔으므로 스핀은 돌리지 않는다.
            if (!res.ok()) com.laststardust.relics.blessing.BlessGui.sync(player, "");
        });
    }

    private static void handleBlessView(BlessViewPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.laststardust.relics.client.BlessScreens.update(payload.view()));
    }

    // ── 마을 관리 ──
    // 규칙(비용·자원·레벨 상한)은 전부 TownService 한 곳에 있다.
    // 화면·명령·패킷이 모두 거기를 거친다 — 판정이 두 곳에 생기면 반드시 어긋난다.

    private static void handleTownOpen(TownOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            com.laststardust.relics.town.TownGui.openHub(player);
        });
    }

    // 클라가 보낸 트랙 키는 카탈로그와 대조해 거른다 — 클라 입력을 그대로 믿지 않는다.
    private static void handleTownAction(TownActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            String action = payload.action();
            String arg = payload.arg();
            switch (action) {
                case "hub" -> com.laststardust.relics.town.TownGui.openHub(player);
                case "open_track" -> {
                    if (com.laststardust.relics.data.TownCatalog.byKey(arg) == null) return;
                    com.laststardust.relics.town.TownGui.openTrack(player, arg);
                }
                case "upgrade" -> {
                    if (com.laststardust.relics.data.TownCatalog.byKey(arg) == null) return;
                    boolean done = com.laststardust.relics.town.TownService.upgrade(player, arg);
                    if (done) {
                        // 단계가 오르면 받는 아이템이 바뀐다. 열려 있는 메뉴는 옛 아이템을 들고 있으므로
                        // 다시 열어 슬롯 필터를 갱신한다 — 안 그러면 이전 자원이 계속 들어간다.
                        com.laststardust.relics.town.TownGui.openTrack(player, arg);
                    } else {
                        com.laststardust.relics.town.TownGui.sync(player);
                    }
                }
                default -> { }
            }
        });
    }

    private static void handleTownView(TownViewPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.laststardust.relics.client.TownScreens.show(payload.view(), payload.openHub()));
    }

    // 가호 선택 — 규칙(중복 선택 금지·패시브 부여·시작 키트)은 전부 KubeJS(ls_fate.js)에 있다.
    // 그래서 여기서 직접 처리하지 않고 /fate choose 로 넘긴다. 판정이 두 곳에 생기지 않게.
    private static void handleChoose(FateChoosePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            String key = payload.key();
            // 클라가 보낸 문자열이 그대로 명령에 들어가므로 화이트리스트로 반드시 거른다.
            if (!FateCatalog.isValid(key)) return;
            MinecraftServer server = player.getServer();
            if (server == null) return;
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "fate choose " + key);

            // ── 거절당했으면 새 목록으로 다시 연다 (2026-08-06) ──
            // 화면의 「남이 가진 가호」는 **열었을 때의 스냅샷**이다. 둘이 동시에 열어 둘 다 같은
            // 가호를 고르면, 두 화면 모두 그게 비어 있다고 보여 준다. 서버가 나중 사람을 거절하는데
            // 그때는 화면이 이미 닫힌 뒤라 채팅 한 줄만 남는다 — 「눌렀는데 아무 일도 안 일어났다」.
            //
            // 명령의 성공 여부를 보는 대신 **결과**를 본다: 가호가 안 생겼으면 실패한 것이다.
            // (`performPrefixedCommand` 의 반환값은 KubeJS 가 등록한 명령에서 신뢰하기 어렵다.)
            if (com.laststardust.relics.data.LSData.get(server).hero()
                    .fate(player.getGameProfile().getName()).isEmpty()) {
                com.laststardust.relics.LSCommands.openFateScreen(player);
            }
        });
    }

    // 화면 열기는 클라에서만 의미가 있다. 클라 전용 클래스는 이 람다가 실행될 때 지연 로드되므로
    // 전용 서버에선 화면 클래스를 아예 건드리지 않는다.
    private static void handleOpen(FateOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() ->
            com.laststardust.relics.client.FateScreenOpener.open(payload.current(), payload.taken()));
    }

    private static void handle(RelicInputPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof RelicActions relic)) return;
            switch (payload.action()) {
                case RelicInputPayload.ATTACK -> relic.leftAttack(level, player, stack);
                case RelicInputPayload.BASIC -> relic.basicSkill(level, player, stack);
                case RelicInputPayload.DODGE -> relic.doubleSneak(level, player, stack);
                case RelicInputPayload.EXTRA -> relic.extraSkill(level, player, stack);
                case RelicInputPayload.ULTIMATE -> relic.ultimate(level, player, stack);
                default -> { }
            }
        });
    }
}
