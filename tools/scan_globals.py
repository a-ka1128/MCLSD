# -*- coding: utf-8 -*-
"""파일 경계를 넘는 전역 심볼과 - 로드 순서 - 를 본다.

KubeJS 서버 스크립트는 전역 스코프를 공유하고, 파일명 알파벳 순으로 로드된다.
그래서 `ls_relic.js` 가 `ls_util.js` 의 `lsCountItem` 을 부르는 게 - 대체로 - 괜찮다:
relic 이 먼저 로드되지만, 실제 호출은 이벤트가 도는 시점이고 그때는 다 로드돼 있다.

**위험한 건 최상위에서 부르는 경우다.** 로드 도중에 실행되므로 아직 없는 심볼을 부른다.
증상은 `Cannot find function` 이고, 그 파일의 핸들러 등록이 통째로 안 된다 —
서버는 멀쩡히 뜨고 그 시스템만 조용히 사라진다.

이 검사가 필요해진 이유(docs/TODO.md D절 5):
  · `ls_hope.js` 를 만들 때 `hoRewardMult` 를 `ls_siege.js` 가 쓰는데, 이름이 hope < siege 라
    우연히 맞았다. `ls_zhope.js` 였으면 틀렸을 것이고, 그 사실이 어디에도 안 적혀 있었다.
  · `BOSS_SET`(bossdiff → enrage)은 `typeof` 로 막아뒀는데, 그게 규칙이라는 걸 아는 사람이
    코드를 쓴 사람뿐이었다.

판정:
  [최상위]  정의보다 먼저 로드되는 파일이 - 최상위에서 - 쓴다 → 터진다
  [핸들러]  이벤트 안에서만 쓴다 → 안전 (로드 순서 무관)
"""
import io
import os
import re
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = r'D:\Study\MC\CustomServer1'
SCRIPTS = os.path.join(ROOT, 'server', 'kubejs', 'server_scripts')

# 최상위 정의만 잡는다 (들여쓰기 없음)
RE_DEF = re.compile(r'^(?:function\s+([A-Za-z_$][\w$]*)|const\s+([A-Z_][A-Z_0-9]*)\s*=)', re.M)


def strip_noise(text):
    """주석과 문자열을 지운다 — 그 안의 이름은 호출이 아니다."""
    text = re.sub(r'/\*.*?\*/', ' ', text, flags=re.S)
    text = re.sub(r'//[^\n]*', '', text)
    text = re.sub(r'`(?:[^`\\]|\\.)*`', '``', text, flags=re.S)
    text = re.sub(r"'(?:[^'\\\n]|\\.)*'", "''", text)
    text = re.sub(r'"(?:[^"\\\n]|\\.)*"', '""', text)
    return text


def toplevel_lines(text):
    """들여쓰기 0 인 줄만 = 로드 시점에 실행되는 자리.

    함수 본문·이벤트 콜백은 전부 들여쓰기가 있다. 완벽하진 않지만
    (한 줄짜리 최상위 function 정의는 들여쓰기 0 이다) 그건 정의라 어차피 안전하다.
    """
    return [l for l in text.split('\n') if l and not l[0].isspace()]


def main():
    files = sorted(f for f in os.listdir(SCRIPTS) if f.endswith('.js'))
    raw = {}
    clean = {}
    for f in files:
        t = io.open(os.path.join(SCRIPTS, f), encoding='utf-8').read()
        raw[f] = t
        clean[f] = strip_noise(t)

    owner = {}
    for f in files:
        for m in RE_DEF.finditer(clean[f]):
            owner[m.group(1) or m.group(2)] = f

    danger = []
    shared = []
    for name in sorted(owner):
        home = owner[name]
        use_re = re.compile(r'\b' + re.escape(name) + r'\b')
        users = []
        risky = []
        for f in files:
            if f == home:
                continue
            if not use_re.search(clean[f]):
                continue
            users.append(f)
            # 정의보다 먼저 로드되는데 최상위에서 쓴다면 터진다.
            if f < home:
                for line in toplevel_lines(clean[f]):
                    # 그 파일 자신의 정의 줄은 뺀다
                    if RE_DEF.match(line):
                        continue
                    if use_re.search(line):
                        risky.append(f)
                        break
        if not users:
            continue
        if risky:
            danger.append((name, home, risky))
        else:
            shared.append((name, home, users))

    print('=' * 74)
    print('파일 경계를 넘는 전역 심볼 — 로드 순서는 파일명 알파벳 순')
    print('=' * 74)

    if danger:
        print('\n[터진다] 정의보다 먼저 로드되는 파일이 - 최상위에서 - 쓴다')
        for name, home, risky in danger:
            print('  %-18s 정의: %-20s 최상위 사용: %s' % (name, home, ', '.join(risky)))
        print('\n  >>> 고치는 법: 그 호출을 이벤트 핸들러 안으로 옮기거나,')
        print('      정의 파일의 이름이 먼저 오도록 바꾼다(예: ls_util 은 늘 마지막이라 안전하지 않다).')
    else:
        print('\n[터진다] 없음 — 최상위에서 남의 심볼을 부르는 파일이 없다.')

    print('\n[공유] 핸들러 안에서만 쓴다 — 로드 순서와 무관하게 안전')
    for name, home, users in shared:
        first = min(users)
        mark = ' *' if first < home else ''
        print('  %-18s %-20s -> %s%s' % (name, home, ', '.join(users), mark))
    print('\n  * = 사용자가 먼저 로드된다. 지금은 핸들러 안이라 괜찮지만,')
    print('    그 호출을 최상위로 올리는 순간 터진다. 옮길 때 이 표를 볼 것.')

    print('\n총 %d 종 · 터짐 %d · 안전 %d' % (len(danger) + len(shared), len(danger), len(shared)))
    return 1 if danger else 0


if __name__ == '__main__':
    sys.exit(main())
