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

    // ── 우클릭 = 쥐고 방어 (2026-08-10, 유저 요청) ──
    //
    // 네메시스의 「흘리기」와 같은 조작인데 **패링이 없다.** 앞에서 오는 피해를 −25% 로
    // 깎는 게 전부다. 판정은 `ParryManager.onParry` 한 곳에서 같이 본다 —
    // 두 무기가 같은 조작을 쓰는데 코드가 둘이면 한쪽만 고치는 날이 온다.
    //
    // ── 왜 이지스에는 패링을 안 주나 ──
    // 이지스의 «반응» 자리는 C「수호 반격」(3초 태세·−40%·반사)이 이미 차지하고 있다.
    // 우클릭까지 반응 조작이 되면 한 무기 안에서 둘이 겹치고, 그러면 네메시스의
    // 정체성(«타이밍을 맞추면 무효화») 도 같이 흐려진다.
    //   이지스 = 타이밍이 아예 필요 없는 탱커 → 두껍다(−25%)
    //   네메시스 = 맞추면 무효화, 못 맞추면 얇다(−15%)
    //
    // ⚠️ 대가: **이 무기를 들고는 상자를 못 열고 블록을 못 놓는다.**
    //    `RelicActions` 머리말의 「우클릭은 비워둔다」 원칙을 깨는 두 번째 근접 유물이다.
    //    실전에서 불편하면 되돌릴 자리다(네메시스 `CLASSES.md 「네메시스」` §2 와 같은 조건).
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        player.startUsingItem(hand);
        return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    /** 방패와 같은 팔 자세. 3인칭에서 「막고 있다」가 보여야 상대도 읽을 수 있다. */
    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.BLOCK;
    }

    /** 놓을 때까지 계속 — 방패와 같다. */
    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity entity) {
        return 72000;
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
