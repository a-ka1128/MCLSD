"""litematic -> Sponge Schematic v3 (.schem) 변환기.

WorldEdit 7.3.8 이 읽는 포맷은 `mcedit` 과 `sponge v1/v2/v3` 뿐이다 —
`.litematic` 은 못 읽는다(BuiltInClipboardFormat 로 확인).
Litematica 는 클라이언트 모드라 서버에 붙이려면 크리에이티브로 블록을 하나씩 놓아야 하는데,
2만 5천 블록짜리 성을 그렇게 놓을 수는 없다. 그래서 파일 쪽에서 해결한다.

    py tools/litematic_to_schem.py <입력.litematic> [출력.schem]

⚠️ **버전은 안 바꾼다.** 1.21.5 에서 만든 것을 1.21.1 에 붙이면 없는 블록이 생길 수 있는데,
   그 변환은 WorldEdit 이 붙이는 시점에 한다(우리가 흉내 내면 더 틀린다).
   여기서는 `DataVersion` 을 원본 그대로 넘겨 **WorldEdit 이 알고 판단하게** 둔다.

── 두 포맷의 다른 점 세 가지 ──
  ① 인덱스 순서   litematic: y*W*L + z*W + x     sponge: x + z*W + y*W*L   (같다)
  ② 비트 배열     litematic 은 **롱 경계를 넘나들며** 붙여 담는다(1.16 이전 바닐라 방식).
                  1.16+ 바닐라처럼 «롱마다 패딩» 이라고 읽으면 통째로 어긋난다.
  ③ 팔레트        litematic 은 {Name, Properties} 복합 태그, sponge 는 "id[k=v,...]" 문자열
"""

import gzip
import io
import struct
import sys
from pathlib import Path

# ── 최소 NBT ────────────────────────────────────────────────────────────────
TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG = 0, 1, 2, 3, 4
TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING = 5, 6, 7, 8
TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 9, 10, 11, 12


class Reader:
    def __init__(self, raw):
        self.f = io.BytesIO(raw)

    def u1(self):
        return struct.unpack(">B", self.f.read(1))[0]

    def i2(self):
        return struct.unpack(">h", self.f.read(2))[0]

    def i4(self):
        return struct.unpack(">i", self.f.read(4))[0]

    def i8(self):
        return struct.unpack(">q", self.f.read(8))[0]

    def string(self):
        n = struct.unpack(">H", self.f.read(2))[0]
        return self.f.read(n).decode("utf-8", "replace")

    def payload(self, t):
        if t == TAG_BYTE:
            return self.u1()
        if t == TAG_SHORT:
            return self.i2()
        if t == TAG_INT:
            return self.i4()
        if t == TAG_LONG:
            return self.i8()
        if t == TAG_FLOAT:
            return struct.unpack(">f", self.f.read(4))[0]
        if t == TAG_DOUBLE:
            return struct.unpack(">d", self.f.read(8))[0]
        if t == TAG_BYTE_ARRAY:
            return self.f.read(self.i4())
        if t == TAG_STRING:
            return self.string()
        if t == TAG_LIST:
            it = self.u1()
            n = self.i4()
            return [self.payload(it) for _ in range(n)]
        if t == TAG_COMPOUND:
            out = {}
            while True:
                tt = self.u1()
                if tt == TAG_END:
                    return out
                # ⚠️ 이름을 **먼저** 변수에 받는다. `out[self.string()] = self.payload(tt)` 로 쓰면
                #    파이썬이 **오른쪽을 먼저** 계산해서 이름 자리에서 값을 읽는다 —
                #    그 순간 스트림이 통째로 어긋나고, 증상은 「키가 깨진 글자로 나온다」다.
                key = self.string()
                out[key] = self.payload(tt)
        if t == TAG_INT_ARRAY:
            n = self.i4()
            return list(struct.unpack(">%di" % n, self.f.read(4 * n)))
        if t == TAG_LONG_ARRAY:
            n = self.i4()
            return list(struct.unpack(">%dq" % n, self.f.read(8 * n)))
        raise ValueError("모르는 태그 %d" % t)

    def root(self):
        t = self.u1()
        self.string()          # 루트 이름 (대개 빈 문자열)
        return self.payload(t)


class Writer:
    """쓰기는 이 변환기가 내보내는 타입만 다룬다 — Sponge v3 에 필요한 것뿐이다."""

    def __init__(self):
        self.b = bytearray()

    def u1(self, v):
        self.b += struct.pack(">B", v)

    def i2(self, v):
        self.b += struct.pack(">h", v)

    def i4(self, v):
        self.b += struct.pack(">i", v)

    def string(self, s):
        e = s.encode("utf-8")
        self.b += struct.pack(">H", len(e)) + e

    def tag(self, name, t, value):
        self.u1(t)
        self.string(name)
        self.payload(t, value)

    def payload(self, t, v):
        if t == TAG_BYTE:
            self.u1(v & 0xFF)
        elif t == TAG_SHORT:
            self.i2(v)
        elif t == TAG_INT:
            self.i4(v)
        elif t == TAG_STRING:
            self.string(v)
        elif t == TAG_BYTE_ARRAY:
            self.i4(len(v))
            self.b += bytes(v)
        elif t == TAG_INT_ARRAY:
            self.i4(len(v))
            for x in v:
                self.i4(x)
        elif t == TAG_COMPOUND:
            for k, (tt, vv) in v.items():
                self.tag(k, tt, vv)
            self.u1(TAG_END)
        else:
            raise ValueError("쓰기 미지원 태그 %d" % t)


