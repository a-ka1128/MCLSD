package com.laststardust.relics.item;

import com.laststardust.relics.HarmonyManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 바르비톤 — 저음 리라(하르모니아의 가호). **원거리 지원 딜러.**
//   · 좌클릭 = 음률 (홀드 연사 — 로즈빛 음표 투사체)
//   · R = 고양의 선율 (기본, 1성)   · V = 엮인 걸음 (이동, 2성)
//   · C = 결속의 매듭 (추가, 3성)   · X = 만상의 화음 (궁극, 4성)
//   · 패시브(1성) = 공명 — 주변 8칸 아군 이동속도 +10% · 아군 처치 시 공격력 중첩 (HarmonyManager)
//
// ── 히기에이아와 겹치지 않는다 ──
// **회복·보호막·부활을 하나도 넣지 않았다.** 히기에이아가 서포트의 «회복» 절반을 갖고
// 이쪽이 «강화» 절반을 갖는다. 둘이 같은 파티에 있어도 역할이 안 먹힌다
// (docs/CLASSES.md 「헤카테·하르모니아」 §2 설계 의도 1).
//
// ── 근접 → 원거리 지원 딜러 (2026-08-09, 유저 결정) ──
// 원래는 근접이었고 피해를 주는 스킬이 하나도 없었다. 설계 의도는 맞았지만 실제로는
// **버프를 거는 사람이 근접 사거리까지 걸어 들어가야 하는** 모양이었다.
//   → 좌클릭을 투사체로 바꾸고(`RelicSkills.chordShot`) 근접 공격력 속성을 뗐다.
//   → 스킬 넷에도 피해를 얹었다(docs/CLASSES.md 「헤카테·하르모니아」 §2-B).
// 총합 목표는 **78** — 원거리 셋(97~99)보다 20% 낮다. 딜러 대역까지 올리면 화력과 파티 버프를
// 한 명이 다 갖게 되어 안 뽑을 이유가 없는 픽이 된다.
public class HarmoniaSash extends Item implements RelicActions {

    public HarmoniaSash(Properties properties) {
        super(properties);
    }

    // ── 좌클릭 = 음률(원거리 평타) ──
    @Override
    public boolean firesOnLeftClick() {
        return true;
    }

    @Override
    public void leftAttack(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.chordShot(level, player, stack);
    }

    // ── R = 고양의 선율 (기본·1성) ──
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.anthem(level, player, stack);
    }

    // ── V = 이동기 · 엮인 걸음 ──
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.wovenStep(level, player, stack);
    }

    // ── C = 결속의 매듭 (추가·3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.bindingKnot(level, player, stack);
    }

    // ── X = 만상의 화음 (궁극·4성) ──
    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.grandChord(level, player, stack);
    }

    // ── 액션바: 매듭 안에 있는지 ──
    // 매듭은 발밑 지대라 파티클이 가려지면 안 보인다. 「지금 그 안인가」가 이 직업의
    // 자리잡기 판단 전부라, 한 글자라도 띄워야 한다.
    @Override
    public String hudStatus(ServerPlayer player, ItemStack stack) {
        return HarmonyManager.inKnot(player) ? "§d✦ 매듭" : null;
    }

    // ── 인챈트 테이블에서도 걸리게 (2026-08-18) ──
    // 바닐라 기본값은 «스택1 && 내구도 있음»이라, 내구도가 없는 유물 9종은
    // 인챈트 테이블도 모루도 통째로 거부했다. 유물은 닳아 없어지면 안 되는 물건이라
    // 내구도를 주는 대신 여기만 연다.
    //
    // ⚠️ **무엇이 붙을지는 여기서 안 정한다.** 그건 데이터팩(`tools/gen_relic_enchants.py`)이
    //    인챈트의 `supported_items` 로 정한다 — 데미지 계열 17종은 거기서 막힌다.
    //    여기서 true 만 돌려주면 「테이블에 올라갈 자격」이 생길 뿐이다.
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.getCount() == 1;
    }

    // 인챈트 «잘 걸리는» 정도. 네더라이트와 같은 15 — 금(22)은 운이 과하고 돌(5)은 답답하다.
    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }
}
