# 네메시스 — NEMESIS
# 이 클래스만 게시한다. 채널(스레드)마다 웹훅이 다르므로 파일도 따로 둔다.
#
#   1) 해당 채널에서 웹훅을 만들어 아래 WEBHOOK 에 붙여넣기
#      (환경변수 LSD_WEBHOOK_NEMESIS 또는 webhooks_local.py 의 "nemesis" 키)
#   2) 포럼 스레드면 THREAD_ID 도 (일반 채널이면 빈 값)
#   3) py post_nemesis.py
#
# ※ art/Nemesis.png 가 아직 없다 — 그림이 생길 때까지는 이미지 없이 글만 올라간다.

import os

from lsdiscord import post
from webhooks import webhook, thread_id

WEBHOOK = webhook("nemesis")
THREAD_ID = thread_id("nemesis")
CLASS = {
    "color": 0x55668A,   # 강철 — 아틀라스(은백)와 색상은 같고 채도·명도로 갈린다
    "icon": "⚔", "name": "네메시스", "en": "NEMESIS",
    "epithet": "되돌려주는 자", "role": "패링 탱커",
    "relic": "아드라스테이아",
    "image": "art/Nemesis.png",
    "lore": [
        "받은 것을 그대로 되돌려주는 자의 가호.",
        "피하지 않는다 — 받아넘긴다. 아드라스테이아의 날이 일격을 흘려내는 순간 그 힘은 고스란히 되돌아간다.",
        "그가 물러서지 않는 한, 뒤에 선 이들은 한 걸음도 밀리지 않는다.",
    ],
    "rows": [
        ("평타", "3타 콤보", "쓸기 → 찌르기 → 내려찍기. 마지막 타가 제일 무겁다", ""),
        ("우클릭", "흘리기", "0.4초 패링 창 — 성공하면 피해 무효 + 적 경직 + 기세 1중첩 / 실패하면 그대로 맞는다", "1.2초"),
        ("패시브", "강철의 각오", "방어력 +5 · 방어 강도 +3 · 기세 중첩당 주는 피해 +6%(최대 5중첩, 10초)", ""),
        ("기본", "강철 발", "3초 뿌리내림 — 넉백 무효 + 받는 피해 −40% · 이동 불가 · 끝날 때 반경 5칸 밀어내며 피해", "12초"),
        ("이동", "참격 인계", "대검을 던지고 그 대검을 잡으며 날아간다 · 지나간 자리의 적이 베인다", "9초"),
        ("추가", "역린", "5초간 패링 창 2배 + 패링 성공 시 반격이 4칸 광역으로", "22초"),
        ("궁극", "일도양단", "전방 12칸 직선 대형 일격 · 경직 + 방어력 절반 · 이후 5초간 주변 8칸 아군 받는 피해 −25%", "90초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)),
         key="nemesis")
