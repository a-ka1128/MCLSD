# 하르모니아 — HARMONIA
# 이 클래스만 게시한다. 채널(스레드)마다 웹훅이 다르므로 파일도 따로 둔다.
#
#   1) 해당 채널에서 웹훅을 만들어 아래 WEBHOOK 에 붙여넣기
#      (환경변수 LSD_WEBHOOK_HARMONIA 또는 webhooks_local.py 의 "harmonia" 키)
#   2) 포럼 스레드면 THREAD_ID 도 (일반 채널이면 빈 값)
#   3) py post_harmonia.py
#
# ※ art/Harmonia.png 가 아직 없다 — 그림이 생길 때까지는 이미지 없이 글만 올라간다.

import os

from lsdiscord import post
from webhooks import webhook, thread_id

WEBHOOK = webhook("harmonia")
THREAD_ID = thread_id("harmonia")
CLASS = {
    "color": 0xE86A9A,   # 로즈 — 서포트 한 쌍이지만 히기에이아(에메랄드)와 한눈에 갈린다
    "icon": "🪢", "name": "하르모니아", "en": "HARMONIA",
    "epithet": "흩어진 것을 묶는 자", "role": "원거리 지원 딜러",
    "relic": "바르비톤",
    "image": "art/Harmonia.png",
    "lore": [
        "흩어진 것들을 하나로 엮는 자의 가호.",
        "상처를 덮는 대신 사이를 잇는다. 선율이 닿는 자리에서 아군의 걸음이 서로 맞물리고, 따로 싸우던 이들이 하나의 진형이 된다.",
        "바르비톤이 마지막 화음을 짚는 순간 모든 굴레가 끊기고, 그 화음이 닿는 곳까지 누구도 흐트러지지 않는다.",
    ],
    "rows": [
        ("평타", "음률", "좌클릭 홀드 — 로즈빛 음표를 원거리로 연사한다", ""),
        ("패시브", "공명", "주변 8칸 아군 이동속도 +10% · 아군이 적 처치 시 8초간 공격력 +4%(최대 3중첩)", ""),
        ("기본", "고양의 선율", "전방 10칸 부채꼴 — 아군 공격속도 +20% 4초 + 넉백 저항 / 적에겐 피해 + 둔화II 3초", "10초"),
        ("이동", "엮인 걸음", "전방 위로 도약 · 착지 지점 4칸 내 아군에게 신속II 3초", "8초"),
        ("추가", "결속의 매듭", "지점에 매듭 12초 · 반경 6칸 아군 받는 피해 −12% + 스킬 쿨 초당 −2% · 같은 원의 적에게 지속 피해", "25초"),
        ("궁극", "만상의 화음", "24칸 내 전 아군 10초간 공격력 +20% · 이동속도 +20% · 디버프 해제 후 3초간 재부여 면역 · 반경 10칸 지대 피해", "90초"),
    ],
}

if __name__ == "__main__":
    post(WEBHOOK, CLASS, thread_id=THREAD_ID, base_dir=os.path.dirname(os.path.abspath(__file__)),
         key="harmonia")
