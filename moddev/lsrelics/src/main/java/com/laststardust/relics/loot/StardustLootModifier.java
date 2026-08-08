package com.laststardust.relics.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import com.mojang.serialization.Codec;

/**
 * 별먼지를 구조물 상자에 넣는다.
 *
 * <p>── 왜 Global Loot Modifier 인가 ──
 * 이 서버에는 상자 전리품표가 <b>566개</b> 있다(WDA 141 · Nova 119 · Repurposed 146 …).
 * KubeJS 2101 에는 전리품 «수정» 이벤트가 없어서(구버전에서 빠졌다) 스크립트로 하려면
 * 표를 통째로 덮어써야 하는데, 그러면 원래 내용이 전부 날아간다. GLM 은 기존 표를
 * 안 건드리고 결과에 얹는 유일한 방법이다.
 *
 * <p>── 왜 등급 판정이 JSON 이 아니라 여기 있나 ──
 * NeoForge 의 {@code neoforge:loot_table_id} 조건은 <b>정확 일치</b>만 된다(정규식이 아니다).
 * 566개를 일일이 적을 수는 없고, 정규식 셋을 서로 배타적으로 짜는 것도 실수하기 쉽다
 * (하나가 두 등급에 걸리면 별먼지가 두 번 나온다). 그래서 표 id 를 여기서 한 번만 읽고
 * 키워드로 가른다. 등급 «기준»은 규칙이고, 등급별 «수치»는 JSON 에 뺐다 — 그쪽은 튜닝이라.
 */
public class StardustLootModifier extends LootModifier {

    /** 등급별 수치. JSON 에서 읽으므로 재빌드 없이 조절할 수 있다. */
    public record Tier(float chance, int min, int max) {
        public static final Codec<Tier> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("chance").forGetter(Tier::chance),
            Codec.INT.fieldOf("min").forGetter(Tier::min),
            Codec.INT.fieldOf("max").forGetter(Tier::max)
        ).apply(i, Tier::new));

        /** 뽑을 개수. 확률에 걸리지 않으면 0. */
        int roll(RandomSource rnd) {
            if (chance < 1.0f && rnd.nextFloat() >= chance) return 0;
            if (max <= min) return Math.max(0, min);
            return min + rnd.nextInt(max - min + 1);
        }
    }

    public static final MapCodec<StardustLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
        codecStart(inst).and(inst.group(
            Tier.CODEC.fieldOf("big").forGetter(m -> m.big),
            Tier.CODEC.fieldOf("mid").forGetter(m -> m.mid),
            Tier.CODEC.fieldOf("small").forGetter(m -> m.small)
        )).apply(inst, StardustLootModifier::new));

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

    private final Tier big, mid, small;

    public StardustLootModifier(LootItemCondition[] conditions, Tier big, Tier mid, Tier small) {
        super(conditions);
        this.big = big;
        this.mid = mid;
        this.small = small;
        // 실렸는지 눈으로 확인할 유일한 지점. GLM 은 JSON 이 조용히 안 읽혀도 아무 말이 없어서,
        // 이 줄이 없으면 「안 나온다」의 원인이 등록인지 판정인지 못 가른다.
        org.slf4j.LoggerFactory.getLogger("lsrelics")
            .info("[LS-LOOT] 별먼지 전리품 수정자 로드 — 대형 {}/{}~{} · 중형 {}/{}~{} · 소형 {}/{}~{}",
                big.chance(), big.min(), big.max(), mid.chance(), mid.min(), mid.max(),
                small.chance(), small.min(), small.max());
    }

    private static boolean has(String path, String[] keys) {
        for (String k : keys) if (path.contains(k)) return true;
        return false;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        ResourceLocation id = ctx.getQueriedLootTableId();
        if (id == null) return loot;

        // 상자만. 몹 드롭·블록 드롭·낚시에는 안 붙인다 — 그쪽까지 열리면
        // 「탐험해야 나온다」가 무너지고 별먼지가 자동화 가능해진다.
        String path = id.getPath();
        if (!path.startsWith("chests/")) return loot;

        net.minecraft.world.item.Item dust = StardustRef.get();
        if (dust == null) return loot;   // 아이템이 없으면 조용히 지나간다 (StardustRef 주석 참고)

        Tier t = has(path, BIG) ? big : (has(path, SMALL) ? small : mid);
        int n = t.roll(ctx.getRandom());
        if (n > 0) loot.add(new ItemStack(dust, n));
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
