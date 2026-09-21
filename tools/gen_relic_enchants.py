# -*- coding: utf-8 -*-
"""유물에 «데미지 이외의» 인챈트를 열어 주는 데이터팩을 생성한다.

    py tools/gen_relic_enchants.py

── 왜 «생성»하나 ──
인챈트가 108종이다(바닐라 42 + 모드 66). 손으로 JSON 을 적으면 모드가 업데이트될 때마다
어긋나고, 어긋난 걸 아무도 못 본다. 원본 jar 을 매번 다시 읽어서 만든다.

── 어떻게 여는가 ──
1.21 의 인챈트는 `supported_items` (HolderSet)로 붙을 아이템을 정한다. 그 값은
«태그 하나» 또는 «아이템 목록»이고 **둘을 섞을 수 없다.** 그래서 태그를 새로 만든다:

    lsrelics:ench/<원본태그> = [ 원본 태그, 유물 아이템들 ]
    → 그 인챈트의 supported_items 를 이 새 태그로 덮어쓴다

아이템 태그는 «다른 태그를 품을 수 있어서» 원본을 그대로 안고 갈 수 있다. 원본에 붙던
아이템은 하나도 안 잃는다.

── 왜 자바를 안 건드리나 ──
모루(AnvilMenu)에서 마법책을 붙이는 경로는 `isEnchantable()` 을 보지 않고 인챈트의
`supported_items` 만 본다. 즉 **데이터팩만으로 열린다.** 인챈트 «테이블»에는 안 뜨는데,
그게 오히려 낫다 — 테이블은 무작위라 원하는 걸 고를 수 없고, 유물은 하나뿐이라
실패가 아프기 때문이다. 책으로 붙이면 무엇이 붙는지 보고 결정한다.

⚠️ 인챈트 레지스트리는 접속할 때 서버가 클라로 보낸다. **서버에만 있으면 된다** —
   친구들이 파일을 받을 필요가 없다.
"""
import glob
import json
import os
import re
import sys
import zipfile

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "server", "kubejs", "data")
MODS = os.path.join(ROOT, "server", "mods", "*.jar")
VANILLA = os.path.join(ROOT, "server", "libraries", "net", "minecraft", "server",
                       "*", "server-*-extra.jar")

MODID = "lsrelics"

# ── 유물 분류 ──
# 근접에 `chiron` 이 있는 이유: 지팡이 모양이지만 6타 콤보를 가진 «근접» 하이브리드 힐러다.
# `hearthstone` 은 무기가 아니라 귀환 도구라 어디에도 안 넣는다.
MELEE = ["guardian", "lancer", "hecate", "nemesis", "assassin", "chiron", "pioneer"]
# 내구도가 «실제로» 있는 셋. 나머지 9종은 닳지 않으므로 내구성·수선·Life-Mending 이
# 붙어도 아무 일도 안 한다 — 열어 두면 인챈트 테이블에서 쓸 만한 게 나올 자리만 깎는다.
# (LSRelics.java: pioneer 2000 · nemesis 2000 · chiron 1600 · 그 외 durability 없음)
DURABLE = ["pioneer", "nemesis", "chiron"]
BOW   = ["hunter"]
SHOOT = ["gunner"]                       # 태양의 총 — 석궁 계열 인챈트를 받는다
STAFF = ["sage", "healer", "harmonia"]
ALL   = MELEE + BOW + SHOOT + STAFF

# ── 제외 ① 데미지 ──
# 관문 보스 체력이 유물 DPS 에서 역산된 값이라(T1 3,500 = 1성 33.6DPS × 4명 × 60초 × 0.45)
# 여기 하나만 열려도 그 표가 통째로 무너진다. `effects` 에 damage 가 없어도 실질이 데미지면 넣는다.
DAMAGE = {
    "minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods",
    "minecraft:power", "minecraft:impaling",
    "minecraft:density",        # 철퇴 낙하 피해 — effects 키가 달라 자동 검출에 안 걸린다
    "minecraft:breach",         # 방어도 무시 = 실질 피해 증가
    "minecraft:sweeping_edge",  # 휩쓸기 피해 비율
    "apothic_enchanting:shield_bash", "apothic_enchanting:berserkers_fury",
    "deeperdarker:sculk_smite", "deeperdarker:volume",
    "nova_structures:illagers_bane", "nova_structures:power",
    "twilightforest:destruction", "wan_ancient_beasts:hunter_mark",
    "farmersdelight:backstabbing",
}

