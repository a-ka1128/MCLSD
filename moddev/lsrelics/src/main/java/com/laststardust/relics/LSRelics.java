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

    // 근접 무기 속성 + 넉백 저항(방벽의 수호자 패시브)
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
    // 딜러 6종 격차는 11.4~13.2 (15.8%) — 중간값 ±8%로 "밸런스 잡힌" 구간이다.
    //
    // 유물은 첫 공세를 맨몸으로 버텨야 얻는다(ls_relic.js) — 받는 무기가 아니라 쟁취한 무기라
    // 평타를 다이아~네더라이트 검 구간에 둔다.
    // 공격력 6.4375 × 공속 1.6 = DPS 10.3 (근접 탱커)
    public static final DeferredItem<BulwarkBlade> GUARDIAN = ITEMS.register("guardian",
        () -> new BulwarkBlade(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).attributes(guardianAttrs(6.0813, -2.4))));

    // 순수 근접 딜러 — 채굴 겸용을 뺀 대신 공격력이 가장 높다.
    // 공격력 9.4286 × 공속 1.4 = DPS 13.2 (묵직하게 느리고 세게)
    public static final DeferredItem<RiftAxe> PIONEER = ITEMS.register("pioneer",
        () -> new RiftAxe(Tiers.NETHERITE, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            // 공속 -2.6(1.4회/초) → -3.0(1.0회/초). 바닐라 네더라이트 도끼와 같은 속도다.
            // 도끼가 검보다 빠를 이유가 없는데 1.4회/초였고, 실측 68.8 DPS 의 상당 부분이
            // 여기서 나왔다. 한 방이 무거운 무기라는 정체성에도 느린 쪽이 맞다.
            .attributes(weapon(8.0514, -3.0)).durability(2000)));

    public static final DeferredItem<StargazerStaff> SAGE = ITEMS.register("sage",
        () -> new StargazerStaff(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // ── 신규 4종 ──
    // 아직 스킬이 없다. 모델·텍스처가 인겜에서 어떻게 보이는지 먼저 확인하기 위한 등록이며,
    // 스킬 설계가 끝나면 각자 전용 아이템 클래스(RelicActions 구현)로 교체한다.
    // 원거리 2종(솔라리·파나케이아)은 좌클릭 투사체가 평타라 근접 공격력을 주지 않는다.
    // 목표 DPS는 솔라리 12.0(0.8발/초 × 15.0), 파나케이아 8.6(2발/초 × 4.3).
    // 솔라리 — 마총. 좌클릭 = 태양탄(6발 탄창 + 1.5초 재장전), 실효 DPS 12.0.
    public static final DeferredItem<SolarMusket> GUNNER = ITEMS.register("gunner",
        () -> new SolarMusket(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(1500)));

    // 파나케이아 — 힐 지팡이. 좌클릭이 조준 대상에 따라 회복/공격으로 자동 전환된다.
    public static final DeferredItem<PanaceaStaff> HEALER = ITEMS.register("healer",
        () -> new PanaceaStaff(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // 스틱스 — 그림자 쌍단검(에레보스의 가호). 주손+보조손 한 쌍으로 든다.
    // 쌍수(Better Combat)로 두 자루가 번갈아 베므로, 한 자루의 공격력을 낮게 잡아
    // 두 자루 합이 근접 순수 딜러 예산(DPS 13.2)에 오도록 한다. 각 자루 공격력 2.75 · 공속 2.4.
    // ※ 실제 쌍수 DPS는 콤보 재생 속도에 달려 있어 인겜 실측 후 미세 조정이 필요하다.
    public static final DeferredItem<SoulDagger> ASSASSIN = ITEMS.register("assassin",
        () -> new SoulDagger(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            .attributes(weapon(2.402, -1.6))));

    // 게볼그 — 창(쿠훌린의 가호). 근접 하이브리드, 긴 사거리.
    // 공격력 8.3333 × 공속 1.5 = DPS 12.5. 사거리 +1.5는 Better Combat weapon_attributes(range_bonus).
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
