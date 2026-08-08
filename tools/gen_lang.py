# 무기 이름 한글화 리소스팩 생성기
#
# `server/mods/*.jar` 를 읽어 **번역이 안 된 무기 이름**을 찾아, 아래 표로 번역해서
# `client/resourcepacks/LS-Korean/` 리소스팩을 통째로 다시 만든다.
#
# ── 왜 리소스팩인가 ──
# 마인크래프트는 lang 파일을 **키 단위로 병합**한다. 나중 팩이 앞의 것을 키마다 덮으므로
# 고칠 키만 넣으면 되고, 모드 jar 를 건드릴 필요가 없다(건드리면 업데이트 때 전부 날아간다).
#
# ── 왜 「번역이 안 된」을 따로 판정하나 ──
# ko_kr.json 이 **있는데도 값이 영어인** 경우가 흔하다. 실제로 Simply Swords 는
# ko_kr 이 있는데 1293개 중 459개만 채워져 있고, 창작 탭 이름은 키가 있는데 값이
# "Simply Swords" 그대로다. 그래서 「ko_kr 파일이 있나」가 아니라
# 「그 키의 한국어 값이 영어와 다른가」로 봐야 한다.
#
# ── 사용 ──
#   py tools/gen_lang.py          # 생성 + 미번역 잔여 보고
#   py tools/gen_lang.py --zip    # 위 + 배포용 zip 까지
#
# 번역을 고치고 싶으면 아래 표만 고치고 다시 돌리면 된다. 생성물은 손으로 고치지 말 것 —
# 다음 실행에 덮어써진다.

import json
import re
import sys
import zipfile
from pathlib import Path

# Simply Swords 는 항목이 300 이 넘어 표를 따로 뒀다. 여기 같이 두면 «규칙» 과 «번역» 이
# 섞여서 도구를 못 읽는다. 저 파일은 무엇으로 옮기는지만, 이 파일은 어떻게 찍는지만 담는다.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from lang_simplyswords import TABLE as SS_TABLE  # noqa: E402

# 콘솔이 cp949 라 한글·기호를 그대로 찍으면 UnicodeEncodeError 로 죽는다.
# 파일은 어차피 UTF-8 로 쓰지만, 「못 옮긴 것」 보고가 콘솔에서 터지면 그게 제일 필요한 순간에 안 보인다.
sys.stdout.reconfigure(encoding='utf-8')

ROOT = Path(__file__).resolve().parent.parent
MODS = ROOT / 'server' / 'mods'
PACK = ROOT / 'client' / 'resourcepacks' / 'LS-Korean'
PACK_FORMAT = 34          # 1.21.1
LANG_EN = 'en_us.json'
LANG_KO = 'ko_kr.json'

# ── 재료 (Epic Knights 는 재료 11종 × 종류 20여종의 곱이라 규칙으로 처리한다) ──
MATS = {
    'Wooden': '나무', 'Wood': '나무', 'Stone': '돌', 'Iron': '철',
    'Golden': '황금', 'Gold': '황금', 'Diamond': '다이아몬드', 'Netherite': '네더라이트',
    'Silver': '은', 'Copper': '구리', 'Steel': '강철', 'Tin': '주석', 'Bronze': '청동',
    'Runic': '룬',
}

# ── 종류 ──
# 실존 무기 이름은 «음차» 를 기본으로 한다. 「츠바이핸더」를 「양손대검」으로 옮기면
# 같은 모드 안의 클레이모어·바스타드소드와 구분이 사라진다 — 이름이 곧 분류인 모드다.
TYPES = {
    'Bastard Sword': '바스타드 소드', 'Claymore': '클레이모어', 'Estoc': '에스톡',
    'Katzbalger': '카츠발거', 'Short Sword': '숏 소드', 'Zweihander': '츠바이핸더',
    'Stiletto': '스틸레토', 'Messer': '메서',
    'Halberd': '할버드', 'Concave Edged Halberd': '곡날 할버드',
    'Warglaive': '워글레이브', 'Guisarme': '기사르메', 'Ranseur': '랜서',
    'Pike': '파이크', 'Lance': '랜스', 'Lochaber Axe': '로카버 도끼',
    'Flail': '플레일', 'Morningstar': '모닝스타', 'Heavy Mace': '중형 철퇴',
    'Heavy War Hammer': '중형 전투망치', 'Lucerne Hammer': '루체른 해머',
    'Flame-Bladed Sword': '화염날 검',
    'Tablet': '서판',
}

