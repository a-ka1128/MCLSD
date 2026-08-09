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
# 가로R → 가로L → 쌍수 교차 → 쌍수 풀기 → 쌍수 찌르기.
# 한 손 베기로 시작해 «양손이 열리는» 순서라, 쌍단검이 점점 살아나는 그림이 된다.
#
# ⚠️⚠️ **평균을 어디에 맞췄는지가 중요하다.**
# 마지막 찌르기에는 원래부터 `DUAL_WIELDING_SAME_CATEGORY` 조건이 붙어 있다.
# 스틱스는 아이템 **하나**이므로 보조손이 비어 이 조건은 **거의 확실히 안 통한다** —
# 즉 실제로 나가는 콤보는 조건 없는 것들뿐이었고, 08-04 실측(그리고 거기 맞춘
# relicScale 1.398)은 **그 «실제»를 잰 값**이다.
#
# 그래서 조건 없는 네 타의 평균을 **옛 조건 없는 두 타의 평균(0.9)에 정확히** 맞춘다.
#   0.80 + 0.85 + 0.92 + 1.03 = 3.60  →  평균 0.90  (옛 0.9 + 0.9 = 1.8 → 평균 0.90)
# 조건부 찌르기는 1.4 그대로 둔다 — 어차피 안 나가고, 나간다면 옛 값과 같아야 한다.
#
# ⚠️ 만약 인게임에서 **찌르기가 실제로 나온다면** 이 가정이 틀린 것이고,
#    그때는 다섯 타 전체 평균을 옛 1.0667 에 다시 맞춰야 한다(전부 ×1.0847).
DAG = "bettercombat:dagger_slash"
write("assassin", {
    "range_bonus": -0.5,
    "two_handed": False,
    "category": "dagger",
    "attacks": [
        atk("one_handed_slash_horizontal_right", 0.80, sound=DAG),
        atk("one_handed_slash_horizontal_left", 0.85, sound=DAG),
        atk("dual_handed_slash_cross", 0.92, hitbox="VERTICAL_PLANE", angle=120, sound=DAG),
        atk("dual_handed_slash_uncross", 1.03, sound=DAG),
        atk("dual_handed_stab", 1.40, hitbox="FORWARD_BOX", sound=DAG,
            conditions=["DUAL_WIELDING_SAME_CATEGORY", "MAIN_HAND_ONLY"]),
    ],
}, "조건 없는 4타 평균 0.90 = 옛 조건 없는 2타 평균 0.90 → 유지")

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
