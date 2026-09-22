# -*- coding: utf-8 -*-
"""디스코드 카드(discord/post_*.py) → 웹 안내서 데이터.

    py tools/build_site.py

── 왜 «생성»하나 (베껴 적지 않고) ──
가호 카드의 내용은 `discord/post_<이름>.py` 의 `CLASS` dict 하나에만 있다.
웹사이트용으로 스킬 설명을 따로 적어두면 **디스코드와 웹 두 벌**이 되고, 수치를 고칠 때
한쪽만 고치게 된다. 이 저장소가 반복해서 밟은 함정이다
(`RelicSkillTable` → `RelicSkills.D_*` 로 합친 것, 봉화 정화 목록이 태그와 배열 두 벌이던 것).

그래서 이 스크립트는 **읽어서 옮기기만** 한다. 문구를 여기서 손보지 않는다 —
고칠 게 있으면 `discord/post_*.py` 를 고치고 이걸 다시 돌린다.

⚠️ **웹훅은 건드리지 않는다.** `post_*.py` 를 import 하면 `webhooks.py` 가 도는데,
   그건 값이 없으면 빈 문자열을 돌려줄 뿐이고 실제 전송은 각 파일의 `__main__` 아래에만
   있다. 즉 import 만으로는 아무것도 안 올라간다. 그리고 이 스크립트는 URL 을
   **읽지도 출력하지도 않는다** — 웹훅 주소는 그 자체가 인증이다(`webhooks.py` 머리말).
"""

import importlib
import json
import os
import re
import shutil
import sys

# ── 콘솔 인코딩 ──
# 윈도우 기본 콘솔은 cp949 라 `✔`·`→` 같은 글자에서 UnicodeEncodeError 로 **죽는다.**
# 파일은 이미 다 쓴 뒤 마지막 print 에서 터지므로 「실패한 줄 알았는데 결과물은 있는」
# 제일 헷갈리는 실패가 된다. 출력만 utf-8 로 돌리고, 안 되면 물음표로 흘린다.
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DISCORD = os.path.join(ROOT, "discord")
SITE = os.path.join(ROOT, "site")
SITE_ART = os.path.join(SITE, "art")

sys.path.insert(0, DISCORD)


# ── 이미지 ──
# 원본 일러스트가 한 장에 2.5~2.9 MB 다. 열두 장이면 31 MB — **폰으로 보는 안내서**인데
# 그대로 올리면 데이터도 로딩도 감당이 안 된다. 가로 1000px 로 줄이고 다시 압축한다.
# 원본은 `discord/art/` 에 그대로 남으므로 언제든 되돌릴 수 있다.
MAX_W = 1000

# PNG 로 줄이기만 하면 한 장에 1 MB 가 남는다(열여섯 장 = 14 MB). WebP 는 알파를 지키면서
# 같은 그림을 1/6 로 만든다 — 2020년 이후 브라우저는 전부 읽는다.
# 실패하면 PNG 로 떨어진다. 그림이 안 나오는 것보다는 무거운 게 낫다.
def copy_image(src, dst_dir, basename):
    """줄여서 저장하고 **실제로 쓴 파일 이름**을 돌려준다(확장자가 바뀔 수 있다)."""
    stem = os.path.splitext(basename)[0]
    try:
        from PIL import Image
        im = Image.open(src)
        if im.width > MAX_W:
            im = im.resize((MAX_W, round(im.height * MAX_W / im.width)), Image.LANCZOS)
        if im.mode not in ("RGB", "RGBA"):
            im = im.convert("RGBA")
        out = stem + ".webp"
        im.save(os.path.join(dst_dir, out), quality=82, method=6)
        return out
    except Exception as e:
        print(f"  ! 이미지 변환 실패({basename}: {e}) — 원본을 그대로 씁니다")
        shutil.copy2(src, os.path.join(dst_dir, basename))
        return basename


# ── 스토리 ──
# `docs/STORY.md` 가 원본이다. 디스코드 포럼에 올라간 것과 같은 글이라 여기서 다시 쓰지 않는다.
#
# ⚠️⚠️ **부록 B(운영자 전용)는 절대 나가면 안 된다.** 그 파일 머리말이 직접 못 박아 뒀다:
#      「맨 아래 "운영자 전용" 구역은 스포일러 — 플레이어에게 공개 금지.」
#      떡밥 대장부·최종 보스 정체·시스템 매핑이 거기 있다. 실수로 한 번 나가면 되돌릴 수 없다.
#      그래서 «부록을 빼는» 게 아니라 **「## 부록」을 만나면 그 자리에서 읽기를 멈춘다** —
#      나중에 부록이 더 붙어도 자동으로 안전한 쪽에 남는다.
STORY_STOP = re.compile(r"^##\s*부록")


