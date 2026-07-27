# -*- coding: utf-8 -*-
"""블록벤치식 아이템 모델(JSON)을 그림으로 뽑는다.

왜 필요한가:
    lsrelics 의 유물 모델은 요소가 70개를 넘는 커스텀 모델이라, 게임을 켜지 않으면
    형태를 확인할 방법이 없었다. 그래서 모델을 고칠 때마다 "인겜에서 봐 달라"고
    부탁하거나, 아예 손을 못 댔다. 이 스크립트는 그 왕복을 없앤다.

무엇을 하고 무엇을 안 하나:
    한다  — 요소(직육면체)의 위치·크기·회전을 읽어 정투영으로 그린다.
            면 방향에 따라 명암을 주고, 뒤에 있는 면부터 칠해 앞뒤를 맞춘다.
    안 한다 — 텍스처 매핑. UV 를 입히지 않고 단색+명암으로만 그린다.
            형태를 보는 게 목적이라 오히려 이쪽이 읽기 쉽다.

사용:
    python tools/render_model.py <모델.json> [출력.png] [--angle 30,45] [--size 900]
"""
import json
import math
import sys
from PIL import Image, ImageDraw

# 면 방향별 밝기 — 위가 밝고 아래가 어두운, 흔한 3점 조명 근사
FACE_SHADE = {
    'up': 1.00, 'north': 0.80, 'east': 0.66,
    'south': 0.58, 'west': 0.48, 'down': 0.38,
}
BASE_RGB = (214, 176, 122)   # 금빛 — 유물 계열 색감
BG_RGB = (24, 26, 32)

# 직육면체의 8개 꼭짓점을 이루는 6면 (꼭짓점 인덱스 순서 = 시계방향)
FACES = {
    'down':  (0, 1, 3, 2), 'up':    (4, 6, 7, 5),
    'north': (0, 2, 6, 4), 'south': (1, 5, 7, 3),
    'west':  (0, 4, 5, 1), 'east':  (2, 3, 7, 6),
}


def corners(a, b):
    """from/to 두 점으로 8개 꼭짓점을 만든다."""
    return [(a[0] if not (i & 1) else b[0],
             a[1] if not (i & 4) else b[1],
             a[2] if not (i & 2) else b[2]) for i in range(8)]


def rot(p, axis, angle_deg, origin):
    """블록벤치 요소 회전 — 한 축, origin 기준."""
    if not angle_deg:
        return p
    t = math.radians(angle_deg)
    c, s = math.cos(t), math.sin(t)
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    if axis == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif axis == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + origin[0], y + origin[1], z + origin[2])


def project(p, yaw, pitch):
    """정투영. 화면 좌표 + 깊이를 돌려준다."""
    ya, pa = math.radians(yaw), math.radians(pitch)
    x, y, z = p
    x, z = x * math.cos(ya) - z * math.sin(ya), x * math.sin(ya) + z * math.cos(ya)
    y, z = y * math.cos(pa) - z * math.sin(pa), y * math.sin(pa) + z * math.cos(pa)
    return x, -y, z


def render(path, out, yaw=35.0, pitch=25.0, size=900):
    model = json.load(open(path, encoding='utf-8'))
    elements = model.get('elements', [])
    if not elements:
        print('요소가 없다:', path)
        return

    polys = []
    for el in elements:
        a, b = el['from'], el['to']
        r = el.get('rotation') or {}
        axis, ang = r.get('axis', 'y'), r.get('angle', 0)
        org = r.get('origin', [8, 8, 8])
        pts = [rot(p, axis, ang, org) for p in corners(a, b)]
        proj = [project(p, yaw, pitch) for p in pts]
        for name, idx in FACES.items():
            quad = [proj[i] for i in idx]
            depth = sum(q[2] for q in quad) / 4.0
            sh = FACE_SHADE[name]
            polys.append((depth, [(q[0], q[1]) for q in quad],
                          tuple(int(c * sh) for c in BASE_RGB)))

    xs = [p[0] for _, q, _ in polys for p in q]
    ys = [p[1] for _, q, _ in polys for p in q]
    w, h = max(xs) - min(xs), max(ys) - min(ys)
    span = max(w, h) or 1
    scale = (size * 0.86) / span
    ox = size / 2 - (min(xs) + w / 2) * scale
    oy = size / 2 - (min(ys) + h / 2) * scale

    img = Image.new('RGB', (size, size), BG_RGB)
    d = ImageDraw.Draw(img)
    # 뒤에서 앞으로 — 화가 알고리즘
    for _, quad, color in sorted(polys, key=lambda t: t[0]):
        d.polygon([(p[0] * scale + ox, p[1] * scale + oy) for p in quad],
                  fill=color, outline=(18, 18, 22))
    img.save(out)
    print('%s  요소 %d개  ->  %s' % (path, len(elements), out))


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    opts = {a.split('=')[0]: a.split('=')[1]
            for a in sys.argv[1:] if a.startswith('--') and '=' in a}
    src = args[0]
    dst = args[1] if len(args) > 1 else src.replace('.json', '.png')
    yaw, pitch = (float(v) for v in opts.get('--angle', '35,25').split(','))
    render(src, dst, yaw, pitch, int(opts.get('--size', 900)))
