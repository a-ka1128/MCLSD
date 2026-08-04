package com.laststardust.relics.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
// 시리우스 — 좌클릭 홀드 = 별빛 화살 즉발 연사(평타). LSNetwork 패킷 → leftAttack.
//   · R = 속사 (기본, 1성)        · V = 회피 도약 (이동, 2성)
//   · C = 유성 사격 (추가, 3성)   · X = 별빛 폭풍 (궁극, 4성)
public class StarBow extends BowItem implements RelicActions {
    // 화살 피해는 ceil() 때문에 정수 단위로 튀어서 미세 조정이 안 된다 → 연사 간격으로 DPS를 맞춘다.
    // 발당 평균 2.6 × 초당 2.5발 = DPS 6.5
    private static final int FIRE_RATE = 8;       // 연사 간격(틱) ~0.4초, 초당 2.5발
    private static final int HASTE_FIRE_RATE = 4; // 속사 중 간격 ~0.2초, 초당 ~5발 (2배)
    private static final float CRIT_CHANCE = 0.6f; // 크리 확률 60% → 발당 평균 ~+30%
    private static final float ARROW_SPEED = 3.0f;
    private static final float ARROW_INACCURACY = 1.0f;
    // 1.354 -> 0.866 -> 0.854 -> 0.94 -> 0.99 -> 1.066 -> 0.932 (2026-07-27~30)
    //
    // 처음 크게 내린 이유: 스킬별 계측에서 평타가 87% 였다. 궁극기(별빛 폭풍)가 8% 라
    // 45초 쿨을 쓰고도 안 눌러서 손해가 거의 없었다. 뺀 몫을 두 스킬로 옮겼다.
    //
    // ── 이 유물은 4판 이상 모아서 판단한다 ──
    //   평타      2,189 / 2,087 / 2,106 / 2,066   (±3%)
    //   유성 사격   379 /   361 /   338 /   418   (±11%)
    //   별빛 폭풍   700 /   600 /   496 /   747   (±20%)
    //
    // ⚠️ **위 유성 사격 4판은 폭발이 죽어 있던 상태의 값이다 (2026-08-04 판명).**
    //    착탄 이벤트가 안 돌아 화살 직격만 들어가고 있었다 — 그 상태의 실측이 「18타 평균 9.9」.
    //    훅을 살린 뒤 같은 스킬이 18타 평균 99.3 / 103.0 이 나왔다. **위 숫자를 기준으로 쓰지 말 것.**
    //    별빛 폭풍의 4판도 착탄 확산이 죽어 있던 값이라 마찬가지다(평균 56.6 → 77.4 / 85.5).
    //
    // ── 2026-08-04 실측 (60초 · 축복 밖 · 방어도 0 · 5성) ──
    //   1판(궁 2회)  총 6,927 · 115.5 DPS   평타 3,515(197타) · 유성 1,788(18타) · 폭풍 1,625(19타)
    //   2판(궁 1회)  총 5,984 ·  99.7 DPS   평타 3,510(199타) · 유성 1,855(18타) · 폭풍   620( 8타)
    // 유성은 쿨 10초라 두 판 다 18타(6시전 × 3발)로 고정 — 궁극기 횟수와 무관해 비교가 깨끗하다.
    // 지속 기준은 104.8 로 봤다(궁극기 쿨 45초 → 60초당 1.33회). 거기서 폭발을 ×0.75 해
    // 97.9 로 내렸다 — `RelicSkills.meteorShot` 주석 참조.
    // 흔들리는 건 궁극기 하나다. 하늘에서 화살이 떨어지는 스킬이라 표적 1기에 몇 발이
    // 맞느냐가 매번 다르다 — 최소 496, 최대 747 로 1.5배 차이다.
    // - 한 판으로 판단하지 말 것 - .
    //
    // ── 마지막 x0.874 는 측정 기준이 바뀌어서다 ──
    // 위 4판은 성소 축복 - 밖 - 에서 잰 값(평균 52.0)이었고, 그 기준으로 x1.077 을 걸었다.
    // 그 뒤 기준이 축복 안으로 확정되면서 같은 값이 67.5 / 60.7 (평균 64.1) 로 나왔다 —
    // 축복의 투사체 +25% 를 원거리는 그대로 받기 때문이다(근접의 Strength +3.0 보다 폭이 크다).
    // 목표 56 에 맞추기 위한 x0.874 다. LSRelics 의 측정 기준 항목을 함께 볼 것.
    private static final double ARROW_DMG = 0.932;

    public StarBow(Properties properties) {
        super(properties);
    }

    // ── 내구도를 소모하지 않는다 (2026-07-27) ──
    // 유물은 첫 공세를 맨몸으로 버텨야 얻는 물건이라 소모품이 아니다. 닳아 없어지면
    // 그 직업을 통째로 잃는다. RiftAxe 와 같은 처리 — 이 훅 하나가 모든 경로를 덮는다.
    @Override
    public <T extends net.minecraft.world.entity.LivingEntity> int damageItem(
            ItemStack stack, int amount, T entity, java.util.function.Consumer<net.minecraft.world.item.Item> onBroken) {
        return 0;
    }

    // ── 우클릭 없음 (2026-07-27) ──
    // BowItem 을 상속만 해두니 우클릭이 바닐라 활 그대로였는데, 쓸 이유가 없는 기능이었다.
    //   · 화살이 있어야 당겨진다 — 이 무기의 평타(좌클릭 별빛 화살)는 화살이 필요 없다
    //   · 풀차지 바닐라 화살이 평타보다 약하다 — 별빛 화살만 각성 배율(ascension)을 받는다
    // 이득이 없는데 조작만 차지하고, 실수로 당겨지면 평타가 끊긴다. 아예 막는다.
    // pass 를 돌려주므로 블록 우클릭 등 다른 상호작용은 그대로 동작한다.
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    // ── 좌클릭 평타 (연사) ──
    @Override
    public boolean firesOnLeftClick() {
        return true;
    }

    @Override
    public void leftAttack(ServerLevel level, ServerPlayer player, ItemStack stack) {
        int rate = RelicSkills.isHasted(level, stack) ? HASTE_FIRE_RATE : FIRE_RATE;
        if (!RelicSkills.shotReady(level, stack, rate)) return;
        fire(level, player, stack);
    }


    // ── X = 별빛 폭풍(궁극) ──
    // 이동기(V·2성) = 회피 도약
    @Override
    public void doubleSneak(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.evadeLeap(level, player, stack);
    }

    @Override
    public void ultimate(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.starfallStorm(level, player, stack);
    }

    private void fire(ServerLevel sl, Player player, ItemStack stack) {
        Vec3 look = player.getViewVector(1.0f);
        Arrow arrow = com.laststardust.relics.LsArrows.create(sl, player, stack);
        arrow.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        arrow.shoot(look.x, look.y, look.z, ARROW_SPEED, ARROW_INACCURACY);
        arrow.setBaseDamage(ARROW_DMG * RelicSkills.power(stack)); // 평타도 각성·전역 배율을 받는다
        if (sl.getRandom().nextFloat() < CRIT_CHANCE) arrow.setCritArrow(true); // 확률 크리
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED; // 별빛 화살(무한, 회수 불가)
        sl.addFreshEntity(arrow);
        sl.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.7f, 1.5f);
    }

    // ── R = 기본 스킬 (1성) ──
    @Override
    public void basicSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.rapidFire(level, player, stack);
    }

    // ── C = 추가 스킬 (3성) ──
    @Override
    public void extraSkill(ServerLevel level, ServerPlayer player, ItemStack stack) {
        RelicSkills.meteorShot(level, player, stack);
    }

}
