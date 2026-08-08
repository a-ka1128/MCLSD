# Last Stardust — 가호 소개 채널.
#
# 디스코드에 처음 들어온 사람이 보는 «한 장짜리 목록». 여덟 가호의 이름·별칭·역할만 있고
# **스킬과 수치는 없다** — 그건 각 가호의 상세 채널에 있고, 그 채널은 가호를 고른 뒤에 열린다.
#
# ── 이름·역할을 여기에 다시 적지 않는 이유 ──
# post_atlas.py … post_cuchulainn.py 의 CLASS 에 이미 icon/name/en/epithet/role 이 있다.
# 여기에 또 적으면 이름이나 역할을 고칠 때 **두 곳을 고쳐야 하고, 한 곳을 빠뜨리면
# 소개 채널과 상세 채널이 서로 다른 말을 한다.** 그래서 그 파일들을 읽어서 만든다.
# 순서도 post_classes.MODULES 를 그대로 쓴다(세 번째 목록을 만들지 않으려고).
#
# ── 사용법 ──
#   1) 소개 채널에서 웹훅을 만들어 webhooks_local.py 에 "intro" 키로 넣는다
#        WEBHOOKS = { …, "intro": "https://discord.com/api/webhooks/…" }
#      (또는 환경변수 LSD_WEBHOOK_INTRO)
#   2) py post_intro.py --dry     ← 보내지 않고 화면에만 출력해 서식 확인
#   3) py post_intro.py           ← 게시 (지난번에 올린 글은 자동으로 지운다)

import importlib
import os
import re
import sys

from lsdiscord import post_text
from post_classes import MODULES
from webhooks import thread_id, webhook

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

WEBHOOK = webhook("intro")
THREAD_ID = thread_id("intro")

# ── 머리말 ──
# 「무엇을 고르는 곳인가」와 「고르면 무슨 일이 생기는가」를 먼저 말한다.
# 상세 채널이 안 보이는 상태에서 들어오면 「채널이 왜 이것뿐이지?」가 되기 때문이다.
TITLE = "# ✦ 별의 가호"

INTRO = [
    "세상이 어둠에 삼켜지던 날, 여덟 개의 별이 각자의 유물을 남겼다.",
    "그 하나가 당신을 기다린다.",
]

# ⚠️ 디스코드에서 가호를 고르는 것은 **읽을 채널을 여는 것**이지 직업 확정이 아니다.
#    실제 선택은 인게임 `/fate` 에서 하고, 거기엔 「한 서버에 같은 직업 둘 금지」 규칙이 있다
#    (HeroData.ownerOf). 디스코드는 그 규칙을 강제할 수 없으므로 문구로 분명히 해 둔다 —
#    안 그러면 둘이 같은 가호를 눌러놓고 인게임에서 한 명이 거절당한다.
GUIDE = [
    "-# 아래에서 가호를 고르면 그 가호의 **상세 채널**이 열립니다. 유물·스킬·성장은 그곳에 있습니다.",
    "-# 여기서 고르는 것은 «읽을 채널을 여는 것»입니다. 실제 직업은 게임 안에서 `/fate` 로 정하며, "
    "같은 직업은 한 사람만 가질 수 있습니다.",
]


# ── 인게임에 «실제로 있는» 가호만 소개한다 ──
# MODULES 는 「디스코드 카드가 있는 것」의 목록이지 「고를 수 있는 것」의 목록이 아니다.
# 카드가 먼저 만들어지고 구현이 나중에 붙는 순서라 둘이 어긋나는 구간이 생긴다.
# 그때 소개 목록에 그대로 실으면 **「골랐는데 게임에 없다」**가 된다.
#
# 기준은 `server/kubejs/server_scripts/ls_fate.js` 다. 여기 목록을 손으로 적는 대신 그 파일을
# 읽어 맞춘다 — 손으로 적으면 다음 가호를 넣을 때 또 빠뜨린다.
#
# ⚠️ **키로는 못 맞춘다.** 디스코드 파일명은 인물명(post_atlas)인데 ls_fate.js 의 키는
#    역할명(guardian)이라 둘이 아예 다르다. 대신 양쪽 다 갖고 있는 **한글 이름**으로 맞춘다.
FATE_JS = os.path.join(BASE_DIR, "..", "server", "kubejs", "server_scripts", "ls_fate.js")