# ── litematic 비트 배열 ─────────────────────────────────────────────────────
def unpack_states(longs, bits, count):
    """
    ⚠️ **롱 경계를 넘나든다.** Litematica 는 1.16 이전 바닐라처럼 값을 «틈 없이» 이어 담는다.
    1.16+ 방식(롱마다 남는 비트를 버림)으로 읽으면 팔레트 인덱스가 통째로 어긋나
    「알아볼 수는 있는데 블록이 전부 엉뚱한」 결과가 나온다 — 조용히 틀리는 종류의 버그다.
    """
    mask = (1 << bits) - 1
    u = [x & 0xFFFFFFFFFFFFFFFF for x in longs]
    out = []
    for i in range(count):
        start = i * bits
        lo = start >> 6
        off = start & 63
        end = ((i + 1) * bits - 1) >> 6
        if lo == end:
            out.append((u[lo] >> off) & mask)
        else:
            out.append(((u[lo] >> off) | (u[end] << (64 - off))) & mask)
    return out


def state_to_string(entry):
    """{Name:"minecraft:oak_stairs", Properties:{facing:"north"}} -> "minecraft:oak_stairs[facing=north]" """
    name = entry.get("Name", "minecraft:air")
    props = entry.get("Properties") or {}
    if not props:
        return name
    inner = ",".join("%s=%s" % (k, props[k]) for k in sorted(props))
    return "%s[%s]" % (name, inner)


def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return out


def convert(src: Path, dst: Path):
    with gzip.open(src, "rb") as g:
        root = Reader(g.read()).root()

    regions = root.get("Regions") or {}
    if len(regions) != 1:
        # 여러 영역은 각자 원점이 달라서 합치려면 좌표 보정이 필요하다.
        # 지금 필요한 파일은 전부 1영역이라 여기서 끊는다 — 조용히 첫 영역만 쓰면
        # 「일부만 붙었는데 왜인지 모르는」 상태가 된다.
        raise SystemExit("영역이 %d 개다. 이 변환기는 1개짜리만 다룬다." % len(regions))

    rname, region = next(iter(regions.items()))
    size = region["Size"]
    # ⚠️ Size 는 음수일 수 있다 — 선택 방향에 따라 부호가 붙는다. 크기는 절댓값이다.
    w, h, l = abs(size["x"]), abs(size["y"]), abs(size["z"])

    palette = [state_to_string(e) for e in region["BlockStatePalette"]]
    bits = max(2, (len(palette) - 1).bit_length())
    total = w * h * l
    idx = unpack_states(region["BlockStates"], bits, total)

    # Sponge v3 의 팔레트는 {상태문자열: 정수}. litematic 팔레트를 그대로 번호만 옮긴다.
    pal_tag = {name: (TAG_INT, i) for i, name in enumerate(palette)}

    # 인덱스 순서는 둘 다 y → z → x 라 그대로 흐른다.
    data = bytearray()
    for i in range(total):
        data += varint(idx[i])

    schem = {
        "Version": (TAG_INT, 3),
        # 원본 버전을 그대로 넘긴다 — 변환은 WorldEdit 이 붙일 때 한다(머리말 참고).
        "DataVersion": (TAG_INT, root.get("MinecraftDataVersion", 3955)),
        "Width": (TAG_SHORT, w),
        "Height": (TAG_SHORT, h),
        "Length": (TAG_SHORT, l),
        "Offset": (TAG_INT_ARRAY, [0, 0, 0]),
        "Blocks": (TAG_COMPOUND, {
            "Palette": (TAG_COMPOUND, pal_tag),
            "Data": (TAG_BYTE_ARRAY, bytes(data)),
        }),
    }

    wtr = Writer()
    wtr.tag("", TAG_COMPOUND, {"Schematic": (TAG_COMPOUND, schem)})
    with gzip.open(dst, "wb") as g:
        g.write(bytes(wtr.b))

    nonair = sum(1 for i in idx if palette[i] != "minecraft:air")
    print("%s -> %s" % (src.name, dst.name))
    print("  크기 %d x %d x %d · 팔레트 %d · 블록 %d (공기 제외)" % (w, h, l, len(palette), nonair))
    print("  DataVersion %d  (서버 1.21.1 = 3955)" % root.get("MinecraftDataVersion", 0))
    ents = region.get("Entities") or []
    tiles = region.get("TileEntities") or []
    if ents or tiles:
        print("  ⚠️ 엔티티 %d · 타일엔티티 %d 는 **안 옮긴다** (상자 내용물·표지판 글씨는 사라진다)"
              % (len(ents), len(tiles)))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    s = Path(sys.argv[1])
    d = Path(sys.argv[2]) if len(sys.argv) > 2 else s.with_suffix(".schem")
    convert(s, d)
