# -*- coding: utf-8 -*-
"""KubeJS 스크립트에서 «만들어뒀는데 안 도는 것»을 찾는다.

왜 필요한가:
    이 프로젝트는 죽은 코드를 늦게 발견한 이력이 반복된다.
      - 성벽 방어 로직이 프로젝트 전체에서 안 돌고 있었다
      - 쇠약의 공격력 감소가 근접 평타에만 걸려 파티 화력 대부분이 면제였다
      - ls_towneffect 의 성역 동기화가 예외로 죽어 귀환석이 몇 시간 먹통이었다
      - stTopContrib 가 항상 0점을 읽어 폐막식 1위가 CSV 첫 사람으로 나온다
    전부 «문법은 멀쩡한데 아무도 안 부르거나, 쓰는 쪽이 사라진» 종류다.
    tools/scan_dead_java.py 는 자바만 본다. 스크립트 쪽 1,800줄은 아무도 안 봤다.

세 가지를 본다:
  A. LS.<메서드>  ↔  LSKubeBridge.Api 대조
     스크립트가 부르는 다리 메서드가 자바에 없으면 - 그 줄에서 런타임에 터진다 - .
     문법 검사로는 절대 안 걸린다.
  B. persistentData 키의 읽기/쓰기 비대칭
     · 읽기만 있고 쓰기가 없다      -> 항상 기본값. stTopContrib 이 이 경우다.
     · 쓰는 쪽이 .disabled 뿐이다   -> 이관 때 끊긴 것. 가장 위험한 형태.
     · 쓰기만 있고 읽기가 없다      -> 아무도 안 보는 값.
  C. 정의됐지만 아무도 안 부르는 함수

래퍼를 반드시 따라가야 한다:
    이 코드베이스는 키를 직접 넣지 않고 파일마다 래퍼를 둔다.
        function sfSetI(server, k, v) { sfStore(server).putInt(k, v) }
        setFinale(server, v) -> sfSetI(server, 'ls_finale', v)
    직접 호출만 보면 ls_finale·town_pop 이 «쓰는 곳 없음»으로 잡힌다(실제로 첫 판에 그랬다).
    그래서 «매개변수를 그대로 put/get 에 넘기는 함수»를 래퍼로 찾아내고, 그 호출도 세어 준다.

한계 (오탐이 나는 자리 — 무시하려면 이유를 알고 무시해야 한다):
  · 키를 문자열 연결로 만들면 «리터럴 앞부분»으로만 맞춘다('star_' + uname -> star_).
    앞부분이 없는 완전 동적 키는 못 본다.
  · getInt/putInt 는 persistentData 말고 NBT 태그에도 쓰인다. 그쪽이 섞여 들어온다.
  · 래퍼 탐지는 «한 줄짜리 함수»를 전제한다. 여러 줄 본문 안의 래퍼는 놓친다.
  · 함수가 다른 스크립트 계열(startup/client)에서만 불리면 안 쓰는 것으로 보인다.
"""
import os
import re
import sys
from collections import defaultdict

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPTS = os.path.join(ROOT, 'server', 'kubejs', 'server_scripts')
BRIDGE = os.path.join(ROOT, 'moddev', 'lsrelics', 'src', 'main', 'java',
                      'com', 'laststardust', 'relics', 'compat', 'LSKubeBridge.java')

NBT_TYPES = 'Int|Boolean|String|Long|Float|Double|IntArray|Compound'
RE_PUT = re.compile(r'\.put(?:%s)\(\s*[\'"]([^\'"]*)' % NBT_TYPES)
RE_GET = re.compile(r'\.get(?:%s)\(\s*[\'"]([^\'"]*)' % NBT_TYPES)
RE_LS = re.compile(r'\bLS\.(\w+)\s*\(')
RE_FUNC = re.compile(r'^\s*function\s+(\w+)\s*\(', re.M)


def read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


def strip_comments(src):
    """주석 안의 코드 예시가 «쓰이고 있다»로 잡히면 안 된다."""
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    return re.sub(r'^\s*//.*$', '', src, flags=re.M)


def collect():
    active, disabled = {}, {}
    for fn in sorted(os.listdir(SCRIPTS)):
        p = os.path.join(SCRIPTS, fn)
        if not os.path.isfile(p):
            continue
        if fn.endswith('.js'):
            active[fn] = strip_comments(read(p))
        elif fn.endswith('.js.disabled'):
            disabled[fn] = strip_comments(read(p))
    return active, disabled