def live_fates():
    """ls_fate.js 에 정의된 가호들의 한글 이름. 못 읽으면 None — 그때는 거르지 않는다."""
    try:
        src = open(FATE_JS, encoding="utf-8").read()
    except OSError:
        return None
    names = set(re.findall(r"name:\s*'([^']+)'", src))
    return names or None


def load_classes():
    """post_*.py 들에서 CLASS 를 읽어온다. 소개에 쓰는 다섯 항목만 뽑는다."""
    live = live_fates()
    out, skipped = [], []
    for name in MODULES:
        mod = importlib.import_module(name)
        c = getattr(mod, "CLASS", None)
        if not c:
            print(f"  ! {name} 에 CLASS 가 없습니다 - 건너뜁니다")
            continue
        if live is not None and c.get("name") not in live:
            skipped.append(c.get("name", name))
            continue
        out.append({
            "icon": c.get("icon", ""),
            "name": c.get("name", "?"),
            "en": c.get("en", ""),
            "epithet": c.get("epithet", ""),
            "role": c.get("role", ""),
        })
    if skipped:
        # 조용히 빼면 「왜 안 뜨지」가 된다. 뺐다는 사실을 반드시 말한다.
        print(f"  · 인게임 미구현이라 뺐습니다: {', '.join(skipped)}")
    return out


def build_entry(c):
    """### 🏹 오리온 — ORION  /  -# 별을 쏘는 자 · 원거리 물리

    상세 채널의 헤더(lsdiscord.build_header)와 같은 서식을 쓴다. 두 채널이 한 시스템으로
    보이게 하려는 것 — 거기선 ## 를 쓰지만 여기는 여덟 개가 이어지므로 ### 로 한 단계 낮춘다.
    """
    head = " ".join(x for x in [c["icon"], c["name"]] if x)
    if c["en"]:
        head = f"{head} — {c['en']}" if head else c["en"]
    sub = " · ".join(x for x in [c["epithet"], c["role"]] if x)
    lines = [f"### {head}"]
    if sub:
        lines.append(f"-# {sub}")
    return "\n".join(lines)


def build_blocks(classes):
    """보낼 메시지 목록. 머리말과 목록을 나눠 보낸다.

    한 메시지에 몰면 나중에 목록만 갱신할 때 머리말까지 다시 올라간다. 나눠 두면
    사람이 채널에서 머리말에 답글을 달아둬도(안내·규칙 등) 목록 갱신에 안 쓸린다.
    """
    header = "\n".join([TITLE, "", *INTRO, "", *GUIDE])
    body = "\n".join(build_entry(c) for c in classes)
    return [header, body]


def main():
    dry = "--dry" in sys.argv
    classes = load_classes()
    if not classes:
        print("가호를 하나도 못 읽었습니다. post_*.py 를 확인하세요.")
        return

    blocks = build_blocks(classes)

    if dry:
        print("=" * 60)
        for b in blocks:
            print(b)
            print("-" * 60)
        print(f"(미리보기 - 보내지 않았습니다. 가호 {len(classes)}종)")
        return

    if not WEBHOOK:
        print("webhooks_local.py 에 \"intro\" 키를 넣어주세요.")
        print("  WEBHOOKS = { ..., \"intro\": \"https://discord.com/api/webhooks/...\" }")
        print("먼저 서식만 보려면:  py post_intro.py --dry")
        return

    post_text(WEBHOOK, blocks, thread_id=THREAD_ID, key="intro", label="가호 소개")


if __name__ == "__main__":
    main()
