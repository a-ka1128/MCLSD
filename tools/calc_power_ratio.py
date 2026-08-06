# -*- coding: utf-8 -*-
"""성급별 파티 DPS 와 보스 절대 체력 — **실측을 기준으로 삼는다.**

── 2026-08-05 전면 재작성 ──
옛 버전은 «5성 실측에서 보정상수 k 를 역산하고 GLOBAL_POWER 를 그 위에 곱하는» 모델이었다.
그 모델이 편차 ±26% 를 예측했는데 실제로 재보니 ±11% 였다 — **절반 이하로 틀렸다.**
원인은 둘이었다:
  · k 를 맞춘 실측이 `GLOBAL_POWER x1.8` **도입 전** 값이라 배수가 이중으로 얹혔다
  · (기본치, 실측) 이 한 쌍이라 한쪽만 고치면 k 가 변화를 그대로 흡수했다
    (실제로 스틱스 기본치만 1.409 로 바꿨더니 결과가 81.2 → 81.7 로 거의 안 움직였다)

지금은 **8종 전부 실측값이 있다.** 그래서 모델링할 게 거의 남지 않았다 —
이 파일이 하는 일은 «실측 → 성급 환산 → 보스 체력» 세 단계뿐이다.

── 성급 환산이 단순한 이유 (축복 밖 기준) ──
`RelicEventHandlers.onRelicAttributes` 가 **평타에도** `power = ASC[star] x GLOBAL` 을 곱하고,
스킬은 `RelicSkills.dmg()` 가 같은 `power` 를 쓴다. **둘 다 ASC 에 정비례**한다.
그래서 축복 밖에서는:

    DPS(성급) = DPS(5성) x ASC[성급] / ASC[5]

정비례를 깨는 항은 둘뿐이고 둘 다 여기선 작거나 없다:
  · 스킬의 `weaponAttack x 0.3` — 성급을 안 타지만 스킬 피해의 2~3% 라 무시한다
  · 성소 축복의 Strength I `+3.0` — **성역 64칸 안에서만 걸린다**(`ls_towneffect.js`).
    관문 제단은 2000~5000m 밖이라 **보스전에는 안 걸린다.**

→ 옛 버전은 이 축복을 항상 켜진 것으로 보고 «축복 안» 기준으로 체력을 냈다. **그게 틀렸다.**
   관문 보스는 축복 밖에서 싸운다. 아래 기본 출력이 축복 밖인 이유다.
   (축복이 걸리는 건 성역에서 싸우는 **공성**이고, 그건 참고용으로만 낸다.)
"""
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ASC = {1: 1.0, 2: 1.5, 3: 2.0, 4: 2.5, 5: 3.0}
G = 1.8                # RelicSkills.GLOBAL_POWER — ASC 와 함께 평타·스킬에 똑같이 걸린다
BLESS_FLAT = 3.0       # 성소 Lv4 축복 Strength I (근접, 덧셈) · 성역 64칸 안에서만
BLESS_PROJ = 1.25      # 같은 축복의 원거리 몫 (apothic_attributes:projectile_damage +25%)

PARTY = 4
SECONDS = 60

# ── 2026-08-05 실측 (60초 · 5성 · **축복 밖** · 방어도 0 · relicScale 적용 후) ──
# 근거: docs/DECISIONS.md 1-B 「최종 확인」. 여덟 종 전부 목표 ±2.2% 안에 들어온 값이다.
# (표시명, 5성 DPS, 평타 비중, 근접?, 아이템 공격력)
#   · 아이템 공격력은 «축복 안» 환산에만 쓴다(원거리는 None).
#   · 스틱스는 백어택만 = «보통 플레이» 를 대표값으로 쓴다. 정면 91.5 / 풀딜 112.1 은 아래 주석.
RELICS = [
    ('솔라리스',   102.2, 0.55, False, None),
    ('스틱스',     101.7, 0.51, True,  1.409),   # 백어택만 · 정면 91.5 · 풀딜 112.1
    ('셀레스티아', 100.5, 0.41, False, None),
    ('시리우스',    98.2, 0.60, False, None),     # 궁극기 1.33회/60초 환산한 지속값
    ('타이탄',      97.4, 0.74, True,  7.292),
    ('게볼그',      95.7, 0.57, True,  4.666),    # 2판 평균 (95.5 / 95.9)
    ('이지스',      88.8, 0.64, True,  4.673),
    ('파나케이아',  87.8, 0.81, False, None),     # 08-04 값 · 유지 결정이라 재측정 안 함
]

# 스틱스의 세 값 — 조작 숙련도로 갈린다. 파티 평균에는 「백어택만」을 쓴다.
STYX = {'정면': 91.5, '백어택만': 101.7, '풀딜': 112.1}

