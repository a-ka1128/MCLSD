# -*- coding: utf-8 -*-
"""유물 평타 콤보를 다양하게 — 단, **평균 배율은 건드리지 않는다.**

08-04 실측으로 8종을 ±2.2% 까지 맞춰놨다. 콤보 길이를 늘리면서 평균
damage_multiplier 가 바뀌면 그 측정이 통째로 무효가 된다.
그래서 «타수는 늘리되 평균은 소수점까지 그대로» 가 이 작업의 규칙이다.

    DPS = 표시 공격력 × 평균배율 × 초당 타수

세 항 중 평균배율만 안 건드리면 DPS 는 안 움직인다.
"""
import io
import json
import os

DIR = r"D:\Study\MC\CustomServer1\moddev\lsrelics\src\main\resources\data\lsrelics\weapon_attributes"


def atk(anim, mult, hitbox="HORIZONTAL_PLANE", angle=150, upswing=0.5, sound=None, conditions=None):
    d = {}
    if conditions:
        d["conditions"] = conditions
    d["hitbox"] = hitbox
    d["damage_multiplier"] = mult
    d["angle"] = angle
    d["upswing"] = upswing
    d["animation"] = "bettercombat:" + anim
    if sound:
        d["swing_sound"] = {"id": sound}
    return d


def write(name, attrs, note):
    body = {"attributes": attrs}
    mults = [a["damage_multiplier"] for a in attrs["attacks"]]
    avg = sum(mults) / len(mults)
    path = os.path.join(DIR, name + ".json")
    io.open(path, 'w', encoding='utf-8', newline='').write(
        json.dumps(body, indent=2, ensure_ascii=False) + "\n")
    print("%-10s %d타  평균 %.4f   %s" % (name, len(mults), avg, note))


# ── 스틱스(에레보스) — 3타 → 5타 (2026-08-10 유저 지정) ──
# 가로R → 가로L → 쌍수 교차 → 쌍수 찌르기 → 쌍수 풀기.
#
# **스틱스는 두 자루가 «정상 상태»다.** `ls_relic.js` 가 가호 지급 때
# `item replace entity … weapon.offhand` 로 두 번째 칼을 같이 준다.
# 그래서 평균도, 조건도 전부 «두 자루 기준»으로 잡는다.
#
# ⚠️ 여기 오기까지 두 번 틀렸다. 남겨두는 이유는 다음에 같은 길로 안 가기 위해서다:
#   ① 「찌르기의 쌍수 조건은 어차피 안 통할 것」이라 보고 평균을 «조건 없는 타»에만 맞췄다
#      → 두 번째 칼이 이미 지급되고 있었으므로 조건은 통한다. 그대로 뒀다면 DPS 가 6% 낮았다.
#   ② 손 번갈이를 끄려고 `two_handed: true` 로 바꿨다
#      → 보조손 칸이 막혀 «왼손에 한 자루»라는 설계 자체가 불가능해졌다. 정반대로 간 것이다.
#   교훈: **`ls_relic.js` 의 지급 코드를 먼저 읽을 것.** 무기 파일만 보면 전제를 놓친다.

