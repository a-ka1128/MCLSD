# -*- coding: utf-8 -*-
"""Last Stardust 도전과제를 **한 표에서** 생성한다.

생성물:
    moddev/lsrelics/src/main/resources/data/lsrelics/advancement/*.json
    moddev/lsrelics/src/main/resources/assets/lsrelics/lang/{ko_kr,en_us}.json 의 advancement 구획

── 왜 손으로 안 쓰나 ──
도전과제 하나에 **파일 셋**이 필요하다: JSON · 한국어 제목/설명 · 영어 제목/설명.
28개면 84 자리이고, 그중 하나만 어긋나면 게임 안에 `advancement.lsrelics.gate2.title` 같은
**날 키가 그대로 뜬다.** 그것도 조용히 — 아무 오류 없이.
표 하나에서 셋을 같이 만들면 어긋날 자리가 없다.

(같은 이유로 `ls_voice.js` 의 대사도 테이블 하나에 모여 있고, `FateCatalog` 도 그렇다.)

── 왜 모드 jar 안인가 ──
FTB Quests 는 `world/ftbquests/` 라 **월드 리셋에 날아간다.** 도전과제는 jar 안이라 남는다.
둘은 겹치지 않는다 — 이건 «지나온 길의 기록», 그쪽은 «할 일과 이야기»다.

── 전부 `minecraft:impossible` 이다 ──
커스텀 Criterion 을 자바로 짜지 않는다. 조건은 이미 스크립트·모드가 판정하고 있으므로,
그 자리에서 `/advancement grant` 하면 된다(`lsAdv` — ls_util.js).
**판정이 두 곳에 생기지 않는다**는 뜻이기도 하다.

사용:
    py tools/gen_advancements.py           # 미리보기 (파일 안 씀)
    py tools/gen_advancements.py --write   # 실제로 쓴다
"""
import io
import json
import os
import sys

sys.stdout.reconfigure(encoding='utf-8', errors='replace')

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'moddev', 'lsrelics', 'src', 'main', 'resources')
ADV_DIR = os.path.join(RES, 'data', 'lsrelics', 'advancement')
LANG_DIR = os.path.join(RES, 'assets', 'lsrelics', 'lang')

NS = 'lsrelics'
BACKGROUND = 'minecraft:textures/gui/advancements/backgrounds/nether.png'

