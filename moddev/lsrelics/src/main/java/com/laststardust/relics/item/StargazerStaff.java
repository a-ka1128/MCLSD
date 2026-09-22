package com.laststardust.relics.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
// 셀레스티아 — 마법 무기. 좌클릭 = 마법 평타(별빛 탄). LSNetwork 패킷 → leftAttack.
//   · R = 소멸 (기본, 1성)        · V = 성간 도약 (이동, 2성)
//   · C = 중력 붕괴 (추가, 3성)   · X = 초신성 (궁극, 4성)
public class StargazerStaff extends Item implements RelicActions {
    public StargazerStaff(Properties properties) {
        super(properties);
    }

    // ── 좌클릭 마법 평타 ──
    @Override
    public boolean firesOnLeftClick() {
        return true;
    }

    @Override
    public void leftAttack(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.magicBolt(level, player, stack);
    }


    // ── V = 성간 도약(이동기) ──
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.blink(level, player, stack);
    }

    // ── X = 초신성(궁극) ──
    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.supernova(level, player, stack);
    }

    // ── R = 기본 스킬 (1성) ──
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.annihilate(level, player, stack);
    }

    // ── C = 추가 스킬 (3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.gravityCollapse(level, player, stack);
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
