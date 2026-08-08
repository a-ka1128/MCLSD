package com.laststardust.relics.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * 구조물 상자 보정 — 별먼지 지급 · 잡템을 자원으로 치환 · 장비 추가.
 *
 * <p>── 왜 Global Loot Modifier 인가 ──
 * 이 서버에는 상자 전리품표가 <b>566개</b> 있다(WDA 141 · Nova 119 · Repurposed 146 …).
 * KubeJS 2101 에는 전리품 «수정» 이벤트가 없어서(구버전에 있던 게 1.21 에서 빠졌다) 스크립트로
 * 하려면 표를 통째로 덮어써야 하는데, 그러면 원래 내용이 전부 날아간다.
 * GLM 은 기존 표를 안 건드리고 <b>결과 목록</b>을 받아 고치는 유일한 방법이다.
 *
 * <p>── 왜 등급 판정이 JSON 이 아니라 여기 있나 ──
 * NeoForge 의 {@code neoforge:loot_table_id} 조건은 <b>정확 일치</b>만 된다(정규식이 아니다).
 * 566개를 일일이 적을 수 없고, 정규식 셋을 서로 배타적으로 짜는 것도 실수하기 쉽다
 * (하나가 두 등급에 걸리면 두 번 처리된다). 표 id 를 여기서 한 번만 읽고 키워드로 가른다.
 * 등급 «기준»은 규칙이라 Java, 등급별 «확률»은 튜닝이라 JSON 이다.
 */
public class ChestLootModifier extends LootModifier {