# ── 표 ──
# (id, 부모, 아이콘, 프레임, 한국어 제목, 한국어 설명, 영어 제목, 영어 설명)
#
# 부모 None = 뿌리. 프레임: 'task'(기본) · 'goal'(둥근 테두리) · 'challenge'(뾰족한 테두리 + 팡파레)
#
# ⚠️ **아이콘 id 가 없는 아이템이면 그 도전과제는 로드에 실패한다.** 그리고 실패는 조용하다 —
#    「Couldn't load advancements」 한 줄이 로그에 뜰 뿐이다(서드파티 모드 넷이 지금 그 상태다).
#    새로 넣을 때는 `/give @s <id>` 로 먼저 확인할 것.
#
# ⚠️ **한 사람이 하나만 가질 수 있는 것은 넣지 않았다** (가호 8종 · 유물 8종).
#    넣으면 화면에 영영 안 열리는 칸이 일곱 개 남고, 그건 기록이 아니라 「못 한 것」으로 보인다.
#    「어느 가호인가」는 칭호와 이름표가 이미 말해준다.
TREE = [
    ('root', None, 'lsrelics:hearthstone', 'task',
     '별지기가 되다', '별의 가호를 받아들였다. 꺼진 별 하나가 너를 알아본다.',
     'Starkeeper', 'You accepted a Star Blessing. One dead star has noticed you.'),

    # ── 성장 ──
    ('relic', 'root', 'lsrelics:guardian', 'task',
     '유물이 깨어나다', '제단에서 네 가호의 유물을 받았다.',
     'The Relic Wakes', 'You received your blessing\'s relic at the altar.'),
    ('star2', 'relic', 'kubejs:star_seal', 'task',
     '두 번째 별', '유물에 두 번째 별을 새겼다 — 이동기가 열린다.',
     'Second Star', 'A second star carved into your relic — mobility unlocked.'),
    ('star3', 'star2', 'kubejs:star_seal', 'task',
     '세 번째 별', '세 번째 별. 새 힘이 하나 더 늘었다.',
     'Third Star', 'A third star. One more power awakens.'),
    ('star4', 'star3', 'kubejs:star_seal', 'goal',
     '네 번째 별', '궁극기가 깨어났다.',
     'Fourth Star', 'Your ultimate has awakened.'),
    ('star5', 'star4', 'minecraft:nether_star', 'challenge',
     '다섯 별이 모두', '유물이 완성됐다. 더 새길 별이 없다.',
     'All Five Stars', 'The relic is complete. No stars remain to carve.'),

    # ── 공성 ──
    ('siege_first', 'root', 'minecraft:shield', 'task',
     '첫 밤을 넘기다', '맨몸으로 첫 공세를 버텨냈다.',
     'Survived the First Night', 'You held the first assault bare-handed.'),
    ('siege_grand', 'siege_first', 'minecraft:netherite_sword', 'goal',
     '대공세를 막다', '가장 큰 물결을 성벽 앞에서 끊었다.',
     'Grand Assault Repelled', 'You broke the largest wave at the walls.'),
    ('wall_break', 'siege_first', 'minecraft:cracked_stone_bricks', 'task',
     '무너진 성벽', '돌은 다시 쌓으면 그만이다. 너희는 아니다.',
     'The Wall Fell', 'Stone can be rebuilt. You cannot.'),
    ('threat_max', 'siege_first', 'minecraft:wither_skeleton_skull', 'goal',
     '가장 짙은 어둠', '위협도가 끝까지 차오른 밤을 겪었다.',
     'Deepest Dark', 'You saw a night with the threat gauge full.'),

    # ── 관문 ──
    ('gate1', 'root', 'kubejs:rift_essence', 'task',
     '첫 번째 뿌리', '첫 봉인을 풀었다. 남은 뿌리는 셋.',
     'First Root', 'The first seal is broken. Three roots remain.'),
    ('gate2', 'gate1', 'kubejs:rift_essence', 'task',
     '두 번째 뿌리', '하늘이 조금 밝아졌다 — 그만큼 그늘도 깊어졌다.',
     'Second Root', 'The sky brightened — and the shadows deepened with it.'),
    ('gate3', 'gate2', 'kubejs:rift_essence', 'task',
     '세 번째 뿌리', '이제 놈들이 먼저 찾아온다.',
     'Third Root', 'Now they come looking for you.'),
    ('gate4', 'gate3', 'kubejs:rift_key_gold', 'goal',
     '네 뿌리가 모두 재가 되다', '아래로 내려가는 길이 열렸다.',
     'All Four Roots Burned', 'The way down has opened.'),
    ('finale', 'gate4', 'minecraft:dragon_egg', 'challenge',
     '가장 긴 밤', '끝내 새벽을 되찾았다.',
     'The Longest Night', 'In the end, you took back the dawn.'),

    # ── 마을 ──
    ('town_ramparts', 'root', 'minecraft:stone_bricks', 'task',
     '방벽 완성', '방벽을 끝까지 올렸다.',
     'Ramparts Complete', 'The ramparts stand at full height.'),
    ('town_workshop', 'root', 'minecraft:smithing_table', 'task',
     '공방 완성', '공방을 끝까지 올렸다.',
     'Workshop Complete', 'The workshop is fully built.'),
    ('town_sanctum', 'root', 'minecraft:enchanting_table', 'task',
     '성소 완성', '성소를 끝까지 올렸다.',
     'Sanctum Complete', 'The sanctum is fully built.'),
    ('town_districts', 'root', 'minecraft:bell', 'task',
     '구역 완성', '구역을 끝까지 올렸다.',
     'Districts Complete', 'The districts are fully built.'),
    ('town_all', 'town_districts', 'minecraft:beacon', 'challenge',
     '성역 재건', '네 갈래를 모두 끝까지 올렸다. 성역이 다시 섰다.',
     'Sanctuary Rebuilt', 'All four tracks at full. The sanctuary stands again.'),

    # ── 사람과 땅 ──
    ('rescue_first', 'root', 'minecraft:lead', 'task',
     '첫 생존자', '갇혀 있던 사람 하나를 성역으로 데려왔다.',
     'First Survivor', 'You brought one captive home to the sanctuary.'),
    ('rescue_all', 'rescue_first', 'minecraft:cake', 'goal',
     '사람이 돌아왔다', '여섯 명 모두 성역에 정착했다.',
     'The People Return', 'All six have settled in the sanctuary.'),
    ('beacon_first', 'root', 'minecraft:beacon', 'task',
     '첫 봉화', '어두운 땅 한 곳에 불을 밝혔다.',
     'First Beacon', 'You lit a fire in one dark place.'),
    ('beacon_four', 'beacon_first', 'minecraft:soul_lantern', 'goal',
     '되찾은 땅', '봉화 넷. 지도 위에 수복이 보인다.',
     'Reclaimed Ground', 'Four beacons. The map shows what you took back.'),

    # ── 그 밖에 ──
    ('bounty_first', 'root', 'minecraft:paper', 'task',
     '첫 현상금', '게시판의 의뢰 하나를 끝냈다.',
     'First Bounty', 'You closed one contract from the board.'),
    ('casino_win', 'root', 'minecraft:emerald', 'task',
     '별똥말이 들어왔다', '경마에서 적중했다. 판돈은 네 것이다.',
     'The Long Shot', 'Your horse came in. The pot is yours.'),
    ('wipe', 'root', 'minecraft:soul_torch', 'task',
     '별빛이 모두 꺼졌다', '전멸했다. ……익숙해지지는 말 것.',
     'All Lights Out', 'You were wiped out. ...Try not to get used to it.'),
]