# ── 고유 이름 (규칙으로 안 되는 것) ──
# 키는 `모드:lang키` 전체. 모드가 달라도 같은 영어를 다르게 옮길 수 있어야 해서 값이 아니라 키로 건다.
OVERRIDES = {
    # Simply Swords — 고유 무기
    'simplyswords:item.simplyswords.arcanethyst': '비전자정',
    'simplyswords:item.simplyswords.caelestis': '카엘레스티스',
    'simplyswords:item.simplyswords.chompolotl': '촘폴로틀',
    'simplyswords:item.simplyswords.dreadtide': '공포의 물결',
    'simplyswords:item.simplyswords.emberlash': '잉걸 채찍',
    'simplyswords:item.simplyswords.enigma': '수수께끼',
    'simplyswords:item.simplyswords.flamewind': '불꽃바람',
    'simplyswords:item.simplyswords.frostfall': '서리내림',
    'simplyswords:item.simplyswords.harbinger': '전조',
    'simplyswords:item.simplyswords.hiveheart': '벌집심장',
    'simplyswords:item.simplyswords.icewhisper': '얼음속삭임',
    'simplyswords:item.simplyswords.livyatan': '리바이어탄',
    'simplyswords:item.simplyswords.magiblade': '마력검',
    'simplyswords:item.simplyswords.magiscythe': '마력낫',
    'simplyswords:item.simplyswords.magispear': '마력창',
    'simplyswords:item.simplyswords.molten_edge': '용융의 칼날',
    'simplyswords:item.simplyswords.ribboncleaver': '리본절단검',
    'simplyswords:item.simplyswords.shadowsting': '그림자침',
    'simplyswords:item.simplyswords.soulpyre': '영혼불꽃',
    'simplyswords:item.simplyswords.stars_edge': '별의 칼날',
    'simplyswords:item.simplyswords.sunfire': '태양불꽃',
    'simplyswords:item.simplyswords.tempest': '폭풍',
    'simplyswords:item.simplyswords.thunderbrand': '뇌명검',
    'simplyswords:item.simplyswords.waxweaver': '밀랍직조검',
    'simplyswords:item.simplyswords.whisperwind': '속삭이는 바람',
    'simplyswords:item.simplyswords.wickpiercer': '심지관통검',
    'simplyswords:item.simplyswords.wraithfang': '망령의 송곳니',
    # Simply Swords — 리치검 3단계 (같은 무기의 성장 단계라 어미를 맞춘다)
    'simplyswords:item.simplyswords.slumbering_lichblade': '잠든 리치검',
    'simplyswords:item.simplyswords.waking_lichblade': '깨어나는 리치검',
    'simplyswords:item.simplyswords.awakened_lichblade': '각성한 리치검',
    # Simply Swords — 유물·보석 계열
    'simplyswords:item.simplyswords.dormant_relic': '잠든 유물',
    'simplyswords:item.simplyswords.decaying_relic': '부식된 유물',
    'simplyswords:item.simplyswords.tainted_relic': '오염된 유물',
    'simplyswords:item.simplyswords.righteous_relic': '정의로운 유물',
    'simplyswords:item.simplyswords.contained_remnant': '봉인된 잔재',
    'simplyswords:item.simplyswords.empowered_remnant': '강화된 잔재',
    'simplyswords:item.simplyswords.tampered_remnant': '변질된 잔재',
    'simplyswords:item.simplyswords.runefused_gem': '룬융합 보석',
    'simplyswords:item.simplyswords.netherfused_gem': '네더융합 보석',
    'simplyswords:item.simplyswords.runic_tablet': '룬 서판',
    # Simply Swords — 무기 툴팁에 같이 뜨는 UI 문구.
    # 이름만 한글이고 바로 밑줄이 영어면 툴팁이 반쪽이 된다. 개수가 적어 같이 옮긴다.
    'simplyswords:item.simplyswords.awakening': '각성 LV: %d ',
    'simplyswords:item.simplyswords.greater_runic_power': '상위',
    'simplyswords:item.simplyswords.runefused_power': '룬융합 힘: ',
    'simplyswords:item.simplyswords.onrightclickheld': 'ꭀ 우클릭 유지: ',
    'simplyswords:item.simplyswords.empty_runic_slot': '◇§7  룬 홈',
    'simplyswords:item.simplyswords.empty_nether_slot': '◇§7  네더 홈',
    'simplyswords:item.simplyswords.filled_runic_slot': '[ 룬융합 보석 ]',
    'simplyswords:item.simplyswords.gem_description': '고유 무기의 대응하는',
    'simplyswords:item.simplyswords.gem_description2': '보석 홈에 끼울 수 있습니다.',
    'simplyswords:item.simplyswords.remnant_description': '룬 서판이나 네더라이트 주괴로 제련하면',
    'simplyswords:item.simplyswords.remnant_description2': '고유 무기에 끼울 수 있는 보석이 됩니다.',
    'simplyswords:item.simplyswords.contained_remnant_description': '잔재에서 뽑아내 안정시킨 에너지.',
    'simplyswords:item.simplyswords.contained_remnant_description3': '어떤 표면 가까이에서 반응하는 것 같다.',
    'simplyswords:item.simplyswords.contained_remnant_description5': '지니고 다니면 더 알게 될지도 모른다.',
    'simplyswords:item.simplyswords.contained_remnant_description7': '룬 서판과 함께 지닌 채 엔드를 탐험해 보라.',
    'simplyswords:item.simplyswords.tampered_remnant_description3': '무언가 손을 탄 흔적이 있다...',

    # Epic Knights — 규칙 밖 단독 이름
    'magistuarmory:item.magistuarmory.club': '곤봉',
    'magistuarmory:item.magistuarmory.barbedclub': '가시 곤봉',
    'magistuarmory:item.magistuarmory.blacksmith_hammer': '대장장이 망치',
    'magistuarmory:item.magistuarmory.heavy_crossbow': '중형 석궁',
    'magistuarmory:item.magistuarmory.longbow': '장궁',
    # ※ 메서(`messer_sword`)는 표시 이름이 재료 없이 그냥 "Messer" 라 TYPES 규칙이 그대로 잡는다.
    #    여기 따로 적을 필요가 없었다 — 안 쓰인 OVERRIDES 보고가 그걸 알려줬다.
    'magistuarmory:item.magistuarmory.noble_sword': '귀족 검',
    'magistuarmory:item.magistuarmory.rusted_bastardsword': '녹슨 바스타드 소드',
    'magistuarmory:item.magistuarmory.rusted_heavymace': '녹슨 중형 철퇴',

    # Aether — 무기·도구 세트. 세트 안에서 검만 한글이면 더 어색해서 도끼·곡괭이도 같이 옮긴다.
    'aether:item.aether.skyroot_sword': '스카이루트 검',
    'aether:item.aether.skyroot_axe': '스카이루트 도끼',
    'aether:item.aether.skyroot_pickaxe': '스카이루트 곡괭이',
    'aether:item.aether.holystone_sword': '성석 검',
    'aether:item.aether.holystone_axe': '성석 도끼',
    'aether:item.aether.holystone_pickaxe': '성석 곡괭이',
    'aether:item.aether.zanite_sword': '자나이트 검',
    'aether:item.aether.zanite_axe': '자나이트 도끼',
    'aether:item.aether.zanite_pickaxe': '자나이트 곡괭이',
    'aether:item.aether.gravitite_sword': '그래비타이트 검',
    'aether:item.aether.gravitite_axe': '그래비타이트 도끼',
    'aether:item.aether.gravitite_pickaxe': '그래비타이트 곡괭이',
    'aether:item.aether.valkyrie_axe': '발키리 도끼',
    'aether:item.aether.valkyrie_pickaxe': '발키리 곡괭이',
    'aether:item.aether.valkyrie_lance': '발키리 랜스',
    'aether:item.aether.flaming_sword': '화염 검',
    'aether:item.aether.lightning_sword': '번개 검',
    'aether:item.aether.lightning_knife': '번개 단검',
    'aether:item.aether.holy_sword': '성검',
    'aether:item.aether.vampire_blade': '흡혈검',
    'aether:item.aether.candy_cane_sword': '사탕지팡이 검',
    'aether:item.aether.phoenix_bow': '불사조 활',
    'aether:item.aether.cloud_staff': '구름 지팡이',
    'aether:item.aether.nature_staff': '자연의 지팡이',
    'aether:item.aether.hammer_of_kingbdogz': '킹브도그즈의 망치',

    # Deeper and Darker
    'deeperdarker:item.deeperdarker.resonarium_sword': '레조나리움 검',
    'deeperdarker:item.deeperdarker.resonarium_axe': '레조나리움 도끼',
    'deeperdarker:item.deeperdarker.resonarium_pickaxe': '레조나리움 곡괭이',
    'deeperdarker:item.deeperdarker.sonorous_staff': '공명의 지팡이',

    # 그 밖
    # ※ 키는 sculptor_staff 인데 표시 이름은 Geomancer Staff 다 — 키만 보고 짐작하면 못 찾는다.
    'mowziesmobs:item.mowziesmobs.sculptor_staff': '지술사의 지팡이',
    # 잿불 검은 키가 5개인데 표시 이름이 전부 같다(모드 쪽 중복). 다섯 다 같은 값을 준다.
    'cnb:item.cnb.cinder_sword': '잿불 검',
    'cnb:item.cnb.cinder_sword_1': '잿불 검',
    'cnb:item.cnb.cinder_sword_2': '잿불 검',
    'cnb:item.cnb.cinder_sword_3': '잿불 검',
    'cnb:item.cnb.cinder_sword_4': '잿불 검',
    'bosses_of_mass_destruction:item.bosses_of_mass_destruction.earthdive_spear': '대지잠행 창',
    'undergarden:item.undergarden.spear': '창',
    'wan_ancient_beasts:item.wan_ancient_beasts.ancient_club': '고대의 곤봉',
    'cnb:item.cnb.cactem_spear': '캑템 창',
}

