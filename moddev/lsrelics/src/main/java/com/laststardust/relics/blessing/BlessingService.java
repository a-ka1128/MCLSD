package com.laststardust.relics.blessing;

import java.util.List;

import com.laststardust.relics.data.BlessingCatalog;
import com.laststardust.relics.data.BlessingCatalog.Blessing;
import com.laststardust.relics.data.BlessingCatalog.Slot;
import com.laststardust.relics.data.BlessingData;
import com.laststardust.relics.data.LSCurrency;
import com.laststardust.relics.data.LSData;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

/**
 * 별의 축복 규칙의 단일 창구. 명령·화면·패킷이 전부 여기를 거친다.
 *
 * <p>{@code TownService} 와 같은 이유로 한 곳이다 — KubeJS 시절 판정이 스크립트에, 표시가 모드에
 * 나뉘어 있어 둘이 어긋나면 <b>「버튼은 켜졌는데 눌러도 안 되는」</b> 상태가 났다.
 *
 * <p><b>굴림은 여기서만 일어난다.</b> 화면의 스핀은 결과를 받아 «연기»할 뿐이라, 연출 도중에
 * 창을 닫아도 결과가 안 날아간다.
 */
public final class BlessingService {
    private BlessingService() {}

    /** 무엇을 하려는가. 셋 다 마지막엔 「슬롯에 값이 박힌다」로 끝난다 — 실패하는 굴림은 없다. */
    public enum Action {
        /** 빈 슬롯을 처음 채운다. <b>무료</b> — 각성이 이미 파편을 먹었다. */
        BLESS,
        /** 종류를 바꾼다. 별의 파편. 수치도 같이 다시 굴린다. */
        REROLL_KIND,
        /** 수치만 다시 굴린다. 별먼지. */
        REROLL_VALUE
    }

    /** 결과. 실패면 {@code roll} 이 null 이고, 사유는 이미 플레이어에게 전달된 상태다. */
    public record Result(boolean ok, Slot slot, BlessingData.Roll roll) {
        public static Result fail() { return new Result(false, null, null); }
    }

    // ── 마을 해금 ──

    /** 공방 Lv2「대장간 지원」— 제단 자체가 열렸는가. */
    public static boolean altarUnlocked(MinecraftServer server) {
        return LSData.get(server).town().flag(BlessingCatalog.FLAG_ALTAR);
    }

    /** 성소 Lv4「성역의 가호」— 수치 리롤이 1 싸진다. */
    public static boolean altarUpgraded(MinecraftServer server) {
        return LSData.get(server).town().flag(BlessingCatalog.FLAG_UPGRADE);
    }

    // ── 비용 ──

    public static Item costItem(Action action) {
        return switch (action) {
            case BLESS        -> null;
            case REROLL_KIND  -> LSCurrency.essence();
            case REROLL_VALUE -> LSCurrency.stardust();
        };
    }

    public static int costAmount(MinecraftServer server, Action action, int star) {
        return switch (action) {
            case BLESS        -> 0;
            case REROLL_KIND  -> BlessingCatalog.kindCost(star);
            case REROLL_VALUE -> BlessingCatalog.valueCost(star, altarUpgraded(server));
        };
    }

    // ── 실행 ──

    /**
     * 검증 → 굴림 → 저장 → 소모. <b>이 순서를 지킨다</b> — 소모를 먼저 하면 뒤에서 걸렸을 때
     * 재화만 사라진다. 「집행 구간」에 들어가면 실패하지 않는다({@code TownService.upgrade} 와 같은 규칙).
     */
    public static Result apply(ServerPlayer player, Slot slot, Action action) {
        return apply(player, slot, action, null);
    }

