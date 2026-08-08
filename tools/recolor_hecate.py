# -*- coding: utf-8 -*-
"""헤카테(헤스페로스) 텍스처를 부위별로 다시 칠한다 — 자루=남색 / 날의 호=금색 / 나머지=흰색.

왜 스크립트인가:
    텍스처가 UV 아틀라스라 «자루의 금색»과 «날의 금색»이 같은 픽셀 값이다. 색만 보고
    바꾸면 둘이 같이 물든다. 그래서 모델 JSON 의 면 UV 를 읽어 **어느 픽셀이 어느 부위
    것인지** 먼저 가른 뒤, 부위별로 다른 램프에 얹는다.

    Blood Scythe(Linaryx, MIT) 26요소를 셋으로 나눴을 때 **세 부위가 공유하는 픽셀이 하나도
    없다**(호 437 · 자루 304 · 나머지 244). 그래서 이 방식이 깨끗하게 먹힌다.

부위 (2026-08-09 유저 지시):
    자루   — 장대 두 토막 (0·1)                     → 어두운 남색
    호     — 굽은 날 본체와 앞뒤 칼끝, 옆 장식띠     → 금색
    나머지 — 소켓·브래킷과 뒤쪽 짧은 돌기           → 흰색

램프를 쓰는 이유:
    한 부위를 단색으로 칠하면 텍스처에 원래 있던 밝고 어두운 띠가 사라져 민무늬 막대가 된다.
    부위 «안에서» 명도를 정규화해 램프에 얹으면 그 결이 「밝은 금색 / 어두운 금색」으로 남는다.
    부위마다 따로 정규화하는 게 중요하다 — 텍스처 전체 범위로 펴면 좁은 부위의 대비가 눌린다.

사용 (PowerShell 기준 — 저장소 루트에서):
    python tools\\recolor_hecate.py

    항상 **칠하지 않은 원본**(`tools/assets/hecate_original.png`)에서 읽어 배포본에 쓴다.
    그래서 몇 번을 돌려도 결과가 같다 — 램프 색이나 부위 집합을 고치고 다시 돌리면 된다.
    (제자리에서 고치는 방식이면 두 번째 실행이 이미 칠해진 것을 또 칠해 색이 어긋난다.)

    --from=<png>  다른 원본에서 읽기      --out=<png>  다른 곳에 쓰기      --dry  저장 안 함
"""
import io
import json
import sys

from PIL import Image

MODEL = 'moddev/lsrelics/src/main/resources/assets/lsrelics/models/item/hecate.json'
TEX = 'moddev/lsrelics/src/main/resources/assets/lsrelics/textures/item/hecate.png'
# 칠하기 «전» 원본. Blood Scythe 를 유물 팔레트로 옮겨놓은 상태이고, 부위 색분리는 안 된 것.
# 리소스 폴더 밖에 둔다 — 안에 두면 쓰지도 않는 두 번째 텍스처가 jar 에 실린다.
SOURCE = 'tools/assets/hecate_original.png'

# ── 부위 (element 번호) ──
SHAFT = {0, 1}
ARC = {2, 3, 4, 8, 9, 10, 14, 15, 16, 17, 18, 22, 23, 24, 25}
# 나머지(소켓·브래킷·뒤 돌기)는 위 둘을 뺀 전부 — 요소가 늘어도 자동으로 여기 들어온다.

# ── 램프 ──
# 세 색 다 기존 유물 9장에서 뽑은 팔레트를 따른다(docs/ASSETS.md §9):
# 금색 #EEE74F/#E4D042 · 남색 #495987 계열 · 흰색 #FCFCFF/#D6DDEC.
NAVY = [(0x0B, 0x0E, 0x1C), (0x16, 0x1C, 0x35), (0x1E, 0x26, 0x44),
        (0x2A, 0x34, 0x5B), (0x3A, 0x47, 0x78)]
GOLD = [(0x4A, 0x42, 0x12), (0x81, 0x73, 0x20), (0xBB, 0xA9, 0x33),
        (0xE4, 0xD0, 0x42), (0xF2, 0xE9, 0x62)]
# 흰색 끝을 #FFFFFF 로 둔다 — 여기를 낮추면 금색 옆에서 계속 «회색»으로 읽힌다.
WHITE = [(0xB4, 0xBA, 0xC8), (0xCA, 0xD0, 0xDB), (0xE2, 0xE6, 0xEE),
         (0xF4, 0xF6, 0xFA), (0xFF, 0xFF, 0xFF)]