def adv_json(entry):
    aid, parent, icon, frame, _kt, _kd, _et, _ed = entry
    out = {}
    if parent:
        out['parent'] = '%s:%s' % (NS, parent)
    # 전부 impossible 이다 — 판정은 이미 스크립트·모드가 하고 있고, 그 자리에서 grant 한다.
    out['criteria'] = {'granted': {'trigger': 'minecraft:impossible'}}
    out['requirements'] = [['granted']]

    display = {
        'icon': {'id': icon, 'count': 1},
        'title': {'translate': 'advancement.%s.%s.title' % (NS, aid)},
        'description': {'translate': 'advancement.%s.%s.description' % (NS, aid)},
    }
    if frame != 'task':
        display['frame'] = frame
    if parent is None:
        display['background'] = BACKGROUND
        # 뿌리는 토스트를 띄우지 않는다 — 가호를 고른 순간엔 이미 타이틀·소리가 나간다.
        display['show_toast'] = False
        display['announce_to_chat'] = False
    else:
        display['show_toast'] = True
        # 채팅 방송은 끈다. 칭호가 이미 방송하고, 둘 다 켜면 같은 순간에 두 줄이 뜬다.
        display['announce_to_chat'] = False
    out['display'] = display
    # sends_telemetry_event 는 바닐라 전용이다. 모드가 켜면 몰래 통계를 보내는 꼴이 된다.
    out['sends_telemetry_event'] = False
    return out


UTIL_JS = os.path.join(ROOT, 'server', 'kubejs', 'server_scripts', 'ls_util.js')


def check_whitelist(known):
    """`ls_util.js` 의 `LS_ADV` 가 이 표와 같은지 본다. 다르면 무엇이 다른지 찍는다."""
    if not os.path.exists(UTIL_JS):
        print('ls_util.js 가 없다 — 화이트리스트 대조를 건너뛴다')
        return True
    with io.open(UTIL_JS, encoding='utf-8') as f:
        src = f.read()
    i = src.find('var LS_ADV = [')
    if i < 0:
        print('ls_util.js 에 `var LS_ADV = [` 가 없다 — 화이트리스트 대조 실패')
        return False
    j = src.find(']', i)
    body = src[i + len('var LS_ADV = ['):j]
    listed = set()
    for tok in body.replace('\n', ' ').split(','):
        tok = tok.strip().strip("'\"")
        if tok:
            listed.add(tok)

    missing = known - listed          # 표엔 있는데 스크립트가 못 주는 것
    extra = listed - known            # 스크립트는 줄 수 있는데 표에 없는 것 = 죽은 id
    if not missing and not extra:
        print('화이트리스트 일치 (ls_util.js LS_ADV %d개)' % len(listed))
        return True
    if missing:
        print('ls_util.js LS_ADV 에 빠짐 (지급할 방법이 없다): %s' % ', '.join(sorted(missing)))
    if extra:
        print('ls_util.js LS_ADV 에만 있음 (없는 도전과제를 주려 한다): %s' % ', '.join(sorted(extra)))
    return False


