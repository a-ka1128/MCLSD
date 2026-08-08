# -*- coding: utf-8 -*-
"""아이템 모델의 z-파이팅(겹친 면이 지직거리는 것)을 찾아 아주 조금씩 밀어 없앤다.

무엇이 문제인가:
    z-파이팅은 «겹친다»고 나는 게 아니라 **같은 평면에 같은 방향 면이 둘 있을 때** 난다.
    한 상자가 다른 상자에 파고든 것(결구)은 안쪽 면이 가려져서 멀쩡하고,
    두 상자가 맞대어 붙은 것(butt joint)도 서로 반대를 보므로 멀쩡하다.
    문제는 **나란히 놓인 마디들이 옆면 x 를 똑같이 쓸 때**다.

    Blood Scythe(헤스페로스)가 그 모양이다 — 26요소 중 대부분이 `x 6.75..8.75` 로 같고,
    곡선을 만들려고 마디를 겹쳐 놨다. 겹친 자리마다 옆면 두 장이 같은 깊이에서 싸운다.
    (리라는 반대로 «x 를 공유하는 바닥 조각»이 원인이었다 — 같은 병이다.)

무엇을 하나:
    1. 회전을 적용한 실제 면끼리 «같은 방향 · 같은 평면 · 실제로 겹침» 을 찾는다.
    2. 그 관계를 충돌 그래프로 보고 색칠한다(그리디).
    3. 색마다 다른 «밀어넣기» 를 준다 — 보통 상자는 양옆을 안으로, 두께 0인 평면은 통째로 이동.

    밀어넣는 양은 0.01칸 단위다. **1/1600 블록이라 눈에 안 보이고**, 실루엣을 정하는
    바깥면을 건드리지 않으려고 색 0(=안 움직임)을 제일 많이 충돌하는 쪽에 준다.

사용:
    python tools\\fix_zfight.py <모델.json> [--step=0.01] [--dry]
"""
import io
import json
import math
import sys
from collections import defaultdict

# corners() 의 비트: 1=x, 2=z, 4=y. 아래 면 정의는 마크 규약과 같다.
FACES = {'down': (0, 1, 3, 2), 'up': (4, 6, 7, 5),
         'north': (0, 4, 5, 1), 'south': (2, 3, 7, 6),
         'west': (0, 2, 6, 4), 'east': (1, 5, 7, 3)}


def rot(p, axis, ang, org):
    if not ang:
        return p
    t = math.radians(ang)
    c, s = math.cos(t), math.sin(t)
    x, y, z = p[0] - org[0], p[1] - org[1], p[2] - org[2]
    if axis == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif axis == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + org[0], y + org[1], z + org[2])


def element_faces(e):
    a, b = e['from'], e['to']
    r = e.get('rotation') or {}
    pts = [rot((a[0] if not k & 1 else b[0],
                a[1] if not k & 4 else b[1],
                a[2] if not k & 2 else b[2]),
               r.get('axis', 'y'), r.get('angle', 0), r.get('origin', [8, 8, 8]))
           for k in range(8)]
    out = []
    for name, idx in FACES.items():
        q = [pts[k] for k in idx]
        u = [q[1][k] - q[0][k] for k in range(3)]
        v = [q[2][k] - q[0][k] for k in range(3)]
        n = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
        L = math.sqrt(sum(c * c for c in n))
        if L < 1e-9:
            continue                      # 넓이 0 — 화면에 안 나온다
        n = tuple(c / L for c in n)
        d = sum(n[k] * q[0][k] for k in range(3))
        bb = [[min(p[k] for p in q), max(p[k] for p in q)] for k in range(3)]
        out.append((name, n, d, bb))
    return out


