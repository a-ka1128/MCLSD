# 헬리오스 — HELIOS
# 이 클래스만 게시한다. 채널(스레드)마다 웹훅이 다르므로 파일도 따로 둔다.
#
#   1) 해당 채널에서 웹훅을 만들어 아래 WEBHOOK 에 붙여넣기
#   2) 포럼 스레드면 THREAD_ID 도 (일반 채널이면 빈 값)
#   3) py post_helios.py

import os

from lsdiscord import post
from webhooks import webhook, thread_id

WEBHOOK = webhook("helios")
THREAD_ID = thread_id("helios")
CLASS = {
    "color": 0xF7931E,   # 태양 오렌지
    "icon": "☀", "name": "헬리오스", "en": "HELIOS",
    "epithet": "태양을 겨눈 자", "role": "원거리 관통",
    "relic": "솔라리스",
    "image": "art/Helios.png",
    "lore": [
        "태양을 별의 탄환으로 벼리는 자의 가호.",
        "별빛을 응축한 마탄으로 전장을 꿰뚫으며, 적이 다가오기 전에 운명을 먼저 겨눈다.",
        "솔라리스의 각인이 총열을 따라 타오르는 순간, 마지막 한 발은 태양의 광선을 닮은 빛이 되어 일직선 위의 모든 것을 관통한다.",
    ],
    "rows": [
        ("패시브", "저격수의 눈", "화염 저항 · 탄창 6발(재장전 1.5초) · 최대 3인 관통 · 탄이 멀리 날수록 위력↑(최대 +40%)", ""),
        ("기본", "스코프 조준", "우클릭 홀드 0.5초 → 3배 줌 · 위력 강화(탄약 2발 소모)", ""),
        ("이동", "산탄", "전방 8칸 부채꼴(60°) 강한 넉백 · 자신은 후방 도약", "11초"),
        ("추가", "작열탄", "최대 32칸 지점 반경 3칸 폭발 + 3초 화염 장판", "11초"),
        ("궁극", "일식", "2초 차징 후 60칸 관통 광선 (반경 2.5칸)", "60초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)))
