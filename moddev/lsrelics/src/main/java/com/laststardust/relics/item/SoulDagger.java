package com.laststardust.relics.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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

}
