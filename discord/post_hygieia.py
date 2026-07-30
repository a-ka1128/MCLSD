# 히기에이아 — HYGIEIA
# 이 클래스만 게시한다. 채널(스레드)마다 웹훅이 다르므로 파일도 따로 둔다.
#
#   1) 해당 채널에서 웹훅을 만들어 아래 WEBHOOK 에 붙여넣기
#   2) 포럼 스레드면 THREAD_ID 도 (일반 채널이면 빈 값)
#   3) py post_hygieia.py

import os

from lsdiscord import post
from webhooks import webhook, thread_id

WEBHOOK = webhook("hygieia")
THREAD_ID = thread_id("hygieia")
CLASS = {
    "color": 0x2ECC71,   # 에메랄드
    "icon": "🕯", "name": "히기에이아", "en": "HYGIEIA",
    "epithet": "불씨를 지키는 자", "role": "힐러·서포트",
    "relic": "파나케이아",
    "image": "art/Hygieia.png",
    "lore": [
        "꺼져가는 생명의 불씨를 되살리는 자의 가호.",
        "상처를 치유하는 것을 넘어 희망마저 이어붙이며, 쓰러진 동료를 다시 전장으로 일으켜 세운다.",
        "파나케이아가 피워낸 치유의 빛은 모든 고통을 잠재우고, 그 빛이 머무는 동안 누구도 홀로 쓰러지지 않는다.",
    ],
    "rows": [
        ("패시브", "생명의 불씨", "최대 체력 +2칸 · 들고 있으면 자가 재생 · 평타로 아군 조준 시 치유(초과분은 보호막)", ""),
        ("기본", "심판의 빛", "전방 12칸 광선 · 명중 시 16칸 내 가장 위험한 아군 치유", "8초"),
        ("이동", "천사의 발걸음", "신속III(+30%) 3초 + 저항 3초", "8초"),
        ("추가", "성역", "반경 4칸 8초 지대 · 초당 치유 + 안에 있는 아군 받는 피해 −20%", "20초"),
        ("궁극", "소생", "20칸 내 쓰러진 아군 최대 2명 부활 (체력 50% · 저항III + 흡수II)", "90초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)),
         key="hygieia")
