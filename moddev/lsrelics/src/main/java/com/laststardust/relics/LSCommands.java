package com.laststardust.relics;

import com.laststardust.relics.item.RelicSkills;
import com.laststardust.relics.network.FateOpenPayload;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 각성 단계를 아이템에 새기는 관리자 명령.
//
// 각성의 "자격 판정"(관문 클리어 수·균열 정수 소모)은 KubeJS(ls_ascend.js)가 한다 —
// 진행도가 전부 거기 persistentData에 있기 때문. 여기서는 판정이 끝난 뒤
// 실제 아이템 NBT에 별을 새기는 일만 맡는다.
//   /lsrelic star <1-5>   손에 든 유물의 각성 단계를 설정
@EventBusSubscriber(modid = LSRelics.MODID)
public final class LSCommands {
    private LSCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("lsrelic")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("star")
                    .then(Commands.argument("n", IntegerArgumentType.integer(1, 5))
                        .executes(ctx -> setStar(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n"))))));

        // 훈련 더미 — 파티 실효 DPS 실측 (보스 체력 설계의 근거가 된다)
        event.getDispatcher().register(
            Commands.literal("dummy")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("spawn").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                    int n = DummyManager.spawn(p);
                    ctx.getSource().sendSuccess(() -> Component.literal("§a훈련 더미 소환 §7(총 " + n + "기) — §e/dummy start §7로 측정 시작"), false);
                    return 1;
                }))
                // 시간을 주면 그만큼 재고 스스로 멈춘다. 안 주면 예전처럼 /dummy stop 까지 계속.
                // 유물끼리 비교할 땐 반드시 시간을 고정해야 한다 — 손으로 멈추면 반응 시간이
                // 측정 창에 섞여 같은 유물도 회차마다 다른 값이 나온다.
                .then(Commands.literal("start")
                    .executes(ctx -> startDummy(ctx.getSource(), 0))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 600))
                        .executes(ctx -> startDummy(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "seconds")))))
                .then(Commands.literal("stop").executes(ctx -> {
                    if (!DummyManager.isMeasuring()) {
                        ctx.getSource().sendFailure(Component.literal("측정 중이 아니다. 먼저 /dummy start"));
                        return 0;
                    }
                    for (Component line : DummyManager.stop()) {
                        ctx.getSource().sendSuccess(() -> line, true);
                    }
                    return 1;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    int n = DummyManager.clear();
                    ctx.getSource().sendSuccess(() -> Component.literal("§7훈련 더미 " + n + "기 제거"), false);
                    return 1;
                }))
                // 표적 방어도 — 보스의 실제 방어도를 넣고 재야 "체감 화력"이 나온다
                .then(Commands.literal("armor")
                    .then(Commands.argument("armor", IntegerArgumentType.integer(0, 30))
                        .executes(ctx -> setDummyArmor(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "armor"), 0))
                        .then(Commands.argument("toughness", IntegerArgumentType.integer(0, 20))
                            .executes(ctx -> setDummyArmor(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "armor"),
                                IntegerArgumentType.getInteger(ctx, "toughness"))))))
                // 몹 공격력 설계용 — 이 피해가 장비별로 얼마나 들어가는지
                .then(Commands.literal("calc")
                    .then(Commands.argument("damage", IntegerArgumentType.integer(1, 1000)).executes(ctx -> {
                        for (Component line : DummyManager.armorTable(IntegerArgumentType.getInteger(ctx, "damage"))) {
                            ctx.getSource().sendSuccess(() -> line, false);
                        }
                        return 1;
                    })))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§6훈련 더미 §7— 더미 " + DummyManager.count() + "기 · "
                        + (DummyManager.isMeasuring()
                            ? String.format("§c측정 중 (%.0f초)", DummyManager.elapsedSeconds())
                            : "§8대기")), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                        "§7표적 방어도 §e%.0f§7 · 견고함 §e%.0f", DummyManager.armor(), DummyManager.toughness())), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§8/dummy spawn · start [초] · stop · clear · armor <값> [견고함] · calc <피해>"), false);
                    return 1;
                }));

        // 가호 선택 화면 열기 — 모든 플레이어가 쓴다(/lsrelic 은 OP 전용이라 그 아래 두면 안 된다).
        // 현재 가호는 KubeJS(persistentData)에만 있어 모드가 읽을 수 없으므로 인자로 받는다.
        //   /fateui           선택 안 한 상태로 열기
        //   /fateui <key>     이미 그 가호를 받은 상태로 열기 (선택 버튼 비활성)
        event.getDispatcher().register(
            Commands.literal("fateui")
                .executes(ctx -> openFateScreen(ctx.getSource(), ""))
                .then(Commands.argument("current", StringArgumentType.word())
                    .executes(ctx -> openFateScreen(ctx.getSource(), StringArgumentType.getString(ctx, "current")))));

        // ── 마을(성역 재건) ──
        // 데이터·판정이 전부 모드에 있으므로 명령도 여기서 등록한다.
        //   /town                열기   · /town info  현황
        //   /town treasury ...   금고   · /town level 관리자 조정
        event.getDispatcher().register(
            Commands.literal("town")
                .executes(ctx -> openTown(ctx.getSource()))
                .then(Commands.literal("info").executes(ctx -> townInfo(ctx.getSource())))
                .then(Commands.literal("treasury")
                    .executes(ctx -> {
                        var s2 = ctx.getSource();
                        int t = com.laststardust.relics.data.LSData.get(s2.getServer()).town().treasury();
                        s2.sendSuccess(() -> Component.translatable("lstown.msg.treasury", t), false);
                        return 1;
                    })
                    .then(Commands.literal("add").requires(s2 -> s2.hasPermission(2))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                            .executes(ctx -> addTreasury(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))))
                    .then(Commands.literal("spend").requires(s2 -> s2.hasPermission(2))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                            .executes(ctx -> addTreasury(ctx.getSource(), -IntegerArgumentType.getInteger(ctx, "n"))))))
                .then(Commands.literal("level").requires(s2 -> s2.hasPermission(2))
                    .then(Commands.argument("track", StringArgumentType.word())
                        .suggests((c, b) -> { com.laststardust.relics.data.TownCatalog.ALL.forEach(t -> b.suggest(t.key())); return b.buildFuture(); })
                        .then(Commands.argument("n", IntegerArgumentType.integer(0, 4))
                            .executes(ctx -> setLevel(ctx.getSource(),
                                StringArgumentType.getString(ctx, "track"),
                                IntegerArgumentType.getInteger(ctx, "n")))))));
    }

    private static int openTown(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) { src.sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
        com.laststardust.relics.town.TownGui.openHub(p);
        return 1;
    }

    private static int townInfo(CommandSourceStack src) {
        var view = com.laststardust.relics.town.TownService.snapshot(src.getServer());
        src.sendSuccess(() -> Component.translatable("lstown.msg.treasury", view.treasury()), false);
        for (var t : view.tracks()) {
            var def = com.laststardust.relics.data.TownCatalog.byKey(t.key());
            String name = def == null ? t.key() : Component.translatable(def.nameKey()).getString();
            String line = "§f" + (def == null ? "" : def.icon() + " ") + name
                + " §7Lv" + t.level() + "/" + t.max()
                + (t.isMax() ? " §6MAX" : " §8→ " + t.nextName() + " (" + t.itemName() + " "
                    + t.have() + "/" + t.need() + " · " + t.ducat() + "D)");
            src.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int addTreasury(CommandSourceStack src, int delta) {
        var data = com.laststardust.relics.data.LSData.get(src.getServer());
        data.town().addTreasury(delta);
        data.dirty();
        int now = data.town().treasury();
        src.sendSuccess(() -> Component.translatable("lstown.msg.treasury", now), true);
        com.laststardust.relics.town.TownGui.syncAll(src.getServer());
        return 1;
    }

    private static int setLevel(CommandSourceStack src, String track, int n) {
        if (com.laststardust.relics.data.TownCatalog.byKey(track) == null) {
            src.sendFailure(Component.literal("알 수 없는 트랙: " + track));
            return 0;
        }
        var data = com.laststardust.relics.data.LSData.get(src.getServer());
        data.town().setLevel(track, n);
        data.dirty();
        // 실제 업그레이드와 같은 상태에 도착해야 한다 — 안 그러면 관리자로 올린 레벨은
        // 귀환석 같은 지급물이 빠진 채로 남아, 검증할 때 "효과가 죽은 것"처럼 보인다.
        com.laststardust.relics.town.TownService.reconcile(src.getServer());
        src.sendSuccess(() -> Component.literal("§a" + track + " → Lv" + n), true);
        com.laststardust.relics.town.TownGui.syncAll(src.getServer());
        return 1;
    }

    private static int startDummy(CommandSourceStack src, int seconds) {
        if (!DummyManager.start(seconds)) {
            src.sendFailure(Component.literal("더미가 없다. 먼저 /dummy spawn"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(seconds > 0
            ? "§a▶ 측정 시작 — §e" + seconds + "초§a 뒤 자동으로 멈추고 결과가 나온다."
            : "§a▶ 측정 시작 — §7/dummy stop 으로 멈춘다. §8(비교하려면 /dummy start <초>)"), true);
        return 1;
    }

    private static int setDummyArmor(CommandSourceStack src, int armor, int toughness) {
        DummyManager.setArmor(armor, toughness);
        src.sendSuccess(() -> Component.literal(String.format(
            "§a표적 방어도 §e%d§a · 견고함 §e%d §7— 이제 측정값이 감쇄를 반영한다", armor, toughness)), false);
        return 1;
    }

    private static int openFateScreen(CommandSourceStack src, String current) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("플레이어만 사용할 수 있다."));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, new FateOpenPayload(current));
        return 1;
    }

    // 인벤토리 전체(주손·보관칸·오프핸드)의 유물에 별을 새긴다.
    // 손에 든 것만 대상으로 하면 /give 직후에는 유물이 아직 보관칸에 있어 각성이 반영되지 않는다.
    private static int setStar(CommandSourceStack src, int star) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("플레이어만 사용할 수 있다."));
            return 0;
        }
        int touched = 0;
        int before = -1;
        for (ItemStack stack : player.getInventory().items) {
            if (!RelicEventHandlers.isRelic(stack)) continue;
            if (before < 0) before = RelicSkills.star(stack);
            stamp(stack, star);
            touched++;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!RelicEventHandlers.isRelic(stack)) continue;
            if (before < 0) before = RelicSkills.star(stack);
            stamp(stack, star);
            touched++;
        }
        if (touched == 0) {
            src.sendFailure(Component.literal("인벤토리에 별의 유물이 없다."));
            return 0;
        }
        final int prev = before;
        final int n = touched;
        src.sendSuccess(() -> Component.literal(
            "§6✦ §7각성 §e" + prev + "성 §7→ §e" + star + "성 §8(유물 " + n + "개)"), false);
        player.level().playSound(null, player.blockPosition(),
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.2f);
        return touched;
    }

    private static void stamp(ItemStack stack, int star) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt("star", star);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
