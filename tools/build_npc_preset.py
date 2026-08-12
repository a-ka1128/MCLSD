"""Easy NPC 프리셋을 **정품 프리셋을 뼈대로** 만든다.

    py tools/build_npc_preset.py <뼈대.npc.snbt> <우리내용.snbt조각> <결과.npc.snbt>

── 왜 이렇게 하나 ──
프리셋을 손으로 쓰면 계속 틀린다. 2026-08-12 에 두 번 막혔는데 둘 다 «빠진 키» 였고,
둘 다 인게임은 이유를 안 알려줬다(`Unable to import` 한 줄, 또는 그냥 «예상치 못한 오류»).

모드가 들고 있는 정품 프리셋 19개를 세어 보니 **17개 키를 19개가 전부** 갖고 있었다 —
`EntityAttribute` · `ModelData` · `ObjectiveData` · `Navigation` · `EasyNPCVersion` 등.
그 목록을 손으로 채우는 건 「다음에 또 하나 빠뜨리는」 방식이다.

그래서 **정품 하나를 통째로 뼈대로 쓰고, 우리가 정할 칸만 갈아끼운다.**
모르는 칸은 건드리지 않는다 — 그게 이 스크립트의 전부다.

⚠️ 뼈대에서 **덜어내는** 것도 있다: `Owner` 는 만든 사람의 UUID 라 그대로 두면
   우리 NPC 의 주인이 남이 된다. `Pos`/`Rotation` 은 소환 위치가 덮으므로 무의미하지만
   남겨도 해가 없어 둔다.
"""
import re
import sys

BS = chr(92)

# 뼈대에서 빼는 키 — 남의 것이라 그대로 쓰면 안 되는 값들
DROP = ("Owner", "PresetUUID", "Offers", "TradingData", "Inventory")


def find_block(s, start):
    """s[start] 가 여는 괄호일 때, 짝이 맞는 닫는 괄호의 «다음» 위치를 준다."""
    depth = 0
    q = None
    i = start
    while i < len(s):
        c = s[i]
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
            if depth == 0:
                return i + 1
        i += 1
    raise SystemExit("괄호가 안 닫혔다 (%d 번째 글자부터)" % start)


def split_pairs(body):
    """`key:value` 를 «바로 아래 칸»에서만 갈라 [(key, value원문)] 로 준다."""
    out = []
    i = 0
    while i < len(body):
        m = re.compile(r"\s*([A-Za-z_][A-Za-z0-9_]*)\s*:").match(body, i)
        if not m:
            break
        key = m.group(1)
        j = m.end()
        while j < len(body) and body[j] in " \t\r\n":
            j += 1
        if j < len(body) and body[j] in "{[":
            end = find_block(body, j)
        else:                       # 스칼라 — 같은 칸의 쉼표까지
            end = j
            q = None
            while end < len(body):
                c = body[end]
                if q:
                    if c == BS:
                        end += 2
                        continue
                    if c == q:
                        q = None
                elif c in ('"', "'"):
                    q = c
                elif c == ",":
                    break
                end += 1
        out.append((key, body[j:end].strip()))
        i = end
        while i < len(body) and body[i] in " \t\r\n,":
            i += 1
    return out


def data_span(s):
    """`data:{ … }` 의 «내용» 범위 (여는 괄호 다음 ~ 닫는 괄호 앞)."""
    m = re.search(r"(?:^|[{,\s])data:\s*\{", s)
    if not m:
        raise SystemExit("뼈대에 data:{ } 가 없다")
    open_at = s.rindex("{", 0, m.end())
    end = find_block(s, open_at)
    return open_at + 1, end - 1


def emit(pairs, indent="    "):
    return ",\n".join("%s%s:%s" % (indent, k, v) for k, v in pairs)


def build(skeleton_path, ours_path, out_path):
    skel = open(skeleton_path, encoding="utf-8").read()
    ours = open(ours_path, encoding="utf-8").read()

    # 우리 조각은 `{ … }` 하나다 — 갈아끼울 키만 들어 있다.
    o_open = ours.index("{")
    o_pairs = split_pairs(ours[o_open + 1:find_block(ours, o_open) - 1])
    override = dict(o_pairs)

    meta = override.pop("PresetMetadata", None)
    if meta is None:
        raise SystemExit("우리 조각에 PresetMetadata 가 없다")

    a, b = data_span(skel)
    merged = []
    seen = set()
    for k, v in split_pairs(skel[a:b]):
        if k in DROP:
            continue
        seen.add(k)
        merged.append((k, override.get(k, v)))
    for k, v in o_pairs:          # 뼈대에 없던 우리 키는 뒤에 붙인다
        if k != "PresetMetadata" and k not in seen:
            merged.append((k, v))

    out = "{\n  PresetMetadata:%s,\n  data:{\n%s\n  }\n}\n" % (meta, emit(merged))
    open(out_path, "w", encoding="utf-8", newline="\n").write(out)

    kept = [k for k, _ in merged if k not in override]
    print("뼈대: %s" % skeleton_path)
    print("갈아끼운 칸 %d개: %s" % (len(override), ", ".join(sorted(override))))
    print("뼈대에서 그대로 가져온 칸 %d개" % len(kept))
    print("덜어낸 칸: %s" % ", ".join(DROP))
    print("→ %s (%d 바이트)" % (out_path, len(out.encode("utf-8"))))


# 함수만 빌려 쓰는 곳이 있다 — 가져오기만 해도 돌아버리면 안 된다.
if __name__ == "__main__":
    build(sys.argv[1], sys.argv[2], sys.argv[3])