RAMP = {'shaft': NAVY, 'arc': GOLD, 'rest': WHITE}


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def ramp(colors, t):
    """0..1 을 색 목록 위의 한 점으로. 구간 안은 선형 보간."""
    t = max(0.0, min(1.0, t))
    pos = t * (len(colors) - 1)
    i = min(int(pos), len(colors) - 2)
    f = pos - i
    a, b = colors[i], colors[i + 1]
    return tuple(int(round(a[j] + (b[j] - a[j]) * f)) for j in range(3))


def face_area(e, name):
    """그 면이 3D 에서 실제로 갖는 넓이. 0 이면 화면에 아무것도 안 나온다."""
    dx = abs(e['to'][0] - e['from'][0])
    dy = abs(e['to'][1] - e['from'][1])
    dz = abs(e['to'][2] - e['from'][2])
    return {'north': dx * dy, 'south': dx * dy,
            'east': dz * dy, 'west': dz * dy,
            'up': dx * dz, 'down': dx * dz}[name]


def group_of(i):
    return 'shaft' if i in SHAFT else ('arc' if i in ARC else 'rest')


def ownership(model, w, h):
    """픽셀 -> 부위 이름. 모델의 모든 면 UV 를 텍스처 좌표로 펴서 도장을 찍는다.

    ⚠️ **넓이 0인 면은 세지 않는다.** element 14~18 은 두께가 0인 평면인데
    (`from.x == to.x`), 그 퇴화면 넷의 UV 가 전부 `[0,0,0,x]` — 텍스처 **0번 열**을 가리킨다.
    블록벤치가 안 쓰는 면에 남긴 쓰레기 값이다. 그런데 0번 열은 자루(element 0)의 면이
    쓰는 자리라, 이걸 세면 「자루와 날이 픽셀을 공유한다」는 가짜 충돌이 생기고
    그 픽셀을 자루로 넘기면 **날 뒷날이 통째로 남색이 된다.** 실제로 한 번 그렇게 나왔다.
    """
    own = {}
    for i, e in enumerate(model['elements']):
        g = group_of(i)
        for fname, face in (e.get('faces') or {}).items():
            if 'uv' not in face or face_area(e, fname) <= 1e-6:
                continue
            u0, v0, u1, v1 = face['uv']
            # ※ UV 는 항상 0..16 공간이다. `texture_size` 는 블록벤치 힌트일 뿐 바닐라는
            #   0..16 을 텍스처 «전체» 에 대응시킨다 — 그래서 여기서도 w/16 로 편다.
            x0, x1 = sorted((u0 / 16 * w, u1 / 16 * w))
            y0, y1 = sorted((v0 / 16 * h, v1 / 16 * h))
            xs, ys = int(round(x0)), int(round(y0))
            xe, ye = max(int(round(x1)), xs + 1), max(int(round(y1)), ys + 1)
            for y in range(ys, ye):
                for x in range(xs, xe):
                    if 0 <= x < w and 0 <= y < h:
                        own.setdefault((x, y), set()).add(g)
    return own


def main():
    opts = {a.split('=')[0]: (a.split('=')[1] if '=' in a else True)
            for a in sys.argv[1:] if a.startswith('--')}
    model = json.load(io.open(MODEL, encoding='utf-8'))
    im = Image.open(opts.get('--from') or SOURCE).convert('RGBA')
    w, h = im.size
    px = im.load()
    own = ownership(model, w, h)

    # 부위별 명도 범위를 «그 부위 안에서» 정규화한다.
    pools = {k: [] for k in RAMP}
    pixels = {}
    for (x, y), groups in own.items():
        c = px[x, y]
        if not c[3]:
            continue
        # 셋은 서로 픽셀을 안 나눠 쓴다(확인함). 그래도 혹시 겹치면 자루 > 호 > 나머지 순.
        g = 'shaft' if 'shaft' in groups else ('arc' if 'arc' in groups else 'rest')
        pixels[(x, y)] = g
        pools[g].append(lum(c))
    span = {k: (min(v), max(v)) for k, v in pools.items() if v}

    for (x, y), g in pixels.items():
        c = px[x, y]
        lo, hi = span[g]
        px[x, y] = ramp(RAMP[g], (lum(c) - lo) / max(1e-6, hi - lo)) + (c[3],)

    out = opts.get('--out') or TEX
    if opts.get('--dry'):
        print('dry run — 저장 안 함')
    else:
        im.save(out)
    print('자루(남색) %d px · 호(금색) %d px · 나머지(흰색) %d px  -> %s'
          % (len(pools['shaft']), len(pools['arc']), len(pools['rest']), out))


if __name__ == '__main__':
    main()
