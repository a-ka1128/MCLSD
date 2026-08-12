"""SNBT 가 «가져와지는가»를 미리 본다 — 값이 맞는지는 못 본다.

    py tools/check_snbt.py <파일.snbt> [...]

여기서 잡는 것은 전부 **인게임에서 이유를 안 알려주는 종류**다:

① `//` 주석 — SNBT 는 주석을 못 읽는다. 한 줄만 넣어도 파서가 죽고
   프리셋이 목록에서 그냥 빠진다. 채팅에 오류가 안 뜬다.
② 괄호·따옴표 불균형 — 같은 증상.
③ `.npc.snbt` 의 필수 키 — Easy NPC 는 `data` 안의 **`id`**(엔티티 타입)가 없으면
   「Unable to import」만 띄우고 이유는 **서버 로그에만** 남긴다
   (`Missing entity ID tag in preset data`). 2026-08-12 에 여기서 한 번 막혔다.
   `PresetMetadata.entityTypeId` 는 별개다 — 그게 있어도 `data.id` 가 없으면 실패한다.
"""
import re
import sys

BS = chr(92)   # 역슬래시. 리터럴로 쓰면 이 파일을 셸에 통째로 넘길 때 인용이 꼬인다.

p = sys.argv[1]
s = open(p, encoding="utf-8").read()

problems = []

# 괄호와 주석을 **한 번에** 훑는다. 둘 다 «문자열 밖에서만» 세야 하기 때문이다.
#
# ⚠️ 주석을 `"//" in s` 로 찾으면 안 된다. 스킨 URL 의 `https://` 가 걸린다 —
#    정품 프리셋 remote_skin.npc.snbt 를 대조군으로 돌려서 이걸 잡았다.
curly = square = 0
quote = None
i = 0
while i < len(s):
    c = s[i]
    if quote:
        if c == BS:
            i += 2
            continue
        if c == quote:
            quote = None
    elif c in ('"', "'"):
        quote = c
    elif c == "/" and s[i:i + 2] in ("//", "/*"):
        line = s.count("\n", 0, i) + 1
        problems.append("%d 번째 줄에 `%s` 주석이 있다 — SNBT 는 주석을 못 읽는다" % (line, s[i:i + 2]))
        break
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

def direct_value(body, key):
    """`body` 의 **바로 아래 칸**에서 key 의 문자열 값을 찾는다. 없으면 None.

    ⚠️ 깊이를 안 세면 안 된다. `id` 를 그냥 찾으면 `ArmorItems:[{id:"minecraft:leather_boots"}]`
       같은 아래칸이 먼저 걸린다 — 실제로 정품 프리셋을 대조군으로 돌려서 이걸 잡았다.
    """
    depth = 0
    q = None
    i = 0
    while i < len(body):
        c = body[i]
        if q:
            if c == BS:
                i += 2
                continue
            if c == q:
                q = None
        elif c in ('"', "'"):
            q = c
        elif c in "{[":
            depth += 1
        elif c in "}]":
            depth -= 1
            if depth < 0:
                return None          # data 섹션이 여기서 끝났다
        elif depth == 0:
            m = re.match(re.escape(key) + r'\s*:\s*"([^"]*)"', body[i:])
            if m and (i == 0 or body[i - 1] in "{,\n\r\t "):
                return m.group(1)
        i += 1
    return None


# ── Easy NPC 프리셋의 필수 키 ──
extra = ""
if p.endswith(".npc.snbt"):
    # ⚠️ `data:\{` 를 그냥 찾으면 **`PresetMeta«data»:{` 에 걸린다.** 앞 글자를 같이 봐야 한다 —
    #    이걸 안 보고 만든 첫 판이 「data 섹션에 created 가 없다」는 헛것을 보고했다.
    mdata = re.search(r"(?:^|[{,\s])data:\s*\{", s)
    if not mdata:
        problems.append("`data:{ … }` 섹션이 없다")
        entity_id = None
    else:
        entity_id = direct_value(s[mdata.end():], "id")
        if not entity_id:
            problems.append('`data` 바로 아래에 `id`(엔티티 타입)가 없다 — 예: id:"easy_npc:humanoid"\n'
                            "     인게임에는 «Unable to import» 만 뜨고 이유는 서버 로그에만 남는다\n"
                            "     (Easy NPC: Missing entity ID tag in preset data)")
    mmeta = re.search(r"PresetMetadata:\s*\{", s)
    if not mmeta:
        problems.append("`PresetMetadata:{ … }` 가 없다")
        type_id = None
    else:
        type_id = direct_value(s[mmeta.end():], "entityTypeId")
    if type_id and entity_id and type_id != entity_id:
        problems.append("entityTypeId(%s) 와 data.id(%s) 가 다르다" % (type_id, entity_id))
    if type_id:
        extra = " · %s" % type_id

print(p)
if problems:
    for x in problems:
        print("  ✘", x)
    sys.exit(1)
print("  ✔ 괄호·따옴표 균형 정상 · 주석 없음 · 필수 키 있음%s · %d 바이트"
      % (extra, len(s.encode("utf-8"))))
