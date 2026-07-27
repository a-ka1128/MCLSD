package com.laststardust.relics.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
// 에테르 이지스 (탱커)
//  · 좌클릭 = 평타 (양손 콤보, Better Combat)
//  · R = 수호의 파동 (기본, 1성)   · V = 이지스 돌진 (이동, 2성)
//  · C = 수호 반격 (추가, 3성)     · X = 불멸의 맹세 (궁극, 4성)
//  · 패시브(1성) = 방벽의 수호자 (넉백 저항 + 근처 아군 피해 감소)
public class BulwarkBlade extends Item implements RelicActions {
    public BulwarkBlade(Properties properties) {
        super(properties);
    }


    // 이동기(V·2성) = 이지스 돌진
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.aegisCharge(level, player, stack);
    }

    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.oathOfImmortality(level, player, stack);
    }

    // ── R = 기본 스킬 (1성) ──
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.guardPulse(level, player, stack);
    }

    // ── C = 추가 스킬 (3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.guardParry(level, player, stack);
    }

}