# ── 일부러 안 옮기는 것 ──
# 무기 어휘에 걸렸지만 무기가 아니다. 여기 적어 두지 않으면 매번 「미번역」으로 다시 보고돼서
# 진짜 빠뜨린 것과 섞인다. 이유를 같이 적는 게 이 표의 존재 이유다.
SKIP = {
    'friendsandfoes': '구리 버튼·피뢰침 — 「rod」 가 무기 어휘에 걸린 오탐',
    'mcwroofs': '지붕용 망치 — 건축 도구',
    'dnt': '삼지창 시련 열쇠 — 열쇠지 무기가 아니다',
}
SKIP_KEYS = {
    'magistuarmory:item.magistuarmory.swords_pattern': '깃발 무늬 — 장식',
    'magistuarmory:item.magistuarmory.rondel_decoration': '장식 부품',
    'magistuarmory:item.magistuarmory.spike_decoration': '장식 부품',
    'wan_ancient_beasts:item.wan_ancient_beasts.spike_armor_trim_smithing_template': '대장기술 형판',
    'wan_ancient_beasts:item.wan_ancient_beasts.spike_pottery_sherd': '도자기 조각',
    'cnb:item.cactem_spear.throw': '「창이 날아간다」 — 무기 이름이 아니라 안내 문구',
}

