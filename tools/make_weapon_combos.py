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

# ── 애니메이션이 «두 번씩» 나오는 것 — 그대로 둔다 (2026-08-10 유저 결정) ──
# 가로R·가로L → 가로R·가로L → 교차·교차 → 찌르기 → 풀기·풀기 로 한 동작이 두 번 보인다.
#
# 원인은 `PlayerAttackHelper` 에 있다:
#   shouldAttackWithOffHand(p, combo) = isDualWielding(p) && combo % 2 == 1
#   selectAttack: 조건으로 거른 목록에서 **쌍수면 인덱스 = combo / 2**
# **주손과 보조손이 같은 인덱스를 쓴다** — 한 항목을 양손이 한 번씩 수행하는 게
# 베터컴뱃의 쌍수 모델이다. **버그가 아니라 설계다.**
#
# 고칠 수는 있었다(주손 전용/보조손 전용을 섞어 두 목록의 «내용»을 다르게 만들면 된다).
# 다만 그러면 **손마다 빈도가 갈려** 평균이 산술평균이 아니게 되고, 칼을 한 자루만 들면
# 콤보가 2타로 줄어드는 구멍도 생긴다. 유저가 **원래 값 유지**를 택했다.
#
# ❌ 전 항목 `MAIN_HAND_ONLY` 는 어떤 경우에도 답이 아니다 — 보조손 목록이 **비어서**
#    선택이 실패하고 콤보가 1번에서 멈춘다. 위 규칙 ①이 그 이유를 설명한다.
DAG = "bettercombat:dagger_slash"
write("assassin", {
    "range_bonus": -0.5,
    "two_handed": False,
    "category": "dagger",
    "attacks": [
        atk("one_handed_slash_horizontal_right", 0.88, sound=DAG),
        atk("one_handed_slash_horizontal_left", 0.95, sound=DAG),
        atk("dual_handed_slash_cross", 1.02, hitbox="VERTICAL_PLANE", angle=120, sound=DAG),
        # ⚠️ 이 한 줄만 조건부다. 두 자루를 실제로 들었을 때만 나가고, 그때만 나가는 게 맞다 —
        #    «쌍수 찌르기»는 칼이 하나면 그림 자체가 성립하지 않는다.
        #    MAIN_HAND_ONLY 가 같이 붙은 이유: 이건 두 손을 한 번에 쓰는 동작이라
        #    주손·보조손 양쪽에서 나가면 같은 동작이 두 번 보인다.
        atk("dual_handed_stab", 1.40, hitbox="FORWARD_BOX", sound=DAG,
            conditions=["DUAL_WIELDING_SAME_CATEGORY", "MAIN_HAND_ONLY"]),
        atk("dual_handed_slash_uncross", 1.0833, sound=DAG),
    ],
}, "두 자루 기준 5타 평균 1.0667 = 옛 3타 평균 1.0667 → 유지")

# ── 이지스 ↔ 크라토스 «모션 맞바꿈» (2026-08-10 유저 요청) ──
# 둘의 동작을 통째로 바꿨다 — 애니메이션 · 각도 · upswing · 판정 · 소리까지.
#   이지스  ← 도끼 동작 (가로L → 가로R → **세로 쪼개기**, 140°/100°, axe_slash)
#   크라토스 ← 망치 동작 (가로R → 가로L → **무거운 내려찍기**, 130°/90°, hammer_slam)
#
# ⚠️ **damage_multiplier 만 각자 자리에 남긴다.** 배율까지 따라가면 평균이 서로 뒤바뀌어
#    (1.05 ↔ 1.10) 이지스 +4.8% · 크라토스 −4.5% 로 08-04 실측이 통째로 무너진다.
#    바꾼 것은 «어떻게 보이는가»뿐이고 «얼마나 아픈가»는 그대로다.
#
# 소리도 같이 옮겼다. **이 상태로 확정한다(2026-08-10 유저 결정).**
#   이지스 → axe_slash · 크라토스 → claymore_swing
# 마무리가 같아지면서 소리를 되돌릴 이유가 생겼지만, 들어보고 그대로 가기로 했다.
# ⚠️ 다음에 읽는 사람에게: 이건 «옮기다 만 것»이 아니라 고른 것이다. 무기 종류와
#    소리가 어긋나 보여도 되돌리지 말 것 — 되돌리려면 먼저 물어볼 것.
#
# ── 마무리를 둘 다 «세로 쪼개기»로 (2026-08-10 유저 결정) ──
# 크라토스의 `two_handed_slam_heavy` 가 «별로»라는 판단. 아틀라스와 같은
# `two_handed_slash_vertical_right` 로 맞췄다.
#
# ⚠️ 그래서 **둘의 콤보가 사실상 같아졌다** — 남은 차이는 좌우 순서(가로L→R vs 가로R→L)와
#    각도(140°/100° vs 130°/100°), 그리고 소리뿐이다. 「모션 맞바꿈」의 실질은 거의 사라졌고,
#    지금 둘을 가르는 건 **드는 자세**다(이지스 sword · 크라토스 heavy).
#
# ⚠️ `two_handed_slam_heavy` 는 이제 **아무도 안 쓴다.** 되살릴 자리가 생기면 여기부터 본다.
CLA = "bettercombat:claymore_swing"
AXE = "bettercombat:axe_slash"

write("guardian", {
    "range_bonus": 0.5,
    # ⚠️ 시험 중 (2026-08-10): heavy → sword.
    #    모션을 크라토스와 맞바꿔도 «드는 자세»가 둘 다 heavy 라 여전히 비슷해 보였다.
    #    이지스는 방패+검이니 sword 자세가 물건에도 더 맞는다.
    #    **인게임에서 어색하면 pose_two_handed_heavy 로 되돌린다 — 이 한 줄이 전부다.**
    "pose": "bettercombat:pose_two_handed_sword",
    "two_handed": True,
    "category": "hammer",
    "attacks": [
        atk("two_handed_slash_horizontal_left", 0.85, angle=140, sound=AXE),
        atk("two_handed_slash_horizontal_right", 0.95, angle=140, sound=AXE),
        atk("two_handed_slash_vertical_right", 1.35, hitbox="VERTICAL_PLANE", angle=100, upswing=0.6, sound=AXE),
    ],
}, "크라토스 동작 · 배율은 이지스 것 (평균 1.05 유지)")

write("pioneer", {
    "range_bonus": 0.5,
    "pose": "bettercombat:pose_two_handed_heavy",
    "two_handed": True,
    "category": "axe",
    "attacks": [
        atk("two_handed_slash_horizontal_right", 0.95, angle=130, sound=CLA),
        atk("two_handed_slash_horizontal_left", 1.05, angle=130, sound=CLA),
        atk("two_handed_slash_vertical_right", 1.30, hitbox="VERTICAL_PLANE", angle=100, upswing=0.6, sound=CLA),
    ],
}, "마무리는 아틀라스와 같은 세로 쪼개기 · 배율은 크라토스 것 (평균 1.10 유지)")

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
