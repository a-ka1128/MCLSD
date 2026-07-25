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
        });
    }

    // 화면 열기는 클라에서만 의미가 있다. 클라 전용 클래스는 이 람다가 실행될 때 지연 로드되므로
    // 전용 서버에선 화면 클래스를 아예 건드리지 않는다.
    private static void handleOpen(FateOpenPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> com.laststardust.relics.client.FateScreenOpener.open(payload.current()));
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
