# -*- coding: utf-8 -*-
"""케이론「펠리온」 — 봉 모델·텍스처를 코드로 굽는다.

── 왜 리소스팩을 안 뒤졌나 (2026-08-10) ──
`ASSETS.md` §10 의 교훈은 「밖을 뒤지기 전에 이미 허락받은 팩부터 열어라」였다.
그런데 **이 무기는 그 규칙의 예외다** — 형태가 거의 없다. 장대 하나에 마디 몇 개다.
라이선스도, 다운로드도, 「우라니아 지팡이와 안 갈린다」는 위험도 통째로 사라지고,
**우리가 원하는 대로 정확히 만들 수 있다** — 팩에서 고르면 늘 «비슷한 것»이다.

── 2차: 흰색·금색·남색 (2026-08-10, 유저 스케치) ──
1차는 물푸레나무 민짜 봉이었다. 유저가 그림으로 다시 잡아줬다:
**양 끝에 금색 구 · 안쪽에 금색 마디 둘 · 가운데는 남색 · 나머지는 흰색.**

이게 맞다. **네메시스(아드라스테이아)가 이미 흰색·금색·남색**이고, 「별의 유물」이
나무 막대기면 열둘 중 이것만 격이 떨어져 보인다. `ASSETS.md` §11 에 적어둔
「직접 만들면 나머지와 나란히 섰을 때 초라해 보인다」는 위험이 정확히 그 지점이었다.

── 로어는 그대로 산다 ──
아킬레우스의 창 자루가 **펠리온산 물푸레나무**였고 케이론이 직접 잘라 준 것이다.
장대의 «형태»가 그 이야기이고, 색은 별의 유물이라는 «격»이다. 둘은 안 싸운다.

── 우라니아(Divine Staff)와 무엇으로 갈리나 ──
그쪽은 «금빛 일색 + 머리에 큰 장식»이다. 이쪽은 **머리 장식이 없고 몸통이 삼색**이다.
실루엣에서 눈이 가는 곳이 윗머리인데, 거기가 «장식»이 아니라 «구»라 다른 물건으로 읽힌다.

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

# ── 팔레트 — 네메시스와 같은 배열 ──
WHITE_L = (0xF2, 0xF2, 0xF6, 255)
WHITE_M = (0xD8, 0xDA, 0xE4, 255)
WHITE_D = (0xB2, 0xB6, 0xC4, 255)
GOLD_L = (0xF6, 0xDA, 0x8E, 255)
GOLD_M = (0xE0, 0xB5, 0x52, 255)
GOLD_D = (0xA6, 0x7C, 0x28, 255)
NAVY_L = (0x3A, 0x4C, 0x8C, 255)
NAVY_M = (0x2A, 0x37, 0x68, 255)
NAVY_D = (0x18, 0x20, 0x42, 255)

# 텍스처 16×16 을 세로 4칸으로 쪼갠다.
#   x 0~3 흰색 | x 4~7 금색 | x 8~11 남색 | x 12~15 짙은 금색(구의 아랫면)
U_WHITE = [0, 0, 4, 16]
U_GOLD = [4, 0, 8, 16]
U_NAVY = [8, 0, 12, 16]
U_GOLD_D = [12, 0, 16, 16]


def build_texture():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            if x < 4:
                c = WHITE_L if y % 6 else (WHITE_D if y % 3 == 0 else WHITE_M)
            elif x < 8:
                c = GOLD_L if y % 5 == 0 else (GOLD_D if y % 5 == 2 else GOLD_M)
            elif x < 12:
                c = NAVY_L if y % 5 == 0 else (NAVY_D if y % 5 == 2 else NAVY_M)
            else:
                c = GOLD_D if y % 3 else GOLD_M
            px[x, y] = c
    img.save(TEX)
    return TEX


def box(half, y0, y1, side_uv, cap_uv, rot=None):
    a, b = 8 - half, 8 + half
    el = {
        "from": [a, y0, a],
        "to": [b, y1, b],
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


def pillar(half, y0, y1, uv, cap=None):
    """정사각 기둥 + 45° 돌린 같은 기둥 = **팔각 근사**.

    두 상자는 «관통»할 뿐 같은 방향의 면이 겹치지 않으므로 z-fighting 이 없다
    (`tools/fix_zfight.py` 가 정리한 것과 같은 판정 — 맞닿음·관통은 안 싸운다).
    """
    cap = cap or uv
    return [box(half, y0, y1, uv, cap), box(half, y0, y1, uv, cap, rot=45)]


def orb(cy):
    """금색 구. 마디 셋을 쌓아 «둥글게» 근사한다 — 마크 모델에 구는 없다.

    가운데를 제일 넓게(1.5) 두고 위아래를 좁혀야(1.0) 실루엣이 공으로 읽힌다.
    같은 폭으로 쌓으면 그냥 «굵은 마디»다.
    """
    return (pillar(1.00, cy - 1.60, cy - 0.95, U_GOLD, U_GOLD_D)
            + pillar(1.50, cy - 0.95, cy + 0.95, U_GOLD, U_GOLD_D)
            + pillar(1.00, cy + 0.95, cy + 1.60, U_GOLD, U_GOLD_D))


# ── 치수 ──
# ⚠️ 마인크래프트 아이템 모델은 좌표를 **−16 ~ 32** 까지 허용한다. 0~16 에 가두면
#    「길다」가 안 나온다 — 두께가 1 인데 길이가 15 면 비율이 1:15 뿐이다.
#    여기서는 −6 ~ 22 (28칸) 를 써서 1:28 로 뽑는다. 소림 봉의 비율에 가깝다.
Y_BOT, Y_TOP = -6.0, 22.0
ORB_B, ORB_T = -4.4, 20.4          # 구의 중심 (양 끝에서 1.6 안쪽)
BAND_B0, BAND_B1 = 2.0, 3.4        # 아래 금색 마디
BAND_T0, BAND_T1 = 12.6, 14.0      # 위 금색 마디
SHAFT = 0.55                       # 자루 반두께
SLEEVE = 0.66                      # 남색 구간 (자루보다 살짝 굵게 = 감싼 느낌)
BAND = 0.82                        # 금색 마디


def build_model():
    els = []
    # 자루 — 끝에서 끝까지 흰색으로 한 줄. 위에 남색·금색이 «덧씌워진다».
    els += pillar(SHAFT, ORB_B, ORB_T, U_WHITE)
    # 가운데 남색
    els += pillar(SLEEVE, BAND_B1, BAND_T0, U_NAVY)
    # 남색 구간의 양 끝을 잡아주는 금색 마디 둘
    els += pillar(BAND, BAND_B0, BAND_B1, U_GOLD, U_GOLD_D)
    els += pillar(BAND, BAND_T0, BAND_T1, U_GOLD, U_GOLD_D)
    # 양 끝 금색 구
    els += orb(ORB_B)
    els += orb(ORB_T)

    model = {
        "credit": "Last Stardust — 케이론 「펠리온」. tools/make_chiron_staff.py 로 생성됨.",
        "parent": "minecraft:item/handheld",
        "textures": {"0": "lsrelics:item/chiron", "particle": "lsrelics:item/chiron"},
        "elements": els,
        # 우라니아(sage.json)의 값을 기준으로 잡았다 — 같은 «장대»라 손에 걸리는 자리가 같다.
        # ⚠️ 길이를 28칸으로 늘렸으므로 3인칭 배율은 오히려 **낮춘다**(2.6 → 1.6).
        #    안 낮추면 3인칭에서 봉이 땅을 뚫고 화면을 가로지른다.
        "display": {
            "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 1.5, 2.5], "scale": [1.6, 1.6, 1.6]},
            "thirdperson_lefthand":  {"rotation": [0, 180, 0], "translation": [0, 1.5, 2.5], "scale": [1.6, 1.6, 1.6]},
            # ⚠️ 1인칭은 «일부러» 작게 둔다. 헤카테에서 「1인칭 시야를 가린다」가 이미 나왔다.
            "firstperson_righthand": {"rotation": [0, -180, 0], "translation": [0, 3, 1], "scale": [1.0, 1.0, 1.0]},
            "firstperson_lefthand":  {"rotation": [0, -180, 0], "translation": [0, 3, 1], "scale": [1.0, 1.0, 1.0]},
            # 인벤토리 아이콘 — 대각선으로 눕혀야 28칸이 칸 안에 들어간다.
            "gui":    {"rotation": [0, 0, -45], "scale": [0.62, 0.62, 0.62]},
            "ground": {"rotation": [0, 0, -45], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed":  {"rotation": [0, -180, -45], "scale": [0.9, 0.9, 0.9]},
            "head":   {"rotation": [0, 0, 45], "translation": [0, 13, 0], "scale": [0.9, 0.9, 0.9]},
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