# ── 제외 ② 남의 아이템 전용 ──
# 유물에 붙어도 «아무 일도 안 일어나는» 것들. 열어 두면 인챈트 목록만 지저분해지고
# 모루에서 잘못 붙여 경험치를 버리게 된다.
FOREIGN_TAG = re.compile(
    r"straddleboard|slingshot|backtank|sonic_weapon|fishing|trident|"
    r"enchantable/(armor|foot|head|chest|leg|equippable)|"
    r"shield|elytra|brush|shears|compass"
)

# 원본 supported_items 태그 → 어떤 유물에 열어 줄지
def targets_for(supported):
    s = str(supported)
    if FOREIGN_TAG.search(s):
        return None
    # 내구성·수선 계열은 «닳는 유물»에게만. 소실의 저주는 내구도와 무관하므로 전부.
    if "enchantable/durability" in s:
        return DURABLE
    if "enchantable/vanishing" in s:
        return ALL
    if "enchantable/bow" in s:
        return BOW
    if "enchantable/crossbow" in s:
        return SHOOT
    # 도끼(개척) 전용 — 채굴·전리품 계열. `pioneer` 는 RiftAxe(네더라이트 티어)라 실제로 캔다.
    if ("enchantable/mining" in s or "minecraft:axes" in s
            or "enchantable/mining_loot" in s):
        return ["pioneer"]
    # 고대 몽둥이 전용(피갈증·생명흡수). 데미지 증가가 아니라 «회복»이라 열어 준다 —
    # 근접 유물의 생존력을 올리는 쪽이고, 보스 체력 계산(DPS 역산)에는 안 들어간다.
    if "ancient_club" in s:
        return MELEE
    if ("enchantable/sword" in s or "enchantable/weapon" in s
            or "enchantable/sharp_weapon" in s or "enchantable/fire_aspect" in s
            or "enchantable/mace" in s):
        return MELEE
    return None                          # 모르는 태그는 «안 연다» — 조용히 여는 쪽이 위험하다


def load_all():
    out = {}
    for pat in (MODS, VANILLA):
        for j in sorted(glob.glob(pat)):
            try:
                z = zipfile.ZipFile(j)
            except Exception:
                continue
            for n in z.namelist():
                m = re.match(r"data/([^/]+)/enchantment/(.+)\.json$", n)
                if not m:
                    continue
                key = "%s:%s" % (m.group(1), m.group(2))
                if key in out:
                    continue
                try:
                    out[key] = json.loads(z.read(n).decode("utf-8"))
                except Exception:
                    pass
    return out


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    ench = load_all()
    print("인챈트 %d종 읽음" % len(ench))

    # (원본 supported_items, 대상 유물) 조합마다 태그 하나. 같은 조합은 공유한다.
    tags = {}
    opened, skipped_dmg, skipped_foreign = [], [], []

    for key in sorted(ench):
        if key in DAMAGE:
            skipped_dmg.append(key)
            continue
        sup = ench[key].get("supported_items")
        if sup is None:
            continue
        tgt = targets_for(sup)
        if not tgt:
            skipped_foreign.append(key)
            continue
        sig = (json.dumps(sup, sort_keys=True), tuple(tgt))
        if sig not in tags:
            tags[sig] = "ench/%d" % len(tags)
        opened.append((key, tags[sig], len(tgt)))

    # ── 태그 파일 ──
    for (sup_json, tgt), name in tags.items():
        sup = json.loads(sup_json)
        values = []
        if isinstance(sup, str):
            values.append(sup)               # "#tag" 또는 "modid:item" 둘 다 그대로 유효
        elif isinstance(sup, list):
            values.extend(sup)
        values += ["%s:%s" % (MODID, r) for r in tgt]
        write(os.path.join(OUT, MODID, "tags", "item", name + ".json"),
              {"replace": False, "values": values})

    # ── 인챈트 덮어쓰기 ──
    for key, tagname, _ in opened:
        ns, path = key.split(":", 1)
        d = dict(ench[key])
        d["supported_items"] = "#%s:%s" % (MODID, tagname)
        write(os.path.join(OUT, ns, "enchantment", path + ".json"), d)

    print("── 결과 ──")
    print("  열어 준 인챈트 : %d" % len(opened))
    print("  생성한 태그    : %d" % len(tags))
    print("  제외(데미지)   : %d" % len(skipped_dmg))
    print("  제외(남의 것)  : %d" % len(skipped_foreign))
    print()
    print("  ※ 데미지 제외 목록:")
    for k in skipped_dmg:
        print("      " + k)


if __name__ == "__main__":
    main()
