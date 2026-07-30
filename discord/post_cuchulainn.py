# 쿠훌린 — CÚ CHULAINN
# 이 클래스만 게시한다. 채널(스레드)마다 웹훅이 다르므로 파일도 따로 둔다.
#
#   1) 해당 채널에서 웹훅을 만들어 아래 WEBHOOK 에 붙여넣기
#   2) 포럼 스레드면 THREAD_ID 도 (일반 채널이면 빈 값)
#   3) py post_cuchulainn.py

import os

from lsdiscord import post
from webhooks import webhook, thread_id

WEBHOOK = webhook("cuchulainn")
THREAD_ID = thread_id("cuchulainn")
CLASS = {
    "color": 0xDC143C,   # 크림슨 레드
    "icon": "🔱", "name": "쿠훌린", "en": "CÚ CHULAINN",
    "epithet": "홀로 여울을 막아선 자", "role": "전선 유지",
    "relic": "게볼그",
    "image": "art/Cuchulainn.png",
    "lore": [
        "죽음을 꿰뚫는 마창을 계승한 자의 가호.",
        "거리를 허락하지 않는 창끝으로 전장을 지배하며, 단 한 번의 돌격으로 적의 진형을 무너뜨린다.",
        "게볼그가 손을 떠나는 순간 운명을 거스르듯 심장을 향해 끝없이 갈라지고, 그 창끝에 겨눠진 자에게는 피할 미래조차 허락되지 않는다.",
    ],
    "rows": [
        ("패시브", "긴 창의 간격", "창 사거리 +1.5칸 · 공격 넉백 +1.5", ""),
        ("기본", "투창", "10~20칸 비행 후 손으로 회수 · 왕복 2회 명중 · 출혈 3초", "8초"),
        ("이동", "질풍 돌진", "바라보는 방향으로 돌진(1초 관통) · 착지 반경 3칸 넉백 + 둔화IV", "9초"),
        ("추가", "꿰뚫기", "전방 7칸 관통", "8초"),
        ("궁극", "백 개의 창", "7초간 반경 14칸에 창비 (~105발)", "60초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)),
         key="cuchulainn")
