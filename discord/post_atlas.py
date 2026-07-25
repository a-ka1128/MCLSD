# 아틀라스 — ATLAS
# 이 클래스만 게시한다. 채널(스레드)마다 웹훅이 다르므로 파일도 따로 둔다.
#
#   1) 해당 채널에서 웹훅을 만들어 아래 WEBHOOK 에 붙여넣기
#   2) 포럼 스레드면 THREAD_ID 도 (일반 채널이면 빈 값)
#   3) py post_atlas.py

import os

from lsdiscord import post
from webhooks import webhook, thread_id

WEBHOOK = webhook("atlas")
THREAD_ID = thread_id("atlas")
CLASS = {
    "color": 0xC9D6E8,   # 은백
    "icon": "🛡", "name": "아틀라스", "en": "ATLAS",
    "epithet": "하늘을 떠받친 자", "role": "탱커",
    "relic": "에테르 이지스",
    "image": "art/Atlas.png",
    "lore": [
        "무너지는 하늘을 홀로 떠받친 자의 가호.",
        "어떤 충격에도 물러서지 않는 철벽이 되어, 모든 적의를 자신의 몸으로 받아낸다.",
        "에테르 이지스가 펼쳐지는 순간 성역은 누구도 넘볼 수 없는 요새가 되고, 모두가 쓰러질 운명의 순간마저 끝내 버텨내며 밤을 되돌린다.",
    ],
    "rows": [
        ("패시브", "방벽의 수호자", "넉백 절반 저항 · 주변 8칸 아군 받는 피해 −5%", ""),
        ("기본", "수호의 파동", "반경 6칸 도발 4초 · 저항II 6초 + 흡수III 6초", "8초"),
        ("이동", "이지스 돌진", "전방 돌진 → 착지 반경 3칸 넉백 + 둔화IV 3초", "10초"),
        ("추가", "수호 반격", "3초간 받는 피해 −40% · 피격 시 주변 3.5칸 반사", "14초"),
        ("궁극", "불멸의 맹세", "5초 무적 · 반경 16칸 도발 8초 · 12칸 아군 보호막", "60초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)))
