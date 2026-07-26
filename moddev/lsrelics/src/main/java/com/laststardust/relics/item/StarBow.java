package com.laststardust.relics.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private static final double ARROW_DMG = 1.354; // 명중 ~3.7, 크리 포함 실효 DPS 12.0 (속사 중엔 2배)

    public StarBow(Properties properties) {
        super(properties);
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