    /**
     * @param costFrom 재화를 여기서 뺀다. {@code null} 이면 인벤토리에서 — 명령({@code /bless})은
     *                 인벤토리, 제단 화면은 재료 슬롯을 넘긴다. 화면에서 인벤토리를 몰래 털면
     *                 「올려둔 건 그대로인데 가방에서 사라졌다」가 되어 무엇을 냈는지 못 따라간다.
     */
    public static Result apply(ServerPlayer player, Slot slot, Action action,
                               net.minecraft.world.Container costFrom) {
        MinecraftServer server = player.getServer();
        if (server == null || slot == null || action == null) return Result.fail();

        String name = player.getGameProfile().getName();
        LSData data = LSData.get(server);
        BlessingData bless = data.blessing();

        // ① 제단이 열렸는가 — 마을 공방 Lv2
        if (!altarUnlocked(server)) {
            player.sendSystemMessage(Component.translatable("lsblessing.msg.no_altar"));
            return Result.fail();
        }

        // ② 유물 — 축복은 유물에 딸린 성장이다
        if (!data.hero().hasRelic(name)) {
            player.sendSystemMessage(Component.translatable("lsblessing.msg.no_relic"));
            return Result.fail();
        }

        // ③ 슬롯이 열렸는가 — 각성 성급
        int star = data.hero().star(name);
        if (!slot.unlockedAt(star)) {
            player.sendSystemMessage(Component.translatable("lsblessing.msg.locked",
                Component.translatable(slot.nameKey()), slot.star, star));
            return Result.fail();
        }

        // ④ 슬롯 상태와 하려는 일이 맞는가
        BlessingData.Roll cur = bless.get(name, slot);
        if (action == Action.BLESS && cur != null) {
            player.sendSystemMessage(Component.translatable("lsblessing.msg.already",
                Component.translatable(slot.nameKey())));
            return Result.fail();
        }
        if (action != Action.BLESS && cur == null) {
            player.sendSystemMessage(Component.translatable("lsblessing.msg.empty",
                Component.translatable(slot.nameKey())));
            return Result.fail();
        }

        // ⑤ 비용 — 세는 것과 빼는 것을 LSCurrency.take 안에서 같이 한다
        Item cost = costItem(action);
        int need = costAmount(server, action, star);
        if (need > 0) {
            if (cost == null) {   // KubeJS 아이템이 아직 없다 — 조용히 통과시키면 공짜가 된다
                player.sendSystemMessage(Component.translatable("lsblessing.msg.no_currency"));
                return Result.fail();
            }
            int have = costFrom == null ? LSCurrency.count(player, cost) : LSCurrency.count(costFrom, cost);
            if (have < need) {
                player.sendSystemMessage(Component.translatable("lsblessing.msg.need",
                    cost.getDescription(), have, need));
                return Result.fail();
            }
        }

        // ── 집행 — 여기서부터는 실패하지 않는다 ──
        RandomSource rnd = player.level().getRandom();
        Blessing def;
        if (action == Action.REROLL_VALUE) {
            def = cur.def();
            // 카탈로그에서 사라진 id 는 로드에서 걸러지므로 여기 null 이면 진짜 이상한 상태다
            if (def == null) { player.sendSystemMessage(Component.translatable("lsblessing.msg.empty",
                    Component.translatable(slot.nameKey()))); return Result.fail(); }
        } else {
            def = pick(bless, name, slot, rnd, action == Action.REROLL_KIND ? cur.id() : null);
            if (def == null) return Result.fail();   // 후보가 없다 — 정의상 불가능하지만 방어
        }

        boolean paid = need <= 0
            || (costFrom == null ? LSCurrency.take(player, cost, need) : LSCurrency.take(costFrom, cost, need));
        if (!paid) {
            // count 를 통과했는데 take 가 실패할 수는 없다. 그래도 조용히 넘어가면 «공짜 굴림»이 된다.
            player.sendSystemMessage(Component.translatable("lsblessing.msg.need",
                cost.getDescription(),
                costFrom == null ? LSCurrency.count(player, cost) : LSCurrency.count(costFrom, cost), need));
            return Result.fail();
        }

        float value = def.roll(rnd, star);
        bless.set(name, slot, def.id(), value);
        data.dirty();

        announce(player, slot, def, value, star);
        // 클라 사본을 갱신한다 — 툴팁이 이걸 읽는다. 화면이 열려 있으면 스핀도 여기서 시작한다.
        // **여기 한 곳에서만 보낸다** — 명령·화면 어느 경로로 들어와도 같은 자리를 지나므로
        // 「명령으로 굴리면 툴팁이 안 바뀐다」 같은 반쪽 상태가 안 생긴다.
        BlessGui.sync(player, slot.name());
        return new Result(true, slot, bless.get(name, slot));
    }

    /**
     * 후보 하나를 고른다 — <b>균등 추첨</b>(희귀도 가중치는 폐기됐다).
     * 짝 슬롯에 있는 것은 {@link BlessingData#candidates} 가 이미 뺀다.
     *
     * @param exclude 종류 리롤일 때 «지금 것». 빼지 않으면 파편을 내고 같은 게 나올 수 있고,
     *                그건 실패가 없다는 원칙과 어긋나 보인다.
     */
    private static Blessing pick(BlessingData bless, String name, Slot slot,
                                 RandomSource rnd, String exclude) {
        List<Blessing> pool = bless.candidates(name, slot);
        if (exclude != null) {
            pool = new java.util.ArrayList<>(pool);
            pool.removeIf(b -> b.id().equals(exclude));
        }
        if (pool.isEmpty()) return null;
        return pool.get(rnd.nextInt(pool.size()));
    }

    /**
     * 결과를 채팅에도 남긴다.
     *
     * <p>곡선은 플레이어에게 안 보인다 — 나쁘게 떠도 <b>버그인지 운인지 구분이 안 되므로</b>
     * 백분위와 「이 성급에서 이만큼 이상 나올 확률」을 같이 띄운다.
     * {@code /mobscale}·{@code /siege status} 가 이미 쓰는 방식이다.
     */
    private static void announce(ServerPlayer player, Slot slot, Blessing def, float value, int star) {
        float pct = def.percentileOf(value);
        player.sendSystemMessage(Component.translatable("lsblessing.msg.rolled",
            Component.translatable(slot.nameKey()),
            Component.translatable(def.nameKey()),
            fmt(value), fmt(def.min()), fmt(def.max()), Math.round(pct * 100)));
        player.sendSystemMessage(Component.translatable("lsblessing.msg.curve",
            star, Math.round(BlessingCatalog.chanceAbove(star, 0.8f) * 100)));
        player.level().playSound(null, player.blockPosition(),
            SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    /** 소수 첫째 자리까지. 「9.2%」로 읽히게 — 9.234% 는 정밀해 보이지만 아무 정보가 없다. */
    public static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
