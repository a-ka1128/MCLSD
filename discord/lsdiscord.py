# Last Stardust — 디스코드 게시 공통 모듈.
# 클래스별 파일(post_*.py)이 이걸 import 해서 자기 데이터만 넘긴다.
#
# 보내는 순서
#   1) 헤더 메시지 — 제목 / 별칭·역할 / 로어 / 유물명 (+ 아트 이미지 첨부)
#   2) 스킬 임베드 — 클래스 색 막대 + 스킬 목록 (rows 가 비어 있으면 건너뜀)
#
# 임베드는 마크다운 제목(##, -#)을 렌더하지 않아서 헤더를 따로 보낸다.
# ANSI 색상 코드블록은 실제 ESC(0x1B)가 필요한데, 웹훅 JSON의  는 그대로 전달되므로
# 파일 복붙과 달리 색이 확실히 살아난다.

import json
import os
import time
import unicodedata
import urllib.error
import urllib.request

# 디스코드(앞단 Cloudflare)는 기본 User-Agent(Python-urllib/3.x)를 403으로 막는다.
UA = "LastStardust-Poster (https://github.com/, 1.0)"

E = ""                 # ANSI 이스케이프
DEFAULT_COLOR = 0x9AA4B2     # 색을 안 준 경우
# 스킬 5슬롯: 패시브 / 기본(우클릭) / 이동(쉬프트 두 번) / 추가(쉬프트+우클릭) / 궁극(쉬프트+좌클릭)
CONTROLS = "R 기본 · V 이동(쉬프트 두 번도 가능) · C 추가 · X 궁극"


# ── 다시 올리면 옛 글은 지운다 (replace) ──
# 스킬 수치가 바뀔 때마다 새 글을 올리면 채널에 여러 세대가 쌓여서, 사람들이 어느 게
# 최신인지 알 수 없다. 그래서 게시할 때마다 - 직전에 올린 것을 지우고 - 새로 올린다.
#
# 웹훅은 자기가 만든 메시지만 지울 수 있고, 그러려면 메시지 ID 가 필요하다. 그래서
# 보낼 때 ?wait=true 응답의 ID 를 아래 파일에 남긴다.
#
# ※ 이 기능이 생기기 전에 올린 글은 ID 가 없어서 스크립트가 못 지운다.
#   - 처음 한 번은 채널의 옛 글을 직접 지워야 한다 - . 그다음부터는 자동이다.
#
# 채널마다 다른 런타임 상태라 git 에 올리지 않는다(settings 는 코드, 상태는 파일 —
# 이 저장소의 다른 봇들과 같은 규칙).
POSTED_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "posted_ids.json")


