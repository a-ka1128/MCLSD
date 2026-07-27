package com.laststardust.relics;

import com.laststardust.relics.item.BulwarkBlade;
import com.laststardust.relics.item.PanaceaStaff;
import com.laststardust.relics.item.RiftAxe;
import com.laststardust.relics.item.SolarMusket;
import com.laststardust.relics.item.GaeBolg;
import com.laststardust.relics.item.SoulDagger;
import com.laststardust.relics.item.StargazerStaff;
import com.laststardust.relics.item.StarBow;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// Last Stardust — 별의 유물 커스텀 모드. 진짜 하이브리드 무기(활/방패/도끼/지팡이).
@Mod(LSRelics.MODID)
public class LSRelics {
    public static final String MODID = "lsrelics";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 근접 무기 속성(공격력·공속). base 공격력 1 + dmg, base 공속 4 + spd.
    public static ItemAttributeModifiers weapon(double dmg, double spd) {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(ResourceLocation.withDefaultNamespace("base_attack_damage"), dmg, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,
                new AttributeModifier(ResourceLocation.withDefaultNamespace("base_attack_speed"), spd, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .build();
    }

    // 근접 무기 속성 + 방어 패시브(방벽의 수호자)
    // ── 2026-07-27: 탱커를 더 단단하게 ──
    // 넉백 저항만으로는 "탱커"라는 느낌이 안 났다. DPS(실측 43.3 / 목표 44)는 이미 맞아 있으니
    // 딜을 건드리지 않고 생존만 올린다 — 방어구와 달리 갑옷 관통에도 방어 강도가 같이 붙는다.
    //   방어력 +6      = 갑옷 1.5벌 분량. 잡몹 타격을 눈에 띄게 깎는다
    //   방어 강도 +4   = 공성 보스처럼 한 방이 큰 피해에서 방어력이 무력해지는 걸 막는다
    // 둘 다 주손 한정이라 무기를 바꾸면 즉시 사라진다(탱킹하려면 들고 있어야 한다).
    public static ItemAttributeModifiers guardianAttrs(double dmg, double spd) {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(ResourceLocation.withDefaultNamespace("base_attack_damage"), dmg, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ATTACK_SPEED,
                new AttributeModifier(ResourceLocation.withDefaultNamespace("base_attack_speed"), spd, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.KNOCKBACK_RESISTANCE,
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath(MODID, "guardian_kb"), 1.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ARMOR,
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath(MODID, "guardian_armor"), 6.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .add(Attributes.ARMOR_TOUGHNESS,
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath(MODID, "guardian_tough"), 4.0, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND)
            .build();
    }

    // 활 패시브(바람의 발걸음): 이동속도 +20%
    public static ItemAttributeModifiers hunterAttrs() {
        return ItemAttributeModifiers.builder()
            .add(Attributes.MOVEMENT_SPEED,
                new AttributeModifier(ResourceLocation.fromNamespaceAndPath(MODID, "hunter_speed"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                EquipmentSlotGroup.MAINHAND)
            .build();
    }

    // ── 유물 4종 ──
    // 겨우살이: 진짜 활 (화살 발사). 방벽: 방패(막기)+무기(공격). 도끼: 진짜 도끼(채굴+공격). 지팡이: 마법 무기.
    public static final DeferredItem<StarBow> HUNTER = ITEMS.register("hunter",
        () -> new StarBow(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(1500).attributes(hunterAttrs())));

    // ── 평타 DPS 예산 ──
    // 상용 RPG의 밸런스 관행을 그대로 계수화했다. 기준 = 원거리 순수 딜러 12.0.
    //   근접 프리미엄 ×1.10  (원거리는 딜 유지율·안전성이 높아 "원거리 세금"을 문다)
    //   유틸 하이브리드 ×0.95 (CC·버프를 가진 만큼 딜을 뺀다)
    //   탱커 ×0.78 / 힐러 ×0.72
    // ※ 아래 개별 주석의 수치는 **초기 설계 예산이 아니라 실측 기반 목표**다(2026-07-27 갱신).
    //   설계 예산으로 잡았던 10.3/13.2/12.5 는 두 번의 실측 리밸런스로 폐기됐다.
    //   현재 목표: 원거리 3종 54 · 게볼그 52 · 타이탄 50 · 이지스 44 · 스틱스 숙련도 밴드.
    //
    // 유물은 첫 공세를 맨몸으로 버텨야 얻는다(ls_relic.js) — 받는 무기가 아니라 쟁취한 무기라
    // 평타를 다이아~네더라이트 검 구간에 둔다.
    // 공격력 7.0813 -> 6.09 (x0.86, 2026-07-27) × 공속 1.6. 방어력/방어강도는 guardianAttrs 참고.
    // 스킬별 계측에서 평타 87% / 스킬 13% 였다. 탱커라 총량(51.4)은 그대로 두고,
    // 평타를 살짝 내린 만큼을 스킬로 옮긴다 (스킬 x1.88 -> 25%).
    public static final DeferredItem<BulwarkBlade> GUARDIAN = ITEMS.register("guardian",
        () -> new BulwarkBlade(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).attributes(guardianAttrs(5.09, -2.4))));

    // 순수 근접 딜러 — 채굴 겸용을 뺀 대신 공격력이 가장 높다.
    // 공격력 9.0514 -> 7.965 (x0.88, 2026-07-27) × 공속 1.0. 바닐라 네더라이트 도끼와 같은 속도다.
    // 스킬별 계측에서 평타 83% 로 상한(75%)을 넘었다. 평타를 내리고 스킬 셋을 x1.39 한다.
    public static final DeferredItem<RiftAxe> PIONEER = ITEMS.register("pioneer",
        () -> new RiftAxe(Tiers.NETHERITE, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            // 공속 -2.6(1.4회/초) → -3.0(1.0회/초). 바닐라 네더라이트 도끼와 같은 속도다.
            // 도끼가 검보다 빠를 이유가 없는데 1.4회/초였고, 실측 68.8 DPS 의 상당 부분이
            // 여기서 나왔다. 한 방이 무거운 무기라는 정체성에도 느린 쪽이 맞다.
            .attributes(weapon(6.965, -3.0)).durability(2000)));

    public static final DeferredItem<StargazerStaff> SAGE = ITEMS.register("sage",
        () -> new StargazerStaff(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // ── 신규 4종 ──
    // 아직 스킬이 없다. 모델·텍스처가 인겜에서 어떻게 보이는지 먼저 확인하기 위한 등록이며,
    // 스킬 설계가 끝나면 각자 전용 아이템 클래스(RelicActions 구현)로 교체한다.
    // 원거리 2종(솔라리·파나케이아)은 좌클릭 투사체가 평타라 근접 공격력을 주지 않는다.
    // 목표 DPS는 솔라리 12.0(0.8발/초 × 15.0), 파나케이아 8.6(2발/초 × 4.3).
    // 솔라리스 — 마총. 좌클릭 = 태양탄(6발 탄창 + 1.5초 재장전).
    // 실측 6인 60.9 (목표 54) — 초과 상태지만 구조 재설계 대기 중이라 수치를 안 건드린다(SolarMusket 주석).
    public static final DeferredItem<SolarMusket> GUNNER = ITEMS.register("gunner",
        () -> new SolarMusket(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(1500)));

    // 파나케이아 — 힐 지팡이. 좌클릭이 조준 대상에 따라 회복/공격으로 자동 전환된다.
    public static final DeferredItem<PanaceaStaff> HEALER = ITEMS.register("healer",
        () -> new PanaceaStaff(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // 스틱스 — 그림자 쌍단검(에레보스의 가호). 주손+보조손 한 쌍으로 든다.
    // 쌍수(Better Combat)로 두 자루가 번갈아 베므로, 한 자루의 공격력을 낮게 잡아
    // 각 자루 공격력 3.402 -> 2.858 (x0.84, 2026-07-27). 공속 2.4.
    // 백어택 ×1.20 과 바닐라 크리 ×1.5 가 평타에만 곱해진다.
    //
    // 스킬별 계측에서 백어택+크리 최대가 76.2 로 나왔다(평타 3,406 / 스킬 1,168).
    // 바닥(백어택만) 50 · 천장 70 으로 조여달라는 요청 — 평타 기본값만 내리면 둘 다 들어온다.
    // 크리 배수를 손댈 필요가 없다: 바닥이 50 이면 바닐라 크리를 다 받아도 천장이 65 다.
    // 스킬은 그대로 둔다 — 평타 비중이 74% -> 61~70% 로 내려가 상한(80%) 안에 남는다.
    // ※ 실제 쌍수 DPS는 콤보 재생 속도에 달려 있어 인겜 실측 후 미세 조정이 필요하다.
    public static final DeferredItem<SoulDagger> ASSASSIN = ITEMS.register("assassin",
        () -> new SoulDagger(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            .attributes(weapon(1.858, -1.6))));

    // 게볼그 — 창(쿠훌린의 가호). 근접 하이브리드, 긴 사거리.
    // 공격력 7.4167 × 공속 1.5 — 실측 6인 52.7 (목표 52).
    // 사거리 +1.5는 Better Combat weapon_attributes(range_bonus).
    // 귀환석 — 공방 Lv4「귀환의 요람」 보상. 유물이 아니라 마을 발전 산물이다.
    public static final DeferredItem<com.laststardust.relics.item.HearthStone> HEARTHSTONE =
        ITEMS.register("hearthstone",
            () -> new com.laststardust.relics.item.HearthStone(
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<GaeBolg> LANCER = ITEMS.register("lancer",
        () -> new GaeBolg(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            .attributes(weapon(6.4167, -2.5))));

    // 크리에이티브 탭 (테스트/EMI 노출)
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("relics",
        () -> CreativeModeTab.builder()
            .title(Component.literal("Last Stardust 유물"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> GUARDIAN.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(GUARDIAN.get());
                output.accept(HUNTER.get());
                output.accept(SAGE.get());
                output.accept(PIONEER.get());
                output.accept(GUNNER.get());
                output.accept(HEALER.get());
                output.accept(ASSASSIN.get());
                output.accept(LANCER.get());
                output.accept(HEARTHSTONE.get());
            }).build());

    public LSRelics(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
        com.laststardust.relics.town.TownMenu.MENUS.register(modEventBus);
    }
}
