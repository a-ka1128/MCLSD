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
    // 넉백 저항만으로는 "탱커"라는 느낌이 안 났다. 딜은 건드리지 않고 생존만 올린다
    // — 방어구와 달리 갑옷 관통에도 방어 강도가 같이 붙는다.
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
    // ※ 위 계수는 초기 설계용이고, 지금 쓰는 값은 전부 실측 기반이다(2026-07-30 갱신).
    //
    // ── 측정 기준 (이걸 안 맞추면 값이 통째로 어긋난다) ──
    //   60초 · 5성 · 표적 방어도 0 · 더미 1기 · - 성소 축복 안 -
    //
    //   축복(성소 Lv4「별빛 축복」, 성역 64칸 안)은 근접에 Strength I = 공격력 +3.0,
    //   원거리에 projectile_damage +25% 를 준다. 안팎이 섞이면 같은 유물이 x1.25 넘게
    //   벌어진다 — 실제로 시리우스에서 그렇게 어긋나 원인을 찾는 데 오래 걸렸다.
    //   공성전이 성역에서 벌어지므로 축복 안이 실전 조건이고, 그래서 이쪽을 기준으로 잡았다.
    //   ※ DummyManager 가 측정 결과에 [힘·투사체+25%] 를 찍어준다. 그게 없으면 축복 밖이다.
    //
    //   목표와 최신 실측 — - 전부 GLOBAL_POWER 도입 전(×1.0) 값이다 - :
    //     셀레스티아 56 (56.8)  · 솔라리스 56 (평타 56.7 / 스코프 58.0)  · 시리우스 56
    //     게볼그    54 (54.2)  · 타이탄  54 (53.1) · 이지스 50 (50.7)
    //     파나케이아 유지(50.5) · 스틱스  50~70 대역 (바닥 50.7 / 풀딜 64.5)
    //   시리우스만 괄호가 없다 — x0.874 를 건 뒤 아직 안 쟀다(직전 실측 64.1).
    //   스틱스만 대역으로 두는 이유는 백어택·크리가 평타에만 곱해져 숙련도 폭이 크기 때문이다.
    //
    // ── 2026-07-30: 전역 위력 ×1.8 (RelicSkills.GLOBAL_POWER) ──
    //   위 목표치는 - 상대 밸런스 - 로 계속 유효하다. 8종이 전부 같은 배수를 받으므로 서로의
    //   비율과 평타/스킬 비중은 하나도 안 움직인다. 절대 수준만 올라간다:
    //     5성 총 DPS 50~56  →  90~101   (다음 실측 때 이 대역과 맞춰본다)
    //   바꾼 이유는 5성이 아니라 - 1성 - 이다. 각성이 등차라 1성은 5성의 1/3인데, 그 1성
    //   근접 평타(9.1/8.5/8.3/5.3)가 맨 네더라이트 검 12.8 보다 약했다. 자세한 근거는
    //   RelicSkills.GLOBAL_POWER 주석.
    //   ※ 개별 유물을 조정할 땐 이 배율을 건드리지 말고 그 유물의 값을 고친다.
    //     GLOBAL_POWER 는 8종을 통째로 올리고 내리는 손잡이다.
    //
    // 유물은 첫 공세를 맨몸으로 버텨야 얻는다(ls_relic.js) — 받는 무기가 아니라 쟁취한 무기라
    // 평타를 다이아~네더라이트 검 구간에 둔다.
    // 공격력 7.0813 -> 6.09 -> 5.524 -> 5.673 × 공속 1.6. 방어력/방어강도는 guardianAttrs 참고.
    //
    // 탱커라 총량(50)은 8종 중 가장 낮게 두되, 스킬 비중은 다른 유물과 같은 30% 로 맞췄다.
    // 스킬별 계측 전에는 평타 87% / 스킬 13% 여서 스킬을 누를 이유가 없었다.
    //
    // 8유물 중 판별 편차가 가장 작다(48.8 / 48.7). 그래서 이 무기는 한 판만 재도 믿을 수 있고,
    // 목표와의 2~3% 차이도 노이즈가 아니라 실제 갭으로 봐도 된다.
    // 무기 값과 실측이 그대로 비례한다 — x0.860 을 걸면 실측이 x0.865 로 따라온다.
    // (스틱스에서 보였던 '스킬트리 고정값 희석'은 이 무기엔 사실상 없다.)
    public static final DeferredItem<BulwarkBlade> GUARDIAN = ITEMS.register("guardian",
        () -> new BulwarkBlade(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).attributes(guardianAttrs(4.673, -2.4))));

    // 순수 근접 딜러 — 채굴 겸용을 뺀 대신 공격력이 가장 높다.
    // 공격력 9.0514 -> 7.965 -> 8.292 × 공속 1.0. 바닐라 네더라이트 도끼와 같은 속도다.
    //
    // 평타 83% 로 상한(75%)을 넘어서 평타를 내리고 스킬 셋을 x1.39 했다 -> 74%.
    // 그다음 총량을 x1.041 했는데, 이건 두 판 평균(53.3 / 50.4 -> 51.9) 기준이다 —
    // 근접이라 타격 타이밍에 따라 4% 정도 흔들려서 한 판에 맞추면 다음 판에 어긋난다.
    public static final DeferredItem<RiftAxe> PIONEER = ITEMS.register("pioneer",
        () -> new RiftAxe(Tiers.NETHERITE, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            // 공속 -2.6(1.4회/초) → -3.0(1.0회/초). 바닐라 네더라이트 도끼와 같은 속도다.
            // 도끼가 검보다 빠를 이유가 없는데 1.4회/초였고, 실측 68.8 DPS 의 상당 부분이
            // 여기서 나왔다. 한 방이 무거운 무기라는 정체성에도 느린 쪽이 맞다.
            .attributes(weapon(7.292, -3.0)).durability(2000)));

    public static final DeferredItem<StargazerStaff> SAGE = ITEMS.register("sage",
        () -> new StargazerStaff(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // ── 후발 4종 (솔라리스·파나케이아·스틱스·게볼그) ──
    // 넷 다 전용 아이템 클래스와 스킬 4종을 갖췄다.
    // 원거리 2종(솔라리스·파나케이아)은 좌클릭 투사체가 평타라 근접 공격력을 주지 않는다 —
    // 그쪽 수치는 각 아이템 클래스 안에 있다(SolarMusket.BULLET_DMG, PanaceaStaff.BOLT_DMG).
    //
    // 솔라리스 — 마총. 좌클릭 = 태양탄(6발 탄창 + 2초 재장전), 우클릭 = 스코프.
    // 목표 56 / 실측 56.7. 수치와 그 근거는 전부 SolarMusket 주석에 있다.
    public static final DeferredItem<SolarMusket> GUNNER = ITEMS.register("gunner",
        () -> new SolarMusket(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).durability(1500)));

    // 파나케이아 — 힐 지팡이. 좌클릭이 조준 대상에 따라 회복/공격으로 자동 전환된다.
    public static final DeferredItem<PanaceaStaff> HEALER = ITEMS.register("healer",
        () -> new PanaceaStaff(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    // 스틱스 — 그림자 쌍단검(에레보스의 가호). 주손+보조손 한 쌍으로 든다.
    // 쌍수(Better Combat)로 두 자루가 번갈아 베므로, 한 자루의 공격력을 낮게 잡아
    // 각 자루 공격력 3.402 -> 2.858 -> 2.225 (2026-07-27). 공속 2.4.
    // 백어택 ×1.20 과 바닐라 크리 ×1.5 가 평타에만 곱해진다.
    //
    // ── 무기 속성만 보고 배수를 잡으면 안 된다 ──
    // 처음에 x0.84 를 걸었는데 실제로는 x0.876 만 걸렸다. 플레이어의 실제 공격력에는
    // 스킬트리에서 오는 - 고정 +3.0 - 이 얹혀 있어서, 무기 쪽을 깎아도 그만큼은 안 줄어든다.
    //   무기 2.858 × 3(5성) = 8.574 · 스킬트리 +3.0 -> 실측 11.574 (/attribute 로 확인)
    // 목표 배수를 실제 공격력 기준으로 역산해야 한다.
    //
    // ── 두 번의 실측으로 구조가 나왔다 (60초, 5성) ──
    //   풀딜            평타 3,074 · 총 72.9
    //   백어택X 노크리   평타 2,249 · 총 56.1
    // 비율 1.367 = 백어택 ×1.2 × (크리 28% 적중). 크리는 낙하 중에만 터져서 100% 가 못 된다.
    //
    // 바닥 50 · 천장 70 이 요구 조건이었다. 평타를 x0.836 해서 맞췄고, 조정 후 실측은
    //   바닥(백어택X 노크리) 50.7 · 백어택만 57.0 · 풀딜 64.2 / 65.1
    // 로 대역 안에 들어왔다. 크리 배수는 건드리지 않았다 — 바닥을 목표에 맞추면
    // 바닐라 크리(x1.5)를 다 받아도 천장이 알아서 70 아래로 떨어진다.
    // 스킬은 그대로 뒀다(평타 비중 66~68%, 상한 80% 안).
    //
    // ── 2026-07-31: 1.225 → 1.409 (×1.15) ──
    // 5성 DPS 가 8유물 중 꼴찌였다(81.2 · 평균 93.6 대비 −13%). 그런데 진단이 「약하다」가
    // 아니었다 — **1성에서는 1위(35.5)고 5성에서 꼴찌다.** 성급 곡선이 혼자 완만하다.
    // 아이템 공격력이 작고(1.225) 공속이 빨라(2.4), 성급을 안 타는 덧셈 항(성역 축복 +3.0)이
    // 저성급에서 비중을 크게 먹고 고성급에서 희석되기 때문이다.
    //
    // 그래서 **축복이 아니라 기본치**를 올렸다. 축복(+3.0 → +4.5)으로 맞추면 5성은 87.5 로
    // 오르지만 1성이 35.5 → **41.7(평균 +24%)** 로 튄다 — 덧셈이라 저성급에 몇 배로 얹힌다.
    // 기본치는 곱셈이라 성급을 타고, 1성은 36.8 로만 움직인다.
    //   (`docs/DECISIONS.md` 1절에 네 안의 성급별 수치를 전부 남겼다.
    //    그 절이 한때 「C안은 1성이 거의 그대로」라고 적고 있었는데 **그게 틀렸다.**)
    //
    // 목표는 「더미에선 평균에 못 미치고, 백어택을 쓰면 평균을 넘는다」였다 —
    // 암살자는 등 뒤로 돌아야 나오는 직업이고, 그 난이도가 보상받아야 한다.
    //   5성 더미 81.2 → 85.4 (평균 −8.8%) · 백어택 시 91.8 → 96.5 (평균 +3.1%)
    //
    // **대역은 요구 조건 안에 그대로 있다.** 위 실측(바닥 50.7 · 백어택 57.0 · 풀딜 64.65)에
    // 배수 1.052 를 곱하면 바닥 53.3 · 백어택 59.9 · 풀딜 68.0 —
    // 원래 요구였던 「바닥 50 · 천장 70」을 여전히 만족한다. 크리 배수는 이번에도 안 건드렸다.
    // ⚠️ 위 수치는 보정상수 k 하나에 기댄 모델값이다. `/dummy` 로 실측해 덮어쓸 것.
    public static final DeferredItem<SoulDagger> ASSASSIN = ITEMS.register("assassin",
        () -> new SoulDagger(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            .attributes(weapon(1.409, -1.6))));

    // 게볼그 — 창(쿠훌린의 가호). 근접 하이브리드, 긴 사거리.
    // 공격력 7.4167 -> 5.666 (x0.764, 2026-07-27) × 공속 1.5.
    // 실측 63.5 로 8유물 중 혼자 높았다. 평타 비중(66%)은 상한(75%) 안이라
    // 궁극기를 뺀 전부에 같은 배수를 걸어 총량만 54 로 내린다.
    // 사거리 +1.5는 Better Combat weapon_attributes(range_bonus).
    // 귀환석 — 공방 Lv4「귀환의 요람」 보상. 유물이 아니라 마을 발전 산물이다.
    public static final DeferredItem<com.laststardust.relics.item.HearthStone> HEARTHSTONE =
        ITEMS.register("hearthstone",
            () -> new com.laststardust.relics.item.HearthStone(
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<GaeBolg> LANCER = ITEMS.register("lancer",
        () -> new GaeBolg(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
            .attributes(weapon(4.666, -2.5))));

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
