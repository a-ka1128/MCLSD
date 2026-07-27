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

    // ── "지금 들어가는 피해가 스킬인가" 표식 ──
    //
    // ThreatManager 가 이지스 평타에만 위협도 ×2 를 주려는데, 근접 유물은 스킬도 바닐라
    // 피해원(playerAttack)을 그대로 쓴다 — DamageSource 로는 평타와 스킬을 구분할 수 없다.
    // 대신 **스킬 피해는 전부 이 클래스를 거친다**(2026-07-27 통일). 그래서 hurt 호출을
    // 감싸는 동안만 깃발을 세우고, 피해 이벤트가 그걸 읽는다.
    //
    // 서버 스레드에서 hurt() 는 동기로 끝나므로 static 하나로 충분하다. 다만 hurt 안에서
    // 다른 피해가 연쇄로 발생할 수 있어(가시 갑옷 등) 되돌릴 때 이전 값을 복원한다.
    private static boolean inSkill = false;

    public static boolean inSkill() { return inSkill; }

    // ── "무엇이 때렸는가" 이름표 ──
    //
    // 밸런스 조정이 총 DPS 하나만 보고 굴러가면 배분을 추측으로 맞추게 된다. 실제로
    // 마총에서 세 번 연속 빗나갔다 — 총량은 목표에 붙였는데 평타·연사·궁의 몫이
    // 계산과 달랐고, 합계만으로는 어느 쪽이 틀렸는지 알 방법이 없었다.
    //
    // 그래서 피해가 지나가는 이 창구에 이름표를 붙인다. DummyManager 가 이걸 읽어
    // 스킬별로 쪼갠 결과를 내놓는다. 이름표가 없는 피해(바닐라 근접 평타 등)는 "평타"다.
    private static String label = null;

    public static String currentLabel() { return label; }

    // 무적 프레임을 무시하고 때린다. 단발·저빈도 스킬용.
    public static boolean hit(LivingEntity target, DamageSource source, float amount) {
        return hit(target, source, amount, null);
    }

    public static boolean hit(LivingEntity target, DamageSource source, float amount, String label) {
        if (target == null || !target.isAlive() || amount <= 0) return false;
        target.invulnerableTime = 0;
        boolean prev = inSkill;
        String prevLabel = LsDamage.label;
        inSkill = true;
        LsDamage.label = label;
        try {
            return target.hurt(source, amount);
        } finally {
            inSkill = prev;
            LsDamage.label = prevLabel;
        }
    }

    // 다단히트용 — 표적별로 최소 간격을 둔다.
    //
    // ※ `key` 는 **스킬마다 달라야 한다.** 처음엔 키 하나를 공유했는데, 그러면 같은 몹 위에서
    //   백창(8틱)과 장판(20틱)이 서로의 도장을 덮어써 **상대의 피해를 지웠다.** 파티가 함께
    //   싸울수록 화력이 줄어드는, 눈에 안 보이는 간섭이었다.
    //
    // gameTime 을 직접 받는 이유: 매니저들이 이미 틱 루프 안이라 레벨을 또 뒤질 필요가 없다.
    public static boolean hitLimited(LivingEntity target, DamageSource source, float amount,
                                     String key, long gameTime, int minInterval) {
        return hitLimited(target, source, amount, key, gameTime, minInterval, null);
    }

    public static boolean hitLimited(LivingEntity target, DamageSource source, float amount,
                                     String key, long gameTime, int minInterval, String label) {
        if (target == null || !target.isAlive() || amount <= 0) return false;
        String tag = "lsHit_" + key;
        long last = target.getPersistentData().getLong(tag);
        if (last != 0 && gameTime - last < minInterval) return false;
        target.getPersistentData().putLong(tag, gameTime);
        target.invulnerableTime = 0;
        boolean prev = inSkill;
        String prevLabel = LsDamage.label;
        inSkill = true;
        LsDamage.label = label;
        try {
            return target.hurt(source, amount);
        } finally {
            inSkill = prev;
            LsDamage.label = prevLabel;
        }
    }
}
