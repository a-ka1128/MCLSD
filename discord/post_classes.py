# Last Stardust — 8종 클래스 카드를 한 번에 게시한다.
#
# 클래스마다 채널(스레드)이 다르므로 웹훅도 각자 자기 파일이 갖는다.
# 그래서 이 파일은 전송 코드를 따로 갖지 않고, post_*.py 를 순서대로 실행해 주기만 한다.
#   · 카드 내용(이름·로어·스킬표·색)   → 각 post_*.py 의 CLASS
#   · 어디로 보낼지(웹훅·스레드)        → 각 post_*.py 의 WEBHOOK / THREAD_ID
#   · 실제 전송·서식                    → lsdiscord.py
#
# 사용법
#   1) 각 post_*.py 에 그 채널의 웹훅을 채운다 (포럼이면 THREAD_ID 도)
#   2) py post_classes.py            ← 전부 게시
#      py post_atlas.py              ← 하나만 게시하고 싶을 때
#
# 웹훅이 비어 있는 클래스는 조용히 건너뛴다 — 일부만 채워두고 돌려도 안전하다.

import importlib
import os
import time

from lsdiscord import post

# 게시 순서 (디스코드에 이 순서로 올라간다)
MODULES = [
    "post_atlas",
    "post_orion",
    "post_urania",
    "post_kratos",
    "post_helios",
    "post_hygieia",
    "post_erebos",
    "post_cuchulainn",
]

BASE_DIR = os.path.dirname(os.path.abspath(__file__))


def main():
    posted, skipped = 0, []
    for name in MODULES:
        mod = importlib.import_module(name)
        webhook = getattr(mod, "WEBHOOK", "")
        cls = getattr(mod, "CLASS", None)
        if not webhook or not cls:
            skipped.append(cls.get("name", name) if cls else name)
            continue
        # 모듈명에서 키를 뽑는다("post_atlas" -> "atlas") — 웹훅 키와 같은 이름이라
        # posted_ids.json 이 개별 실행(py post_atlas.py)과 같은 칸을 쓴다.
        post(webhook, cls, thread_id=getattr(mod, "THREAD_ID", ""), base_dir=BASE_DIR,
             key=name.removeprefix("post_"))
        posted += 1
        time.sleep(0.7)   # 웹훅 레이트리밋 여유

    print(f"\n완료 : {posted}종 게시")
    if skipped:
        print("건너뜀(웹훅 없음):", ", ".join(skipped))


if __name__ == "__main__":
    main()
