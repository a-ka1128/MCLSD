# -*- coding: utf-8 -*-
"""케이론「펠리온」 — 봉 모델·텍스처를 코드로 굽는다.

── 왜 리소스팩을 안 뒤졌나 (2026-08-10) ──
`ASSETS.md` §10 의 교훈은 「밖을 뒤지기 전에 이미 허락받은 팩부터 열어라」였다.
그런데 **이 무기는 그 규칙의 예외다** — 유저가 원한 그림이 소림 승려가 드는
**아무 장식 없는 나무 봉**이고, 그건 대검·낫·활과 달리 **형태가 거의 없다.**
원통 하나에 금속 마감 둘, 손잡이 감김 둘. 열 개 남짓한 상자로 끝난다.

라이선스도, 다운로드도, 「우라니아 지팡이와 안 갈린다」는 위험도 통째로 사라진다.
**그리고 우리가 원하는 대로 정확히 만들 수 있다** — 팩에서 고르면 늘 «비슷한 것»이다.

── 로어가 이 형태를 부른다 ──
아킬레우스의 창 자루는 **펠리온산 물푸레나무**였고, 케이론이 직접 잘라 준 것이다.
가공하지 않은 물푸레나무 장대 — **그게 신화 그대로다.**

── 우라니아(Divine Staff)와 무엇으로 갈리나 ──
그쪽은 «길고 가늘고 금빛이며 머리에 큰 장식»이다. 이쪽은 **머리 장식이 아예 없다.**
실루엣에서 눈이 가는 곳이 «윗머리»인데 거기가 비어 있으면 다른 물건으로 읽힌다.

실행:
    python tools/make_chiron_staff.py
"""
import io
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'moddev', 'lsrelics', 'src', 'main', 'resources', 'assets', 'lsrelics')
MODEL = os.path.join(ASSETS, 'models', 'item', 'chiron.json')
TEX = os.path.join(ASSETS, 'textures', 'item', 'chiron.png')

# ── 팔레트 ──
# 물푸레나무는 «창백한» 나무다. 참나무처럼 붉으면 곤봉이 아니라 몽둥이로 보인다.
WOOD_L = (0xE0, 0xD2, 0xB4, 255)   # 밝은 면 (빛 받는 쪽)
WOOD_M = (0xC6, 0xB3, 0x8E, 255)   # 중간
WOOD_D = (0x9E, 0x8A, 0x67, 255)   # 그늘 · 나뭇결
WRAP_L = (0x5E, 0x49, 0x33, 255)   # 손잡이 가죽
WRAP_D = (0x40, 0x30, 0x1F, 255)
BRZ_L = (0xA8, 0x84, 0x4E, 255)    # 청동 마감 — 유물 색 #B08D57 의 밝은 쪽
BRZ_M = (0x82, 0x63, 0x36, 255)
BRZ_D = (0x59, 0x42, 0x22, 255)

# 텍스처는 16x16 을 세로 4칸으로 쪼개 쓴다.
#   x 0~3  밝은 나무 | x 4~7  그늘 나무 | x 8~11 가죽 | x 12~15 청동
U_WOOD_L = [0, 0, 4, 16]
U_WOOD_D = [4, 0, 8, 16]
U_WRAP = [8, 0, 12, 16]
U_BRZ = [12, 0, 16, 16]


def build_texture():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x < 4:
                c = WOOD_L if (y % 5) else WOOD_M          # 5픽셀마다 결 한 줄
            elif x < 8:
                c = WOOD_M if (y % 4) else WOOD_D
            elif x < 12:
                c = WRAP_L if (y % 2) else WRAP_D          # 감긴 가죽 = 촘촘한 줄
            else:
                c = BRZ_L if y % 6 == 0 else (BRZ_D if y % 6 == 3 else BRZ_M)
            px[x, y] = c
    img.save(TEX)
    return TEX


def box(x0, y0, x1, y1, z0, z1, side_uv, cap_uv, rot=None):
    """세로 기둥 하나. 옆면은 side_uv, 위아래는 cap_uv 를 쓴다."""
    el = {
        "from": [x0, y0, z0],
        "to": [x1, y1, z1],
        "faces": {
            "north": {"uv": side_uv, "texture": "#0"},
            "south": {"uv": side_uv, "texture": "#0"},
            "west":  {"uv": side_uv, "texture": "#0"},
            "east":  {"uv": side_uv, "texture": "#0"},
            "up":    {"uv": cap_uv,  "texture": "#0"},
            "down":  {"uv": cap_uv,  "texture": "#0"},
        },
    }
    if rot is not None:
        el["rotation"] = {"angle": rot, "axis": "y", "origin": [8, 8, 8]}
    return el


