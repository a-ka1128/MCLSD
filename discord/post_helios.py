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
        ("패시브", "저격수의 눈", "화염 저항 · 탄창 6발(재장전 2초) · 최대 3인 관통 · 탄이 멀리 날수록 위력↑(30칸에서 +40%)", ""),
        ("우클릭", "스코프 조준", "홀드 0.5초 → 3배 줌 · 위력 +10%(탄약 1발 · 이동속도 감소)", ""),
        ("기본(R)", "재장전", "즉시 재장전 · 쿨 중이면 2초 재장전으로 대체", "20초"),
        ("이동", "산탄", "전방 8칸 부채꼴(60°) 강한 넉백 · 자신은 후방 도약", "11초"),
        ("추가", "과열 전환", "총이 라이플로 · 12초간 30발을 초당 6발로 연사", "36초"),
        ("궁극", "일식", "2초 차징 후 60칸 관통 광선 · 과열 중이면 「탄막 집중」으로 전환", "60초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)),
         key="helios")
