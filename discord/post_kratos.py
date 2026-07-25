# 크라토스 — KRATOS
# 이 클래스만 게시한다. 채널(스레드)마다 웹훅이 다르므로 파일도 따로 둔다.
#
#   1) 해당 채널에서 웹훅을 만들어 아래 WEBHOOK 에 붙여넣기
#   2) 포럼 스레드면 THREAD_ID 도 (일반 채널이면 빈 값)
#   3) py post_kratos.py

import os

from lsdiscord import post
from webhooks import webhook, thread_id

WEBHOOK = webhook("kratos")
THREAD_ID = thread_id("kratos")
CLASS = {
    "color": 0xE8B24A,   # 골드
    "icon": "⚔", "name": "크라토스", "en": "KRATOS",
    "epithet": "거인을 부순 자", "role": "근접 딜러",
    "relic": "타이탄 브레이커",
    "image": "art/Kratos.png",
    "lore": [
        "힘 그 자체가 형상을 얻은 자의 가호.",
        "대지를 흔드는 일격으로 적진을 갈라버리며, 거대한 적일수록 더욱 강한 파괴를 퍼붓는다.",
        "타이탄 브레이커가 대지를 내리치는 순간, 스스로 거인의 형상을 두르고 전장을 짓밟는다.",
    ],
    "rows": [
        ("패시브", "거인 살해자", "체력 20칸 이상 적에게 피해 +15% · 방어구 견고함 +2", ""),
        ("기본", "균열 붕괴", "전방 6칸 관통 · 약점 노출 5초(받는 피해 +10%)", "8초"),
        ("이동", "지축 밟기", "짧은 도약 → 착지 반경 3.5칸 넉백", "6초"),
        ("추가", "대지 쪼개기", "전방 7칸 부채꼴(90°) · 둔화II · 5초 균열 장판", "12초"),
        ("궁극", "타이탄 강림", "12초간 거대화 · 사거리 +2.5칸 · 넉백 면역 · 흡수IV", "60초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)))