def conflicts(elements):
    """z-파이팅하는 (i, j) 쌍. 넓이가 큰 것부터."""
    F = [element_faces(e) for e in elements]
    out = []
    for i in range(len(F)):
        for j in range(i + 1, len(F)):
            best = 0.0
            for _, n1, d1, b1 in F[i]:
                for _, n2, d2, b2 in F[j]:
                    # 법선이 «같은 방향» 이어야 한다. 반대면 맞댄 이음이라 서로 가려준다.
                    if sum(abs(n1[k] - n2[k]) for k in range(3)) > 1e-6:
                        continue
                    if abs(d1 - d2) > 1e-4:
                        continue
                    ov = [min(b1[k][1], b2[k][1]) - max(b1[k][0], b2[k][0]) for k in range(3)]
                    plane = [k for k in range(3) if b1[k][1] - b1[k][0] < 1e-6]
                    axes = [k for k in range(3) if k not in plane]
                    if len(axes) != 2:
                        continue
                    # 두 축 «다» 실제로 겹쳐야 면이 겹친 것이다. 한 축이 0 이면 모서리만 닿은 것.
                    if any(ov[k] <= 1e-6 for k in axes):
                        continue
                    best = max(best, ov[axes[0]] * ov[axes[1]])
            if best > 0:
                out.append((best, i, j))
    out.sort(reverse=True)
    return out


def color(pairs, n):
    """충돌 그래프 색칠. 충돌이 많은 요소부터 칠하고, 0번 색을 제일 바쁜 쪽에 준다 —
    0 은 «안 움직임» 이라 실루엣을 정하는 큰 마디가 그대로 남는 게 낫다."""
    adj = defaultdict(set)
    for _, i, j in pairs:
        adj[i].add(j)
        adj[j].add(i)
    col = {}
    for i in sorted(adj, key=lambda k: -len(adj[k])):
        used = {col[j] for j in adj[i] if j in col}
        c = 0
        while c in used:
            c += 1
        col[i] = c
    return col


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    opts = {a.split('=')[0]: (a.split('=')[1] if '=' in a else True)
            for a in sys.argv[1:] if a.startswith('--')}
    if not args:
        print(__doc__)
        return
    path = args[0]
    step = float(opts.get('--step', 0.01))
    m = json.load(io.open(path, encoding='utf-8'))
    els = m['elements']

    pairs = conflicts(els)
    print('z-파이팅 %d쌍' % len(pairs))
    for a, i, j in pairs[:8]:
        print('   el%-2d x el%-2d  넓이 %.2f' % (i, j, a))
    if not pairs:
        return

    col = color(pairs, len(els))
    moved = 0
    for i, c in sorted(col.items()):
        if c == 0:
            continue
        e = els[i]
        d = c * step
        # ── 세 축 다 줄인다 ──
        # 충돌은 x 면에서만 나는 게 아니다(헤스페로스는 우연히 전부 x 였지만, 활·방패는
        # y·z 쪽이 더 많다). 한 요소가 여러 축에서 동시에 싸울 수도 있어서 «충돌 축만»
        # 골라 미는 방식으로는 못 푼다. 균일하게 줄이면 어느 축이든 한 번에 어긋난다.
        for k in range(3):
            if abs(e['to'][k] - e['from'][k]) < 1e-6:
                # 두께 0인 평면 — 줄이면 뒤집힌다. 통째로 옮긴다.
                e['from'][k] = round(e['from'][k] + d, 4)
                e['to'][k] = round(e['to'][k] + d, 4)
            else:
                e['from'][k] = round(e['from'][k] + d, 4)
                e['to'][k] = round(e['to'][k] - d, 4)
        moved += 1
    print('색 %d가지 · %d개 요소를 최대 %.3f칸 밀어넣음 (1칸 = 1/16 블록)'
          % (max(col.values()) + 1, moved, max(col.values()) * step))

    left = conflicts(els)
    print('남은 z-파이팅: %d쌍' % len(left))
    for a, i, j in left[:5]:
        print('   el%-2d x el%-2d  넓이 %.2f' % (i, j, a))
    if opts.get('--dry'):
        print('dry run — 저장 안 함')
        return
    # ⚠️ indent=2 로 쓴다. 한 줄로 압축해 저장했더니 **diff 가 21만 줄**이 나와서
    #    「숫자 몇 개가 0.01 움직였다」가 통째로 묻혔다. 모델 파일은 사람이 읽는 것이다.
    io.open(path, 'w', encoding='utf-8').write(
        json.dumps(m, ensure_ascii=False, indent=2) + '\n')
    print('저장:', path)


if __name__ == '__main__':
    main()