# ── 애니메이션이 «두 번씩» 나오던 것 — 원인을 코드에서 찾았다 (2026-08-10) ──
# 두 번 헛짚은 뒤 `PlayerAttackHelper` 를 직접 열어봤다. 규칙은 이렇다:
#
#   shouldAttackWithOffHand(p, combo) = isDualWielding(p) && combo % 2 == 1
#   selectAttack(combo, attrs, p, isOffHand):
#       ① 조건(conditions)으로 attacks 를 «먼저 거른다» — 손마다 목록이 달라진다
#       ② 쌍수면 인덱스는 **combo / 2**, 아니면 combo
#       ③ 걸러진 목록에서 그 인덱스를 % 로 돌린다
#
# ②가 핵심이다. **주손과 보조손이 «같은 인덱스»를 쓴다** — 한 항목을 양손이 한 번씩
# 수행하는 게 베터컴뱃의 쌍수 모델이고, 그래서 같은 동작이 두 번 보인다. 버그가 아니다.
#
# ❌ 그래서 전 항목 `MAIN_HAND_ONLY` 는 통할 수가 없었다 — 보조손 목록이 **비어서**
#    선택이 실패하고 콤보가 안 넘어갔다(1번만 무한 반복).
#
# ✅ 해법은 ①을 쓰는 것이다. **주손 전용과 보조손 전용을 섞으면 두 목록의 «내용»이 달라진다.**
#      주손 목록 = [가로R, 교차, 찌르기]  (3)
#      보조손 목록 = [가로L, 풀기]        (2)
#    n번째 스윙 = (n%2 손) · (n/2 % 목록길이) 이므로 실제로 나가는 순서는
#      가로R → 가로L → 교차 → 풀기 → 찌르기 → 가로L → 가로R → 풀기 → 교차 …
#    **같은 동작이 연달아 두 번 나오지 않는다.** 3과 2라 주기는 6이고, 그만큼 덜 뻔해진다.
#
# ⚠️ **빈도가 손마다 달라져서 평균 계산이 바뀐다.** 주손 셋은 각각 전체의 1/6,
#    보조손 둘은 각각 1/4 를 차지한다:
#      (가로R + 교차 + 찌르기)/6 + (가로L + 풀기)/4 = 평균
#      (0.90 + 1.05 + 1.40)/6 + (0.95 + 1.0833)/4 = 0.5583 + 0.5083 = **1.0667** (옛값과 같다)
#
# ⚠️ **칼을 한 자루만 들면 콤보가 2타로 줄어든다.** 쌍수가 아니면 보조손 목록을 아예 안 쓰고,
#    주손 목록에서도 쌍수 조건인 찌르기가 빠져 [가로R, 교차] 만 남는다.
#    두 자루가 정상 상태이므로 감수한다 — 잃으면 `/relic` 로 다시 받는다.
MH = ["MAIN_HAND_ONLY"]
OH = ["OFF_HAND_ONLY"]
DAG = "bettercombat:dagger_slash"
write("assassin", {
    "range_bonus": -0.5,
    "two_handed": False,
    "category": "dagger",
    "attacks": [
        atk("one_handed_slash_horizontal_right", 0.90, sound=DAG, conditions=MH),
        atk("one_handed_slash_horizontal_left", 0.95, sound=DAG, conditions=OH),
        atk("dual_handed_slash_cross", 1.05, hitbox="VERTICAL_PLANE", angle=120, sound=DAG, conditions=MH),
        # ⚠️ 이 한 줄만 조건부다. 두 자루를 실제로 들었을 때만 나가고, 그때만 나가는 게 맞다 —
        #    «쌍수 찌르기»는 칼이 하나면 그림 자체가 성립하지 않는다.
        #    MAIN_HAND_ONLY 가 같이 붙은 이유: 이건 두 손을 한 번에 쓰는 동작이라
        #    주손·보조손 양쪽에서 나가면 같은 동작이 두 번 보인다.
        atk("dual_handed_stab", 1.40, hitbox="FORWARD_BOX", sound=DAG,
            conditions=["DUAL_WIELDING_SAME_CATEGORY", "MAIN_HAND_ONLY"]),
        atk("dual_handed_slash_uncross", 1.0833, sound=DAG, conditions=OH),
    ],
}, "주손3/보조손2 로 갈라 빈도 가중 평균 1.0667 = 옛값 → 유지")

# ── 이지스 — 2타 → 3타 ──
# 망치인데 «내려찍고 옆으로 후린다» 두 동작뿐이라 금방 질린다.
# 가로 둘로 시작해 **무거운 내려찍기로 마무리**하는 상승 구조로 바꾼다.
HAM, CLA = "bettercombat:hammer_slam", "bettercombat:claymore_swing"
write("guardian", {
    "range_bonus": 0.5,
    "pose": "bettercombat:pose_two_handed_heavy",
    "two_handed": True,
    "category": "hammer",
    "attacks": [
        atk("two_handed_slash_horizontal_right", 0.85, angle=130, sound=CLA),
        atk("two_handed_slash_horizontal_left", 0.95, angle=130, sound=CLA),
        atk("two_handed_slam_heavy", 1.35, hitbox="VERTICAL_PLANE", angle=90, upswing=0.55, sound=HAM),
    ],
}, "원래 (1.2+0.9)/2 = 1.05 → 유지")

# ── 크라토스 — 2타 → 3타 ──
# 도끼는 «가로로 두 번 후리고 세로로 쪼갠다»가 제일 도끼답다.
AXE = "bettercombat:axe_slash"
write("pioneer", {
    "range_bonus": 0.5,
    "pose": "bettercombat:pose_two_handed_heavy",
    "two_handed": True,
    "category": "axe",
    "attacks": [
        atk("two_handed_slash_horizontal_left", 0.95, angle=140, sound=AXE),
        atk("two_handed_slash_horizontal_right", 1.05, angle=140, sound=AXE),
        atk("two_handed_slash_vertical_right", 1.30, hitbox="VERTICAL_PLANE", angle=100, upswing=0.6, sound=AXE),
    ],
}, "원래 (1.25+0.95)/2 = 1.10 → 유지")

