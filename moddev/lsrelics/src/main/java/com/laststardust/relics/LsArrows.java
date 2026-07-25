package com.laststardust.relics;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

// 별빛 화살 생성 공통 헬퍼 — 서버를 죽이는 크래시 계열의 원천 차단용.
//
// 1.21.1의 AbstractArrow 는 firedFromWeapon 인자에 "빈(EMPTY) 스택"을 받으면
//   java.lang.IllegalArgumentException: Invalid weapon firing an arrow
// 를 던진다. 이게 서버 틱 스레드에서 터지면 서버 전체가 다운된다(2026-07-23 크래시).
//   · null            → OK (무기 없이 발사)
//   · 실제 아이템 스택  → OK
//   · ItemStack.EMPTY  → 크래시
// 모든 유물 화살은 반드시 이 헬퍼를 거치게 해서, 실수로 EMPTY 가 넘어와도
// null 로 정규화되어 절대 크래시로 이어지지 않도록 한다.
public final class LsArrows {
    private LsArrows() {}

    // 유물 화살 표식. 착탄 시 무적 프레임을 걷어내는 대상을 가리는 데 쓴다.
    // (RelicEventHandlers.onRelicArrowPierce — 이유는 그쪽 주석에)
    public static final String TAG = "lsRelicArrow";

    // pickup 은 항상 일반 화살, firedFromWeapon 은 EMPTY→null 정규화.
    public static Arrow create(Level level, LivingEntity owner, ItemStack firedFromWeapon) {
        ItemStack weapon = (firedFromWeapon == null || firedFromWeapon.isEmpty()) ? null : firedFromWeapon;
        Arrow arrow = new Arrow(level, owner, new ItemStack(Items.ARROW), weapon);
        arrow.getPersistentData().putBoolean(TAG, true);
        return arrow;
    }
}