# 무기로 볼 어휘. 실존 무기명이 많아 길지만, 넓게 잡고 SKIP 으로 걸러내는 편이
# 좁게 잡아 조용히 빠뜨리는 것보다 낫다.
# 서식 문자. `%d%%` 를 `%d` + `%%` 로 나눠 잡도록 `%%` 를 먼저 둔다.
FMT = re.compile(r'%%|%\d*[dsf]')

WEAPON_RE = re.compile(
    r'sword|axe|blade|spear|halberd|glaive|katana|dagger|bow\b|hammer|mace|scythe|rapier|'
    r'club|lance|pike|flail|whip|staff|cutlass|claymore|sabre|saber|trident|warglaive|knife|'
    r'crossbow|polearm|chakram|sickle|kanabo|stiletto|katzbalger|falchion|estoc|zweihander|'
    r'bardiche|voulge|morningstar|warhammer|bastard|messer|rondel|guisarme|ranseur|lucerne|'
    r'lochaber|partisan|nodachi|scimitar|gladius', re.I)


def read_json(z, name):
    try:
        return json.loads(z.read(name).decode('utf-8'))
    except Exception:
        return {}


def translate(mod, key, en):
    """번역문 또는 None. None 이면 «못 옮겼다» 로 보고된다 — 조용히 버리지 않는다."""
    full = f'{mod}:{key}'
    if full in OVERRIDES:
        return OVERRIDES[full]
    if mod == 'simplyswords' and key in SS_TABLE:
        return SS_TABLE[key]
    # 재료 + 종류 조합
    for mat_en, mat_ko in MATS.items():
        if en.startswith(mat_en + ' '):
            rest = en[len(mat_en) + 1:]
            if rest in TYPES:
                return f'{mat_ko} {TYPES[rest]}'
    if en in TYPES:
        return TYPES[en]
    return None


