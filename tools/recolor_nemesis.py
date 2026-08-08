# -*- coding: utf-8 -*-
"""네메시스(아드라스테이아) 텍스처를 유물 팔레트로 옮긴다 — 칼날=흰색 / 가드=금색 / 그립=남색.

── 왜 헤카테와 방법이 다른가 ──
    `recolor_hecate.py` 는 **모델의 면 UV** 를 읽어 픽셀 주인을 갈랐다. Blood Scythe 는
    자루와 날이 같은 색(올리브)이라 색만 보고는 나눌 수 없었기 때문이다.

    Grand Claymore 는 반대다. **색이 16개뿐이고 이미 세 덩어리로 갈려 있다** —
    푸른 회백(칼날) · 따뜻한 금갈(가드·폼멜) · 어두운 보라회(그립).
    렌더로 확인했더니 부위와 정확히 일치했다. 그래서 UV 를 볼 이유가 없다.
    색으로 가르는 쪽이 코드도 짧고, 모델을 고쳐도 안 깨진다.

── 가르는 규칙 ──
    색상 15~75° 이고 채도 15% 초과   -> 금 (가드·폼멜)
    그 외에 명도 50% 이상            -> 흰 (칼날)
    나머지                           -> 남 (그립)

램프를 쓰는 이유는 헤카테와 같다 — 단색으로 칠하면 원래의 밝고 어두운 결이 사라져
민무늬가 된다. 부위 «안에서» 명도를 정규화해야 좁은 부위의 대비가 안 눌린다.

사용 (저장소 루트에서):
    python tools\\recolor_nemesis.py

    항상 원본(`tools/assets/nemesis_original.png`)에서 읽어 배포본에 쓴다 — 몇 번을 돌려도
    결과가 같다. 램프만 고치고 다시 돌리면 된다.
"""
import colorsys
import io
import sys

from PIL import Image

SOURCE = 'tools/assets/nemesis_original.png'
TEX = 'moddev/lsrelics/src/main/resources/assets/lsrelics/textures/item/nemesis.png'

# ── 램프 (docs/ASSETS.md §9 의 유물 팔레트) ──
# 대표값: 금색 #EEE74F/#E4D042 · 남색 #495987/#889CCA · 흰색 #FCFCFF/#D6DDEC
WHITE = [(0x8E, 0x97, 0xAB), (0xA9, 0xB2, 0xC4), (0xC6, 0xCE, 0xDC),
         (0xE4, 0xE9, 0xF2), (0xFC, 0xFC, 0xFF)]
GOLD = [(0x7A, 0x6A, 0x22), (0xB3, 0x9A, 0x32), (0xD8, 0xC4, 0x3E),
        (0xE4, 0xD0, 0x42), (0xEE, 0xE7, 0x4F)]
# 그립은 좁고 어두운 부위다. 흰·금과 같은 폭으로 펴면 손잡이가 하늘색으로 떠버린다 —
# 위쪽을 #495987 에서 끊어 「어두운 남색 가죽」으로 읽히게 한다.
NAVY = [(0x1B, 0x21, 0x3A), (0x27, 0x30, 0x52), (0x35, 0x41, 0x6B),
        (0x3F, 0x4D, 0x7B), (0x49, 0x59, 0x87)]

RAMP = {'white': WHITE, 'gold': GOLD, 'navy': NAVY}


def part(rgb):
    r, g, b = rgb
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    hue = h * 360
    if 15 <= hue <= 75 and s > 0.15:
        return 'gold'
    return 'white' if l >= 0.5 else 'navy'


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def ramp(colors, t):
    t = max(0.0, min(1.0, t))
    pos = t * (len(colors) - 1)
    i = min(int(pos), len(colors) - 2)
    f = pos - i
    a, b = colors[i], colors[i + 1]
    return tuple(int(round(a[j] + (b[j] - a[j]) * f)) for j in range(3))


def main():
    opts = {a.split('=')[0]: (a.split('=')[1] if '=' in a else True)
            for a in sys.argv[1:] if a.startswith('--')}
    im = Image.open(opts.get('--from') or SOURCE).convert('RGBA')
    w, h = im.size
    px = im.load()

    # 부위별 명도 범위를 «그 부위 안에서» 정규화한다.
    pools = {k: [] for k in RAMP}
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3]:
                pools[part(c[:3])].append(lum(c[:3]))
    span = {k: (min(v), max(v)) for k, v in pools.items() if v}

    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if not c[3]:
                continue
            g = part(c[:3])
            lo, hi = span[g]
            px[x, y] = ramp(RAMP[g], (lum(c[:3]) - lo) / max(1e-6, hi - lo)) + (c[3],)

    out = opts.get('--out') or TEX
    if opts.get('--dry'):
        print('dry run — 저장 안 함')
    else:
        im.save(out)
    print('칼날(흰) %d px · 가드(금) %d px · 그립(남) %d px  -> %s'
          % (len(pools['white']), len(pools['gold']), len(pools['navy']), out))


if __name__ == '__main__':
    main()
