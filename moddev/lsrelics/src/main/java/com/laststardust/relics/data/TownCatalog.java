package com.laststardust.relics.data;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// 마을 발전 트랙 정의 — 4트랙 × 4레벨. (KubeJS ls_town.js 의 TRACKS 를 옮겨온 것)
//
// 여기가 밸런스 다이얼이다. 수치를 바꾸면 모드를 다시 빌드해야 하지만, 그 대신
// 오타는 컴파일에서 걸리고 아이템 ID 도 실존 여부를 부팅 때 확인할 수 있다.
//
// ── 한 레벨에 여러 자원 (2026-08-08) ──
// 원래는 레벨당 아이템 «한 종류»였다. 「돌 + 나무」처럼 성격이 다른 자원을 같이 요구하려고
// {아이템, 개수} 목록으로 바꿨다. 트랙별 색이 이걸로 갈린다 —
// 방벽은 건축 자재(양·가공), 공방은 금속(깊이), 성소는 희귀 재료, 구역은 거래품.
public final class TownCatalog {
    private TownCatalog() {}

    // 별의 파편 — 보스를 잡아야 나오는 전용 재화. 제작·수집과 겹치지 않아 진행도에 비례해 쌓인다.
    public static final ResourceLocation ESSENCE = ResourceLocation.parse("kubejs:rift_essence");

    /**
     * 요구 자원 하나. 아이템 id 이거나 <b>아이템 태그</b>다.
     *
     * <p>태그를 지원하는 이유는 「원목」 하나 때문이다. 단일 아이템으로 하면 `oak_log` 처럼
     * 수종이 하나로 고정되는데, 모드가 잔뜩 깔린 월드에서 참나무만 받는 건 그냥 짜증이다.
     * 대신 <b>맞춰보는 곳을 {@link #matches} 하나로 모아서</b> 태그가 늘어나도 호출부가 안 늘게 했다.
     */
    public record Req(ResourceLocation id, int count, boolean isTag) {

        public static Req item(String path, int n) {
            return new Req(ResourceLocation.withDefaultNamespace(path), n, false);
        }
        public static Req tagged(String path, int n) {
            return new Req(ResourceLocation.withDefaultNamespace(path), n, true);
        }
        public static Req essence(int n) {
            return new Req(ESSENCE, n, false);
        }

        public TagKey<Item> tagKey() {
            return TagKey.create(Registries.ITEM, id);
        }

        public Item itemOrNull() {
            return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : null;
        }

        /** 보관함에 넣을 수 있는가 — 서버·클라 양쪽이 <b>이 함수 하나</b>로 판정한다. */
        public boolean matches(ItemStack st) {
            if (st.isEmpty()) return false;
            if (isTag) return st.is(tagKey());
            Item i = itemOrNull();
            return i != null && st.is(i);
        }

        /** 화면에 그릴 대표 아이템. 태그면 첫 원소, 없으면 배리어(원인 추적용). */
        public Item displayItem() {
            if (!isTag) {
                Item i = itemOrNull();
                return i == null ? Items.BARRIER : i;
            }
            var tag = BuiltInRegistries.ITEM.getTag(tagKey());
            if (tag.isPresent()) {
                for (var holder : tag.get()) return holder.value();
            }
            return Items.BARRIER;
        }

        public ItemStack icon() { return new ItemStack(displayItem()); }

        public boolean isEssence() { return !isTag && id.equals(ESSENCE); }
    }

    public record Level(int ducat, List<Req> reqs, String nameKey, String fxKey, String flag) {
        /** 기여도 점수용 — 요구 수량의 총합. */
        public int totalCount() {
            int n = 0;
            for (Req r : reqs) n += r.count();
            return n;
        }
    }

    public record Track(String key, String icon, String nameKey, String blurbKey, List<Level> levels) {
        public int max() { return levels.size(); }
        public Level next(int currentLevel) {
            return currentLevel >= 0 && currentLevel < levels.size() ? levels.get(currentLevel) : null;
        }
    }

