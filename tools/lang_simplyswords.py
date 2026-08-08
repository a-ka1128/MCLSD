# Simply Swords — 게임 화면에 보이는 문자열 한글화표
#
# `gen_lang.py` 가 이 파일의 `TABLE` 을 읽어 리소스팩에 넣는다. 여기만 고치면 된다.
#
# ── 왜 파일을 나눴나 ──
# 항목이 300 을 넘어서 `gen_lang.py` 에 같이 두면 «규칙» 과 «번역» 이 섞여 도구를 못 읽는다.
# 저쪽은 어떻게 찍어내는지, 이쪽은 무엇으로 옮기는지만 담는다.
#
# ── 안 옮긴 것 (일부러) ──
# 모드 «설정 화면» 문자열 약 470개(`simplyswords.weapon_attributes` ·`unique_effects` ·
# `general` ·`loot` ·`config` ·`gem_powers`). 그건 fzzy_config 설정 창에서만 보이고
# 플레이 중에는 안 뜬다. 항목당 설명이 길어 분량은 큰데 읽는 사람은 서버 여는 사람 하나다.
# 필요해지면 같은 형식으로 여기 더하면 된다.
#
# 미설치 호환 모드(MythicMetals·Gobber) 아이템 이름 63개도 뺐다 — 그 모드가 없어서 안 뜬다.
#
# ── 번역 원칙 ──
# · 고유 효과 이름(`Unique Effect: X`)은 «고유 효과: X» 로 통일한다. 화면에서 이 줄이
#   무기마다 첫 줄에 오므로 형식이 흔들리면 툴팁이 지저분해진다.
# · `%d` `%s` `%d%%` 같은 서식은 **그대로 둔다.** 지우면 숫자가 안 나오고, 순서를 바꾸면
#   엉뚱한 값이 박힌다.
# · `§6` 등 색 코드도 그대로 둔다.

# ── 우리 모드(lsrelics)의 낡은 문구 덮어쓰기 ──
# jar 안 ko_kr.json 은 이미 한국어인데 «균열 정수» 라는 옛 이름을 쓰고 있다. 소스는 고쳤지만
# 배포된 jar 는 그대로라, 고치려면 Gradle 재빌드 + jar 교체가 필요하다.
# 리소스팩은 mod 의 lang 도 키 단위로 덮으므로 여기서 한 줄로 끝난다 — 재빌드 위험을 안 진다.
# (모드를 다시 빌드해도 팩이 이기므로 어긋나지 않는다. 소스도 같이 고쳐 뒀다.)
LSRELICS = {
    'lsfate.gui.hint': '가호를 정하고 첫 공세를 버텨 얻은 별의 파편을 제단에 바쳐야 유물이 깨어난다.',
}

P = 'item.simplyswords.'
E = 'effect.simplyswords.'
A = 'advancements.simplyswords.'

