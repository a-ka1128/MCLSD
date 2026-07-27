# -*- coding: utf-8 -*-
"""lsrelics 안에서 아무도 안 부르는 메서드·필드·클래스를 찾는다.

왜 필요한가:
    스킬을 갈아끼우면 옛 구현이 그대로 남는다(작열탄 -> 과열 전환 때 실제로 그랬다).
    지금은 무해하지만, 나중에 "여기 수치를 고쳤는데 왜 반영이 안 되지"의 원인이 된다.

걸러내는 것 (참조가 없어도 살아 있는 것들):
    @Override        상위 타입이 부른다
    @SubscribeEvent  이벤트 버스가 부른다
    생성자·main      진입점
    LSKubeBridge     KubeJS 스크립트가 이름으로 부른다 (자바 쪽 참조가 없다)

한계:
    문자열·리플렉션으로 부르는 건 못 잡는다. 그래서 "지워도 되는 후보"를 내놓을 뿐,
    지우기 전에 사람이 한 번 봐야 한다.
"""
import io
import os
import re
import sys

ROOT = 'moddev/lsrelics/src/main/java'
# 참조가 없어도 살아 있는 것들. 지우면 안 되므로 아예 보고에서 뺀다.
#   LSKubeBridge — KubeJS 스크립트가 LS.* 로 이름 호출한다 (자바 쪽 참조가 없다)
#   LSRelics.TAB — DeferredRegister 등록이 필드 초기화로 일어난다. 필드를 지우면
#                  크리에이티브 탭이 통째로 사라지는데, 참조는 어디에도 없다.
SKIP_FILES = {'LSKubeBridge.java'}
SKIP_CLASSES = {'LSKubeBridge'}
SKIP_MEMBERS = {'TAB'}

DECL = re.compile(
    r'^\s*(?:@\w+\s+)*'
    r'(?:public|private|protected)\s+'
    r'(?:static\s+)?(?:final\s+)?'
    r'(?:[\w.<>\[\],\s?]+?)\s+'
    r'(\w+)\s*(\(|=|;)', re.M)


def collect():
    files = {}
    for dirpath, _, names in os.walk(ROOT):
        for n in names:
            if n.endswith('.java'):
                p = os.path.join(dirpath, n)
                files[p] = io.open(p, encoding='utf-8').read()
    return files


def main():
    files = collect()
    allsrc = '\n'.join(files.values())

    dead = []
    for path, src in files.items():
        if os.path.basename(path) in SKIP_FILES:
            continue
        cls = os.path.basename(path)[:-5]
        lines = src.split('\n')
        for i, ln in enumerate(lines):
            m = DECL.match(ln)
            if not m:
                continue
            name, kind = m.group(1), m.group(2)
            if name == cls:                      # 생성자
                continue
            # 바로 위 줄들의 애너테이션을 본다
            anns = []
            for j in range(i - 1, max(-1, i - 4), -1):
                t = lines[j].strip()
                if t.startswith('@'):
                    anns.append(t)
                elif t and not t.startswith('//'):
                    break
            if any(a.startswith('@Override') or a.startswith('@SubscribeEvent') for a in anns):
                continue
            # 전체에서 이름 등장 횟수 (선언 1회 제외)
            uses = len(re.findall(r'\b%s\b' % re.escape(name), allsrc))
            if uses <= 1 and name not in SKIP_MEMBERS:
                dead.append((path, i + 1, '메서드' if kind == '(' else '필드', name))

    # 아무도 임포트/참조하지 않는 클래스
    for path in files:
        cls = os.path.basename(path)[:-5]
        uses = len(re.findall(r'\b%s\b' % re.escape(cls), allsrc))
        if uses <= 1 and cls not in SKIP_CLASSES:
            dead.append((path, 0, '클래스', cls))

    if not dead:
        sys.stdout.write('죽은 코드 없음\n')
        return
    for path, line, kind, name in sorted(dead):
        sys.stdout.write('%-58s %5s  %-5s %s\n'
                         % (path.replace(ROOT + os.sep, ''), line or '-', kind, name))
    sys.stdout.write('\n총 %d건\n' % len(dead))


if __name__ == '__main__':
    main()
