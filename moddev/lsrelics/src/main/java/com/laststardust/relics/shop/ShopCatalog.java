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
    public record Entry(String id, int count, int price, String group) {
        public ItemStack stack() {
            var rl = ResourceLocation.tryParse(id);
            if (rl == null) return ItemStack.EMPTY;
            var item = BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
            return new ItemStack(item, count);
        }
    }

    // 순서가 화면 순서다. 묶음별로 모아 둔다.
    public static final List<Entry> ALL = List.of(
        // ── 편의 ── 노가다를 줄여 주는 것들. 파워가 아니라 «시간»을 산다.
        new Entry("minecraft:ender_chest",  1,  180, "편의"),
        new Entry("minecraft:anvil",        1,  120, "편의"),
        new Entry("minecraft:name_tag",     1,   60, "편의"),
        new Entry("minecraft:saddle",       1,   50, "편의"),
        new Entry("minecraft:shulker_box",  1,  260, "편의"),

        // ── 소모품 ── 원정·공성 준비물.
        new Entry("minecraft:golden_apple",       4,  90, "소모품"),
        new Entry("minecraft:arrow",             64,  40, "소모품"),
        new Entry("minecraft:cooked_beef",       32,  30, "소모품"),
        new Entry("minecraft:experience_bottle", 16,  70, "소모품"),

        // ── 재료 ── 캐러 다니는 시간을 줄이는 용도. 값을 일부러 후하게 매기지 않는다.
        new Entry("minecraft:obsidian", 8, 100, "재료"),
        new Entry("minecraft:emerald",  4,  80, "재료"),
        new Entry("minecraft:iron_ingot", 16, 60, "재료"),

        // ── 치장 ── 개인 지갑의 «본래» 쓰임.
        new Entry("minecraft:firework_rocket", 16, 40, "치장"),
        new Entry("minecraft:white_banner",     1, 25, "치장"),
        new Entry("minecraft:flower_pot",       4, 20, "치장"),
        new Entry("minecraft:lantern",         16, 45, "치장"),
        new Entry("minecraft:painting",         4, 30, "치장")
    );

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
