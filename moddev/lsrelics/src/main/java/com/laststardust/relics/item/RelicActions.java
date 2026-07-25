package com.laststardust.relics.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

// 유물이 클라이언트 키 입력에 반응하기 위한 공통 인터페이스.
//
// 서버는 좌클릭·전용 키를 스스로 감지할 수 없어 클라가 패킷(LSNetwork)으로 알려주고,
// 그게 여기로 라우팅된다. 우클릭만 예외 — 그건 바닐라 Item.use() 가 그대로 처리한다.
//
// ── 조작 배치 ──
//   좌클릭        평타 (원거리는 홀드 연사 · 근접은 Better Combat 무브셋)
//   우클릭        비워둠 — 상자 열기·블록 설치·도끼 껍질 벗기기가 방해받지 않게
//                 (솔라리스만 예외: 스코프 홀드)
//   R             기본 스킬 (1성) — 솔라리스는 수동 재장전
//   V             이동기   (2성)
//   C             추가 스킬 (3성)
//   X             궁극기   (4성)
public interface RelicActions {
    // 좌클릭 홀드로 평타(투사체) 발사하는 유물인가? (활/지팡이/총=true, 근접=false)
    default boolean firesOnLeftClick() { return false; }

    // 좌클릭 평타 (홀드 중 매 틱 호출 — 구현부에서 연사 속도 제한)
    default void leftAttack(ServerLevel level, ServerPlayer player, ItemStack stack) {}

    // 기본 스킬 (R, 1성). 솔라리스만 이 자리가 수동 재장전이다.
    default void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {}

    // 이동기 (V, 2성) — 유물마다 다른 돌진·이속·순간이동
    // ※ 메서드 이름이 doubleSneak 인 건 예전 조작(쉬프트 두 번)의 잔재다 — 호출부가 많아 이름만 남겼다.
    default void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {}

    // 추가 스킬 (C, 3성)
    default void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {}

    // 궁극기 (X, 4성)
    default void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {}

    // 액션바 앞머리에 붙일 상태 문자열 (없으면 null).
    //
    // 액션바는 한 줄뿐이라 유물마다 따로 쓰면 쿨다운 줄과 매 틱 서로를 덮어써 깜빡인다.
    // 그래서 **쿨다운 표시가 액션바의 유일한 주인**이고, 유물은 여기에 자기 상태를 얹기만 한다.
    // (솔라리스의 잔탄이 이 자리를 쓴다 — 탄약과 쿨다운을 동시에 봐야 하는 무기라서)
    default String hudStatus(ServerPlayer player, ItemStack stack) { return null; }
}
