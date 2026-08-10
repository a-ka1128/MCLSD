package com.laststardust.relics.item;

import com.laststardust.relics.ChironManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// 펠리온 — 봉(케이론의 가호). **봉술 몽크 · 근접 하이브리드 힐러.**
//   · 좌클릭 = 평타 (Better Combat `battlestaff` 6타 콤보) — **때릴 때마다 아군이 낫는다**
//   · 우클릭 = 비워둠 (RelicActions 원칙)
//   · R = 축성 (기본, 1성)   · V = 바람 걸음 (이동, 2성)
//   · C = 가르침 (추가, 3성) · X = 펠리온의 밤 (궁극, 4성)
//   · 패시브(1성) = 상처 입은 치유자 — 전이 회복. **자기 자신은 제외** (ChironManager)
//
// ── 이 직업이 존재하는 이유 ──
// `CLASS-RESEARCH.md` 관찰2: 「히기에이아 1인 과부하 — 힐러가 결석하면 파티가 성립하지 않는
// 단일 장애점」. 그런데 같은 문서 §6 이 「힐러를 2종으로 분리」를 명시적으로 반대한다 —
// 순수 힐러가 둘이면 파나케이아가 오는 날 두 번째 힐러는 할 일이 없어 아무도 안 고르고,
// 그러면 **힐러 없는 날은 여전히 없다.**
//
// 그래서 「힐러」가 아니라 **「힐도 되는 전사」**다. 파나케이아가 있으면 근접 딜로 놀고,
// 없으면 파티가 굴러가는 최소한의 힐을 댄다. **항상 쓸 자리가 있어야 실제로 선택되고,
// 그래야 단일 장애점이 진짜로 풀린다** (docs/CLASSES.md 「케이론」 §0).
public class ChironStaff extends Item implements RelicActions {

    public ChironStaff(Properties properties) {
        super(properties);
    }

    // ── R = 축성 (기본·1성) ──
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.consecrate(level, player, stack);
    }

    // ── V = 이동기 · 바람 걸음 ──
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.windStep(level, player, stack);
    }

    // ── C = 가르침 (추가·3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.teaching(level, player, stack);
    }

    // ── X = 펠리온의 밤 (궁극·4성) ──
    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.pelionNight(level, player, stack);
    }

    // ── 액션바: 지금 켜져 있는 창 ──
    // 이 직업의 두 창은 **화면에 흔적이 거의 없다**. 「가르침」은 남의 평타에 붙는 효과라
    // 내 화면에서 안 보이고, 「펠리온의 밤」은 아군이 죽을 뻔해야 티가 난다.
    // 남은 시간이 안 보이면 언제 다시 눌러야 하는지 알 방법이 없다.
    // (네메시스가 기세를 같은 자리에 띄우는 것과 같은 이유.)
    @Override
    public String hudStatus(ServerPlayer player, ItemStack stack) {
        long night = ChironManager.nightLeft(player);
        if (night > 0) return "§b✶ 펠리온의 밤 " + (night / 20 + 1) + "s";
        long teach = ChironManager.teachingLeft(player);
        if (teach > 0) return "§6⚕ 가르침 " + (teach / 20 + 1) + "s";
        long wind = ChironManager.windLeft(player);
        if (wind > 0) return "§7≫ 바람 걸음 " + (wind / 20 + 1) + "s";
        return null;
    }
}
