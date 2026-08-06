# -*- coding: utf-8 -*-
"""서버 로그에서 **새로운** 문제만 골라낸다.

── 왜 필요한가 ──
기동 한 번에 WARN·ERROR 가 100 줄 가까이 나온다. 그런데 훑어보면 **우리 것이 하나도 없다** —
전부 서드파티 모드가 자기들끼리 내는 소리다. 그 상태로 두면 두 가지가 일어난다:

  ① 로그를 안 보게 된다. 매번 같은 100 줄이 나오니까.
  ② 진짜 오류가 그 안에 묻힌다.

②는 가정이 아니다. 2026-07-31 에 격노 버그를 로그에서 찾았는데, 그때 이 소음이 조금만 더
많았으면 못 찾았다. `docs/TODO.md` C절이 그걸 「로그 노이즈가 진짜 오류를 가린다」로 올렸다.

── 왜 로그 필터가 아니라 검사기인가 ──
log4j2 필터로 지우는 방법도 있다. 안 한다 — **지우면 필요할 때도 없다.** 필터는 조용히 진짜
문제까지 삼키고, 삼킨 걸 알아챌 방법이 없다. 여기서는 로그를 그대로 두고, «지금 무엇이 나오는
것이 정상인가»를 적어 둔 뒤 **그 밖의 것만** 보여준다.

이건 `docs/ARCHITECTURE.md` 「함정 검출」의 기준선과 같은 방식이다:
  > 새로 생긴 것만 눈에 띄게 하려면 지금 무엇이 나오는 게 정상인지 적어둬야 한다.
  > 안 그러면 매번 같은 줄이 나와서 결국 출력을 안 보게 된다.

── 기준선에 넣는 규칙 ──
**「왜 양성인가」를 반드시 적는다.** 이유 없이 넣으면 다음 사람이 다시 조사하거나, 더 나쁘게는
이 파일 자체를 안 믿게 된다. 조사해서 양성이라고 판단한 근거가 곧 그 줄의 존재 이유다.

**우리 코드(`lsrelics` · KubeJS)의 오류는 절대 넣지 않는다.** 그건 고칠 것이지 재울 것이 아니다.

사용:
    py tools/check_log.py                # server/logs/latest.log
    py tools/check_log.py <파일>          # 다른 로그
    py tools/check_log.py --all          # 기준선에 걸린 것도 전부 보여준다
"""
import os
import re
import sys
from collections import Counter

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_LOG = os.path.join(ROOT, 'server', 'logs', 'latest.log')

# ── 로그 파일은 UTF-8 이다 ──
# **콘솔 출력과 헷갈리지 말 것.** RCON·콘솔 창은 cp949 로 나오는데(윈도 코드페이지),
# `logs/latest.log` 는 log4j2 가 UTF-8 로 쓴다. cp949 로 읽으면 날짜의 「월」이 깨져
# (`068월2026` → `068썡2026`) 한글이 든 줄의 정규식이 조용히 어긋난다.
LOG_ENCODING = 'utf-8'

# 볼 줄: WARN · ERROR · FATAL, 그리고 우리 스크립트가 찍는 [LS-WARN].
RE_LEVEL = re.compile(r'/(WARN|ERROR|FATAL)\]')

# ── 우리 것 ──
# 처음엔 `KubeJS.*(ERROR|error)` 도 넣었는데, **「Reloaded with no KubeJS errors!」의 "errors" 에
# 걸렸다.** 도구가 자기 오탐을 내면 그게 제일 나쁘다 — 그 한 줄 때문에 매번 「새 문제 1건」이
# 뜨고, 결국 이 도구도 안 보게 된다(바로 이 도구를 만든 이유가 그거다).
# KubeJS 의 진짜 오류는 `[KubeJS Server/ERROR]` 라 위 RE_LEVEL 이 이미 잡는다.
RE_OURS = re.compile(r'\[LS-WARN\]')

# **INFO 인데 봐야 하는 줄.** KubeJS 의 로드 요약은 오류가 있어도 INFO 로 찍힌다:
#     Loaded 24/24 KubeJS server scripts ... with 2 errors and 0 warnings
# 레벨만 보면 이걸 놓친다. 0 이 아닌 숫자일 때만 잡는다.
RE_INFO_BAD = re.compile(r'with (?!0 errors and 0 warnings)\d+ errors? and \d+ warnings?')