    /** 등급별 수치. 전부 JSON 에서 읽으므로 재빌드 없이 조절된다. */
    public record Tier(float chance, int min, int max, float upgrade, float gear) {
        public static final Codec<Tier> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("chance").forGetter(Tier::chance),
            Codec.INT.fieldOf("min").forGetter(Tier::min),
            Codec.INT.fieldOf("max").forGetter(Tier::max),
            Codec.FLOAT.fieldOf("upgrade").forGetter(Tier::upgrade),
            Codec.FLOAT.fieldOf("gear").forGetter(Tier::gear)
        ).apply(i, Tier::new));

        int rollDust(RandomSource rnd) {
            if (chance < 1.0f && rnd.nextFloat() >= chance) return 0;
            if (max <= min) return Math.max(0, min);
            return min + rnd.nextInt(max - min + 1);
        }
    }

    public static final MapCodec<ChestLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
        codecStart(inst).and(inst.group(
            Tier.CODEC.fieldOf("big").forGetter(m -> m.big),
            Tier.CODEC.fieldOf("mid").forGetter(m -> m.mid),
            Tier.CODEC.fieldOf("small").forGetter(m -> m.small)
        )).apply(inst, ChestLootModifier::new));

    // ── 등급 키워드 ──
    // 표 이름 규칙에서 뽑았다. When Dungeons Arise 가 접미사를 제일 잘 나눠 놨고
    // (`_treasure` 33 · `_normal` 28 · `_barrels` 21 · `_supply` 11) 다른 모드도 비슷하다.
    //
    // ※ `tresure` 는 오타가 아니라 **Nova Structures 의 실제 오타 테이블 4개**다.
    //   빼면 그 넷이 조용히 중형으로 떨어진다.
    private static final String[] BIG = { "treasure", "tresure", "vault", "hoard", "boss" };
    private static final String[] SMALL = {
        "barrel", "supply", "storage", "house", "grave", "kitchen", "garden", "wool"
    };

    // ── 잡템 ──
    // 「양은 많은데 쓸 데가 없는 것」. 지우지 않고 자원으로 «치환»한다 — 지우면 상자가 휑해져서
    // 「보상이 줄었다」로 느껴진다. 채움은 그대로 두고 질만 올린다.
    private static final Item[] JUNK = {
        Items.ROTTEN_FLESH, Items.STRING, Items.STICK, Items.WHEAT_SEEDS, Items.PAPER,
        Items.POISONOUS_POTATO, Items.CLAY_BALL, Items.FLINT, Items.BONE
    };

    // ── 치환 결과 ──
    // {아이템, 가중치, 최대 개수}. **에메랄드는 절대 넣지 않는다** — 도박장 칩이 에메랄드 실물이라
    // (`ls_casino.js`) 상자에서 쏟아지면 판돈이 무의미해진다. 균열 보상에서 뺀 것과 같은 이유다.
    private static final Object[][] RESOURCES = {
        { Items.IRON_INGOT, 20, 6 }, { Items.COPPER_INGOT, 15, 8 }, { Items.COAL, 15, 8 },
        { Items.GOLD_INGOT, 10, 4 }, { Items.REDSTONE, 10, 8 }, { Items.LAPIS_LAZULI, 8, 6 },
        { Items.AMETHYST_SHARD, 8, 4 }, { Items.LEATHER, 8, 5 }, { Items.QUARTZ, 6, 6 },
        { Items.DIAMOND, 3, 2 }, { Items.NETHERITE_SCRAP, 1, 1 }
    };
    private static final int RES_TOTAL;
    static {
        int s = 0;
        for (Object[] r : RESOURCES) s += (int) r[1];
        RES_TOTAL = s;
    }

    // ── 추가 장비 ──
    // Apotheosis 가 자기 GLM 으로 일부를 어픽스 장비로 바꾼다. 다만 GLM 실행 순서는 모드 간에
    // 보장되지 않으므로 **여기서 넣은 것이 어픽스를 받는다고 가정하지 않는다** — 받으면 덤이다.
    private static final Item[] GEAR = {
        Items.IRON_SWORD, Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL,
        Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
        Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.SHIELD, Items.BOW,
        Items.CROSSBOW, Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_HELMET
    };

    private final Tier big, mid, small;

    public ChestLootModifier(LootItemCondition[] conditions, Tier big, Tier mid, Tier small) {
        super(conditions);
        this.big = big;
        this.mid = mid;
        this.small = small;
        // 실렸는지 눈으로 확인할 유일한 지점. GLM 은 JSON 이 조용히 안 읽혀도 아무 말이 없어서,
        // 이 줄이 없으면 「안 나온다」의 원인이 등록인지 판정인지 못 가른다.
        org.slf4j.LoggerFactory.getLogger("lsrelics").info(
            "[LS-LOOT] 구조물 상자 보정 로드 — 대형 먼지 {}/{}~{} 치환 {} 장비 {} · "
            + "중형 {}/{}~{} 치환 {} 장비 {} · 소형 {}/{}~{} 치환 {} 장비 {}",
            big.chance(), big.min(), big.max(), big.upgrade(), big.gear(),
            mid.chance(), mid.min(), mid.max(), mid.upgrade(), mid.gear(),
            small.chance(), small.min(), small.max(), small.upgrade(), small.gear());
    }

    private static boolean has(String path, String[] keys) {
        for (String k : keys) if (path.contains(k)) return true;
        return false;
    }

    private static boolean isJunk(Item it) {
        for (Item j : JUNK) if (j == it) return true;
        return false;
    }

    /** 가중치로 자원 하나를 고른다. 원래 스택이 클수록 조금 더 준다(뼈 47 → 철 6 정도). */
    private static ItemStack pickResource(RandomSource rnd, int origCount) {
        int r = rnd.nextInt(RES_TOTAL);
        for (Object[] e : RESOURCES) {
            r -= (int) e[1];
            if (r < 0) {
                int cap = (int) e[2];
                int n = Math.max(1, Math.min(cap, 1 + origCount / 6));
                return new ItemStack((Item) e[0], n);
            }
        }
        return new ItemStack(Items.IRON_INGOT, 1);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        ResourceLocation id = ctx.getQueriedLootTableId();
        if (id == null) return loot;

        // 상자만. 몹 드롭·블록 드롭·낚시에는 안 붙인다 — 그쪽까지 열리면
        // 「탐험해야 나온다」가 무너지고 별먼지가 자동화 가능해진다.
        String path = id.getPath();
        if (!path.startsWith("chests/")) return loot;

        Tier t = has(path, BIG) ? big : (has(path, SMALL) ? small : mid);
        RandomSource rnd = ctx.getRandom();

        // ① 잡템 → 자원 치환 (자리에서 바꾼다 — 목록 크기가 안 변해 상자 채움이 유지된다)
        if (t.upgrade() > 0) {
            for (int i = 0; i < loot.size(); i++) {
                ItemStack s = loot.get(i);
                if (isJunk(s.getItem()) && rnd.nextFloat() < t.upgrade()) {
                    loot.set(i, pickResource(rnd, s.getCount()));
                }
            }
        }

        // ② 장비 한 점
        if (t.gear() > 0 && rnd.nextFloat() < t.gear()) {
            loot.add(new ItemStack(GEAR[rnd.nextInt(GEAR.length)]));
        }

        // ③ 별먼지
        Item dust = StardustRef.get();
        if (dust != null) {
            int n = t.rollDust(rnd);
            if (n > 0) loot.add(new ItemStack(dust, n));
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
