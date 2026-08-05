# -*- coding: utf-8 -*-
"""성급별 파티 DPS 를 계산해 보스 절대 체력을 역산한다.

왜 «성급 배율로 나누기»가 안 되나:
    ls_config.js 는 5성 DPS 에 ASCENSION 비(1.0/3.0 등)를 곱해 1성 DPS 를 냈다.
    그런데 플레이어 피해에는 - 성급을 안 타는 덧셈 항 - 이 섞여 있다.
    성소 축복의 Strength I (근접 공격력 +3.0)이 그것이다. 이 항이 있으면
    1성 DPS 는 5성의 1/3 보다 - 높다 - . 반대로 GLOBAL_POWER=1.8 은 그 항에 안 곱해지므로
    실효 배수는 1.8 보다 - 낮다 - . 두 오차가 반대 방향이라 어림으로는 못 맞춘다.

    ※ ls_config.js 주석의 «스킬트리 고정 +3.0» 은 존재하지 않는다.
      data/puffish_skills/.../combat/definitions.json 의 공격력 노드는 전부
      melee/ranged/magic_damage 의 multiply_total 3% — 순수 곱셈이라 상쇄된다.
      실제 덧셈 항은 축복 하나뿐이다.

방법:
    ① 5성 실측값(LSRelics.java)에서 그 유물의 - 곱셈 뭉치 K - 를 역산한다.
       K 는 스킬트리·크리·기타 곱셈 전부를 뭉뚱그린 값이고, 측정 조건이 곧 실전 조건이다.
    ② 그 K 로 임의 성급의 평타 DPS 를 다시 만든다. 덧셈 항은 성급을 안 탄다.
    ③ 스킬은 성급과 GLOBAL 에 정비례로 둔다(dmg() 의 weaponAttack*0.3 항은 무시 —
       5성 스킬 피해 앞에서 작고, 무시하면 체력을 - 보수적으로 - 낮게 잡는 쪽이다).
"""
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

G = 1.8                      # RelicSkills.GLOBAL_POWER
ASC = {1: 1.0, 2: 1.5, 3: 2.0, 4: 2.5, 5: 3.0}
BLESS_FLAT = 3.0             # 성소 Lv4 별빛 축복 = Strength I (근접에만, 덧셈)

# (표시명, 아이템 공격력, 공속, 근접?, 5성 실측 DPS, 평타 비중)
# 실측·비중 출처: LSRelics.java 「측정 기준」 (60초 · 5성 · 방어도 0 · 축복 안)
RELICS = [
    ('이지스',     4.673, 1.6, True,  50.7, 0.70),
    ('타이탄',     7.292, 1.0, True,  53.1, 0.75),
    ('게볼그',     4.666, 1.5, True,  54.2, 0.75),
    # ⚠️ 스틱스 — 인게임 값은 2026-07-31 부로 **1.409** 다(1.225 ×1.15, LSRelics.ASSASSIN).
    #    여기 1.225 를 남겨둔 건 오타가 아니다. 이 표의 (기본치, 실측) 은 **한 쌍**이고,
    #    k = 실측/이론 으로 보정상수를 역산하는 구조라 **한쪽만 바꾸면 k 가 그 변화를 그대로
    #    흡수해 결과가 안 움직인다**(실제로 1.409 만 넣어 봤더니 81.2 → 81.7 이었다).
    #    그래서 «마지막으로 실측한 쌍»을 그대로 두고, 월요일 /dummy 로 새 바닥값을 재서
    #    **둘을 같이** 갱신한다. 그때까지 이 표의 스틱스는 실제보다 약 5% 낮게 나온다.
    #    예측값: 5성 더미 85.4 · 백어택 96.5 (docs/DECISIONS.md 1절)
    ('스틱스',     1.225, 2.4, True,  50.7, 0.80),   # 백어택·크리 없는 바닥값 (변경 전 쌍)
    ('시리우스',   None,  None, False, 56.0, 0.70),
    ('솔라리스',   None,  None, False, 56.7, 0.70),
    ('셀레스티아', None,  None, False, 56.8, 0.70),
    ('파나케이아', None,  None, False, 50.5, 0.70),
]

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

PARTY = 4
SECONDS = 60


def dps(relic, star):
    name, base, speed, melee, measured5, share = relic
    auto5 = measured5 * share
    skill5 = measured5 * (1.0 - share)

    if melee:
        # ① 5성 실측에서 곱셈 뭉치 K 를 역산 (GLOBAL 도입 전 기준)
        raw5 = ((1.0 + base) * ASC[5] + BLESS_FLAT) * speed
        k = auto5 / raw5
        # ② 덧셈 항은 성급을 안 탄다. GLOBAL 은 유물 항에만 곱해진다.
        auto = ((1.0 + base) * ASC[star] * G + BLESS_FLAT) * speed * k
    else:
        # 원거리 축복은 projectile_damage +25% = 곱셈이라 5성 실측에 이미 녹아 있다.
        auto = auto5 * (ASC[star] / ASC[5]) * G

    skill = skill5 * (ASC[star] / ASC[5]) * G
    return auto + skill


def main():
    print('GLOBAL_POWER = %.2f · 축복 덧셈 +%.1f (근접만) · 파티 %d명 · 목표 %d초'
          % (G, BLESS_FLAT, PARTY, SECONDS))

    print('\n성급별 1인 DPS (축복 안 · 방어도 0)')
    print('%-12s %7s %8s %8s %8s %8s %8s' % ('유물', '5성실측', '1성', '2성', '3성', '4성', '5성'))
    for r in RELICS:
        row = [dps(r, s) for s in range(1, 6)]
        print('%-12s %7.1f %8.1f %8.1f %8.1f %8.1f %8.1f' % (r[0], r[4], *row))

    avg = {}
    print('\n파티 평균 1인 DPS  (괄호 = 단순히 5성을 성급비로 나눴을 때)')
    for s in range(1, 6):
        avg[s] = sum(dps(r, s) for r in RELICS) / len(RELICS)
        naive = sum(r[4] for r in RELICS) / len(RELICS) * (ASC[s] / ASC[5]) * G
        print('  %d성  %6.1f   (%.1f)  차이 %+.0f%%' % (s, avg[s], naive, (avg[s] / naive - 1) * 100))

    print('\n보스 절대 체력 역산')
    print('%-22s %5s %8s %7s %10s %10s' % ('관문', '성급', '1인DPS', '유지율', '계산값', '반올림'))
    for name, star, uptime, extra, note in GATES:
        hp = avg[star] * PARTY * SECONDS * uptime * extra
        print('%-22s %4d성 %8.1f %7.2f %10.0f %10d   %s'
              % (name, star, avg[star], uptime, hp, round(hp / 50) * 50, note))

    print('\n※ 유지율(0.45~0.60)이 유일한 추정치다. 첫 전투에서 시간을 재고 덮어쓴다:')
    print('    새 체력 = 지금 체력 x (60 / 실제걸린초)')
    return 0


if __name__ == '__main__':
    sys.exit(main())
