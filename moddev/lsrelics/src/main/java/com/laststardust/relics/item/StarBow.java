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
    // 1.354 -> 0.866 (x0.64, 2026-07-27). 스킬별 계측에서 평타가 87% 였다 —
    // 궁극기(별빛 폭풍)가 8% 라 60초 쿨을 쓰고도 안 눌러서 손해가 거의 없었다.
    // 평타 70% / 스킬 30% 가 되도록 내리고, 뺀 몫을 두 스킬로 옮긴다.
    // 0.866 -> 0.854 -> 0.94 (2026-07-27)
    //
    // ── 이 유물만 판별 편차가 크다 ──
    // 같은 값으로 두 판을 재니 평타가 2,449 / 1,989 로 19% 벌어졌다. 다른 유물은 3% 안이다.
    // 이유는 둘이고 둘 다 이 무기 고유의 것이다:
    //   · 8종 중 유일하게 평타가 빗나갈 수 있다 (실제 투사체, 명중 판정)
    //   · 속사(12초 쿨)가 연사 속도를 2배로 만드는데 가동률이 판마다 다르다
    // 그래서 한 판에 맞추면 다음 판에 또 어긋난다. 두 판 평균(2,219)을 기준으로 잡는다.
    private static final double ARROW_DMG = 0.94;

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
        arrow.setBaseDamage(ARROW_DMG * RelicSkills.ascension(stack)); // 평타도 각성 배율을 받는다
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
