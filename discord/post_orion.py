# 오리온 — ORION
# 이 클래스만 게시한다. 채널(스레드)마다 웹훅이 다르므로 파일도 따로 둔다.
#
#   1) 해당 채널에서 웹훅을 만들어 아래 WEBHOOK 에 붙여넣기
#   2) 포럼 스레드면 THREAD_ID 도 (일반 채널이면 빈 값)
#   3) py post_orion.py

import os

from lsdiscord import post
from webhooks import webhook, thread_id

WEBHOOK = webhook("orion")
THREAD_ID = thread_id("orion")
CLASS = {
    "color": 0x2D6FD9,   # 사파이어 블루
    "icon": "🏹", "name": "오리온", "en": "ORION",
    "epithet": "별을 쏘는 자", "role": "원거리 물리",
    "relic": "시리우스",
    "image": "art/Orion.png",
    "lore": [
        "가장 밝은 별을 길잡이 삼아 어둠을 사냥하던 자의 가호.",
        "별빛보다 빠른 화살로 적의 숨통을 조준하며, 누구도 그의 사정거리에서 벗어날 수 없다.",
        "시리우스가 빛을 머금는 순간 쏟아진 별의 화살은 밤하늘을 뒤덮고, 전장은 별빛 폭풍 속으로 사라진다.",
    ],
    "rows": [
        ("패시브", "바람의 발걸음", "이동속도 +20% · 처치 시 신속 4초", ""),
        ("기본", "속사", "4초간 연사 2배 (초당 5발)", "12초"),
        ("이동", "회피 도약", "뒤로(또는 입력 방향) 도약", "6초"),
        ("추가", "유성 사격", "화살 3발 · 명중마다 반경 2.5칸 폭발", "10초"),
        ("궁극", "별빛 폭풍", "최대 40칸 지점 반경 8칸에 3초간 화살비 (~90발)", "45초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)))
