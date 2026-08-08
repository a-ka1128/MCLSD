package com.laststardust.relics.item;

import com.laststardust.relics.ParryManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// 아드라스테이아 — 대검(네메시스의 가호). **패링 탱커.**
//   · 좌클릭 = 평타 (Better Combat `claymore` 3타 콤보)
//   · 우클릭 = 흘리기 — 0.4초 패링 창
//   · R = 강철 발 (기본, 1성)   · V = 참격 인계 (이동, 2성)
//   · C = 역린 (추가, 3성)      · X = 일도양단 (궁극, 4성)
//   · 패시브(1성) = 강철의 각오 — 방어력 +5 · 방어 강도 +3 · 기세 (ParryManager)
//
// ── 우클릭을 쓰는 근접 유물은 이것뿐이다 ──
// `RelicActions` 머리말의 원칙은 「우클릭은 비워둔다 — 상자 열기·블록 설치가 방해받지 않게」이고,
// 실제로 use() 를 쓰는 건 귀환석·솔라리스(스코프)·시리우스(활) 셋뿐이다. **이지스도 안 쓴다.**
//
// 그럼에도 여기로 온 이유: 패링은 «반응» 조작이다. R/V/C/X 는 성급 해금과 쿨 표시에 묶인
// 스킬 슬롯이라 거기 넣으면 스킬 하나를 통째로 잡아먹는다. 대검은 two_handed 라 어차피
// 보조손을 못 쓰기도 한다.
//   ⚠️ 대가: 이 무기를 들고는 상자를 못 열고 블록을 못 놓는다. 실전에서 그게 불편하면
//      「R 로 옮기고 → 강철 발을 V 로 → 참격 인계를 버린다」 순서로 물러선다
//      (docs/CLASS-11.md §2).
public class NemesisBlade extends Item implements RelicActions {

    public NemesisBlade(Properties properties) {
        super(properties);
    }

    // ── 우클릭 = 흘리기 ──
    // 여기서는 «창을 여는 것»만 한다. 성공/실패 판정은 맞는 순간에 나므로
    // ParryManager.onParry 가 맡는다.
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack);
        }
        if (!RelicSkills.shotReady(sl, stack, ParryManager.PARRY_CD)) {
            return InteractionResultHolder.fail(stack);
        }
        RelicSkills.deflect(sl, sp, stack);
        return InteractionResultHolder.success(stack);
    }

    // ── R = 강철 발 (기본·1성) ──
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.steelStance(level, player, stack);
    }

    // ── V = 이동기 · 참격 인계 ──
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.bladeRecall(level, player, stack);
    }

    // ── C = 역린 (추가·3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.reverseScale(level, player, stack);
    }

    // ── X = 일도양단 (궁극·4성) ──
    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.sunderAll(level, player, stack);
    }

    // ── 액션바: 기세 중첩 ──
    // 이게 없으면 「패링이 먹혔나」를 알 방법이 파티클뿐이다. 기세는 주는 피해에만 붙어서
    // 화면에 흔적이 없고, 그 중첩이 이 직업의 유일한 자원이다.
    // (헤카테가 저주 중첩을 같은 자리에 띄우는 것과 같은 이유.)
    @Override
    public String hudStatus(ServerPlayer player, ItemStack stack) {
        int n = ParryManager.momentum(player);
        if (n <= 0) return null;
        String color = n >= ParryManager.MOMENTUM_MAX ? "§6" : "§7";
        return color + "⊗ " + n + "/" + ParryManager.MOMENTUM_MAX;
    }
}