# (관문, 파티 성급, 딜 유지율, 추가 계수, 설명)
GATES = [
    ('T1 강철거인',        1, 0.45, 1.00, '패링형 — 못 때리는 구간이 길다'),
    ('T2 이그니스',        2, 0.60, 1.00, ''),
    # 방어도 8 이면 실제로 들어가는 피해가 68% 다. 실측은 방어도 0 표적이라, 같은 체력을
    # 두면 60초가 아니라 88초(=60/0.68)가 걸린다. 그래서 체력을 - 곱해서 낮춘다 - .
    # (한 번 1/0.68 로 뒤집어 넣어 T3 만 두 배로 나왔다. 방향을 헷갈리기 쉬운 자리다.)
    ('T3 건틀렛',          3, 0.60, 0.68, '방어도 8 → 실피해 68%'),
    ('T4 네더라이트 괴물', 4, 0.55, 1.00, ''),
]

# 지금 ls_config.js 에 들어 있는 값 — 계산 결과와 대조해 보여준다.
IN_GAME = {'T1 강철거인': 3500, 'T2 이그니스': 6950,
           'T3 건틀렛': 6300, 'T4 네더라이트 괴물': 10600}


def dps(relic, star):
    """축복 밖. ASC 정비례 — 위 머리말 참조."""
    return relic[1] * ASC[star] / ASC[5]


def dps_blessed(relic, star):
    """축복 안(성역 64칸). 공성 참고용 — **관문 보스 체력에는 쓰지 않는다.**

    근접은 덧셈(+3.0)이라 성급이 낮을수록 비중이 커진다. 원거리는 곱셈(x1.25)이라 균일하다.
    둘 다 **평타에만** 걸린다고 본다 — 스킬은 우리 코드가 직접 계산해 넣는 고정 피해라
    무기 공격력 속성을 대부분 안 탄다.
    """
    name, d5, share, melee, base = relic
    auto = d5 * share * ASC[star] / ASC[5]
    skill = d5 * (1.0 - share) * ASC[star] / ASC[5]
    if melee:
        # 평타 한 방 = (1.0 + 아이템공격력) x ASC x G. 거기에 +3.0 이 더해진다.
        swing = (1.0 + base) * ASC[star] * G
        auto *= (swing + BLESS_FLAT) / swing
    else:
        auto *= BLESS_PROJ
    return auto + skill


def main():
    print('실측 기준 2026-08-05 · 60초 · 5성 · 축복 밖 · 방어도 0 · 파티 %d명' % PARTY)
    print('성급 환산 = ASC 정비례 (평타·스킬 둘 다 ASC x GLOBAL 을 탄다)')

    print('\n성급별 1인 DPS (축복 밖 — 관문 보스전 조건)')
    print('%-12s %8s %8s %8s %8s %8s' % ('유물', '1성', '2성', '3성', '4성', '5성(실측)'))
    for r in RELICS:
        print('%-12s %8.1f %8.1f %8.1f %8.1f %8.1f'
              % (r[0], *[dps(r, s) for s in range(1, 6)]))

    avg = {s: sum(dps(r, s) for r in RELICS) / len(RELICS) for s in range(1, 6)}
    bavg = {s: sum(dps_blessed(r, s) for r in RELICS) / len(RELICS) for s in range(1, 6)}

    print('\n파티 평균 1인 DPS')
    print('%-8s %10s %10s %8s' % ('성급', '축복 밖', '축복 안', '차이'))
    for s in range(1, 6):
        print('%-7d성 %10.1f %10.1f %+7.0f%%' % (s, avg[s], bavg[s], (bavg[s] / avg[s] - 1) * 100))
    print('  ※ 축복은 성역 64칸 안에서만 걸린다. 낮은 성급일수록 덧셈(+3.0)의 비중이 커서 차이가 크다.')

    print('\n스틱스 — 조작 숙련도로 갈린다 (파티 평균에는 「백어택만」을 썼다)')
    for k, v in STYX.items():
        print('   %-8s %6.1f   평균 대비 %+5.1f%%' % (k, v, (v / avg[5] - 1) * 100))

    print('\n보스 절대 체력 역산 (축복 밖 — 관문 제단은 성역에서 2000~5000m 밖이다)')
    print('%-22s %5s %8s %7s %9s %9s %9s'
          % ('관문', '성급', '1인DPS', '유지율', '계산값', '반올림', '지금값'))
    for name, star, uptime, extra, note in GATES:
        hp = avg[star] * PARTY * SECONDS * uptime * extra
        rounded = round(hp / 50) * 50
        cur = IN_GAME.get(name, 0)
        gap = (cur / rounded - 1) * 100 if rounded else 0
        print('%-22s %4d성 %8.1f %7.2f %9.0f %9d %9d (%+.0f%%)'
              % (name, star, avg[star], uptime, hp, rounded, cur, gap))
        if note:
            print('%-22s %s' % ('', note))

    print('\n※ 유지율(0.45~0.60)이 **유일한 추정치**다. 나머지는 전부 실측이다.')
    print('   첫 전투에서 전투 기록계가 실측 DPS 와 권장 체력을 스스로 낸다 —')
    print('   그 값이 이 표보다 낫다. `/bossdiff abs <id> <hp>` 로 덮어쓰고 `export`.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