def main():
    write = '--write' in sys.argv[1:]

    # id 중복·부모 없음을 먼저 막는다. 부모가 없으면 **조용히 로드에 실패한다** —
    # 지금 서드파티 모드 넷이 정확히 그 상태다(docs/TODO.md C절).
    ids = [e[0] for e in TREE]
    dup = {i for i in ids if ids.count(i) > 1}
    if dup:
        print('id 중복: %s' % ', '.join(sorted(dup)))
        return 2
    known = set(ids)
    roots = 0
    for aid, parent, icon, frame, *_ in TREE:
        if parent is None:
            roots += 1
        elif parent not in known:
            print('부모가 없다: %s -> %s' % (aid, parent))
            return 2
        if frame not in ('task', 'goal', 'challenge'):
            print('프레임이 이상하다: %s -> %s' % (aid, frame))
            return 2
    if roots != 1:
        print('뿌리는 하나여야 한다 (지금 %d개)' % roots)
        return 2

    print('도전과제 %d종 · 뿌리 1 · 프레임 goal %d · challenge %d'
          % (len(TREE),
             sum(1 for e in TREE if e[3] == 'goal'),
             sum(1 for e in TREE if e[3] == 'challenge')))

    # ── `ls_util.js` 의 화이트리스트와 대조 ──
    # 지급은 `lsAdv(server, target, id)` 하나로만 한다. 그 함수가 id 를 걸러야 오타가 조용히
    # 죽지 않는데(`/advancement grant` 는 없는 id 에 아무 말도 안 한다), 목록이 두 곳이면
    # 언젠가 어긋난다. **생성하지 않고 대조만 한다** — 손으로 읽히는 자리에 두는 편이 낫고,
    # 어긋나면 여기서 알려준다.
    if not check_whitelist(known):
        return 2

    if not write:
        print('\n(미리보기다. 실제로 쓰려면 --write)')
        for aid, parent, icon, frame, kt, _kd, *_ in TREE:
            print('  %-16s %-14s %-28s %s' % (aid, parent or '(뿌리)', icon, kt))
        return 0

    os.makedirs(ADV_DIR, exist_ok=True)
    for entry in TREE:
        p = os.path.join(ADV_DIR, entry[0] + '.json')
        with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(adv_json(entry), f, ensure_ascii=False, indent=2)
            f.write('\n')
    print('JSON %d개 → %s' % (len(TREE), os.path.relpath(ADV_DIR, ROOT)))

    # ── lang ──
    # 기존 파일의 다른 키는 건드리지 않는다. `advancement.lsrelics.` 로 시작하는 것만 갈아끼운다.
    for fname, ti, di in (('ko_kr.json', 4, 5), ('en_us.json', 6, 7)):
        p = os.path.join(LANG_DIR, fname)
        data = {}
        if os.path.exists(p):
            with io.open(p, encoding='utf-8') as f:
                data = json.load(f)
        for k in [k for k in data if k.startswith('advancement.%s.' % NS)]:
            del data[k]
        for e in TREE:
            data['advancement.%s.%s.title' % (NS, e[0])] = e[ti]
            data['advancement.%s.%s.description' % (NS, e[0])] = e[di]
        with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=True)
            f.write('\n')
        print('lang %-10s → 키 %d개' % (fname, len(TREE) * 2))

    print('\n※ 아이콘 id 가 실제로 없는 아이템이면 그 도전과제는 **조용히 로드에 실패한다.**')
    print('   기동 뒤 `py tools/check_log.py` 로 「Couldn\'t load advancements」 가 없는지 볼 것.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
