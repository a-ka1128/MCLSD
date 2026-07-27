package com.laststardust.relics.data;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

// 마을 발전 트랙 정의 — 4트랙 × 4레벨. (KubeJS ls_town.js 의 TRACKS 를 옮겨온 것)
//
// 여기가 밸런스 다이얼이다. 수치를 바꾸면 모드를 다시 빌드해야 하지만, 그 대신
// 오타는 컴파일에서 걸리고 아이템 ID 도 실존 여부를 부팅 때 확인할 수 있다.
public final class TownCatalog {
    private TownCatalog() {}

    // 균열 정수 — 보스를 잡아야 나오는 전용 재화. 제작·수집과 겹치지 않아 진행도에 비례해 쌓인다.
    public static final ResourceLocation ESSENCE = ResourceLocation.parse("kubejs:rift_essence");

    public record Level(int ducat, ResourceLocation item, int count, String nameKey, String fxKey, String flag) {
        public Item itemOrNull() {
            return BuiltInRegistries.ITEM.containsKey(item) ? BuiltInRegistries.ITEM.get(item) : null;
        }
        public ItemStack icon() {
            Item i = itemOrNull();
            return i == null ? new ItemStack(Items.BARRIER) : new ItemStack(i);
        }
        public boolean isEssence() {
            return item.equals(ESSENCE);
        }
    }

    public record Track(String key, String icon, String nameKey, String blurbKey, List<Level> levels) {
        public int max() { return levels.size(); }
        public Level next(int currentLevel) {
            return currentLevel >= 0 && currentLevel < levels.size() ? levels.get(currentLevel) : null;
        }
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    // 트랙 키는 KubeJS 시절과 동일하게 유지한다 — 기존 명령·문서·저장 데이터가 이 문자열을 쓴다.
    public static final List<Track> ALL = List.of(
        new Track("ramparts", "▦", "lstown.track.ramparts", "lstown.track.ramparts.blurb", List.of(
            new Level(200,  mc("iron_ingot"),    24, "lstown.ramparts.1", "lstown.ramparts.1.fx", null),
            new Level(450,  mc("iron_block"),    16, "lstown.ramparts.2", "lstown.ramparts.2.fx", null),
            new Level(850,  ESSENCE,              2, "lstown.ramparts.3", "lstown.ramparts.3.fx", null),
            new Level(1850, ESSENCE,              4, "lstown.ramparts.4", "lstown.ramparts.4.fx", null))),

        new Track("workshop", "⚙", "lstown.track.workshop", "lstown.track.workshop.blurb", List.of(
            new Level(150,  mc("oak_planks"),    64, "lstown.workshop.1", "lstown.workshop.1.fx", null),
            new Level(450,  mc("iron_ingot"),    32, "lstown.workshop.2", "lstown.workshop.2.fx", null),
            new Level(900,  mc("gold_ingot"),    24, "lstown.workshop.3", "lstown.workshop.3.fx", null),
            new Level(1750, mc("diamond"),       24, "lstown.workshop.4", "lstown.workshop.4.fx", "cradle"))),

        new Track("sanctum", "✧", "lstown.track.sanctum", "lstown.track.sanctum.blurb", List.of(
            new Level(200,  mc("ender_pearl"),   16, "lstown.sanctum.1", "lstown.sanctum.1.fx", null),
            new Level(550,  mc("lapis_block"),   12, "lstown.sanctum.2", "lstown.sanctum.2.fx", null),
            new Level(1000, ESSENCE,              2, "lstown.sanctum.3", "lstown.sanctum.3.fx", null),
            new Level(2000, ESSENCE,              4, "lstown.sanctum.4", "lstown.sanctum.4.fx", "blessing"))),

        new Track("districts", "⌂", "lstown.track.districts", "lstown.track.districts.blurb", List.of(
            new Level(250,  mc("emerald"),       16, "lstown.districts.1", "lstown.districts.1.fx", "market"),
            new Level(550,  mc("book"),          32, "lstown.districts.2", "lstown.districts.2.fx", "archive"),
            new Level(1000, mc("emerald_block"),  4, "lstown.districts.3", "lstown.districts.3.fx", "village"),
            new Level(2350, ESSENCE,              5, "lstown.districts.4", "lstown.districts.4.fx", "lighthouse")))
    );

    public static Track byKey(String key) {
        for (Track t : ALL) if (t.key().equals(key)) return t;
        return null;
    }


    // 제출 슬롯 개수. 한 레벨에 필요한 최대 수량이 64(공방1 참나무 판자)라 한 칸으로는 모자란다.
    public static final int DEPOSIT_SLOTS = 3;
}