def _load_posted():
    try:
        with open(POSTED_FILE, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def _save_posted(data):
    with open(POSTED_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


# ── 서식 ──
def _ansi(code, s):
    return f"{E}[{code}m{s}{E}[0m"


def _width(s):
    # 코드블록에서 한글·이모지는 2칸을 먹는다
    return sum(2 if unicodedata.east_asian_width(ch) in "WF" else 1 for ch in s)


def _pad(s, n):
    return s + " " * max(0, n - _width(s))


def build_header(c):
    """## 제목 / -# 별칭·역할 / > 로어 / ### 유물 — 이름"""
    out = []
    head = " ".join(x for x in [c.get("icon"), c.get("name")] if x)
    if c.get("en"):
        head = f"{head} — {c['en']}" if head else c["en"]
    if head:
        out.append(f"## {head}")
    sub = " · ".join(x for x in [c.get("epithet"), c.get("role")] if x)
    if sub:
        out.append(f"-# {sub}")
    out += [f"> {line}" for line in c.get("lore", [])]
    if c.get("relic"):
        out.append(f"### 유물 — {c['relic']}")
    return "\n".join(out) if out else None


def build_embed(c):
    """색 막대 + 스킬 목록. 헤더에서 이미 말한 제목·로어는 넣지 않는다(중복 방지)."""
    fields = []
    for label, name, detail, cd in c.get("rows", []):
        head = f"{label} · {name}" + (f"  `{cd}`" if cd else "")
        fields.append({"name": head, "value": detail or "​", "inline": False})
    return {
        "color": c.get("color", DEFAULT_COLOR),
        "fields": fields,
        "footer": {"text": CONTROLS},
    }


def build_ansi(c):
    out = ["```ansi"]
    head = " ".join(x for x in [c.get("icon"), c.get("name")] if x)
    if head:
        line = _ansi("1;33", head)
        if c.get("epithet"):
            line += "  " + _ansi("2;30", f"— {c['epithet']}")
        out += [line, ""]
    for label, name, detail, cd in c.get("rows", []):
        line = "  " + _ansi("2;36", _pad(label, 7)) + _ansi("0;37", _pad(name, 16)) + _ansi("2;30", detail)
        if cd:
            line += _ansi("2;30", f"   [{cd}]")
        out.append(line)
    out.append("```")
    return "\n".join(out)


# ── 전송 ──
def _resolve(path, base_dir):
    """상대경로는 호출한 스크립트 폴더 기준. 파일이 없으면 None(→ 이미지 없이 전송)."""
    if not path:
        return None
    if os.path.isabs(path):
        return path if os.path.isfile(path) else None
    p = os.path.join(base_dir, path)
    return p if os.path.isfile(p) else None


def _send(webhook, thread_id, payload, image=None):
    url = webhook + "?wait=true" + (f"&thread_id={thread_id}" if thread_id else "")
    if image:
        return _send_multipart(url, payload, image)
    req = urllib.request.Request(
        url, data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "User-Agent": UA})
    try:
        with urllib.request.urlopen(req) as r:
            return _message_id(r)
    except urllib.error.HTTPError as e:
        _explain(e)


def _send_multipart(url, payload, path):
    """이미지는 multipart/form-data 로 — 외부 호스팅 없이 로컬 파일이 그대로 올라간다."""
    boundary = "----LastStardustBoundary7f3a"
    name = os.path.basename(path)
    ext = os.path.splitext(name)[1].lower().lstrip(".")
    mime = {"jpg": "jpeg", "jfif": "jpeg"}.get(ext, ext) or "png"

    body = bytearray()

    def part(header, data):
        body.extend(f"--{boundary}\r\n{header}\r\n\r\n".encode("utf-8"))
        body.extend(data)
        body.extend(b"\r\n")

    part('Content-Disposition: form-data; name="payload_json"\r\n'
         'Content-Type: application/json',
         json.dumps(payload, ensure_ascii=False).encode("utf-8"))
    with open(path, "rb") as f:
        part(f'Content-Disposition: form-data; name="files[0]"; filename="{name}"\r\n'
             f'Content-Type: image/{mime}', f.read())
    body.extend(f"--{boundary}--\r\n".encode("utf-8"))

    req = urllib.request.Request(
        url, data=bytes(body),
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}", "User-Agent": UA})
    try:
        with urllib.request.urlopen(req) as r:
            return _message_id(r)
    except urllib.error.HTTPError as e:
        _explain(e)


def _message_id(resp):
    """?wait=true 응답에서 메시지 ID. 나중에 이 글을 지우기 위해 필요하다."""
    try:
        return json.loads(resp.read().decode("utf-8")).get("id")
    except (ValueError, OSError):
        return None


def _delete(webhook, thread_id, message_id):
    """웹훅이 올린 메시지 하나를 지운다. 이미 없으면(404) 조용히 넘어간다."""
    url = f"{webhook}/messages/{message_id}"
    if thread_id:
        url += f"?thread_id={thread_id}"
    req = urllib.request.Request(url, method="DELETE", headers={"User-Agent": UA})
    try:
        urllib.request.urlopen(req)
        return True
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return False          # 사람이 이미 지웠다 — 정상
        print(f"  ! 삭제 실패 HTTP {e.code} (id={message_id})")
        return False


def _explain(e):
    # cp949 콘솔이 U+2014(em dash) 를 못 써서, print 문에는 쓰지 않는다.
    print(f"  ! HTTP {e.code} : {e.read().decode('utf-8', 'replace')[:300]}")
    if e.code == 401:
        print("    → 웹훅 URL(토큰)이 틀렸습니다.")
    elif e.code == 404:
        print("    → 웹훅이 삭제됐거나 THREAD_ID 가 잘못됐습니다. 일반 채널이면 THREAD_ID 를 비우세요.")
    elif e.code == 403:
        print("    → User-Agent 차단이거나 채널 권한 문제입니다.")
    raise SystemExit(1)


def post(webhook, cls, thread_id="", base_dir=".", header=True, mode="embed",
         key=None, replace=True):
    """클래스 하나를 게시한다. mode: 'embed' | 'ansi' | 'both'

    key      posted_ids.json 에 쓸 이름(웹훅 키와 같게 준다). 없으면 교체를 못 한다.
    replace  True 면 이 키로 지난번에 올린 글을 먼저 지운다.
    """
    if not webhook:
        print("WEBHOOK 을 먼저 채워주세요. (채널 설정 → 연동 → 웹훅)")
        return

    posted = _load_posted()
    old = posted.get(key, []) if key else []

    # ── 옛 글 정리 ──
    # 새로 올리기 전에 지운다. 순서가 반대면 실패 시 아무것도 안 남는 순간이 생긴다.
    if replace and key:
        if old:
            gone = sum(1 for mid in old if _delete(webhook, thread_id, mid))
            print(f"  · 옛 글 {gone}/{len(old)}개 제거")
        else:
            # ※ print 에는 em dash 를 쓰지 않는다 — 한국어 윈도 콘솔(cp949)이 U+2014 를
            #    인코딩하지 못해 스크립트가 UnicodeEncodeError 로 죽는다.
            print("  · 기록된 옛 글이 없습니다. 이번에 올리는 것부터 추적합니다.")
            print("    (이 기능 이전에 올린 글이 채널에 있으면 직접 지워주세요)")

    fresh = []

    def keep(mid):
        if mid:
            fresh.append(mid)

    if header:
        text = build_header(cls)
        if text:
            img_path = cls.get("image")
            resolved = _resolve(img_path, base_dir)
            keep(_send(webhook, thread_id, {"content": text}, image=resolved))
            if img_path and not resolved:
                print(f"  · 이미지 없음(건너뜀): {img_path}")
            time.sleep(0.7)

    if cls.get("rows"):
        if mode in ("embed", "both"):
            keep(_send(webhook, thread_id, {"embeds": [build_embed(cls)]}))
            time.sleep(0.7)
        if mode in ("ansi", "both"):
            keep(_send(webhook, thread_id, {"content": build_ansi(cls)}))
            time.sleep(0.7)
    else:
        print("  · 스킬 목록이 비어 있어 임베드는 건너뜁니다 (로어만 게시)")

    # 전송이 중간에 죽으면 _explain 이 SystemExit 을 던져 여기 못 온다 —
    # 그러면 옛 ID 가 그대로 남아, 다음 실행이 남은 글을 다시 지우려 시도한다(무해).
    if key:
        posted[key] = fresh
        _save_posted(posted)

    print("게시 완료:", cls.get("name", "?"), f"({len(fresh)}개)")
