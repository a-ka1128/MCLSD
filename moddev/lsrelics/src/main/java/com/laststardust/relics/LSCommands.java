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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

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

        // ── T1 관문 기믹 시험 ──
        // 실전 검증이 8분짜리 보스전을 요구하면 아무도 검증하지 않는다. 소환·즉시시전·정리를
        // 한 명령에 묶어 "고치고 30초 안에 다시 본다"가 되게 한다.
        event.getDispatcher().register(
            Commands.literal("lsgimmick")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("summon").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                    if (!WroughtnautGimmick.summon(p)) {
                        ctx.getSource().sendFailure(Component.literal("소환 실패 — Mowzie's Mobs 가 설치되어 있는지 확인."));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§a강철거인 소환 §7— 때려서 교전이 시작되면 §e8초§7 뒤 첫 「대지 가르기」. §8(/lsgimmick now 로 즉시)"), false);
                    return 1;
                }))
                .then(Commands.literal("now").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                    if (!WroughtnautGimmick.forceNear(p)) {
                        ctx.getSource().sendFailure(Component.literal("근처에 강철거인이 없다. 먼저 /lsgimmick summon"));
                        return 0;
                    }
                    return 1;
                }))
                // 취약 창(초록 띠)을 2.9초 강제로 켠다. 진짜 창은 보스가 도끼를 내려찍을 때만
                // 열려서, 그걸 기다려서는 «띠가 제대로 보이는가»를 확인할 수 없다.
                .then(Commands.literal("window").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayer();
                    if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                    if (!WroughtnautGimmick.forceWindow(p)) {
                        ctx.getSource().sendFailure(Component.literal("근처에 강철거인이 없다. 먼저 /lsgimmick summon"));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§a취약 창 2.9초 §7— 초록 띠 안에 서야 딜이 들어간다. §8(표시만 — 실제 무적은 모드 소관)"), false);
                    return 1;
                }))
                // 보스 없이 어휘만 눈으로 본다 — 셰이더·밤·군중 속에서 색이 읽히는지 확인용.
                .then(Commands.literal("test")
                    .then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((c, b) -> { b.suggest("danger"); b.suggest("stack"); b.suggest("spread"); return b.buildFuture(); })
                        .executes(ctx -> testTelegraph(ctx.getSource(), StringArgumentType.getString(ctx, "kind")))))
                .then(Commands.literal("clear").executes(ctx -> {
                    // 기믹을 새로 만들 때마다 여기에 한 줄을 - 반드시 - 더한다.
                    // T4·최종이 빠져 있던 적이 있는데, 시험 소환은 PersistenceRequired 라
                    // 청크를 벗어나도 안 사라진다 — 빠뜨리면 체력 1만짜리가 세계에 영구히 남는다.
                    int n = WroughtnautGimmick.clearTest() + IgnisGimmick.clearTest()
                          + GauntletGimmick.clearTest() + MonstrosityGimmick.clearTest()
                          + LichGimmick.clearTest() + SiegeVanguardGimmick.clearTest();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§7시험 소환분 " + n + "기 제거 §8(세계에서 만난 개체는 건드리지 않는다)"), false);
                    return 1;
                }))
                // ── T2 이그니스 「업화의 결계」 (노랑 = 뭉쳐라) ──
                .then(Commands.literal("ignis")
                    .then(Commands.literal("summon").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                        if (!IgnisGimmick.summon(p)) {
                            ctx.getSource().sendFailure(Component.literal("소환 실패 — Cataclysm 이 설치되어 있는지 확인."));
                            return 0;
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§a이그니스 소환 §7— 교전이 시작되면 §e" + (IgnisGimmick.FIRST_DELAY / 20)
                            + "초§7 뒤 첫 결계. §8(/lsgimmick ignis now 로 즉시)"), false);
                        return 1;
                    }))
                    .then(Commands.literal("now").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                        if (!IgnisGimmick.forceNear(p)) {
                            ctx.getSource().sendFailure(Component.literal("근처에 이그니스가 없다. 먼저 /lsgimmick ignis summon"));
                            return 0;
                        }
                        return 1;
                    }))
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6◆ 이그니스 §e「업화의 결계」 §8— 파티에서 "
                            + (int) IgnisGimmick.CAST_MIN + "~" + (int) IgnisGimmick.CAST_MAX
                            + "칸 떨어진 곳, 반경 " + IgnisGimmick.RADIUS
                            + ", §7원 밖 §8피해 " + IgnisGimmick.DAMAGE + " + 화상"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§8  주기 " + (IgnisGimmick.INTERVAL_P1 / 20) + "초 → 2페이즈("
                            + (int) (IgnisGimmick.PHASE2_HP * 100) + "%) "
                            + (IgnisGimmick.INTERVAL_P2 / 20) + "초 + 연속 시전"), false);
                        for (Component line : IgnisGimmick.status()) {
                            ctx.getSource().sendSuccess(() -> line, false);
                        }
                        return 1;
                    }))
                // ── T3 건틀렛 「분쇄 파문」 (보라 = 흩어져라) ──
                .then(Commands.literal("monstrosity")
                    .then(Commands.literal("summon").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                        if (!MonstrosityGimmick.summon(p)) {
                            ctx.getSource().sendFailure(Component.literal("소환 실패 — L_Ender's Cataclysm 이 설치되어 있는지 확인."));
                            return 0;
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§a네더라이트 괴물 소환 §7— 교전 후 §e" + (MonstrosityGimmick.FIRST_DELAY / 20)
                            + "초§7 뒤 첫 심판. §8(/lsgimmick monstrosity now 로 즉시)"), false);
                        return 1;
                    }))
                    .then(Commands.literal("now").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                        if (!MonstrosityGimmick.forceNear(p)) {
                            ctx.getSource().sendFailure(Component.literal("근처에 네더라이트 괴물이 없다. 먼저 /lsgimmick monstrosity summon"));
                            return 0;
                        }
                        return 1;
                    }))
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6◆ 네더라이트 괴물 §6「용암의 심판」 §8— 두 박자: §d흩어져라§8 → "
                            + (MonstrosityGimmick.BEAT / 20.0) + "초 뒤 §e뭉쳐라"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§8  주기 " + (MonstrosityGimmick.INTERVAL_P1 / 20) + "초 → 2페이즈("
                            + (int) (MonstrosityGimmick.PHASE2_HP * 100) + "%) "
                            + (MonstrosityGimmick.INTERVAL_P2 / 20) + "초 + 보스 발밑 빨강 = 세 어휘 동시"), false);
                        for (Component line : MonstrosityGimmick.status()) {
                            ctx.getSource().sendSuccess(() -> line, false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("lich")
                    .then(Commands.literal("summon").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                        if (!LichGimmick.summon(p)) {
                            ctx.getSource().sendFailure(Component.literal("소환 실패 — Bosses of Mass Destruction 이 설치되어 있는지 확인."));
                            return 0;
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§a리치 소환 §7— 교전 후 §e" + (LichGimmick.FIRST_DELAY / 20)
                            + "초§7 뒤 첫 삼킴. §8(/lsgimmick lich now 로 즉시)"), false);
                        return 1;
                    }))
                    .then(Commands.literal("now").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                        if (!LichGimmick.forceNear(p)) {
                            ctx.getSource().sendFailure(Component.literal("근처에 리치가 없다. 먼저 /lsgimmick lich summon"));
                            return 0;
                        }
                        return 1;
                    }))
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6◆ 리치 §9「별을 삼키는 자」 §8— " + (LichGimmick.SWALLOW_TICKS / 20)
                            + "초 무적(§9파랑§8), §e노란 자리§8에 절반 이상이 "
                            + (LichGimmick.HOLD_NEEDED / 20) + "초 모이면 해제"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§8  못 막으면 최대 체력 " + (int) (LichGimmick.HEAL_FRAC * 100)
                            + "% 회복 · 주기 " + (LichGimmick.INTERVAL_P1 / 20) + "초 → 2페이즈("
                            + (int) (LichGimmick.PHASE2_HP * 100) + "%) " + (LichGimmick.INTERVAL_P2 / 20) + "초"), false);
                        for (Component line : LichGimmick.status()) {
                            ctx.getSource().sendSuccess(() -> line, false);
                        }
                        return 1;
                    }))
                .then(Commands.literal("gauntlet")
                    .then(Commands.literal("summon").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                        if (!GauntletGimmick.summon(p)) {
                            ctx.getSource().sendFailure(Component.literal("소환 실패 — Bosses of Mass Destruction 이 설치되어 있는지 확인."));
                            return 0;
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§a건틀렛 소환 §7— 교전이 시작되면 §e" + (GauntletGimmick.FIRST_DELAY / 20)
                            + "초§7 뒤 첫 파문. §8(/lsgimmick gauntlet now 로 즉시)"), false);
                        return 1;
                    }))
                    .then(Commands.literal("now").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                        if (!GauntletGimmick.forceNear(p)) {
                            ctx.getSource().sendFailure(Component.literal("근처에 건틀렛이 없다. 먼저 /lsgimmick gauntlet summon"));
                            return 0;
                        }
                        return 1;
                    }))
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6◆ 건틀렛 §d「분쇄 파문」 §8— 사람마다 원 하나(반경 "
                            + GauntletGimmick.RADIUS + "), 둘 이상 겹치면 피해 "
                            + GauntletGimmick.DAMAGE), false);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§8  주기 " + (GauntletGimmick.INTERVAL_P1 / 20) + "초 → 2페이즈("
                            + (int) (GauntletGimmick.PHASE2_HP * 100) + "%) "
                            + (GauntletGimmick.INTERVAL_P2 / 20) + "초 + 보스 발밑 빨강"), false);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§8  ※ 1인 시험에서는 원만 뜨고 피해가 없다 — 보라는 정의상 2인 이상 기믹이다"), false);
                        for (Component line : GauntletGimmick.status()) {
                            ctx.getSource().sendSuccess(() -> line, false);
                        }
                        return 1;
                    }))
                // ── 공성 선봉 「무너지는 땅」 — 관문 전에 빨강을 처음 만나는 자리 ──
                .then(Commands.literal("vanguard")
                    .then(Commands.literal("summon").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                        if (!SiegeVanguardGimmick.summon(p)) {
                            ctx.getSource().sendFailure(Component.literal("소환 실패 — Cataclysm 이 설치되어 있는지 확인."));
                            return 0;
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§a균열의 선봉 소환 §7— 교전 후 §e" + (SiegeVanguardGimmick.FIRST_DELAY / 20)
                            + "초§7 뒤 첫 장판. §8(피해 " + SiegeVanguardGimmick.DAMAGE
                            + " — T1 의 " + WroughtnautGimmick.DAMAGE + " 보다 약하다. 연습이니까)"), false);
                        return 1;
                    }))
                    .then(Commands.literal("now").executes(ctx -> {
                        ServerPlayer p = ctx.getSource().getPlayer();
                        if (p == null) { ctx.getSource().sendFailure(Component.literal("플레이어만 사용할 수 있다.")); return 0; }
                        if (!SiegeVanguardGimmick.forceNear(p)) {
                            ctx.getSource().sendFailure(Component.literal("근처에 선봉이 없다. 먼저 /lsgimmick vanguard summon"));
                            return 0;
                        }
                        return 1;
                    }))
                    .executes(ctx -> {
                        for (Component line : SiegeVanguardGimmick.status()) {
                            ctx.getSource().sendSuccess(() -> line, false);
                        }
                        return 1;
                    }))
                // ── 탐험 보스: 모드가 이미 가진 규칙을 보이게 만든 것들 ──
                // 소환·즉시시전이 없다(ExplorerGimmicks 주석 참고). 규칙표와 추적 현황만.
                .then(Commands.literal("explorer").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§6◆ 탐험 보스의 숨은 규칙 §8— 전부 모드가 원래 가진 것. 우리는 보이게만 했다"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§7프로스트모 §8— 불 ×1.25 · §c화살은 피해 0§8 (시리우스가 통째로 막힌다)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§7히드라 §8— §a벌린 입§8만 온전한 피해, 나머지는 1/8 · "
                        + (int) ExplorerGimmicks.HYDRA_MAX_DIST + "칸 밖은 피해 0"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§7유령기사 §8— 돌진 중이 아니면 §a방어도 5배§8 (돌진 때만 딜이 들어간다)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§7우르가스트 §8— 발작 중 피해 1/10 · 누적 18 이면 페이즈 전환"), false);
                    for (Component line : ExplorerGimmicks.status()) {
                        ctx.getSource().sendSuccess(() -> line, false);
                    }
                    return 1;
                }))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§6◆ 강철거인 기믹 §c「대지 가르기」 §8— 무작위 플레이어 발밑, 반경 "
                        + WroughtnautGimmick.RADIUS + ", 피해 " + WroughtnautGimmick.DAMAGE
                        + ", 주기 " + (WroughtnautGimmick.INTERVAL / 20) + "초"), false);
                    for (Component line : WroughtnautGimmick.status()) {
                        ctx.getSource().sendSuccess(() -> line, false);
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§7T1 §c빨강§7 · T2 §e노랑§7 · T3 §d보라§7(+2P 조합) · T4 §6조합 시험§7 · 최종 §9파랑"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§7언제 때릴 것인가 §8— §a초록§7 지금 쳐라(강철거인 취약 창) · §9파랑§7 멈춰라(이그니스 반격)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§8/lsgimmick summon · now · window · test <danger|stack|spread> · clear"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§8/lsgimmick ignis · gauntlet · monstrosity · lich  <summon|now>"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§8/lsgimmick vanguard <summon|now> §8— 공성 선봉(관문 전 연습)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§8/lsgimmick explorer §8— 탐험 보스 4종의 숨은 규칙"), false);
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

    // 어휘 확인 — 판정은 하지 않는다. "저 색이 저 뜻으로 읽히는가"만 본다.
    private static int testTelegraph(CommandSourceStack src, String kind) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("플레이어만 사용할 수 있다."));
            return 0;
        }
        Telegraph.Kind k;
        switch (kind.toLowerCase(java.util.Locale.ROOT)) {
            case "danger": k = Telegraph.Kind.DANGER; break;
            case "stack":  k = Telegraph.Kind.STACK;  break;
            case "spread": k = Telegraph.Kind.SPREAD; break;
            default:
                src.sendFailure(Component.literal("danger · stack · spread 중 하나"));
                return 0;
        }
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        double r = WroughtnautGimmick.RADIUS;
        Telegraph.cast(level, k, center, r, () -> player.sendSystemMessage(Component.literal(
            "§8… 판정 시점 §7— 안쪽 " + Telegraph.inside(level, center, r).size()
            + "명 · 바깥 " + Telegraph.outside(level, center, r).size() + "명")));
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