    // 트랙 키는 KubeJS 시절과 동일하게 유지한다 — 기존 명령·문서·저장 데이터가 이 문자열을 쓴다.
    //
    // ── 수량 기준 (2026-08-08 재조정) ──
    // 「4명이 한 세션 같이 모으면 한 레벨」이 목표다. 이전 값은 4인 기준 몇 분이면 끝나서
    // 사실상 Ducat 만이 유일한 게이트였다.
    //
    // 조약돌은 «양»으로 게이트가 안 된다(효율 곡괭이면 1인당 분당 수백 개). 그래서 방벽 2레벨부터
    // **석재·심층암 벽돌**로 간다 — 제련(연료·화로 시간)이 진짜 병목이고 심층암은 깊이도 요구한다.
    public static final List<Track> ALL = List.of(
        // ▦ 방벽 — 건축 자재. 넓게, 많이.
        new Track("ramparts", "▦", "lstown.track.ramparts", "lstown.track.ramparts.blurb", List.of(
            new Level(200, List.of(
                Req.item("cobblestone", 512), Req.tagged("logs", 128)),
                "lstown.ramparts.1", "lstown.ramparts.1.fx", null),
            new Level(450, List.of(
                Req.item("stone_bricks", 512), Req.tagged("logs", 256)),
                "lstown.ramparts.2", "lstown.ramparts.2.fx", null),
            new Level(850, List.of(
                Req.essence(2), Req.item("deepslate_bricks", 256), Req.tagged("logs", 384)),
                "lstown.ramparts.3", "lstown.ramparts.3.fx", null),
            new Level(1850, List.of(
                Req.essence(4), Req.item("deepslate_bricks", 384), Req.item("obsidian", 32)),
                "lstown.ramparts.4", "lstown.ramparts.4.fx", null))),

        // ⚙ 공방 — 금속. 깊게 파고 제련한다.
        // 2레벨 `forge` 플래그가 **별의 제단(축복 UI)** 을 연다 — docs/BLESSING.md 7절.
        new Track("workshop", "⚙", "lstown.track.workshop", "lstown.track.workshop.blurb", List.of(
            new Level(150, List.of(
                Req.item("iron_ingot", 64)),
                "lstown.workshop.1", "lstown.workshop.1.fx", null),
            new Level(450, List.of(
                Req.item("iron_block", 24)),
                "lstown.workshop.2", "lstown.workshop.2.fx", "forge"),
            new Level(900, List.of(
                Req.item("iron_block", 24), Req.item("gold_ingot", 96)),
                "lstown.workshop.3", "lstown.workshop.3.fx", null),
            new Level(1750, List.of(
                Req.item("iron_block", 32), Req.item("gold_block", 12), Req.item("diamond", 48)),
                "lstown.workshop.4", "lstown.workshop.4.fx", "cradle"))),

        // ✧ 성소 — 희귀·마법 재료.
        new Track("sanctum", "✧", "lstown.track.sanctum", "lstown.track.sanctum.blurb", List.of(
            new Level(200, List.of(
                Req.item("ender_pearl", 32)),
                "lstown.sanctum.1", "lstown.sanctum.1.fx", null),
            new Level(550, List.of(
                Req.item("lapis_block", 24), Req.item("amethyst_shard", 64)),
                "lstown.sanctum.2", "lstown.sanctum.2.fx", null),
            new Level(1000, List.of(
                Req.essence(2), Req.item("diamond", 32)),
                "lstown.sanctum.3", "lstown.sanctum.3.fx", null),
            new Level(2000, List.of(
                Req.essence(4), Req.item("diamond_block", 6), Req.item("netherite_ingot", 2)),
                "lstown.sanctum.4", "lstown.sanctum.4.fx", "blessing"))),

        // ⌂ 구역 — 거래품. 에메랄드는 균열·상자 보상에서 빼놨으므로 **주민 거래로만** 모인다.
        // 1레벨이 「상인 광장」이라 그게 오히려 결에 맞는다.
        new Track("districts", "⌂", "lstown.track.districts", "lstown.track.districts.blurb", List.of(
            new Level(250, List.of(
                Req.item("emerald", 32)),
                "lstown.districts.1", "lstown.districts.1.fx", "market"),
            new Level(550, List.of(
                Req.item("book", 64), Req.item("paper", 256)),
                "lstown.districts.2", "lstown.districts.2.fx", "archive"),
            new Level(1000, List.of(
                Req.item("emerald_block", 8)),
                "lstown.districts.3", "lstown.districts.3.fx", "village"),
            new Level(2350, List.of(
                Req.essence(5), Req.item("emerald_block", 12), Req.item("gold_block", 8)),
                "lstown.districts.4", "lstown.districts.4.fx", "lighthouse")))
    );

    public static Track byKey(String key) {
        for (Track t : ALL) if (t.key().equals(key)) return t;
        return null;
    }

    // 제출 슬롯 개수 — 2줄 × 9칸.
    // 한 레벨의 최대 요구가 «석재 512 + 원목 256» = 8+4 = 12스택이라 한 줄(9칸)로는 모자란다.
    // 12를 넘는 여유를 둬서 나중에 수량을 올려도 화면을 다시 안 짜도 되게 했다.
    public static final int DEPOSIT_SLOTS = 18;
    public static final int DEPOSIT_COLS = 9;
}
