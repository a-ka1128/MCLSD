package com.laststardust.relics.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
// 스틱스 — 그림자 단검(에레보스의 가호). 한 쌍(주손+보조손)으로 드는 쌍단검.
//   · 좌클릭 = 평타 (Better Combat 쌍수 콤보, 좌우 번갈아 베기)
//   · R = 급소 가르기 (기본, 1성)  · V = 그림자 도약 (이동, 2성)
//   · C = 망각의 안개 (추가, 3성)  · X = 무저갱 (궁극, 4성)
//   · 패시브 = 배후의 일격(+20%) · 연쇄 살상 — RelicEventHandlers 처리
public class SoulDagger extends Item implements RelicActions {
    public SoulDagger(Properties properties) {
        super(properties);
    }


    // 이동기(V·2성) = 그림자 도약
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.shadowLeap(level, player, stack);
    }

    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.abyss(level, player, stack);
    }

    // ── R = 기본 스킬 (1성) ──
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.viciousStrike(level, player, stack);
    }

    // ── C = 추가 스킬 (3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.oblivionMist(level, player, stack);
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
