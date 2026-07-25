# 에레보스 — EREBOS
# 이 클래스만 게시한다. 채널(스레드)마다 웹훅이 다르므로 파일도 따로 둔다.
#
#   1) 해당 채널에서 웹훅을 만들어 아래 WEBHOOK 에 붙여넣기
#   2) 포럼 스레드면 THREAD_ID 도 (일반 채널이면 빈 값)
#   3) py post_erebos.py

import os

from lsdiscord import post
from webhooks import webhook, thread_id

WEBHOOK = webhook("erebos")
THREAD_ID = thread_id("erebos")
CLASS = {
    "color": 0x9400D3,   # 다크 바이올렛
    "icon": "🗡", "name": "에레보스", "en": "EREBOS",
    "epithet": "그림자에 선 자", "role": "단일 폭딜",
    "relic": "스틱스",
    "image": "art/Erebos.png",
    "lore": [
        "심연의 어둠을 품은 자의 가호.",
        "그림자와 하나 되어 존재마저 감춘 채, 가장 위험한 적의 등 뒤에 조용히 선다.",
        "스틱스가 어둠을 가르는 순간 단 한 번의 베임으로 모든 것을 끝내고, 흔적조차 남기지 않은 채 그림자 속으로 사라진다.",
    ],
    "rows": [
        ("패시브", "그림자의 일격", "쌍단검 좌우 연격 · 공격속도 +12% · 배후 공격 +35% · 처치 시 이속·공속 +15%(최대 +45%, 3초)", ""),
        ("기본", "급소 가르기", "전방 4칸 · 출혈 3초", "4초"),
        ("이동", "그림자 도약", "12칸 내 적의 등 뒤로 순간이동 후 강타 (적 없으면 전방 대시)", "7초"),
        ("추가", "망각의 안개", "투명 3초 · 24칸 내 어그로 해제 · 다음 일격 2배(5초 내)", "15초"),
        ("궁극", "무저갱", "8초간 이동속도 +40% · 공격속도 +50% · 방어 무시", "60초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)))
