package com.laststardust.relics.item;

import com.laststardust.relics.ParryManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

// 아드라스테이아 — 대검(네메시스의 가호). **패링 탱커.**
//   · 좌클릭 = 평타 (Better Combat `claymore` 3타 콤보)
//   · 우클릭 = 흘리기 — 쥐고 방어(−15%), 쥔 직후 0.4초는 완벽 패링
//   · R = 강철 발 (기본, 1성)   · V = 참격 인계 (이동, 2성)
//   · C = 불굴 (추가, 3성)      · X = 일도양단 (궁극, 4성)
//   · 패시브(1성) = 강철의 각오 — 방어력 +5 · 방어 강도 +3 · 기세 (ParryManager)
//
// ── 우클릭을 쓰는 근접 유물은 둘이다 (이지스와 여기) ──
// `RelicActions` 머리말의 원칙은 「우클릭은 비워둔다 — 상자 열기·블록 설치가 방해받지 않게」이고,
// 실제로 use() 를 쓰는 건 귀환석·솔라리스(스코프)·시리우스(활) 셋뿐이다. **이지스도 안 쓴다.**
//
// 그럼에도 여기로 온 이유: 패링은 «반응» 조작이다. R/V/C/X 는 성급 해금과 쿨 표시에 묶인
// 스킬 슬롯이라 거기 넣으면 스킬 하나를 통째로 잡아먹는다. 대검은 two_handed 라 어차피
// 보조손을 못 쓰기도 한다.
//   ⚠️ 대가: 이 무기를 들고는 상자를 못 열고 블록을 못 놓는다. 실전에서 그게 불편하면
//      「R 로 옮기고 → 강철 발을 V 로 → 참격 인계를 버린다」 순서로 물러선다
//      (docs/CLASSES.md 「네메시스」 §2).
public class NemesisBlade extends Item implements RelicActions {

    public NemesisBlade(Properties properties) {
        super(properties);
    }

    // ── 우클릭 = 흘리기 (방패처럼 «누르고 있는다») ──
    //
    // ── 왜 탭이 아니라 홀드인가 (2026-08-09, 유저 요청) ──
    // 처음엔 «한 번 눌러 0.4초 창을 연다» 였는데, 인게임에서 방패처럼 쥐고 있는 조작을 원했다.
    // 그런데 홀드를 «계속 막기»로만 만들면 이지스의 「수호 반격」(태세)과 똑같아진다 —
    // 그건 이 직업을 만들 때 제일 피하려던 것이다.
    //
    // 그래서 둘을 겹쳤다(소울류가 쓰는 방식):
    //   쥐고 있는 동안        방어 자세 — 앞에서 오는 피해 −15% · 쿨 없음 · 이동이 느려진다
    //   **쥔 뒤 0.4초 안**   완벽 패링 — 무효화 + 반격 + 기세
    // 조작은 방패 그대로인데 타이밍의 값어치가 남는다. 못 맞춰도 −15% 는 받는다.
    //
    // ⚠️ 0.25 → 0.15 (2026-08-10). 같은 조작이 **이지스에도 붙으면서** 둘의 몫을 갈랐다 —
    //    이지스는 패링이 없는 대신 −25% 로 두껍고, 이쪽은 얇은 대신 패링이 있다.
    //    그대로 뒀다면 「이지스가 하는 걸 다 하는데 패링까지 있는」 무기가 된다.
    //    「못 해도 탱커」의 바닥은 이제 R「강철 발」(기세 0 이어도 −20%)이 혼자 진다.
    //
    // 판정은 전부 ParryManager.onParry 가 한다 — 여기서는 자세를 취하기만 한다.
    // 창의 시작점은 별도 저장 없이 `getTicksUsingItem()` 이 알려준다.
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            RelicSkills.deflect(sl, sp, stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** 방패와 같은 팔 자세. 3인칭에서 「막고 있다」가 보여야 상대도 읽을 수 있다. */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    /** 놓을 때까지 계속 — 방패와 같다. */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
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

    // ── C = 불굴 (추가·3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.unyielding(level, player, stack);
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
