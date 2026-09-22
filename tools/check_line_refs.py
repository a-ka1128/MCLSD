# -*- coding: utf-8 -*-
"""문서가 가리키는 <파일:줄> 이 아직 그 자리인지 확인한다.

줄 번호는 적는 순간 낡는다. TOWN.md 에서 4개가 전부 빈 줄을 가리키고 있었고
(ls_siege.js 가 923 -> 1082 줄로 자라면서), 그 문서의 존재 이유가 «정의와 구현을 잇는 것»이었다.
같은 일이 다른 문서에도 있는지 한 번에 본다.

판정: 그 줄이 - 비었거나 닫는 괄호뿐 - 이면 거의 확실히 낡은 것이다.
      내용이 있으면 사람이 봐야 한다(맥락까지 맞는지는 기계가 모른다).
"""
import os
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = r'D:\Study\MC\CustomServer1'
SEARCH = [
    os.path.join(ROOT, 'server', 'kubejs', 'server_scripts'),
    os.path.join(ROOT, 'moddev', 'lsrelics', 'src', 'main', 'java', 'com', 'laststardust', 'relics'),
    os.path.join(ROOT, 'moddev', 'lsrelics', 'src', 'main', 'java', 'com', 'laststardust', 'relics', 'item'),
]
RE_REF = re.compile(r'([A-Za-z_][A-Za-z_0-9]*\.(?:js|java)):(\d+)(?:-(\d+))?')

# 스택 트레이스는 참조가 아니라 - 그때 일어난 일의 기록 - 이다. 코드가 바뀌면 당연히
# 어긋나고, 어긋난 채로 두는 게 맞다(고치면 그건 거짓 기록이 된다).
# 이걸 안 걸러서 `상용화_비교_리뷰` 의 붙여넣은 트레이스가 영구 오탐으로 남아 있었다.
# 상시로 울리는 경보는 무시하게 되고, 그러면 진짜 낡은 참조가 같이 묻힌다.
RE_STACK = re.compile(r'^\s*at [A-Za-z_$][\w.$]*\(')

# ── 날짜가 박힌 «그날의 보고서» 는 안 본다 (2026-08-06) ──
# `SELF-CHECK-2026-07-31.md` 는 **「낡은 줄 번호」를 조사한 보고서**다. 본문에 «이 문서가
# 가리키는 ls_siege.js:399 는 빈 줄이다» 같은 예시가 그대로 적혀 있고, 그건 - 틀린 게 맞다 - .
# 검사기가 그 예시를 다시 지적하면 영구 오탐이 되고, 상시로 울리는 경보는 무시하게 된다
# (RE_STACK 을 만든 것과 같은 이유다). 스택 트레이스처럼 «그때의 기록»이라 고치면 거짓이 된다.
#
# 여기 이름을 더할 때 기준: **날짜가 박혀 있고, 그 시점의 상태를 적은 문서인가.**
# 살아 있는 문서(ARCHITECTURE·TODO·TOWN·TEST-PLAN)는 절대 넣지 않는다 — 그쪽이 낡는 게
# 진짜 문제고, 이 도구는 그걸 잡으려고 만들었다.
SKIP_DOCS = {'SELF-CHECK-2026-07-31.md'}


def find(fname):
    for d in SEARCH:
        p = os.path.join(d, fname)
        if os.path.exists(p):
            return p
    return None


def main():
    docs = []
    for d in (os.path.join(ROOT, 'docs'), ROOT):
        for f in sorted(os.listdir(d)):
            if f.endswith('.md'):
                docs.append(os.path.join(d, f))

    total = stale = unknown = 0
    skipped = 0
    for doc in docs:
        if os.path.basename(doc) in SKIP_DOCS:
            skipped += 1
            continue
        rows = []
        for i, line in enumerate(open(doc, encoding='utf-8'), 1):
            if RE_STACK.match(line):
                continue
            for m in RE_REF.finditer(line):
                fname, ln = m.group(1), int(m.group(2))
                total += 1
                src = find(fname)
                if not src:
                    rows.append((i, m.group(0), '파일 없음'))
                    stale += 1
                    continue
                lines = open(src, encoding='utf-8').read().split('\n')
                if ln > len(lines):
                    rows.append((i, m.group(0), '범위 밖 (파일 %d줄)' % len(lines)))
                    stale += 1
                    continue
                body = lines[ln - 1].strip()
                if body in ('', '}', '})', '});', '{') or body.startswith('//'):
                    rows.append((i, m.group(0), '빈 줄/닫는 괄호 → 낡음: %r' % body[:40]))
                    stale += 1
                else:
                    rows.append((i, m.group(0), '내용 있음(사람 확인): %s' % body[:52]))
                    unknown += 1
        if rows:
            print('\n### %s' % os.path.relpath(doc, ROOT))
            for i, ref, verdict in rows:
                print('  L%-4d %-28s %s' % (i, ref, verdict))

    print('\n총 %d 개 · 확실히 낡음 %d · 사람 확인 필요 %d%s'
          % (total, stale, unknown,
             (' · 건너뛴 문서 %d (SKIP_DOCS)' % skipped) if skipped else ''))
    return 0


if __name__ == '__main__':
    sys.exit(main())