def section(title):
    print('\n' + '=' * 74)
    print(title)
    print('=' * 74)


def check_bridge(active):
    section('A. LS.<메서드> 대조  (없으면 그 줄에서 런타임에 터진다)')
    if not os.path.exists(BRIDGE):
        print('  LSKubeBridge.java 를 못 찾았다: %s' % BRIDGE)
        return
    java = read(BRIDGE)
    # class Api 안의 public 메서드만
    m = re.search(r'class\s+Api\s*\{(.*)\n\s*\}\s*\n\s*\}', java, re.S)
    body = m.group(1) if m else java
    have = set(re.findall(r'public\s+[\w<>\[\]., ]+?\s+(\w+)\s*\(', body))

    used = defaultdict(list)
    for fn, src in active.items():
        for name in RE_LS.findall(src):
            used[name].append(fn)

    bad = sorted(n for n in used if n not in have)
    for n in sorted(used):
        mark = '  §없음' if n in bad else ''
        print('  LS.%-22s %s%s' % (n, ', '.join(sorted(set(used[n]))), mark))
    print()
    if bad:
        print('  >>> 자바에 없는 메서드 %d 개: %s' % (len(bad), ', '.join(bad)))
        print('      해당 스크립트는 그 줄에 닿는 순간 예외로 죽는다.')
    else:
        print('  >>> 전부 존재한다.')
    unused = sorted(n for n in have if n not in used)
    if unused:
        print('  (자바에만 있고 스크립트가 안 쓰는 것: %s)' % ', '.join(unused))


RE_FUNC_ONELINE = re.compile(r'function\s+(\w+)\s*\(([^)]*)\)\s*\{([^{}]*)\}')


def find_wrappers(sources):
    """매개변수를 그대로 put/get 의 키 자리에 넘기는 함수 -> (이름, 종류, 인자번호).

    setFinale 처럼 «래퍼를 부르는 래퍼»도 있으므로 더 안 늘어날 때까지 반복한다.
    """
    wrappers = {}
    allsrc = '\n'.join(sources)
    for _round in range(4):
        before = len(wrappers)
        for name, params, body in RE_FUNC_ONELINE.findall(allsrc):
            if name in wrappers:
                continue
            ps = [p.strip() for p in params.split(',') if p.strip()]
            for kind, rx in (('w', r'\.put(?:%s)\(\s*(\w+)' % NBT_TYPES),
                             ('r', r'\.get(?:%s)\(\s*(\w+)' % NBT_TYPES)):
                for arg in re.findall(rx, body):
                    if arg in ps:
                        wrappers[name] = (kind, ps.index(arg))
                        break
                if name in wrappers:
                    break
            if name in wrappers:
                continue
            # 래퍼를 부르는 래퍼: 자기 매개변수를 이미 알려진 래퍼의 키 자리에 넘긴다
            for wname, (kind, idx) in list(wrappers.items()):
                for call in re.findall(r'\b%s\s*\(([^()]*)\)' % re.escape(wname), body):
                    args = split_args(call)
                    if idx < len(args) and args[idx].strip() in ps:
                        wrappers[name] = (kind, ps.index(args[idx].strip()))
                        break
                if name in wrappers:
                    break
        if len(wrappers) == before:
            break
    return wrappers


def split_args(s):
    out, depth, cur, quote = [], 0, '', None
    for ch in s:
        if quote:
            cur += ch
            if ch == quote:
                quote = None
            continue
        if ch in '\'"':
            quote = ch
            cur += ch
        elif ch in '([{':
            depth += 1
            cur += ch
        elif ch in ')]}':
            depth -= 1
            cur += ch
        elif ch == ',' and depth == 0:
            out.append(cur)
            cur = ''
        else:
            cur += ch
    out.append(cur)
    return out


def call_args(src, name):
    """src 안의 name(...) 호출마다 인자 리스트를 돌려준다.

    정규식으로 괄호를 맞추려다 두 번 헛다리를 짚었다 — btSetS(s,'bt1',btEnc('hunt',btPick(X)))
    처럼 두 겹 이상 중첩되면 못 잡아서 bt1·wall_hp 가 «쓰는 곳 없음»으로 나왔다.
    괄호는 세어야 한다.
    """
    out = []
    for m in re.finditer(r'\b%s\s*\(' % re.escape(name), src):
        i = m.end()
        depth, quote, start = 1, None, i
        while i < len(src) and depth > 0:
            ch = src[i]
            if quote:
                if ch == quote:
                    quote = None
            elif ch in '\'"':
                quote = ch
            elif ch in '([{':
                depth += 1
            elif ch in ')]}':
                depth -= 1
            i += 1
        if depth == 0:
            out.append(split_args(src[start:i - 1]))
    return out


