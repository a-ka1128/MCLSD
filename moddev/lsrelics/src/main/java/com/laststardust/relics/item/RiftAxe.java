package com.laststardust.relics.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
// 타이탄 브레이커 — 순수 근접 무기(도끼). 채굴 겸용은 뺐고 그만큼 공격력이 높다.
// 타이탄 브레이커 — 도끼. 좌클릭 = 강타(근접). 블록 껍질 벗기기는 바닐라 그대로 동작한다
// (우클릭을 비워둔 덕분에 도끼 본래 기능이 막히지 않는다).
//   · R = 균열 붕괴 (기본, 1성)   · V = 지축 밟기 (이동, 2성)
//   · C = 대지 쪼개기 (추가, 3성) · X = 타이탄 강림 (궁극, 4성)
public class RiftAxe extends AxeItem implements RelicActions {
    public RiftAxe(Tier tier, Properties properties) {
        super(tier, properties);
    }


    // ── 좌클릭은 평범한 근접 공격이므로 패킷 발사 안 함 ──
    @Override
    public boolean firesOnLeftClick() {
        return false;
    }

    @Override
    public void leftAttack(ServerLevel level, ServerPlayer player, ItemStack stack) {
        // 미사용
    }

    // ── V = 지축 밟기(이동기) ──
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.earthStomp(level, player, stack);
    }

    // ── X = 타이탄 강림(궁극) ──
    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.titanAscension(level, player, stack);
    }

    // ── R = 기본 스킬 (1성) ──
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.riftCollapse(level, player, stack);
    }

    // ── C = 추가 스킬 (3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.earthSplitter(level, player, stack);
    }

}