# ── 기준선 ──
# (정규식, 왜 양성인가)
#
# 2026-08-06 에 모드 jar 를 직접 열어 확인한 결과다. 이유 없이 늘리지 말 것.
BASELINE = [
    (r"Reference map '.*' for .* could not be read",
     "믹스인 refmap 부재 — 배포 jar 는 refmap 을 안 싣는 경우가 흔하다. "
     "메시지 자신이 «개발 환경이면 무시해도 된다»고 말한다. 55종, 전부 서드파티."),

    (r"Error loading class: (org/violetmoon/quark|com/hollingsworth/arsnouveau|"
     r"xfacthd/framedblocks|mezz/jei|net/minecraft/client/|org/jetbrains/annotations)",
     "선택적 믹스인 대상 — 안 깐 모드(quark·ars nouveau·framedblocks)거나 "
     "**클라 전용 클래스**(jei GUI·client.gui.screens)다. 전용 서버에는 없는 게 정상이다."),

    (r"@Mixin target .* was not found (createbigcannons|regions_unexplored|ftbxmodcompat)",
     "위와 같다 — 있으면 붙이고 없으면 넘어가는 호환 믹스인이다."),

    (r"Static binding violation: PRIVATE @Overwrite .* from mod modernfix",
     "ModernFix 자기 최적화 믹스인. 가시성을 자동으로 올리고 계속 진행한다 — 동작에 영향 없다."),

    (r"Couldn't load advancements:",
     "**부모가 존재하지 않는 도전과제 4개.** 2026-08-06 에 jar 를 열어 확인했다:\n"
     "        · dungeons_arise:find_thornborn_towers / find_fishing_hut\n"
     "          → 부모 `dungeons_arise:find_small_prairie_house` 가 **jar 안에 없다**\n"
     "        · minecraft:wander_add_map / give_quest_trader_trade (dungeons-and-taverns)\n"
     "          → 부모 `minecraft:root` 는 **바닐라에 존재하지 않는다**(story/root·nether/root…)\n"
     "        둘 다 그 모드의 버그다. 게다가 뒤의 둘은 보상이 `nova_structures:choose_wander` 인데\n"
     "        그 모드를 안 깔았으므로 고쳐도 의미가 없다. 우리가 잃는 건 장식 도전과제 4개뿐이다.\n"
     "        ※ 남의 모드 파일을 덮어써 고치지 않는다 — 그쪽이 업데이트되면 그 덮어쓰기가 남는다."),

    (r"Skipping recipe (farmersdelight:melon_juice|farmersrespite:brewing/melon_juice_from_water), not a json object",
     "**의도된 레시피 제거다.** expandeddelight 가 `{\"neoforge:conditions\":[{\"type\":\"neoforge:never\"}]}` "
     "만 담은 파일로 두 레시피를 덮어쓴다 — NeoForge 의 표준 제거 관용구다.\n"
     "        레시피는 정상적으로 빠지고 있고, 경고만 나온다. 잃는 것 없다."),

    (r"Not all defined tags for registry .* cataclysm:needs_(black_steel|monstrosity)_tool",
     "Cataclysm 이 자기 블록 태그를 참조하는데 그 태그 파일을 안 실었다. 모드 자체 버그이고, "
     "해당 도구 요구 판정만 비어 있을 뿐 블록은 정상 동작한다."),

    (r"Detected \S+ that was registered with CREATURE mob category but was added under MONSTER",
     "바이옴 정의와 몹 등록 카테고리가 어긋난다는 NeoForge 경고. alexsmobs·cnb·regions_unexplored "
     "셋의 조합에서 나온다 — 스폰 캡 계산이 조금 어긋날 뿐이고 서드파티끼리의 문제다."),

    (r"Could not decode GlobalLootModifier with json id expandeddelight:add_loot_sniffer_digging",
     "expandeddelight 의 전리품 수정자 하나가 형식이 깨져 있다. 그 모드의 버그이고 "
     "스니퍼 발굴 전리품 추가가 안 되는 것뿐이다."),

    (r"Dye color white produced diffuse color -1",
     "Supplementaries 가 흰색 염료를 계산할 때 내는 자기 경고. 메시지 자신이 "
     "«이런 일은 없어야 한다»고 하지만 동작엔 영향이 없다."),

    (r"\[Debug Manager\] (Detected debug log level|Adjusting log level|Add new logger config)",
     "Easy NPC 가 자기 로그 레벨을 ALL → INFO 로 낮췄다는 알림. 오히려 소음을 줄이는 쪽이다."),

    (r"Failed to read pack metadata|Ignoring unknown|VanillaPackResourcesBuilder",
     "바닐라 팩 빌더가 없는 선택적 리소스를 건너뛴다는 알림."),

    (r"(VersionChecker|Failed to process update information|promos)",
     "NeoForge 업데이트 확인이 외부 JSON 을 못 읽었다. 인터넷/원격 파일 문제이고 서버와 무관하다."),

    (r"ModernFix|Applying Nashorn fix",
     "ModernFix 자기 상태 알림."),

    (r"KubeJS.*Loaded \d+/\d+ .* with 0 errors and 0 warnings",
     "정상 로드 줄이 WARN 패턴에 걸리는 경우를 막는다."),

    (r"Plugin \S+ does not (load on server side|have required mod '\S+' loaded), skipping",
     "KubeJS 플러그인 선별 — 클라 전용 플러그인(BuiltinKubeJSClientPlugin)과 안 깐 모드용 "
     "연동(ftbfiltersystem)을 건너뛴다는 알림이다. 건너뛰는 게 정상 동작이다."),
]

