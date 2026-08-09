# 케이론 — CHIRON
# 이 클래스만 게시한다. 채널(스레드)마다 웹훅이 다르므로 파일도 따로 둔다.
#
#   1) 해당 채널에서 웹훅을 만들어 webhooks_local.py 의 "chiron" 키에 넣기
#   2) 포럼 스레드면 THREAD_IDS 에도
#   3) py post_chiron.py

import os

from lsdiscord import post
from webhooks import webhook, thread_id

WEBHOOK = webhook("chiron")
THREAD_ID = thread_id("chiron")
CLASS = {
    "color": 0x8FA36B,   # 올리브 — 약초를 아는 스승
    "icon": "🏛", "name": "케이론", "en": "CHIRON",
    "epithet": "상처 입은 치유자", "role": "근접 하이브리드 힐러",
    "relic": "펠리온",
    "image": "art/Cheiron.png",
    "lore": [
        "상처 입은 자의 곁을 지키며, 자신의 상처를 끝내 치유하지 못한 자의 가호.",
        "적을 쓰러뜨릴 때마다 가장 다친 동료에게 생명의 힘을 나누고, 펠리온의 가르침이 울려 퍼지는 순간 모두가 서로를 치유하는 전장이 된다.",
        "자신은 끝내 상처를 짊어진 채, 마지막 순간까지 동료들이 살아남을 길을 열어준다.",
    ],
    "echo": "아픈 건 괜찮아. 다른 사람부터 봐줘.",
    "rows": [
        ("평타", "6타 콤보", "가로 2 → 360° 회전 → 내려찍기 → 찌르기 2. 근접 유물 중 제일 빠르다(초당 1.8회)", ""),
        ("패시브", "상처 입은 치유자", "적을 때릴 때마다 반경 12칸에서 가장 다친 아군이 회복된다(5성 2.2 · 약 4 HPS) — 자기 자신은 제외", ""),
        ("기본", "축성", "전방 부채꼴 6칸 강타 · 맞힌 수만큼 아군 회복(1마리 3 · 최대 5마리 15)", "10초"),
        ("이동", "바람 걸음", "6초간 이동속도 +40% · 지나가면서 반경 4칸 아군을 계속 회복", "12초"),
        ("추가", "가르침", "8초간 주변 아군의 평타에도 전이 회복이 생긴다 · 그동안만 나도 회복 대상이 된다", "22초"),
        ("궁극", "펠리온의 밤", "10초간 반경 12칸 아군이 죽지 않는다(체력 1에서 버틴다) · 케이론 자신은 제외", "100초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)),
         key="chiron")