def load_story(path):
    if not os.path.isfile(path):
        print(f"  ! 스토리 없음: {path}")
        return []
    chapters, cur = [], None
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if STORY_STOP.match(line):
            break                                  # ← 여기서 끝. 부록은 한 줄도 안 읽는다.
        m = re.match(r"^##\s+(.*)$", line)
        if m:
            cur = {"title": m.group(1).strip(), "lines": []}
            chapters.append(cur)
            continue
        if cur is None:
            continue                               # 문서 머리말(운영 지침)은 버린다
        cur["lines"].append(line)
    out = []
    for c in chapters:
        out_paras = _blocks(c["lines"])
        if out_paras:
            out.append({"title": c["title"], "paras": out_paras})
    return out


# 「기호 + 공백」으로 시작하는 줄. 3장의 가호 열두 줄(▣ ➶ ✧ ⚔ …)이 이 모양이다.
# 공백을 요구하는 이유: `……깨어났군.` 처럼 말줄임표로 시작하는 «산문»과 갈라야 한다.
BULLET = re.compile(r"^[^\w\s]\s")


def _blocks(lines):
    """한 장의 본문을 블록으로 나눈다.

    ⚠️ **줄바꿈의 뜻이 자리마다 다르다.**
       산문에서는 폭에 맞춰 접은 것이라 **이어 붙여야** 하고,
       목록에서는 한 줄이 한 항목이라 **끊어야** 한다.
       예전엔 전부 이어 붙여서 3장의 가호 열두 줄이 한 문단으로 뭉갰다(2026-08-14).

    블록 종류
      sys  ``` 로 감싼 구역 — 시스템 목소리(`[ ~합니다 ]`). STORY.md 문체 규칙의 세 목소리
           중 하나라 산문과 같은 서식으로 흘리면 안 된다.
      list 「기호 + 공백」 줄이 둘 이상 연달아 나온 구역
      q    `> ` 화자 지문 (호데고스가 말한다)
      p    나머지 산문
    """
    out, buf, fence = [], [], None

    def flush():
        nonlocal buf
        if not buf:
            return
        if len(buf) >= 2 and all(BULLET.match(x) for x in buf):
            out.append({"t": "list", "items": buf[:]})
        else:
            out.append({"t": "p", "text": " ".join(buf)})
        buf = []

    for raw in lines:
        l = raw.strip()
        if l.startswith("```"):
            if fence is None:
                flush()
                fence = []
            else:
                if fence:
                    out.append({"t": "sys", "items": fence})
                fence = None
            continue
        if fence is not None:
            if l:
                fence.append(l)
            continue
        if l == "---" or not l:
            flush()
            continue
        if l.startswith(">"):
            flush()
            out.append({"t": "q", "text": l.lstrip("> ").strip()})
            continue
        buf.append(l)
    flush()
    return out


# ── 별의 축복 ──
# 원본은 자바 두 곳이다: 값(min~max)은 `data/BlessingCatalog.java`, 이름·설명은
# `assets/lsrelics/lang/ko_kr.json`. 여기서 «옮기기만» 한다 — 값을 다시 적으면
# 게임과 사이트가 갈라지고, 갈라진 걸 아무도 못 본다.
#
# 설명 문자열은 `%s%%` 자리에 수치가 들어가는 서식이다. 사이트에서는 굴림 폭을
# 보여주는 게 맞으므로 `%s` 를 「min~max」로 채운다.
BLESS_JAVA = os.path.join(ROOT, "moddev", "lsrelics", "src", "main", "java",
                          "com", "laststardust", "relics", "data", "BlessingCatalog.java")
BLESS_LANG = os.path.join(ROOT, "moddev", "lsrelics", "src", "main", "resources",
                          "assets", "lsrelics", "lang", "ko_kr.json")