def scan_keys(src, wrappers):
    """이 소스에서 쓰는/읽는 키. 직접 호출 + 래퍼 호출 둘 다."""
    w = set(RE_PUT.findall(src))
    r = set(RE_GET.findall(src))
    for name, (kind, idx) in wrappers.items():
        for args in call_args(src, name):
            if idx >= len(args):
                continue
            m = re.match(r"\s*['\"]([^'\"]*)", args[idx])
            if m:
                (w if kind == 'w' else r).add(m.group(1))
    return w, r


def covered(key, others):
    """키 하나가 다른 쪽 집합에 «닿는가».

    동적 키는 리터럴 앞부분만 남는다: 쓰기가 'vc_pend_' 인데 읽기는 'vc_pend_first_siege' 다.
    한쪽이 다른 쪽의 앞부분이면 같은 키로 본다.
    """
    return any(key == o or key.startswith(o) or o.startswith(key) for o in others)


def check_keys(active, disabled):
    section('B. persistentData 키 읽기/쓰기 비대칭')
    wrappers = find_wrappers(list(active.values()) + list(disabled.values()))
    print('  키 래퍼 %d 개 인식: %s' % (len(wrappers), ', '.join(sorted(wrappers))))

    writes, reads = defaultdict(set), defaultdict(set)
    for fn, src in active.items():
        w, r = scan_keys(src, wrappers)
        for k in w:
            writes[k].add(fn)
        for k in r:
            reads[k].add(fn)
    dwrites = defaultdict(set)
    for fn, src in disabled.items():
        w, _ = scan_keys(src, wrappers)
        for k in w:
            dwrites[k].add(fn)

    ghost = []      # 읽는데 쓰는 곳이 .disabled 뿐 — 가장 위험
    orphan = []     # 읽는데 쓰는 곳이 아예 없음
    unread = []     # 쓰는데 읽는 곳이 없음
    for k in sorted(reads):
        if covered(k, writes):
            continue
        (ghost if covered(k, dwrites) else orphan).append(k)
    for k in sorted(writes):
        if not covered(k, reads):
            unread.append(k)

    if ghost:
        print('\n  [치명] 읽고 있는데 쓰는 쪽이 .disabled 파일뿐이다 — 값이 늘 기본값이다')
        for k in ghost:
            print('    %-24s 읽음: %-28s 쓰던 곳: %s'
                  % (k, ','.join(sorted(reads[k])), ','.join(sorted(dwrites[k]))))
    if orphan:
        print('\n  [주의] 읽는데 쓰는 곳이 없다')
        for k in orphan:
            print('    %-24s 읽음: %s' % (k, ','.join(sorted(reads[k]))))
    if unread:
        print('\n  [참고] 쓰는데 읽는 곳이 없다 (아무도 안 보는 값)')
        for k in unread:
            print('    %-24s 씀: %s' % (k, ','.join(sorted(writes[k]))))
    if not (ghost or orphan or unread):
        print('  >>> 비대칭 없음.')
    print('\n  키 %d 종 (쓰기 %d · 읽기 %d)' % (len(set(writes) | set(reads)), len(writes), len(reads)))


def check_funcs(active):
    section('C. 정의됐지만 아무도 안 부르는 함수')
    defined = {}
    for fn, src in active.items():
        for name in RE_FUNC.findall(src):
            defined.setdefault(name, fn)
    allsrc = '\n'.join(active.values())
    dead = []
    for name, fn in sorted(defined.items()):
        calls = len(re.findall(r'\b%s\s*\(' % re.escape(name), allsrc))
        # 정의 자체가 1회로 잡힌다. 그 이상이 없으면 아무도 안 부른다.
        if calls <= 1:
            dead.append((name, fn))
    if dead:
        for name, fn in dead:
            print('  %-28s %s' % (name, fn))
        print('\n  >>> %d 개. 다른 계열(startup/client)에서만 부르는 것일 수 있으니 확인 후 지운다.' % len(dead))
    else:
        print('  >>> 없음.')


def main():
    active, disabled = collect()
    print('server_scripts: 활성 %d · 비활성 %d' % (len(active), len(disabled)))
    check_bridge(active)
    check_keys(active, disabled)
    check_funcs(active)
    return 0


if __name__ == '__main__':
    sys.exit(main())
