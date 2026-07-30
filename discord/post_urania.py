# 우라니아 — URANIA
# 이 클래스만 게시한다. 채널(스레드)마다 웹훅이 다르므로 파일도 따로 둔다.
#
#   1) 해당 채널에서 웹훅을 만들어 아래 WEBHOOK 에 붙여넣기
#   2) 포럼 스레드면 THREAD_ID 도 (일반 채널이면 빈 값)
#   3) py post_urania.py

import os

from lsdiscord import post
from webhooks import webhook, thread_id

WEBHOOK = webhook("urania")
THREAD_ID = thread_id("urania")
CLASS = {
    "color": 0xB57EDC,   # 라벤더
    "icon": "🔮", "name": "우라니아", "en": "URANIA",
    "epithet": "별지기", "role": "광역 마법",
    "relic": "셀레스티아",
    "image": "art/Urania.png",
    "lore": [
        "별의 궤도를 읽어 그 힘을 다루는 자의 가호.",
        "밤하늘에 새겨진 별의 힘을 불러내 전장을 자신의 영역으로 바꾸고, 흩어진 적들을 하나의 운명으로 엮어낸다.",
        "셀레스티아가 하늘을 가리키는 순간 거대한 별이 낙하하며, 그 아래 남은 모든 것은 빛과 함께 소멸한다.",
    ],
    "rows": [
        ("패시브", "별빛 충전", "평타 적중 시 해금된 스킬 1개의 쿨 −8%(최대 1초)", ""),
        ("기본", "소멸", "전방 11칸 관통 (반경 5칸)", "6초"),
        ("이동", "성간 도약", "바라보는 방향으로 최대 12칸 순간이동", "7초"),
        ("추가", "중력 붕괴", "최대 24칸 지점 반경 6칸 · 2초간 흡인 + 둔화III", "12초"),
        ("궁극", "초신성", "최대 32칸 지점 · 1.5초 차징 후 반경 7칸 대폭발", "60초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)))