TABLE = {
    # ── 무기 능력 툴팁 ──
    P + 'soulpyresworditem.tooltip1': '고유 효과: 영혼의 사슬',
    P + 'soulpyresworditem.tooltip2': '몸 주위로 퍼지는 영역이 생긴다. 맥동할 때마다 자신을 치유하고 적에게 피해를 준다.',
    P + 'soulpyresworditem.tooltip6': '지속되는 동안 밀려남 저항을 얻고 받는 피해가 50% 줄어든다.',

    P + 'frostfallsworditem.tooltip1': '고유 효과: 서리의 분노',
    P + 'frostfallsworditem.tooltip2': '서리내림을 앞으로 던진다. 체공 시간과 각도가 충돌 피해를 좌우한다.',
    P + 'frostfallsworditem.tooltip4': '착지 후 5회 맥동하며 적에게 피해를 주고 둔화시키며 끌어당긴다.',
    P + 'frostfallsworditem.tooltip6': '충성 III 이 기본으로 붙어 있다',

    P + 'moltenedgesworditem.tooltip1': '고유 효과: 용융의 포효',
    P + 'moltenedgesworditem.tooltip2': '적중 시 일정 확률로 적이나 자신을 불태운다. 체력이 낮으면 대신 재생을 얻는다.',
    P + 'moltenedgesworditem.tooltip4': '잃은 체력에 비례해 힘과 신속을 얻는다.',
    P + 'moltenedgesworditem.tooltip5': '강력한 포효를 터뜨려 주변 적을 밀어내고 불태운다. 맞힌 적 수에 비례한 시간 동안 저항과 맹공을 얻는다.',

    P + 'livyatansworditem.tooltip1': "고유 효과: 요툰의 분노",
    P + 'livyatansworditem.tooltip2': '리바이어탄을 앞으로 던진다. 체공 시간과 각도가 충돌 피해를 좌우한다.',
    P + 'livyatansworditem.tooltip4': '돌아올 때 적을 둔화시키고 피해를 준다.',
    P + 'livyatansworditem.tooltip6': '충성 III 이 기본으로 붙어 있다',

    P + 'icewhispersworditem.tooltip1': '고유 효과: 영구동토',
    P + 'icewhispersworditem.tooltip2': '들고 있는 동안 서리 오라가 생겨 %d 칸 안의 적에게 피해를 주고 둔화시킨다.',
    P + 'icewhispersworditem.tooltip4': '허기를 소모해 그 자리에 눈보라를 부른다. %d 칸 안의 적을 둔화시키고 더 큰 피해를 준다.',

    P + 'arcanethystsworditem.tooltip1': '고유 효과: 비전 강습',
    P + 'arcanethystsworditem.tooltip2': '적중 시 일정 확률로 대상을 공중에 띄운다.',
    P + 'arcanethystsworditem.tooltip3': '비전 에너지를 모아 주변 적을 덮친다. 적을 공중에 띄운 채 지속 피해를 준 뒤 땅에 내리꽂는다.',

    P + 'thunderbrandsworditem.tooltip1': '고유 효과: 뇌전 돌격',
    P + 'thunderbrandsworditem.tooltip2': '적중 시 일정 확률로 능력 재사용 대기시간이 초기화된다.',
    P + 'thunderbrandsworditem.tooltip3': '잠시 자신을 느리게 하며 무기를 충전해 주변에 광역 피해를 준다.',
    P + 'thunderbrandsworditem.tooltip5': '충전이 끝나면 앞으로 돌진해 성급함을 얻고 경로의 적에게 막대한 피해를 준다.',

    P + 'stormsedgesworditem.tooltip1': '고유 효과: 폭풍 충격',
    P + 'stormsedgesworditem.tooltip2': '적중 시 일정 확률로 능력 재사용 대기시간이 초기화된다.',
    P + 'stormsedgesworditem.tooltip3': '번개처럼 앞으로 튀어나가며 잠깐 무적이 되고, 신속과 성급함이 남는다.',

    P + 'lichbladesworditem.tooltip1': '고유 효과: 영혼의 고통 I',
    P + 'lichbladesworditem.tooltip1.2': '고유 효과: 영혼의 고통 II',
    P + 'lichbladesworditem.tooltip1.3': '고유 효과: 영혼의 고통 III',
    P + 'lichbladesworditem.tooltip2': '고통받는 영혼이 몸에서 뻗어나가 주변 적에게 피해를 준다.',
    P + 'lichbladesworditem.tooltip4': '영혼에게 명령해 먼 대상을 빠르게 연타한다. 일정 확률로 체력을 흡수한다.',
    P + 'lichbladesworditem.tooltip7': '공격이 끝나면 영혼이 돌아오고, 도달하는 순간 흡수 실드를 준다.',

    P + 'stormbringersworditem.tooltip1': '고유 효과: 충격 반사',
    P + 'stormbringersworditem.tooltip2': '칼끝 한 점에 힘을 모아 잠시 공격을 막아낸다.',
    P + 'stormbringersworditem.tooltip5': '적의 근접 공격에 타이밍을 맞추면 받아넘기기가 되어 피해를 주고 상대를 날려버리며, 재사용 대기시간도 줄어든다.',
    P + 'stormbringersworditem.tooltip10': '연속으로 받아넘기면 피해와 재사용 대기시간이 함께 늘어난다.',

    P + 'shadowmistsworditem.tooltip1': '고유 효과: 그림자 안개',
    P + 'shadowmistsworditem.tooltip2': '적중 시 일정 확률로 대상의 방어도에 비례하는 마법 피해를 준다.',
    P + 'shadowmistsworditem.tooltip4': '발밑에 눈을 가리는 그림자 안개를 만들고 짧게 앞으로 그림자 이동한다.',

    P + 'harbingersworditem.tooltip1': '고유 효과: 심연의 군기',
    P + 'harbingersworditem.tooltip2': '적중 시 일정 확률로 나약함을 건다.',
    P + 'harbingersworditem.tooltip3': '심연의 전투 군기를 세운다. 적을 끌어당기고 피해를 주며 둔화시키는 한편, 주변 아군에게는 주기적으로 성급함을 준다.',

    P + 'sunfiresworditem.tooltip1': '고유 효과: 정의의 군기',
    P + 'sunfiresworditem.tooltip2': '적중 시 일정 확률로 체력이 회복된다.',
    P + 'sunfiresworditem.tooltip3': '정의의 전투 군기를 세운다. 적을 불태우고 피해를 주며 둔화시키는 한편, 주변 아군에게는 주기적으로 힘과 체력을 준다.',

    P + 'whisperwindsworditem.tooltip1': '고유 효과: 치명적 명멸',
    P + 'whisperwindsworditem.tooltip2': '적중 시 일정 확률로 능력 재사용 대기시간이 초기화된다.',
    P + 'whisperwindsworditem.tooltip3': '앞으로 빠르게 돌진해 저항과 흡수 실드를 얻고, 경로의 적에게 메아리를 건다. 돌진으로 맞힌 대상이 많을수록 메아리 수치가 커진다.',

    P + 'emberlashsworditem.tooltip1': '고유 효과: 잔불',
    P + 'emberlashsworditem.tooltip2': '공격이 잔불 상태를 건다.',
    P + 'emberlashsworditem.tooltip3': '잔불에 걸린 적을 공격하면 중첩당 %d 의 추가 피해를 준다.',
    P + 'emberlashsworditem.tooltip6': '뒤로 회피하며 상처를 지져 최대 체력의 %d%% 를 회복한다.',

    P + 'waxweaversworditem.tooltip1': '고유 효과: 밀랍 직조',
    P + 'waxweaversworditem.tooltip2': '불타는 적을 공격하면 힘과 성급함을 얻는다.',
    P + 'waxweaversworditem.tooltip4': '죽을 상황이 되면 대신 재생의 밀랍에 감싸여 체력을 모두 회복하고 잠시 저항을 얻는다.',
    P + 'waxweaversworditem.tooltip8': '이 효과는 %d 초에 한 번만 발동한다. 주손에 들었을 때만.',

    P + 'hiveheartsworditem.tooltip1': '고유 효과: 벌떼 정신',
    P + 'hiveheartsworditem.tooltip2': '적중 시 성난 벌을 풀어 대상을 쏘게 한 뒤 사라진다.',
    P + 'hiveheartsworditem.tooltip5': '이 효과는 %d 초에 한 번만 발동한다.',
    P + 'hiveheartsworditem.tooltip7': '벌들이 재생의 꿀을 만드는 데 집중한다. 시간에 걸쳐 체력을 회복하지만 벌떼 정신이 재사용 대기 상태가 된다.',

    P + 'starsedgesworditem.tooltip1': '고유 효과: 별의 칼날',
    P + 'starsedgesworditem.tooltip2': '낮에는 공격이 %s 의 추가 피해를 준다.',
    P + 'starsedgesworditem.tooltip4': '밤에는 공격에 흡혈이 붙는다.',
    P + 'starsedgesworditem.tooltip5': '뒤로 회피하며 신속을 얻는다.',
    P + 'starsedgesworditem.tooltip6': '한 번 더 쓰면 앞으로 돌진하며 저항과 성급함을 얻는다.',

    P + 'wickpiercersworditem.tooltip1': '고유 효과: 명멸의 분노',
    P + 'wickpiercersworditem.tooltip2': '충성 III 이 기본으로 붙어 있다',
    P + 'wickpiercersworditem.tooltip3': '심지관통검을 던져 광란 중첩을 얻는다. 공격 속도가 오르고',
    P + 'wickpiercersworditem.tooltip6': '%d 초 동안 두 번 타격한다.',
    P + 'wickpiercersworditem.tooltip7': '최대 5 중첩. 피격 시 사라진다.',

    P + 'tempestsworditem.tooltip1': '고유 효과: 소용돌이',
    P + 'tempestsworditem.tooltip2': '공격이 대상에게 원소 에너지를 붙여 지속 피해와 원소별 약화를 건다.',
    P + 'tempestsworditem.tooltip6': '이 효과는 중첩된다.',
    P + 'tempestsworditem.tooltip7': '주변의 원소를 모두 자신에게 되불러 원소 에너지의 소용돌이를 만든다. 주변 적에게 피해를 주며 점점 커진다.',

    P + 'dreadtidesworditem.tooltip1': '고유 효과: 공허 부름',
    P + 'dreadtidesworditem.tooltip2': '자동으로 공허망토 중첩이 쌓인다. 중첩당 공격 속도가 오르고 받는 피해가 10% 줄어든다.',
    P + 'dreadtidesworditem.tooltip6': '피해를 받으면 한 중첩이 사라진다. 오염 20 당 최대 1 중첩.',
    P + 'dreadtidesworditem.tooltip8': '공허망토를 풀어 가장 가까운 적에게 이계의 힘을 보내 빠르게 연타한다.',
    P + 'dreadtidesworditem.tooltip12': '이 무기를 들고 있으면 시간이 갈수록 오염이 쌓인다.',

    P + 'flamewindsworditem.tooltip1': '고유 효과: 잉걸 폭풍',
    P + 'flamewindsworditem.tooltip2': '불꽃 씨앗이 터지면 성급함을 얻는다.',
    P + 'flamewindsworditem.tooltip4': '가장 가까운 적의 몸속에 불꽃 씨앗을 심어 지속 피해를 준다.',
    P + 'flamewindsworditem.tooltip7': '씨앗은 시간이 다하면 터지며 광역 피해를 주고 주변 적에게 효과를 옮긴다.',
    P + 'flamewindsworditem.tooltip11': '최대 %d 회까지 옮는다.',

    P + 'ribboncleaversworditem.tooltip1': '고유 효과: 리본의 분노',
    P + 'ribboncleaversworditem.tooltip2': '리본절단검을 들면 이동 속도가 5% 느려지지만 받는 피해가 15% 줄어든다.',
    P + 'ribboncleaversworditem.tooltip5': '앞으로 돌진해 다음 몇 대를 흘려내고 밀려남 저항을 얻으며, 다음 공격이 한 대상에게 +95% 피해를 준다.',

    P + 'magiscythesworditem.tooltip1': '고유 효과: 마력 폭풍',
    P + 'magiscythesworditem.tooltip2': '마력 폭풍이 도는 동안 적을 공격하면 착용 중인 장비가 수리될 확률이 있다.',
    P + 'magiscythesworditem.tooltip6': '머리 위에 마력의 폭풍이 나타나 주변 적에게 피해를 준다. 폭풍이 내리칠 때마다 지속 시간이 갱신되고 위력이 세질 확률이 있다.',

    P + 'enigmasworditem.tooltip1': '고유 효과: 질풍',
    P + 'enigmasworditem.tooltip2': '회오리 근처에 있으면 성급함을 얻는다.',
    P + 'enigmasworditem.tooltip4': '주변 적을 쫓는 회오리를 부른다. 적이 안에 오래 갇힐수록 피해가 커진다.',

    P + 'magibladesworditem.tooltip1': '고유 효과: 마력 음파',
    P + 'magibladesworditem.tooltip2': '마력검이 이따금 접근하는 적을 감지해 밀어낸다.',
    P + 'magibladesworditem.tooltip5': '잠시 뒤 마력검이 앞으로 음파 폭발을 내보내 경로의 적에게 피해를 준다.',

    P + 'magispearsworditem.tooltip1': '고유 효과: 마력 강타',
    P + 'magispearsworditem.tooltip2': '충성 III 이 기본으로 붙어 있다',
    P + 'magispearsworditem.tooltip3': '적중 시 일정 확률로 추가 마법 피해를 준다.',
    P + 'magispearsworditem.tooltip5': '마력창을 던지고 앞으로 도약한 뒤 내리찍어 주변 적에게 피해를 준다.',
    P + 'magispearsworditem.tooltip9': '도약하는 동안에는 무적이다.',

    P + 'caelestissworditem.tooltip1': '고유 효과: 성계 전이',
    P + 'caelestissworditem.tooltip2': '들고 있는 동안 들어오는 피해를 완전히 회피할 확률을 얻는다.',
    P + 'caelestissworditem.tooltip5': '잠시 성계로 들어가 %d 초 동안 피해에 면역이 된다',
    P + 'caelestissworditem.tooltip8': '성계에서 나오는 순간 격렬하게 폭발한다. 성계에 있는 동안 막아낸 피해에 비례해 광역 피해를 준다.',

    P + 'wraithfangsworditem.tooltip1': '고유 효과: 망령 도약',
    P + 'wraithfangsworditem.tooltip2': '망령의 송곳니를 던지고 그 위치로 도약하며 잠시 피해에 면역이 된다.',
    P + 'wraithfangsworditem.tooltip5': '망령의 송곳니에 도달하면 성급함을 얻는다.',
    P + 'wraithfangsworditem.tooltip7': '충성 I 이 기본으로 붙어 있다',

    P + 'chompolotlsworditem.tooltip1': '고유 효과: 촘포칼립스',
    P + 'chompolotlsworditem.tooltip2': '적중 시 우파루파를 소환한다.',
    P + 'chompolotlsworditem.tooltip3': '우파루파가 주변 적을 공격한다. 어깨에 올려 두면 주변에 이로운 효과를 준다.',
    P + 'chompolotlsworditem.tooltip7': '희귀한 파란 우파루파를 소환한다.',

    P + 'bramblesworditem.tooltip5': '포자는 사거리 안에 대상이 있는 한 계속 작동하며, 무기를 바꿔도 효과는 약해진 채로 남는다.',
    P + 'stormsworditem.tooltip4': '집중하는 동안 저항을 얻고, 주변 적에게 번개를 쏟아붓는 국지적 폭풍을 만든다.',
    P + 'emberiresworditem.tooltip6': '마지막 1초 안에 집중을 풀면 파편을 한 발 더 쏠 수 있다.',
    P + 'emberiresworditem.tooltip9': '파편을 쏠 때, 집중한 시간에 비례한 확률로 신속·성급함·힘을 얻는다.',
    P + 'volcanicfurysworditem.tooltip4': '분노를 집중해 체력을 대가로 저항을 얻고, 진동을 일으켜 적을 끌어당긴다.',
    P + 'volcanicfurysworditem.tooltip7': '집중을 풀어 분노를 터뜨린다. 오래 집중할수록 더 멀리 날리고 더 크게 불태우며 더 큰 피해를 준다.',

    P + 'decayingrelicsworditem.tooltip1': '오버월드의 가장 깊고 어두운 곳을 탐험할 때 지니고 다녀 보라.',
    P + 'dormantrelicsworditem.tooltip2': '안에서 희미한 힘이 새어 나오는 것 같다.',
    P + 'empoweredrelicsworditem.tooltip2': '안에서 점점 커지는 힘이 새어 나오는 것 같다.',

    # ── 룬 힘 (Runic Power) ──
    P + 'zephyrsworditem.tooltip1': '룬의 힘: 산들바람',
    P + 'zephyrsworditem.tooltip2': '적중 시 일정 확률로 공격 속도와 이동 속도를 얻는다.',
    P + 'shieldingsworditem.tooltip1': '룬의 힘: 보호막',
    P + 'shieldingsworditem.tooltip2': '적중 시 일정 확률로 흡수 실드를 얻는다.',
    P + 'stoneskinsworditem.tooltip1': '룬의 힘: 돌갗',
    P + 'stoneskinsworditem.tooltip2': '적중 시 일정 확률로 이동 속도를 잃는 대신 저항이 오른다.',
    P + 'trailblazesworditem.tooltip1': '룬의 힘: 불길 자국',
    P + 'trailblazesworditem.tooltip2': '적중 시 일정 확률로 신속을 얻고 자신이 불탄다.',
    P + 'weakensworditem.tooltip1': '룬의 힘: 약화',
    P + 'weakensworditem.tooltip2': '적중 시 일정 확률로 대상을 둔화시키고 나약하게 만든다.',
    P + 'unstablesworditem.tooltip1': '룬의 힘: 불안정',
    P + 'unstablesworditem.tooltip2': '들고 있는 자에게 주기적으로 무작위 효과를 건다.',
    P + 'activedefencesworditem.tooltip1': '룬의 힘: 능동 방어',
    P + 'activedefencesworditem.tooltip2': '주기적으로 주변 적에게 화살을 쏜다 (화살 필요).',
    P + 'frostwardsworditem.tooltip1': '룬의 힘: 서리 방벽',
    P + 'frostwardsworditem.tooltip2': '주기적으로 주변 모든 적에게 이동을 방해하는 눈덩이를 쏜다.',
    P + 'momentumsworditem.tooltip1': '룬의 힘: 가속',
    P + 'momentumsworditem.tooltip2': '짧게 앞으로 강하게 밀고 나간다.',
    P + 'imbuedsworditem.tooltip1': '룬의 힘: 주입',
    P + 'imbuedsworditem.tooltip2': '적중 시 일정 확률로 내구도에 비례하는 추가 마법 피해를 준다.',
    P + 'pincushionsworditem.tooltip1': '룬의 힘: 바늘꽂이',
    P + 'pincushionsworditem.tooltip2': '몸에 박힌 화살 하나마다 추가 피해를 준다.',
    P + 'wardsworditem.tooltip1': '룬의 힘: 방벽',
    P + 'wardsworditem.tooltip2': '현재 체력의 절반을 바쳐, 남은 체력에 비례하는 흡수 실드를 6초간 맥동시킨다.',
    P + 'immolationsworditem.tooltip1': '룬의 힘: 소신',
    P + 'immolationsworditem.tooltip2': '불타오르는 오라를 얻어 자신과 주변 적에게 주기적으로 피해를 준다. 주는 피해는 현재 체력에 비례한다.',
    P + 'throwingsworditem.tooltip1': '룬의 힘: 투척',
    P + 'throwingsworditem.tooltip2': '던질 수 있으며 충성 III 이 기본으로 붙어 있다.',
    P + 'unidentifiedsworditem.tooltip1': '룬의 힘: ????',
    P + 'unidentifiedsworditem.tooltip2': 'ꬹ 클릭해 감정한다.',
    P + 'netherfused_gem.tooltip1': '네더의 힘: ????',
    P + 'runic_tablet.tooltip': '룬 무기를 제작하고',
    P + 'runic_tablet.tooltip2': '다시 굴리는 데 쓸 수 있다.',
    P + 'runic_tablet.tooltip3': '고유 무기를 수리하는 데도 쓸 수 있다.',

    # ── 룬/네더 융합 힘 목록 (툴팁의 ◆ 줄) ──
    P + 'uniquesworditem.runefused_power.float': '◆  부유',
    P + 'uniquesworditem.runefused_power.swiftness': '◆  신속',
    P + 'uniquesworditem.runefused_power.slow': '◆  둔화',
    P + 'uniquesworditem.runefused_power.freeze': '◆  빙결',
    P + 'uniquesworditem.runefused_power.wildfire': '◆  들불',
    P + 'uniquesworditem.runefused_power.zephyr': '◆  산들바람',
    P + 'uniquesworditem.runefused_power.shielding': '◆  보호막',
    P + 'uniquesworditem.runefused_power.stoneskin': '◆  돌갗',
    P + 'uniquesworditem.runefused_power.trailblaze': '◆  불길 자국',
    P + 'uniquesworditem.runefused_power.weaken': '◆  약화',
    P + 'uniquesworditem.runefused_power.unstable': '◆  불안정',
    P + 'uniquesworditem.runefused_power.active_defence': '◆  능동 방어',
    P + 'uniquesworditem.runefused_power.frost_ward': '◆  서리 방벽',
    P + 'uniquesworditem.runefused_power.imbued': '◆  주입',
    P + 'uniquesworditem.runefused_power.pincushion': '◆  바늘꽂이',

    P + 'uniquesworditem.netherfused_power.echo': '◆  메아리',
    P + 'uniquesworditem.netherfused_power.echo.description': '무기를 휘두르면 메아리가 남아, 잠시 뒤 경감되지 않는 추가 피해를 준다.',
    P + 'uniquesworditem.netherfused_power.berserk': '◆  광전',
    P + 'uniquesworditem.netherfused_power.berserk.description': '방어도가 10 미만일 때, 무기를 휘두르면 대상의 체력을 흡수한다.',
    P + 'uniquesworditem.netherfused_power.radiance': '◆  광휘',
    P + 'uniquesworditem.netherfused_power.radiance.description': '나약함에 걸린 대상을 때리면 잠시 소신을 얻는다.',
    P + 'uniquesworditem.netherfused_power.onslaught': '◆  맹공',
    P + 'uniquesworditem.netherfused_power.onslaught.description': '둔화에 걸린 대상을 때리면 잠시 맹공을 얻는다. (맹공은 성급함을 맥동시키지만, 끝날 때 나약함을 남긴다.)',
    P + 'uniquesworditem.netherfused_power.nullification': '◆  무효화',
    P + 'uniquesworditem.netherfused_power.nullification.description': '적중 시 전투 군기를 세운다. 주변 아군의 해로운 효과를 주기적으로 지우고, 적의 이로운 효과는 벗겨낸다.',
    P + 'uniquesworditem.netherfused_power.precise': '◆  정밀',
    P + 'uniquesworditem.netherfused_power.precise.description': '고유 능력을 쓸 때 일정 확률로 정밀 중첩을 여러 개 얻는다.',
    P + 'uniquesworditem.netherfused_power.mighty': '◆  강력',
    P + 'uniquesworditem.netherfused_power.mighty.description': '고유 능력을 쓸 때 일정 확률로 위력 중첩을 여러 개 얻는다.',
    P + 'uniquesworditem.netherfused_power.stealthy': '◆  은신',
    P + 'uniquesworditem.netherfused_power.stealthy.description': '고유 능력을 쓸 때 일정 확률로 은신을 얻는다.',
    P + 'uniquesworditem.netherfused_power.renewed': '◆  쇄신',
    P + 'uniquesworditem.netherfused_power.renewed.description': '고유 능력을 쓸 때 일정 확률로 그 재사용 대기시간이 크게 줄어든다.',
    P + 'uniquesworditem.netherfused_power.accelerant': '◆  촉진',
    P + 'uniquesworditem.netherfused_power.accelerant.description': '광전사의 광란 고유 능력이 더 이상 광란을 주지 않는 대신, 재사용 대기시간이 크게 줄어든다.',
    P + 'uniquesworditem.netherfused_power.leaping': '◆  도약',
    P + 'uniquesworditem.netherfused_power.leaping.description': '도약 강타를 성공시키면 일정 확률로 즉시 한 번 더 도약한다.',
    P + 'uniquesworditem.netherfused_power.spellshield': '◆  주문 방패',
    P + 'uniquesworditem.netherfused_power.spellshield.description': '주문을 시전할 때 일정 확률로 보호막을 얻는다.',
    P + 'uniquesworditem.netherfused_power.spellforged': '◆  주문 벼림',
    P + 'uniquesworditem.netherfused_power.spellforged.description': '주손에 들고 있으면 주문력이 오른다.',
    P + 'uniquesworditem.netherfused_power.soulshock': '◆  영혼 충격',
    P + 'uniquesworditem.netherfused_power.soulshock.description': '들고 있으면 영혼·번개 주문력이 오른다.',
    P + 'uniquesworditem.netherfused_power.spellstandard': '◆  주문 군기',
    P + 'uniquesworditem.netherfused_power.spellstandard.description': '주문이 적중할 때 일정 확률로 정밀과 주문 벼림을 주는 주문 군기를 떨어뜨린다.',
    P + 'uniquesworditem.netherfused_power.warstandard': '◆  전투 군기',
    P + 'uniquesworditem.netherfused_power.warstandard.description': '전방 돌진 능력을 쓰면 위력을 주고 주변 적을 드러내는 전투 군기를 떨어뜨린다.',
    P + 'uniquesworditem.netherfused_power.deception': '◆  기만',
    P + 'uniquesworditem.netherfused_power.deception.description': '회피 숙련으로 공격을 피할 때 일정 확률로 «드러남»을 지운다.',

    # ── 각성·유물 안내 문구 ──
    P + 'awakening.exp': '각성 XP: %d%% ',
    P + 'awakening.powers': '각성한 힘: ',
    P + 'contained_remnant.event': '소지품 속 잔재의 형태가 변한 것 같다',
    P + 'contained_remnant.event2': '소지품 속 잔재가 이상하게 군다',
    P + 'contained_remnant.event3': '잔재가 가까운 블록에 반응하는 것 같다',
    P + 'magicythe.event': '부식된 유물이 소리를 내기 시작했다',
    P + 'magicythe.event2': '부식된 유물이 이 지역에 반응하는 것 같다',
    P + 'magiblade.event': '부식된 유물이 떨리는 것 같다',
    P + 'magiblade.event2': '부식된 유물이 발밑의 블록에 끌리는 것 같다',
    P + 'magispear.event': '부식된 유물이 무언가의 영향을 받은 것 같다',
    P + 'magispear.event2': '부식된 유물이 어둠에 반응하는 것 같다',
    P + 'common.blacklisteffect': '능력 비활성화',
    P + 'compat.mythicmetals.regrowth': '재생',
    P + 'compat.mythicmetals.looting': '약탈 보너스',
    P + 'compat.spellScaling': '§7주문 계수: ',
    P + 'compat.scaleFire': ' §6화염',
    P + 'compat.scaleFrost': ' §b냉기',
    P + 'compat.scaleLightning': ' §e번개',
    P + 'compat.scaleArcane': ' §d비전',
    P + 'compat.scaleSoul': ' §9영혼',
    P + 'compat.scaleHealing': ' §a치유',

    # ── 상태 효과 이름 (효과 목록에 그대로 뜬다) ──
    E + 'storm': '폭풍',
    E + 'echo': '메아리',
    E + 'ward': '방벽',
    E + 'immolation': '소신',
    E + 'onslaught': '맹공',
    E + 'smouldering': '잔불',
    E + 'frenzy': '광란',
    E + 'voidcloak': '공허망토',
    E + 'void_assault': '공허 강습',
    E + 'voidhunger': '공허 굶주림',
    E + 'fire_vortex': '화염 소용돌이',
    E + 'frost_vortex': '서리 소용돌이',
    E + 'elemental_vortex': '원소 소용돌이',
    E + 'flameseed': '불꽃 씨앗',
    E + 'ribbonwrath': '리본의 분노',
    E + 'ribboncleave': '리본 가르기',
    E + 'resilience': '강인함',
    E + 'battle_fatigue': '전투 피로',
    E + 'pain': '고통',
    E + 'spore_swarm': '포자 무리',
    E + 'magistorm': '마력 폭풍',
    E + 'magislam': '마력 강타',
    E + 'astral_shift': '성계 전이',
    E + 'fatal_flicker': '치명적 명멸',

    # ── 엔티티·안내 ──
    'entity.simplyswords.battlestandard.name': '%d 의 전투 군기',
    'entity.simplyswords.battlestandard': '전투 군기',
    'entity.simplyswords.thrown_sword': '투척 무기',
    'message.simplyswords.documentation.error': '인게임 문서를 보려면 설치하세요:',
    'message.simplyswords.documentation.error2': 'Oracle Index, REI, 또는 EMI.',

    # ── 도전과제 ──
    # 제목은 말장난이 많다. 직역하면 뜻만 남고 농담이 죽어서, 한국어로 다시 지은 것이 여럿이다.
    A + 'root.title': 'Simply Swords',
    A + 'root.description': '룬의 힘이 당신을 기다린다.',
    A + 'runic_gear.title': '룬 새기기',
    A + 'runic_gear.description': '룬 무기를 손에 넣는다.',
    A + 'find_unique.description': '고유 무기 %s 를 찾아낸다.',
    A + 'obtain_unique.description': '고유 무기 %s 를 손에 넣는다.',
    A + 'flamewind.title': '이거 살아 있는 거 아냐?',
    A + 'twisted_blade.title': '꼬일 대로 꼬였다',
    A + 'whisperwind.title': '들리나?',
    A + 'wickpiercer.title': '왁스 오프',
    A + 'watcher_claymore.title': '언제나 지켜본다',
    A + 'shadowsting.title': '그림자 속에서',
    A + 'dormant_relic.title': '건전지는 별매입니다',
    A + 'sunfire.title': '축성됨',
    A + 'harbinger.title': '심연에서',
    A + 'waxweaver.title': '왁스 온',
    A + 'ribboncleaver.title': '이두박근 필수',
    A + 'livyatan.title': '살얼음판 위에서',
    A + 'arcanethyst.title': '비전의 힘',
    A + 'bramblethorn.title': '포자 대잔치',
    A + 'storms_edge.title': '번개는 두 번 친다',
    A + 'tempest.title': '기상 특보',
    A + 'slumbering_lichblade.title': '일어나!',
    A + 'awakened_lichblade.title': '드디어 깨어났다!',
    A + 'watching_warglaive.title': '눈을 뜨고',
    A + 'stars_edge.title': '우주의 분노',
    A + 'hearthflame.title': '불꽃 속에서 벼려지다',
    A + 'sword_on_a_stick.title': '공학의 경이',
    A + 'toxic_longsword.title': '만지지 않는 게 좋겠어',
    A + 'brimstone_claymore.title': '폭발 저항은 별매입니다',
    A + 'caelestis.title': '다른 세계로 난 창',
    A + 'molten_edge.title': '못 잡을 정도는 아니야',
    A + 'hiveheart.title': '벌은 안 돼!',
    A + 'enigma.title': '날씨 조종사',
    A + 'mjolnir.title': '돌아오는 거 맞지?',
    A + 'soulstealer.title': '영혼을 훔치는 자',
    A + 'soulpyre.title': '영혼을 태우는 자',
    A + 'emberlash.title': '화재 위험',
    A + 'soulrender.title': '영혼을 찢는 자',
    A + 'emberblade.title': '불 속의 분노',
    A + 'icewhisper.title': '가만히 들어 보라',
    A + 'decaying_relic.title': '고대의 유물',
    A + 'magiscythe.title': '고대의 기술',
    A + 'magispear.title': '고대의 기술',
    A + 'magiblade.title': '고대의 기술',
    A + 'frostfall.title': '얼음 세 제곱',
    A + 'stormbringer.title': '폭풍을 부르는 자',
    A + 'soulkeeper.title': '영혼을 지키는 자',
    A + 'thunderbrand.title': '천둥의 시간',
    A + 'wraithfang.title': '망령의 분노',
    A + 'chompolotl.title': '꼬물 군단',
}
