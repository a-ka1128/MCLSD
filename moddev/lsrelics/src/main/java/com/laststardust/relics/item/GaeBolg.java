package com.laststardust.relics.item;


import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
// 게볼그 — 창(쿠훌린의 가호). 근접 하이브리드, 긴 사거리.
//   · 좌클릭 = 평타 (Better Combat 창 무브셋, 긴 사거리 찌르기·베기)
//   · R = 투창 (기본, 1성)        · V = 질풍 돌진 (이동, 2성)
//   · C = 꿰뚫기 (추가, 3성)      · X = 백 개의 창 (궁극, 4성)
//   · 패시브(1성) = 긴 창 (사거리 +1.5 — weapon_attributes range_bonus)
public class GaeBolg extends Item implements RelicActions {

    public GaeBolg(Properties properties) {
        super(properties);
    }

    // ── R = 투창 (기본·1성) ──
    // 차징이 사라졌으므로 항상 최대 위력(1.0)으로 나간다. 쿨다운 8초는 그대로다.
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.javelinThrow(level, player, stack, 1.0f);
    }

    // ── V = 이동기 · 질풍 돌진 ──
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.gustDash(level, player, stack);
    }

    // ── C = 꿰뚫기 (추가·3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.pierceThrust(level, player, stack);
    }

    // ── X = 백 개의 창 (궁극·4성) ──
    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.hundredSpears(level, player, stack);
    }
}
