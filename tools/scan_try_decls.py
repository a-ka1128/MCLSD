#!/usr/bin/env python3
"""Find (and optionally fix) `const`/`let` in blocks that run more than once.

    python tools/scan_try_decls.py            # 목록만
    python tools/scan_try_decls.py --write    # 안전한 것만 var 로 바꾼다

── 왜 필요한가 ──
Rhino(KubeJS)는 **반복 실행되는 블록에 새 스코프를 만들지 않는다.** 그래서 그런 블록 안의
`const`/`let` 은 두 번째 실행에서 `InternalError: TypeError: redeclaration of var X` 로 터진다.
이름을 유일하게 바꿔도 낫지 않는다 — 전역에 하나뿐인 이름으로도 터진다. `var` 는 재선언이
합법이라 안전하다. (docs/TODO.md 함정 1번)

대상은 두 가지다:
  · `try { ... }`  — 그 함수가 반복 호출되면 터진다
  · 루프 본문        — 두 번째 순회에서 바로 터진다  ← 2026-07-25 에 이걸 놓쳐 재발했다

증상이 지독한 이유는 **catch 가 있으면 조용히 삼켜지고, 없으면 핸들러가 통째로 중단**되기
때문이다. 어느 쪽이든 "그 기능만 아무 말 없이 사라진다".

이 스캐너가 실제로 잡아낸 것:
  · ls_bounty  — 킬 집계 중단 → **사냥형 현상금이 전혀 진행되지 않음** (두 번 재발)
  · ls_stats   — **킬 통계 전부 0**
  · surfaceY×3 — 공성 몹·균열 제단·구출 지점이 **지형 무시하고 배치**

── 자동 변환하지 않는 것 ──
루프 본문에 함수(화살표 포함)가 있으면 **건너뛰고 표시만 한다.** `for (let i…)` 를 `var` 로
바꾸면 루프 안에서 만든 콜백이 전부 마지막 i 를 공유하는 고전적 버그가 생긴다.
그런 자리는 사람이 보고 판단해야 한다.

── 비용 ──
`var` 는 `const` 의 "재대입 금지"를 포기하는 것이다. 이 프로젝트는 재대입된 `const` 로
한 번 물린 적이 있다(TODO A6). 그래도 아예 실행되지 않는 코드보다는 낫다.

── 한계 ──
중괄호 깊이로 범위를 잡으므로 문자열·정규식 안의 중괄호에 속을 수 있다.
적용 후 반드시 `/reload` 로 22/22 · 오류 0 을 확인할 것.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "server" / "kubejs" / "server_scripts"

DECL = re.compile(r"\b(const|let)\s+(?=[A-Za-z_$])")
# try·루프뿐 아니라 **모든 중첩 블록**이 대상이다. 2026-07-26 에 `if` 블록 안의 선언이
# 공성 틱 핸들러를 통째로 죽이고 있는 것을 발견해 범위를 넓혔다 (ls_siege.js 664/735 —
# 성벽 판정과 방벽 효과가 둘 다 안 돌고 있었다).
#
# 함수 본문 바로 아래 선언은 문제가 없다. 그보다 **한 겹 더 들어간 블록**이 위험하다.
TRY_OPEN = re.compile(r"\btry\s*\{")
LOOP_OPEN = re.compile(r"\b(for|while|if|else)\s*[({]")
# 함수만 새 선언 스코프를 연다. `) {` 까지 함수로 보면 모든 `if (...) {` 이 함수로 오인되어
# 본문의 나머지를 건너뛴다 — 숨은 고장을 찾는 스캐너에게 누락이 가장 나쁜 실패다.
FN_OPEN = re.compile(r"(\bfunction\b|=>)")


def scan(path):
    lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
    depth = 0
    repeat_depths = []          # try / 루프 본문이 열린 깊이
    fn_depths = []
    hits, skipped = [], []

    for i, raw in enumerate(lines):
        code = raw.split("//")[0]
        opens_repeat = bool(TRY_OPEN.search(code) or LOOP_OPEN.search(code))
        opens_fn = bool(FN_OPEN.search(code)) and not opens_repeat
        shielded = bool(fn_depths) and (not repeat_depths or fn_depths[-1] > repeat_depths[-1])

        if repeat_depths and not shielded and DECL.search(code):
            # 이 블록 안에 함수가 있으면 손대지 않는다 (클로저가 변수를 붙잡을 수 있다)
            if FN_OPEN.search(code):
                skipped.append((i + 1, raw.strip()[:96]))
            else:
                fixed = DECL.sub("var ", code)
                if fixed != code:
                    lines[i] = raw.replace(code, fixed, 1)
                    for kw in DECL.findall(code):
                        hits.append((i + 1, kw, raw.strip()[:96]))

        if opens_repeat:
            repeat_depths.append(depth + 1)
        elif opens_fn:
            fn_depths.append(depth + 1)

        depth += code.count("{") - code.count("}")
        while repeat_depths and depth < repeat_depths[-1]:
            repeat_depths.pop()
        while fn_depths and depth < fn_depths[-1]:
            fn_depths.pop()

    return lines, hits, skipped


def main(write):
    if not ROOT.is_dir():
        sys.exit(f"server_scripts 를 찾을 수 없다: {ROOT}")
    total = skipped_total = 0
    for path in sorted(ROOT.glob("*.js")):
        lines, hits, skipped = scan(path)
        if hits and write:
            path.write_text("".join(lines), encoding="utf-8")
        if hits:
            total += len(hits)
            print(f"\n--- {path.name} ({len(hits)}) ---")
            for ln, kw, src in hits:
                print(f"  {ln:>4} [{kw}] {src}")
        if skipped:
            skipped_total += len(skipped)
            print(f"\n--- {path.name} · 사람이 볼 것 ({len(skipped)}) ---")
            for ln, src in skipped:
                print(f"  {ln:>4} [함수 포함 — 클로저 확인 필요] {src}")

    print(f"\n변환 대상 {total} · 수동 확인 {skipped_total}")
    if total and not write:
        print("(--write 를 붙이면 변환한다)")
    if total == 0 and skipped_total == 0:
        print("반복 블록 안의 const/let 없음 — 정상")


if __name__ == "__main__":
    main("--write" in sys.argv)
