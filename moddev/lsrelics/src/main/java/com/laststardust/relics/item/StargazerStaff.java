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

}
