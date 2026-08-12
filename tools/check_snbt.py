"""SNBT 가 «파싱은 되는가»만 본다 — 값이 맞는지는 못 본다.

SNBT 는 주석을 지원하지 않는다. `//` 를 넣으면 그 줄에서 파서가 죽고,
데이터팩 로딩 때 조용히 프리셋 목록에서 빠진다(오류가 채팅에 안 뜬다).
"""
import sys

p = sys.argv[1]
s = open(p, encoding="utf-8").read()

problems = []
if "//" in s:
    problems.append("`//` 주석이 남아 있다 — SNBT 는 주석을 못 읽는다")

# 문자열 안의 괄호는 세면 안 된다. 따옴표 두 종류와 역슬래시 탈출을 같이 본다.
curly = square = 0
quote = None
i = 0
while i < len(s):
    c = s[i]
    if quote:
        if c == "\\":
            i += 2
            continue
        if c == quote:
            quote = None
    elif c in ('"', "'"):
        quote = c
    elif c == "{":
        curly += 1
    elif c == "}":
        curly -= 1
    elif c == "[":
        square += 1
    elif c == "]":
        square -= 1
    if curly < 0 or square < 0:
        problems.append("%d 번째 글자에서 괄호가 먼저 닫혔다" % i)
        break
    i += 1

if quote:
    problems.append("따옴표(%s)가 안 닫혔다" % quote)
if curly:
    problems.append("중괄호가 %+d" % curly)
if square:
    problems.append("대괄호가 %+d" % square)

print(p)
if problems:
    for x in problems:
        print("  ✘", x)
    sys.exit(1)
print("  ✔ 괄호·따옴표 균형 정상 · 주석 없음 · %d 바이트" % len(s.encode("utf-8")))
