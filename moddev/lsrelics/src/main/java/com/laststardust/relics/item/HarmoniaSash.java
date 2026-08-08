package com.laststardust.relics.item;

import com.laststardust.relics.HarmonyManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 케스토스 — 엮는 띠(하르모니아의 가호). 버프·지휘.
//   · 좌클릭 = 평타 (약하다 — 이 유물은 때리는 물건이 아니다)
//   · R = 고양의 선율 (기본, 1성)   · V = 엮인 걸음 (이동, 2성)
//   · C = 결속의 매듭 (추가, 3성)   · X = 만상의 화음 (궁극, 4성)
//   · 패시브(1성) = 공명 — 주변 8칸 아군 이동속도 +10% · 아군 처치 시 공격력 중첩 (HarmonyManager)
//
// ── 히기에이아와 겹치지 않는다 ──
// **회복·보호막·부활을 하나도 넣지 않았다.** 히기에이아가 서포트의 «회복» 절반을 갖고
// 이쪽이 «강화» 절반을 갖는다. 둘이 같은 파티에 있어도 역할이 안 먹힌다
// (docs/CLASS-9-10.md §2 설계 의도 1).
//
// ── /dummy 로는 0 이 나온다 ──
// 피해를 주는 스킬이 하나도 없다. 그게 정상이다 — 값어치가 전부 남의 숫자로 나가기 때문이다.
// 실측은 「하르모니아가 있을 때 파티 총합」으로 재야 하고, 그건 8종 단독 측정과 다른 방법이다.
public class HarmoniaSash extends Item implements RelicActions {

    public HarmoniaSash(Properties properties) {
        super(properties);
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
}
