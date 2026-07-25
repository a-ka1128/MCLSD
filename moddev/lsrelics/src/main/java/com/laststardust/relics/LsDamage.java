package com.laststardust.relics;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

// 유물 피해를 넣는 단일 창구.
//
// ── 왜 필요한가 ──
// 바닐라는 피해를 받으면 20틱 무적이 걸린다. 혼자 싸울 땐 잘 보이지 않지만 **파티에서는
// 표적이 거의 항상 무적 상태**라, 그 창에 들어간 공격은 `hurt()` 가 false 를 돌려주며
// 통째로 사라진다. 로그도 안 남고 이펙트는 그대로 나가서 "분명 맞았는데 체력이 안 준다"가 된다.
//
// 실제로 확인된 피해:
//   · 마총 스코프 사격(2초에 한 발)이 스킬라전에서 거의 안 들어갔다
//   · 시리우스는 화력원 셋이 전부 다단히트라 DPS 26 까지 떨어졌다 (수정 후 47)
//   · 법사처럼 "적게·크게" 때리는 직업일수록 한 번 씹힐 때 손실이 크다
//
// 그래서 유물 피해는 무적 프레임을 무시한다. **몹의 공격은 그대로 둔다** —
// 전역으로 풀면 수성전에서 플레이어가 순식간에 녹는다.
//
// ── 쓰면 안 되는 곳 ──
// 초당 수십 번 때리는 지속 장판·화살비는 이걸 쓰면 피해가 20배가 된다(실제로 별빛 폭풍이
// 그렇게 터졌다). 그런 스킬은 타격 빈도 자체를 설계로 조절하거나, 아래 hitLimited 를 쓴다.
public final class LsDamage {
    private LsDamage() {}

    // 무적 프레임을 무시하고 때린다. 단발·저빈도 스킬용.
    public static boolean hit(LivingEntity target, DamageSource source, float amount) {
        if (target == null || !target.isAlive() || amount <= 0) return false;
        target.invulnerableTime = 0;
        return target.hurt(source, amount);
    }

    // 다단히트용 — 표적별로 최소 간격을 둔다.
    // gameTime 을 직접 받는 이유: 매니저들이 이미 틱 루프 안이라 레벨을 또 뒤질 필요가 없고,
    // 테스트에서 시간을 넣어보기도 쉽다.
    public static boolean hitLimited(LivingEntity target, DamageSource source, float amount,
                                     long gameTime, int minInterval) {
        if (target == null || !target.isAlive() || amount <= 0) return false;
        long last = target.getPersistentData().getLong("lsLastRelicHit");
        if (last != 0 && gameTime - last < minInterval) return false;
        target.getPersistentData().putLong("lsLastRelicHit", gameTime);
        target.invulnerableTime = 0;
        return target.hurt(source, amount);
    }
}
