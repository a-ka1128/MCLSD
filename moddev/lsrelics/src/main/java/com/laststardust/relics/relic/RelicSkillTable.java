package com.laststardust.relics.relic;

import java.util.List;
import java.util.Map;

import com.laststardust.relics.item.RelicSkills;

import net.minecraft.world.item.ItemStack;

/**
 * 유물마다 «어떤 스킬이 얼마나 아픈가».
 *
 * <p>── 왜 이 표가 «숫자»가 아니라 «상수 참조» 인가 ──
 * 스킬 피해는 원래 호출 자리에 그대로 박혀 있었다({@code dmg(stack, 27.6f)}).
 * 화면에 보여주려고 표를 손으로 적으면 그건 <b>사본</b>이고, 누가 27.6 을 고치는 날
 * 실제 피해만 바뀌고 화면은 옛 숫자를 계속 보여준다 — 이 저장소가 반복해서 겪은 실패다.
 * 그래서 숫자에 이름을 붙여({@code RelicSkills.D_*}) 여기서 <b>그 상수를 가리킨다.</b>
 * 값을 고치면 실제 피해와 화면이 같이 움직인다.
 *
 * <p>── 여기 없는 것 ──
 * <ul>
 *   <li><b>불굴</b>({@code unyielding}) — 피해가 «막아낸 기세»에 비례해 계산된다.
 *       고정값이 없어서 한 줄로 못 적는다. 적으면 그게 거짓말이 된다.</li>
 *   <li><b>심판의 빛·그림자 도약</b> — 원래부터 이름 있는 상수라 그대로 가리킨다.</li>
 *   <li>평타·투사체 — 무기 툴팁에 이미 있다.</li>
 * </ul>
 */
public final class RelicSkillTable {
    private RelicSkillTable() {}

    /** @param base {@code dmg(stack, base)} 에 들어가는 값. 최종 피해는 배율이 얹힌 뒤다. */
    public record Skill(String label, float base) {}

    // 유물 아이템 id → 스킬들. 순서가 화면 순서다 — 약한 것부터 센 것 순으로 둔다.
    private static final Map<String, List<Skill>> BY_ITEM = Map.ofEntries(
        Map.entry("lsrelics:guardian", List.of(
            new Skill("수호의 파동", RelicSkills.D_GUARD_PULSE),
            new Skill("이지스 돌진", RelicSkills.D_AEGIS_CHARGE))),
        Map.entry("lsrelics:sage", List.of(
            new Skill("별빛 탄", RelicSkills.D_MAGIC_BOLT),
            new Skill("소멸", RelicSkills.D_ANNIHILATE),
            new Skill("중력 붕괴", RelicSkills.D_GRAVITY),
            new Skill("초신성", RelicSkills.D_SUPERNOVA))),
        Map.entry("lsrelics:hunter", List.of(
            new Skill("유성 화살", RelicSkills.D_METEOR))),
        Map.entry("lsrelics:pioneer", List.of(
            new Skill("대지 쪼개기", RelicSkills.D_EARTH_SPLIT),
            new Skill("균열 붕괴", RelicSkills.D_RIFT_COLLAPSE))),
        Map.entry("lsrelics:gunner", List.of(
            new Skill("산탄", RelicSkills.D_BUCKSHOT),
            new Skill("일식", RelicSkills.D_ECLIPSE))),
        Map.entry("lsrelics:healer", List.of(
            new Skill("심판의 빛", RelicSkills.D_JUDGE))),
        Map.entry("lsrelics:assassin", List.of(
            new Skill("급소 가르기", RelicSkills.D_VICIOUS),
            new Skill("그림자 도약", RelicSkills.D_LEAP))),
        Map.entry("lsrelics:lancer", List.of(
            new Skill("질풍 돌진", RelicSkills.D_GUST_DASH),
            new Skill("꿰뚫기", RelicSkills.D_PIERCE),
            new Skill("투창", RelicSkills.D_JAVELIN))),
        Map.entry("lsrelics:hecate", List.of(
            new Skill("연좌(표식)", RelicSkills.D_GUILT_MARK),
            new Skill("연좌(폭발)", RelicSkills.D_GUILT_BURST),
            new Skill("재의 채찍", RelicSkills.D_ASH_WHIP))),
        Map.entry("lsrelics:harmonia", List.of(
            new Skill("음률", RelicSkills.D_CHORD),
            new Skill("고양의 선율", RelicSkills.D_ANTHEM))),
        Map.entry("lsrelics:nemesis", List.of(
            new Skill("강철 발", RelicSkills.D_STANCE_BURST),
            new Skill("참격 인계", RelicSkills.D_BLADE_RECALL),
            new Skill("일도양단", RelicSkills.D_SUNDER))),
        Map.entry("lsrelics:chiron", List.of(
            new Skill("축성", RelicSkills.D_CONSECRATE)))
    );

    /** 이 유물의 스킬들. 유물이 아니면 빈 목록. */
    public static List<Skill> of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return BY_ITEM.getOrDefault(id.toString(), List.of());
    }
}
