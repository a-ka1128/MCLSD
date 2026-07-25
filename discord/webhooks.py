# 웹훅 주소를 코드 밖에서 가져오는 창구.
#
# ── 왜 분리했나 ──
# **디스코드 웹훅은 URL 자체가 인증이다.** 주소를 아는 사람은 별도 인증 없이 그 채널에
# 글과 이미지를 올릴 수 있다. 그래서 소스에 박아두면 저장소를 공개하는 순간 채널이 열린다.
# (2026-07-26 git 올리기 직전에 post_*.py 8개에 하드코딩돼 있던 것을 여기로 옮겼다)
#
# ── 값을 넣는 곳 두 군데 ──
#   1) 환경변수  LSD_WEBHOOK_<대문자키>   예: LSD_WEBHOOK_ATLAS
#   2) 같은 폴더의 webhooks_local.py      (git 에 올라가지 않는다)
#
# webhooks_local.py 는 이런 모양이면 된다:
#
#     WEBHOOKS = {
#         "atlas": "https://discord.com/api/webhooks/…",
#         "orion": "https://discord.com/api/webhooks/…",
#     }
#     THREAD_IDS = {}          # 포럼 스레드에 올릴 때만
#
# 둘 다 없으면 빈 문자열을 돌려주고, 호출부(lsdiscord.post)가 안내문을 띄운다.
import os

try:
    from webhooks_local import WEBHOOKS as _LOCAL
except ImportError:
    _LOCAL = {}

try:
    from webhooks_local import THREAD_IDS as _LOCAL_THREADS
except ImportError:
    _LOCAL_THREADS = {}


def webhook(key):
    """키(예: 'atlas')에 해당하는 웹훅 URL. 없으면 빈 문자열."""
    return os.environ.get(f"LSD_WEBHOOK_{key.upper()}", "") or _LOCAL.get(key, "")


def thread_id(key):
    """포럼 스레드 ID. 일반 채널이면 빈 문자열."""
    return os.environ.get(f"LSD_THREAD_{key.upper()}", "") or _LOCAL_THREADS.get(key, "")