def main():
    if not MODS.is_dir():
        print(f'ERR: {MODS} 없음')
        return 1

    todo = {}          # mod -> {key: en}
    for jar in sorted(MODS.glob('*.jar')):
        try:
            z = zipfile.ZipFile(jar)
        except Exception:
            continue
        names = z.namelist()
        for en_path in [n for n in names if n.endswith('/lang/' + LANG_EN)]:
            mod = en_path.split('/')[1]
            if mod in SKIP:
                continue
            en = read_json(z, en_path)
            ko = read_json(z, en_path.replace(LANG_EN, LANG_KO))
            for k, v in en.items():
                # 아이템 «이름» 키만 본다 (item.<mod>.<name>). 점이 셋 이상이면 툴팁·부가 문구다.
                if not (k.startswith('item.') and k.count('.') == 2):
                    continue
                if f'{mod}:{k}' in SKIP_KEYS:
                    continue
                # ko 에 있어도 값이 영어 그대로면 «미번역» 이다 — 이게 이 도구의 핵심 판정이다.
                if k in ko and ko[k] != v:
                    continue
                if mod == 'simplyswords' or WEAPON_RE.search(k + ' ' + str(v)):
                    todo.setdefault(mod, {})[k] = v
            # Simply Swords 는 이름 밖의 것도 옮긴다 — 무기 툴팁·상태효과·도전과제.
            # 이름만 한글이고 바로 밑줄이 영어면 툴팁이 반쪽이 된다.
            # 위 루프는 `item.<mod>.<name>` 만 보므로 점이 셋 이상인 툴팁 키가 안 걸린다.
            if mod == 'simplyswords':
                for k, v in en.items():
                    if k in ko and ko[k] != v:
                        continue
                    if f'{mod}:{k}' in OVERRIDES or k in SS_TABLE:
                        todo.setdefault(mod, {})[k] = v

    out, missed, used, bad = {}, [], set(), []
    for mod, kv in todo.items():
        for k, v in kv.items():
            t = translate(mod, k, v)
            if t is None:
                missed.append((mod, k, v))
            else:
                out.setdefault(mod, {})[k] = t
                used.add(f'{mod}:{k}')
                # ── 서식 문자 검증 ──
                # `%d` `%s` 를 빠뜨리면 숫자가 아예 안 나오고, 개수·순서가 어긋나면
                # 엉뚱한 값이 박힌다. 둘 다 «화면에는 멀쩡한 한국어» 로 보여서
                # 눈으로는 못 잡는다. 그래서 여기서 센다.
                if FMT.findall(v) != FMT.findall(t):
                    bad.append((mod, k, FMT.findall(v), FMT.findall(t)))

    # ── 팩 쓰기 (통째로 다시 만든다 — 손으로 고친 게 남으면 다음 실행과 갈린다) ──
    assets = PACK / 'assets'
    if assets.exists():
        for p in sorted(assets.rglob('*'), reverse=True):
            p.unlink() if p.is_file() else p.rmdir()
    total = 0
    for mod, kv in sorted(out.items()):
        d = assets / mod / 'lang'
        d.mkdir(parents=True, exist_ok=True)
        (d / LANG_KO).write_text(
            json.dumps(dict(sorted(kv.items())), ensure_ascii=False, indent=2) + '\n',
            encoding='utf-8')
        total += len(kv)
        print(f'  {len(kv):4d}  {mod}')
    PACK.mkdir(parents=True, exist_ok=True)
    (PACK / 'pack.mcmeta').write_text(json.dumps({
        'pack': {'pack_format': PACK_FORMAT,
                 'description': 'Last Stardust — 무기 이름 한글화'}
    }, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    print(f'\n총 {total} 항목 · {len(out)} 모드 → {PACK.relative_to(ROOT)}')

    # ── 안 쓰인 OVERRIDES ──
    # 모드가 업데이트되면서 키가 바뀌면 여기 항목이 조용히 죽는다. 그러면 그 무기는 다시
    # 영어로 돌아가는데 «표에는 번역이 적혀 있어서» 다 된 줄 알게 된다. 그래서 보고한다.
    dead = sorted(set(OVERRIDES) - used)
    if dead:
        print(f'\n※ 안 쓰인 OVERRIDES {len(dead)} — 키가 바뀌었거나 이미 번역된 것:')
        for d in dead:
            print(f'   {d}')
    if bad:
        print(f'\n※ 서식 문자가 어긋난 항목 {len(bad)} — 반드시 고칠 것:')
        for mod, k, a, b in bad:
            print(f'   {mod}:{k}\n      원문 {a} → 번역 {b}')
        return 3

    if missed:
        # 조용히 빠뜨리면 「다 됐다」로 읽힌다. 남은 건 반드시 보여준다.
        print(f'\n※ 못 옮긴 것 {len(missed)} — OVERRIDES 나 SKIP_KEYS 에 넣을 것:')
        for mod, k, v in missed:
            print(f'   {mod}:{k}  = {v!r}')
        return 2

    if '--zip' in sys.argv:
        zp = PACK.parent / 'LS-Korean.zip'
        with zipfile.ZipFile(zp, 'w', zipfile.ZIP_DEFLATED) as z:
            for p in PACK.rglob('*'):
                if p.is_file():
                    z.write(p, p.relative_to(PACK).as_posix())
        print(f'zip: {zp.relative_to(ROOT)}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