def load_bless():
    if not (os.path.isfile(BLESS_JAVA) and os.path.isfile(BLESS_LANG)):
        print("  ! 축복 원본 없음 — 건너뜀")
        return []
    src = open(BLESS_JAVA, encoding="utf-8").read()
    lang = json.load(open(BLESS_LANG, encoding="utf-8"))
    axes = []
    # `public static final List<Blessing> WEAPONS = List.of( ... );` 블록마다 하나
    for m in re.finditer(r"List<Blessing>\s+(\w+)\s*=\s*List\.of\((.*?)\);", src, re.S):
        axis_key = m.group(1).lower()
        items = []
        for e in re.finditer(r'new Blessing\("([a-z_]+)",\s*Axis\.(\w+),\s*([\d.]+)f,\s*([\d.]+)f\)',
                             m.group(2)):
            bid, _ax, lo, hi = e.group(1), e.group(2), float(e.group(3)), float(e.group(4))
            def num(x):
                return str(int(x)) if x == int(x) else str(x)
            span = "%s~%s" % (num(lo), num(hi))
            desc = lang.get("lsblessing." + bid + ".desc", "")
            # `%s%%` → 「6~11%」. %% 는 리터럴 % 다.
            desc = desc.replace("%s", span).replace("%%", "%")
            items.append({
                "id": bid,
                "name": lang.get("lsblessing." + bid, bid),
                "desc": desc,
                "span": span,
            })
        if items:
            axes.append({
                "title": lang.get("lsblessing.axis." + axis_key.rstrip("s"), axis_key),
                "items": items,
            })
    return axes


# ── 공지 ──
def load_notice(path):
    """`## 제목` 이 묶음, `- ` 가 항목, `> ` 가 경고 상자.

    ⚠️ **줄바꿈은 «문단 나누기»가 아니다.** 마크다운을 쓰는 사람은 화면 폭에 맞춰 문장을
       두 줄로 접는데, 줄마다 항목을 만들면 한 문장이 상자 두 개로 찢어진다
       (2026-08-14 실제로 그렇게 나왔다 — 「…가 뜨면」 / 「모드팩이 최신이…」).
       그래서 **표식 없는 줄은 앞 항목에 이어 붙이고**, 빈 줄에서만 항목을 닫는다.
       `> ` 가 연달아 나와도 하나의 상자다.
    """
    if not os.path.isfile(path):
        print(f"  ! 공지 없음: {path}")
        return []
    secs, cur, item = [], None, None

    def close():
        nonlocal item
        if item and item["text"].strip():
            cur["items"].append(item)
        item = None

    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        m = re.match(r"^##\s+(.*)$", line)
        if m:
            if cur is not None:
                close()
            cur = {"title": m.group(1).strip(), "items": []}
            secs.append(cur)
            continue
        if cur is None:
            continue                               # 파일 머리말(편집 안내)은 사이트에 안 나간다
        s = line.strip()
        if not s:
            close()
            continue
        if s.startswith("- "):
            close()
            item = {"warn": False, "text": s[2:].strip()}
        elif s.startswith(">"):
            body = s[1:].strip()
            if item and item["warn"]:
                item["text"] += " " + body         # 연달은 `>` 는 한 상자
            else:
                close()
                item = {"warn": True, "text": body}
        elif item is not None:
            item["text"] += " " + s                # 접힌 줄 — 앞 항목에 이어 붙인다
    if cur is not None:
        close()
    return [s for s in secs if s["items"]]


def load():
    """post_classes.MODULES 순서대로 CLASS dict 를 전부 읽는다."""
    import post_classes
    import post_intro

    # 인게임에 «실제로 있는» 가호만. 기준은 ls_fate.js — 디스코드 소개가 쓰는 것과 같은 규칙이다.
    # 카드가 먼저 만들어지고 구현이 나중에 붙는 순서라, 안 거르면 「골랐는데 게임에 없다」가 된다.
    live = post_intro.live_fates()

    out, skipped, missing_art = [], [], []
    for name in post_classes.MODULES:
        mod = importlib.import_module(name)
        c = getattr(mod, "CLASS", None)
        if not c:
            print(f"  ! {name}: CLASS 없음 — 건너뜀")
            continue
        if live is not None and c.get("name") not in live:
            skipped.append(c.get("name", name))
            continue

        img = c.get("image") or ""
        art_name = ""
        if img:
            src = os.path.join(DISCORD, img)
            if os.path.isfile(src):
                os.makedirs(SITE_ART, exist_ok=True)
                art_name = copy_image(src, SITE_ART, os.path.basename(img))
            else:
                missing_art.append(f"{c.get('name')} → {img}")

        out.append({
            "key": name.replace("post_", ""),
            "icon": c.get("icon", ""),
            "name": c.get("name", "?"),
            "en": c.get("en", ""),
            "epithet": c.get("epithet", ""),
            "role": c.get("role", ""),
            "relic": c.get("relic", ""),
            "color": "#%06X" % (c.get("color") or 0x9AA4B2),
            "art": art_name,
            "lore": list(c.get("lore", [])),
            "echo": c.get("echo", ""),
            # rows = (종류, 이름, 설명, 쿨타임). 튜플이라 JSON 을 위해 리스트로 편다.
            "rows": [list(r) for r in c.get("rows", [])],
        })

    # 조용히 빼면 「왜 안 뜨지」가 된다. 뺀 것은 반드시 말한다 (post_intro 와 같은 규칙).
    if skipped:
        print(f"  · 인게임 미구현이라 뺐습니다: {', '.join(skipped)}")
    if missing_art:
        print("  ! 이미지 없음: " + " / ".join(missing_art))
    return out, post_intro