# ── 헤스페로스(헤카테) — 2타 → 3타 ──
# 부모 프리셋(scythe)이 가로 베기 둘뿐이라 좌우로 왔다갔다 하는 것만 보였다.
# **360° 회전**을 마무리로 넣는다 — 낫이 한 바퀴 도는 그림이 이 무기의 정체성에 맞고,
# 저주를 «주변 전부»에 새기는 헤카테의 역할과도 맞물린다(회전은 360° 판정이다).
SCY = "bettercombat:scythe_slash"
write("hecate", {
    "range_bonus": 0.5,
    "pose": "bettercombat:pose_two_handed_scythe",
    "two_handed": True,
    "category": "scythe",
    "attacks": [
        atk("two_handed_slash_horizontal_right", 0.90, sound=SCY),
        atk("two_handed_slash_horizontal_left", 0.95, sound=SCY),
        atk("two_handed_spin", 1.15, angle=360, sound=SCY),
    ],
}, "원래 (1+1)/2 = 1.0 → 유지")

# ── 게볼그 — 3타 → 4타 ──
# 찌르기 두 번 사이에 가로 베기가 하나뿐이라 «창» 특유의 리듬이 짧았다.
write("lancer", {
    "range_bonus": 1.5,
    "pose": "bettercombat:pose_two_handed_polearm",
    "two_handed": True,
    "category": "spear",
    "attacks": [
        atk("two_handed_stab_right", 1.00, hitbox="FORWARD_BOX", angle=0,
            sound="minecraft:item.trident.riptide_1"),
        atk("two_handed_slash_horizontal_right", 0.90,
            sound="minecraft:item.trident.riptide_2"),
        atk("two_handed_slash_horizontal_left", 0.95,
            sound="minecraft:item.trident.riptide_3"),
        atk("two_handed_stab_left", 1.15, hitbox="FORWARD_BOX", angle=0, upswing=0.55,
            sound="minecraft:item.trident.riptide_1"),
    ],
}, "원래 (1.0+0.9+1.1)/3 = 1.0 → 유지")

# ── 아드라스테이아(네메시스) — 부분 병합을 걷어냈다 ──
# 원래는 `attacks: [{angle:170}, {}, {angle:170}]` 로 **인덱스별 부분 병합**에 기대고 있었다.
# 그 동작은 아직 인게임에서 확인된 적이 없다(TEST-PLAN 2-C). 여기서 세 타를 전부
# 적어버리면 그 미검증 의존이 통째로 사라진다. 배율은 claymore 원본 그대로다.
write("nemesis", {
    "range_bonus": 1.0,
    "pose": "bettercombat:pose_two_handed_sword",
    "two_handed": True,
    "category": "claymore",
    "attacks": [
        atk("two_handed_slash_horizontal_right", 0.75, angle=170,
            sound="bettercombat:claymore_swing"),
        atk("two_handed_stab_left", 1.00, hitbox="FORWARD_BOX", angle=0,
            sound="bettercombat:claymore_stab"),
        atk("two_handed_slam", 1.25, hitbox="VERTICAL_PLANE", angle=170,
            sound="bettercombat:claymore_slam"),
    ],
}, "claymore 원본 (0.75+1.0+1.25)/3 = 1.0 → 유지")

# ── 펠리온(케이론) — 상속을 걷어냈다 ──
# battlestaff 6타를 그대로 쓰되 파일에 전부 적는다. `pose` 만 덮어쓰던 구조는
# 동작이 확인됐지만(TEST-PLAN 2-C), 이제 나머지가 전부 명시라 여기만 상속으로 두면
# 「이 파일은 어떤 콤보인가」를 보려고 모드 jar 을 열어야 한다.
STF = "bettercombat:staff_slash"
write("chiron", {
    "range_bonus": 0.5,
    "pose": "bettercombat:pose_two_handed_polearm",
    "two_handed": True,
    "category": "battlestaff",
    "attacks": [
        atk("two_handed_slash_horizontal_right", 0.8, angle=180, sound=STF),
        atk("two_handed_slash_horizontal_left", 1.0, angle=180, sound=STF),
        atk("two_handed_spin", 1.2, angle=360, sound="bettercombat:staff_spin"),
        atk("two_handed_slam", 1.4, hitbox="VERTICAL_PLANE", angle=160, sound="bettercombat:staff_slam"),
        atk("two_handed_stab_left", 0.8, hitbox="FORWARD_BOX", angle=0, sound="bettercombat:staff_stab"),
        atk("two_handed_stab_right", 0.8, hitbox="FORWARD_BOX", angle=0, sound="bettercombat:staff_stab"),
    ],
}, "battlestaff 원본 평균 1.0 → 유지")
