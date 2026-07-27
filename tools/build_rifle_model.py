# -*- coding: utf-8 -*-
"""gunner.json 으로부터 연사 모드용 gunner_rifle.json 을 만든다.

왜 스크립트인가:
    손으로 고치면 원본이 바뀔 때마다 다시 해야 한다. 원본에서 파생시키면
    총 모델을 손봐도 라이플 변형이 따라온다.

바꾸는 것 (원본은 볼트액션 장총이므로, "같은 총의 전투 형태"로 읽히게 한다):
    1. 앞으로 튀어나온 총열을 짧게 자른다 (총구 z -7.3 -> -2.0)
    2. 총열 덮개 + 방열구를 얹는다  ("과열 전환"이라는 이름의 시각화)
    3. 전방 손잡이를 단다          (두 손 자세와 맞물린다)
    4. 탄창을 단다                 (24발 탄창의 시각화)

좌표계 (원본 분석 결과):
    총열 방향 = Z. 총구 z≈-7.3, 개머리판 z≈31.6.
    본체 두께 = X 6.2~7.0.  높이 = Y 12.2~18.7.
"""
import json
import io
import copy

SRC = 'moddev/lsrelics/src/main/resources/assets/lsrelics/models/item/gunner.json'
DST = 'moddev/lsrelics/src/main/resources/assets/lsrelics/models/item/gunner_rifle.json'

BARREL = 63          # 긴 총열 막대 (rot 45)
FRONT_SIGHT = (68, 69)   # 가늠쇠 — 총열을 자르면 허공에 뜬다
MUZZLE_NEW = -2.0    # 새 총구 위치

X0, X1 = 6.20, 7.00  # 본체 두께


def box(name, x, y, z, tex='0'):
    """(from, to) 를 받아 요소 하나를 만든다."""
    return {
        'name': name,
        'from': [x[0], y[0], z[0]],
        'to': [x[1], y[1], z[1]],
        'faces': {f: {'uv': [0, 0, 4, 4], 'texture': '#' + tex}
                  for f in ('north', 'east', 'south', 'west', 'up', 'down')},
    }


COMPRESS_AT = 13.0   # 이 z 평면을 기준으로
COMPRESS_BY = 4.0    # 이만큼 잘라내고 뒤쪽을 당긴다


def compress_z(els, cut, amount):
    """z=cut 평면에서 amount 만큼 잘라내고, 그 뒤(z 큰 쪽) 전체를 당긴다.

    스코프와 그 아래 총몸이 길어 총 전체가 늘어져 보인다. 요소를 하나씩 줄이면
    사이가 벌어지므로, 구간을 통째로 압축한다.

    회전이 있는 요소는 **회전 원점도 같이 옮겨야** 한다. 한 점을 중심으로 한 회전은
    요소와 원점을 같은 벡터로 옮기면 결과도 그만큼 옮겨진다 — 원점을 두고 요소만
    옮기면 회전 반경이 바뀌어 엉뚱한 곳으로 튄다.
    """
    warn = []
    for i, e in enumerate(els):
        a, b = e['from'], e['to']
        if b[2] <= cut:
            continue                     # 앞쪽 — 그대로
        if a[2] >= cut:                  # 뒤쪽 — 통째로 당긴다
            a[2] -= amount
            b[2] -= amount
            r = e.get('rotation')
            if r:
                r['origin'][2] -= amount
            continue
        # 걸친 요소 — 뒤쪽 끝만 당겨 짧게 만든다
        if b[2] - amount <= a[2] + 0.05:
            warn.append(i)               # 너무 짧아 뒤집힌다 — 건드리지 않는다
            continue
        b[2] -= amount
    if warn:
        print('  주의: 너무 짧아 건너뛴 요소 %s' % warn)


def main():
    src = json.load(io.open(SRC, encoding='utf-8'))
    m = copy.deepcopy(src)
    els = m['elements']

    # ── 1. 총열 자르기 ──
    cut = els[BARREL]['from'][2]
    els[BARREL]['from'][2] = MUZZLE_NEW
    # 가늠쇠를 새 총구 끝으로 당긴다 (잘라낸 길이만큼 뒤로)
    shift = MUZZLE_NEW - cut
    for i in FRONT_SIGHT:
        els[i]['from'][2] += shift
        els[i]['to'][2] += shift

    add = []
    # ── 2. 총열 덮개 + 방열구 ──
    # 기존 전방 총열덮개(62번)보다 확실히 두꺼워야 실루엣이 바뀐다.
    # 위쪽은 스코프 레일이 지나가므로 방열구는 **옆면**에 낸다.
    add.append(box('shroud', (X0 - 0.34, X1 + 0.34), (16.35, 18.15), (MUZZLE_NEW, 8.20)))
    add.append(box('shroud_ring', (X0 - 0.42, X1 + 0.42), (16.20, 18.30), (7.60, 8.60)))
    # 옆면 방열구 5줄 — 덮개보다 살짝 더 튀어나온 얇은 띠
    for k in range(5):
        z0 = -0.9 + k * 1.55
        add.append(box('vent%d' % k, (X0 - 0.50, X1 + 0.50), (16.80, 17.70), (z0, z0 + 0.75)))

    # ── 3. 전방 손잡이 ──
    # 총구 가까이, 굵고 짧게. 얇으면 손잡이가 아니라 다리처럼 보인다.
    add.append(box('foregrip', (X0 - 0.14, X1 + 0.14), (13.60, 16.45), (1.20, 3.30)))
    add.append(box('foregrip_cap', (X0 - 0.26, X1 + 0.26), (13.25, 13.75), (1.00, 3.50)))

    # ── 4. 탄창 ──  방아쇠울 바로 앞 아래. 아래로 갈수록 좁아지게 두 단으로 쌓는다
    add.append(box('mag_top', (X0 + 0.02, X1 - 0.02), (14.60, 16.75), (15.30, 18.40)))
    add.append(box('mag_body', (X0 + 0.08, X1 - 0.08), (12.90, 14.70), (15.65, 18.05)))
    add.append(box('mag_lip', (X0 - 0.10, X1 + 0.10), (16.55, 17.00), (15.10, 18.60)))

    els.extend(add)

    # ── 5. 전체 길이 압축 ──
    # 추가 요소까지 다 붙인 뒤에 한다 — 탄창(z 15.3~18.6)이 압축 구간 뒤라 같이 당겨져야
    # 방아쇠와의 간격이 유지된다.
    compress_z(els, COMPRESS_AT, COMPRESS_BY)

    # 손에 든 크기는 원본 그대로 둔다 — 형태로 구분되므로 키울 이유가 없다
    io.open(DST, 'w', encoding='utf-8', newline='').write(
        json.dumps(m, ensure_ascii=False, indent=2))
    zs = [v for e in els for v in (e['from'][2], e['to'][2])]
    src_zs = [v for e in src['elements'] for v in (e['from'][2], e['to'][2])]
    print('요소 %d -> %d (추가 %d)  총구 %.2f -> %.2f'
          % (len(src['elements']), len(els), len(add), cut, MUZZLE_NEW))
    print('전체 길이 %.1f -> %.1f  (z %.1f~%.1f)'
          % (max(src_zs) - min(src_zs), max(zs) - min(zs), min(zs), max(zs)))


if __name__ == '__main__':
    main()
