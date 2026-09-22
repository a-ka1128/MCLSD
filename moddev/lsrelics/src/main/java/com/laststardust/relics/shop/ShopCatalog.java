package com.laststardust.relics.shop;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * <h2>상인 광장에서 파는 것</h2>
 *
 * <p>── 무엇을 «안» 파는가가 더 중요하다 ──
 * {@code DESIGN.md} P2·P4: 개인 지갑은 <b>치장·편의·수평</b>에 쓴다.
 * <b>파워는 안 판다</b> — 진짜 강해지는 건 공동 금고(마을 발전) 쪽이어야
 * 「강해지는 게 경쟁이 아니라 협동」이 성립한다.
 * 그래서 여기에 무기·방어구·인챈트책·유물 관련은 <b>넣지 않는다.</b>
 * 넣는 순간 「돈 많은 사람이 센 사람」이 되고, 그건 이 서버가 피하려던 바로 그 구조다.
 *
 * <p>── 값 ──
 * 지금은 고정가다. 마을 레벨에 따라 깎아 주는 건 나중에 얹을 수 있다
 * (구역 트랙이 그 자리를 이미 비워 두고 있다).
 *
 * <p>⚠️ 아이템 id 가 틀리면 <b>조용히 안 팔린다.</b> {@link #resolve} 가 못 찾은 것을
 * 로그로 알린다 — 목록에서 사라진 이유를 화면만 봐서는 알 수 없다.
 */
public final class ShopCatalog {
    private ShopCatalog() {}

    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    /**
     * @param id    아이템
     * @param count 한 번에 주는 개수
     * @param price 그 묶음의 값 (Ducat)
     * @param group 화면에서 묶어 보여줄 이름
     */
    /**
     * @param ench 마법책일 때 «부여할 인챈트» id (`minecraft:mending` 등). 아니면 빈 문자열.
     * @param lvl  그 인챈트의 레벨
     *
     * <p>⚠️ 실제 책은 여기서 못 만든다 — 인챈트는 «데이터팩 레지스트리»라
     * RegistryAccess 가 필요한데 이 record 는 서버를 모른다.
     * 책은 {@code ShopService.buy} 가 서버를 들고 만들고, 화면 표시는 클라가
     * 번역키로 이름만 붙인다. 그래서 여기엔 «무엇을 만들지»만 적는다.
     */
    public record Entry(String id, int count, int price, String group, String ench, int lvl) {
        public Entry(String id, int count, int price, String group) {
            this(id, count, price, group, "", 0);
        }

        public ItemStack stack() {
            var rl = ResourceLocation.tryParse(id);
            if (rl == null) return ItemStack.EMPTY;
            var item = BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
            return new ItemStack(item, count);
        }
    }

    // 순서가 화면 순서다. 묶음별로 모아 둔다.
    //
    // ⚠️ 2026-08-13 유저가 골라낸 목록이다. 뺀 것들(모루·소고기·깃발·화분·랜턴·그림·
    //    철 주괴·흑요석)은 «가서 캐거나 만들면 되는 것»이라 상점에 있으면 오히려
    //    나가서 무언가 할 이유를 지운다. 파는 것은 «구하기 번거로운데 파워는 아닌 것»으로 좁힌다.
    // ── 2026-08-14: 사는 값 전부 ×5 ──
    // 첫 세션에서 «너무 싸다»가 나왔다. 개인 지갑 수입이 공성 한 번에 40(대공세 80)에
    // 판매액 70% 인데, 옛 값이면 밤 두어 번에 엔더 상자를 사고도 남았다.
    // 파는 값(아래 SELL)은 «그대로» 둔다 — 여기만 올려야 「팔아서 산다」의 환율이 조여진다.
    // 둘 다 올리면 아무것도 안 바뀐다.
    public static final List<Entry> ALL = List.of(
        // ── 편의 ── 노가다를 줄여 주는 것들. 파워가 아니라 «시간»을 산다.
        new Entry("minecraft:ender_chest",  1,  900, "편의"),
        new Entry("minecraft:shulker_box",  1, 1300, "편의"),
        new Entry("minecraft:name_tag",     1,  300, "편의"),
        new Entry("minecraft:saddle",       1,  250, "편의"),

        // ── 소모품 ── 원정·공성 준비물.
        new Entry("minecraft:golden_apple",       4, 450, "소모품"),
        new Entry("minecraft:arrow",             64, 200, "소모품"),
        new Entry("minecraft:experience_bottle", 16, 350, "소모품"),

        // ── 재료 ── 거래에 쓰는 것만 남긴다.
        new Entry("minecraft:emerald", 4, 400, "재료"),

        // ── 치장 ── 개인 지갑의 «본래» 쓰임.
        new Entry("minecraft:firework_rocket", 16, 200, "치장"),

        // ── 마법책 ──
        // 유물은 부술 수도 없고 새로 얻을 수도 없다. 그래서 «수리»가 다른 무기보다 훨씬 무겁다 —
        // 상점에 두는 이유가 그것이다. 값은 편의품(엔더 상자 900)보다 위에 둔다.
        //
        // ⚠️ 인챈트 테이블에서도 나오지만 **무작위다.** 유물은 하나뿐이라 실패가 아프므로,
        //    「확실하게 사는 길」에 값을 매긴다.
        new Entry("minecraft:enchanted_book", 1, 1000, "마법책", "minecraft:mending", 1),
        // 체력으로 수리한다(아포테오시스). 수선과 달리 경험치를 안 먹어서 각성과 안 겹친다.
        new Entry("minecraft:enchanted_book", 1, 1400, "마법책", "apothic_enchanting:life_mending", 1)
    );

    // ── 파는 쪽 (플레이어 → 상인) ──
    //
    // 광물과 전리품. «쌓이기만 하고 쓸 데 없는 것»에 값을 붙여 주는 자리다 —
    // 썩은 살점 한 무더기가 화살 한 뭉치가 되면 창고를 비울 이유가 생긴다.
    //
    // ⚠️ 값을 후하게 매기면 몹 농장 하나로 상점이 끝난다. 전리품은 1~3 으로 낮게 두고,
    //    캐야 나오는 광물에 무게를 준다 — 「나가서 무언가 한 대가」가 커야 한다.
    //
    // ⚠️ 사는 값과 파는 값을 겹치게 두면 안 된다. 예를 들어 에메랄드를 4개 80(개당 20)에
    //    팔면서 개당 20에 사들이면 무한 순환이 된다. 파는 값은 반드시 더 싸다.
    public record Sell(String id, int unit, String group) {
        public net.minecraft.world.item.Item item() {
            var rl = ResourceLocation.tryParse(id);
            return rl == null ? null : BuiltInRegistries.ITEM.get(rl);
        }
    }

    public static final List<Sell> SELLABLE = List.of(
        // ── 광물 ──
        new Sell("minecraft:coal",            1, "광물"),
        new Sell("minecraft:copper_ingot",    2, "광물"),
        new Sell("minecraft:redstone",        2, "광물"),
        new Sell("minecraft:lapis_lazuli",    2, "광물"),
        new Sell("minecraft:iron_ingot",      3, "광물"),
        new Sell("minecraft:gold_ingot",      5, "광물"),
        new Sell("minecraft:emerald",        12, "광물"),   // 사는 값 개당 20 보다 싸다
        new Sell("minecraft:diamond",        25, "광물"),
        new Sell("minecraft:netherite_scrap", 90, "광물"),

        // ── 전리품 ── 몹에서 나오는 것. 값이 낮은 이유는 위 주석에.
        new Sell("minecraft:rotten_flesh",   1, "전리품"),
        new Sell("minecraft:bone",           1, "전리품"),
        // 화살: 사는 값이 64개 200(개당 3.125)이라 그보다 반드시 싸야 한다 — 위 ⚠️ 규칙.
        // 1 로 두는 또 다른 이유: 화살은 부싯돌+막대기+깃털로 «만들 수도» 있어서,
        // 값을 올리면 해골 농장이 아니라 «닭 농장»이 돈줄이 된다.
        new Sell("minecraft:arrow",          1, "전리품"),
        new Sell("minecraft:string",         1, "전리품"),
        new Sell("minecraft:spider_eye",     2, "전리품"),
        new Sell("minecraft:gunpowder",      3, "전리품"),
        new Sell("minecraft:slime_ball",     3, "전리품"),
        new Sell("minecraft:ender_pearl",    8, "전리품"),
        new Sell("minecraft:blaze_rod",     10, "전리품"),
        new Sell("minecraft:ghast_tear",    15, "전리품"),
        new Sell("minecraft:phantom_membrane", 6, "전리품")
    );

    public static List<Sell> resolveSell() {
        var out = new java.util.ArrayList<Sell>();
        for (Sell s : SELLABLE) {
            var it = s.item();
            if (it == null || it == net.minecraft.world.item.Items.AIR) {
                LOG.warn("[상점] 매입 목록에서 뺀다(아이템 없음): {}", s.id());
                continue;
            }
            out.add(s);
        }
        return out;
    }

    public static Sell sellByIndex(int i) {
        var list = resolveSell();
        return (i < 0 || i >= list.size()) ? null : list.get(i);
    }

    /** 실제로 존재하는 아이템만 남긴다. 없는 것은 로그로 알리고 뺀다. */
    public static List<Entry> resolve() {
        var out = new java.util.ArrayList<Entry>();
        for (Entry e : ALL) {
            if (e.stack().isEmpty()) {
                LOG.warn("[상점] 아이템을 못 찾아 목록에서 뺀다: {}", e.id());
                continue;
            }
            out.add(e);
        }
        return out;
    }

    /** 화면이 보낸 번호가 실제 항목인지 서버가 다시 본다 — 클라를 믿으면 아무거나 살 수 있다. */
    public static Entry byIndex(int i) {
        var list = resolve();
        return (i < 0 || i >= list.size()) ? null : list.get(i);
    }
}