BASELINE = [(re.compile(p), why) for p, why in BASELINE]


def classify(line):
    """기준선에 걸리면 그 인덱스, 아니면 -1."""
    for i, (rx, _why) in enumerate(BASELINE):
        if rx.search(line):
            return i
    return -1


def main():
    args = [a for a in sys.argv[1:] if a != '--all']
    show_all = '--all' in sys.argv[1:]
    path = args[0] if args else DEFAULT_LOG

    if not os.path.exists(path):
        print('로그가 없다: %s' % path)
        return 2

    hits = Counter()
    new_lines = []
    ours = []

    with open(path, encoding=LOG_ENCODING, errors='replace') as f:
        for i, raw in enumerate(f, 1):
            line = raw.rstrip('\n')
            is_level = RE_LEVEL.search(line)
            is_ours = RE_OURS.search(line)
            is_info_bad = RE_INFO_BAD.search(line)
            if not is_level and not is_ours and not is_info_bad:
                continue

            # 우리 코드가 낸 것은 **기준선을 타지 않는다.** 고칠 것이지 재울 것이 아니다.
            if is_ours:
                ours.append((i, line))
                continue
            # 스크립트 로드에 오류가 있으면 그것도 우리 것이다 (INFO 로 찍혀도).
            if is_info_bad:
                ours.append((i, line))
                continue

            k = classify(line)
            if k >= 0:
                hits[k] += 1
            else:
                new_lines.append((i, line))

    # 다른 드라이브의 로그를 넘길 수 있다(임시 폴더 등). relpath 는 그때 예외를 던진다.
    try:
        shown = os.path.relpath(path, ROOT)
    except ValueError:
        shown = path
    print('로그: %s' % shown)
    print('기준선 %d 종 · 걸린 줄 %d · **새로운 줄 %d** · 우리 경고 %d'
          % (len(BASELINE), sum(hits.values()), len(new_lines), len(ours)))

    if ours:
        print('\n=== 우리 코드의 경고 (lsWarn) — 이건 고칠 것이다 ===')
        for i, line in ours[:40]:
            print('  L%-6d %s' % (i, line.strip()[:150]))
        if len(ours) > 40:
            print('  … 외 %d 줄' % (len(ours) - 40))

    if new_lines:
        print('\n=== 기준선에 없는 줄 — 새 문제이거나, 조사해서 기준선에 넣을 것 ===')
        for i, line in new_lines[:60]:
            print('  L%-6d %s' % (i, line.strip()[:160]))
        if len(new_lines) > 60:
            print('  … 외 %d 줄' % (len(new_lines) - 60))
    else:
        print('\n>>> 새로운 문제 없음.')

    if show_all:
        print('\n=== 기준선 내역 (--all) ===')
        for i, (rx, why) in enumerate(BASELINE):
            print('\n  [%d] %d줄  /%s/' % (i, hits[i], rx.pattern[:70]))
            for wl in why.split('\n'):
                print('      %s' % wl)

    print('\n※ 새 줄이 나오면 **먼저 조사한다.** 우리 것이면 고치고, 서드파티 것이면')
    print('   «왜 양성인가»를 적어 BASELINE 에 넣는다. 이유 없이 넣으면 이 도구를 안 믿게 된다.')

    return 1 if (new_lines or ours) else 0


if __name__ == '__main__':
    sys.exit(main())