def main():
    os.makedirs(SITE, exist_ok=True)
    os.makedirs(SITE_ART, exist_ok=True)
    classes, intro = load()

    # 배경·로고·안내자 초상. 클래스 일러스트와 달리 이름이 고정이라 따로 옮긴다.
    chrome = {}
    for key, fname in (("bg", "background.png"), ("bgTitle", "background_title.png"),
                       ("logo", "logo_last_stardust.png"), ("guide", "Hodēgos.png")):
        src = os.path.join(DISCORD, "art", fname)
        if os.path.isfile(src):
            # 파일명에 ē 가 들어가면 URL 인코딩이 브라우저마다 갈린다. 안전한 이름으로 바꾼다.
            safe = re.sub(r"[^A-Za-z0-9_.-]", "_", fname)
            chrome[key] = copy_image(src, SITE_ART, safe)
        else:
            print(f"  ! 없음: art/{fname}")

    data = {
        "title": "별의 가호",
        "intro": list(intro.INTRO),
        # 디스코드 전용 안내(「채널이 열립니다」)는 웹에서 뜻이 없다. 대신 lsdiscord 의
        # 조작키 안내는 그대로 쓸모가 있어서 가져온다.
        "controls": __import__("lsdiscord").CONTROLS,
        "art": chrome,
        "notice": load_notice(os.path.join(ROOT, "docs", "NOTICE.md")),
        "systems": load_notice(os.path.join(ROOT, "docs", "SYSTEMS.md")),
        "bless": load_bless(),
        "story": load_story(os.path.join(ROOT, "docs", "STORY.md")),
        "classes": classes,
    }

    # ── data.js 로 쓰는 이유 ──
    # data.json + fetch() 는 file:// 로 열면 CORS 로 막힌다. 배포 전에 브라우저로 그냥
    # 열어보는 걸 못 하게 되는데, 그게 이 페이지에서 제일 자주 하는 일이다.
    # <script src> 는 file:// 에서도 돌아간다.
    js = "window.LS_DATA = " + json.dumps(data, ensure_ascii=False, indent=1) + ";\n"
    with open(os.path.join(SITE, "data.js"), "w", encoding="utf-8", newline="\n") as f:
        f.write(js)

    art_bytes = sum(os.path.getsize(os.path.join(SITE_ART, f)) for f in os.listdir(SITE_ART))
    print(f"✔ site/data.js  {len(js):,} bytes")
    print(f"  가호 {len(classes)}종 · 스킬 {sum(len(c['rows']) for c in classes)}행")
    print(f"  공지 {len(data['notice'])}묶음 · 시스템 {len(data['systems'])}묶음 · 스토리 {len(data['story'])}장")
    print(f"  축복 {sum(len(a['items']) for a in data['bless'])}종 ({len(data['bless'])}축)")
    print(f"  이미지 {len(os.listdir(SITE_ART))}장 · {art_bytes/1024/1024:.1f} MB")
    if data["story"]:
        print("  스토리: " + " / ".join(s["title"].split("—")[0].strip() for s in data["story"]))
    # 부록이 새어나갔는지 매번 확인한다. 사람이 기억해서 지키는 규칙은 언젠가 깨진다.
    leaked = [s["title"] for s in data["story"] if "부록" in s["title"] or "운영자" in s["title"]]
    print("  ✘ 운영자 전용 구역이 섞였다: " + ", ".join(leaked) if leaked
          else "  · 운영자 전용(부록) 제외 확인")


if __name__ == "__main__":
    main()