def pillar(half, y0, y1, side_uv, cap_uv):
    """정사각 기둥 + 45° 돌린 같은 기둥 = **팔각**. 원형에 제일 가까운 근사다.

    두 상자는 «관통»할 뿐 같은 방향의 면이 겹치지 않으므로 z-fighting 이 없다
    (`tools/fix_zfight.py` 가 정리한 것과 같은 판정 — 맞닿음·관통은 안 싸운다).
    """
    a, b = 8 - half, 8 + half
    cap = [8 - half * 0.5, 8 - half * 0.5, 8 + half * 0.5, 8 + half * 0.5]
    cap = cap_uv  # 위아래는 작아서 결이 안 보인다 — 그냥 같은 영역을 쓴다
    return [
        box(a, y0, b, y1, a, b, side_uv, cap),
        box(a, y0, b, y1, a, b, side_uv, cap, rot=45),
    ]


def build_model():
    els = []
    # ── 자루 ── 가늘고 길게. 굵으면 「몽둥이」가 되고 가늘면 「지팡이」가 된다.
    els += pillar(0.52, 0.5, 15.5, U_WOOD_L, U_WOOD_D)
    # ── 양 끝 청동 마감 ── 나무만 있으면 장난감처럼 보인다. 여기만 금속이다.
    els += pillar(0.66, 0.3, 1.5, U_BRZ, U_BRZ)
    els += pillar(0.66, 14.5, 15.7, U_BRZ, U_BRZ)
    # ── 손잡이 감김 두 곳 ── 두 손으로 잡는 봉이라는 걸 알려주는 유일한 표식이다.
    els += pillar(0.62, 5.4, 6.7, U_WRAP, U_WRAP)
    els += pillar(0.62, 9.3, 10.6, U_WRAP, U_WRAP)

    model = {
        "credit": "Last Stardust — 케이론 「펠리온」. tools/make_chiron_staff.py 로 생성됨.",
        "parent": "minecraft:item/handheld",
        "textures": {"0": "lsrelics:item/chiron", "particle": "lsrelics:item/chiron"},
        "elements": els,
        # 우라니아(sage.json)의 값을 기준으로 잡았다 — 같은 «장대»라 손에 걸리는 자리가 같다.
        # 다만 3인칭은 조금 키웠다. 이 봉은 장식이 없어 작으면 그냥 막대기로 보인다.
        "display": {
            "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 0, 2.5], "scale": [2.6, 2.6, 2.6]},
            "thirdperson_lefthand":  {"rotation": [0, 180, 0], "translation": [0, 0, 2.5], "scale": [2.6, 2.6, 2.6]},
            # ⚠️ 1인칭은 «일부러» 작게 둔다. 헤카테에서 「1인칭 시야를 가린다」가 이미 나왔다.
            "firstperson_righthand": {"rotation": [0, -180, 0], "translation": [0, 4, 1], "scale": [1.6, 1.6, 1.6]},
            "firstperson_lefthand":  {"rotation": [0, -180, 0], "translation": [0, 4, 1], "scale": [1.6, 1.6, 1.6]},
            "gui":    {"rotation": [0, 0, -45], "scale": [1.05, 1.05, 1.05]},
            "ground": {"rotation": [0, 0, -45], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
            "fixed":  {"rotation": [0, -180, -45], "scale": [1.6, 1.6, 1.6]},
            "head":   {"rotation": [0, 0, 45], "translation": [0, 14, 0], "scale": [1.4, 1.4, 1.4]},
        },
    }
    io.open(MODEL, 'w', encoding='utf-8', newline='').write(
        json.dumps(model, indent=2, ensure_ascii=False) + '\n')
    return MODEL, len(els)


if __name__ == '__main__':
    t = build_texture()
    m, n = build_model()
    print('텍스처 ->', t)
    print('모델   ->', m, '(요소 %d개)' % n)
