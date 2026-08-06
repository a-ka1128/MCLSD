// priority: 990
// Last Stardust — 공용 유틸 (모든 ls_*.js 보다 먼저 로드)
//
// ── 왜 이 파일이 있나 ──
// 여러 스크립트가 "플레이어가 이 아이템을 몇 개 갖고 있나?" 를 알아야 하는데,
// 지금까지는 전부 이 관용구를 썼다:
//
//     have = server.runCommandSilent(`clear ${name} ${item} 0`)   // ← 개수를 센다고 믿었음
//
// **이건 처음부터 작동한 적이 없다.** KubeJS 2101 의 runCommandSilent 는 반환값이 void 다:
//     public default void kjs$runCommandSilent(java.lang.String);
// 그래서 have 에는 항상 undefined 가 들어갔고, JS 에서 undefined 는 모든 비교가 false 라
//   · `have <= 0`      → false → "없음" 가드를 통과해 NaN 이 저장까지 흘러가 명령이 죽거나
//   · `have < cost`    → false → 비용 검사를 공짜로 통과했다
// 두 증상이 파일마다 갈렸을 뿐 원인은 하나였다.
//
// 아래 두 함수는 명령이 아니라 인벤토리를 직접 읽고 쓴다. 반환값에 기대지 않는다.

// 플레이어가 가진 개수 (주 인벤 + 핫바 + 오프핸드 + 방어구 슬롯 전부)
function lsCountItem(player, id) {
  let n = 0
  try {
    var inv = player.getInventory()
    var size = inv.getContainerSize()
    for (var i = 0; i < size; i++) {
      var st = inv.getItem(i)
      if (!st || st.isEmpty()) continue
      if (String(st.getId()) === id) n += st.getCount()
    }
  } catch (e) { console.log('[LS-UTIL] count fail: ' + e); return 0 }
  return n
}

// 최대 want 개를 실제로 회수한다. 반환 = 실제로 가져간 개수.
// ※ 세는 것과 빼는 것을 한 함수로 묶지 않는다 — 호출부가 "얼마나 가져갈지" 를 먼저 정해야 하는
//   경우(필요량이 남은 양보다 적을 때)가 대부분이라, 세기(lsCountItem)와 나눠 두는 게 맞다.
function lsTakeItem(player, id, want) {
  let left = Math.floor(Number(want))
  if (!isFinite(left) || left <= 0) return 0
  let took = 0
  try {
    var inv = player.getInventory()
    var size = inv.getContainerSize()
    for (var i = 0; i < size && left > 0; i++) {
      var st = inv.getItem(i)
      if (!st || st.isEmpty()) continue
      if (String(st.getId()) !== id) continue
      var c = st.getCount()
      var take = Math.min(c, left)
      st.shrink(take)          // 0 이 되면 마인크래프트가 알아서 빈 칸으로 만든다
      left -= take
      took += take
    }
    if (took > 0) player.sendInventoryUpdate()   // 클라 인벤 화면 동기화
  } catch (e) { console.log('[LS-UTIL] take fail: ' + e); return took }
  return took
}

// ── 삼킨 예외를 드러내는 창구 ──
//
// 2026-07-25 하루에 찾은 버그 다섯 개가 전부 "조용히 실패"였고, 찾아낸 유일한 수단이
// 로그였다. 그래서 catch 는 반드시 뭔가를 남겨야 한다 — `catch (e) { }` 는
// "이 실패는 없던 일로 한다"는 선언이고, 그게 성역 동기화를 몇 시간 동안 죽여놨다.
//
// 다만 그냥 console.log 를 박으면 안 된다. 틱 핸들러 안에서 터지는 예외는 초당 수십 줄이
// 되어 로그를 뒤덮고, 그러면 정작 다른 문제가 그 아래 묻힌다 — 로그를 살리려고 넣은 것이
// 로그를 죽이는 셈이다. 그래서 자리마다 횟수를 세서 처음 몇 번만 남긴다.
var LS_WARN_COUNT = {}
var LS_WARN_MAX = 3

function lsWarn(where, e) {
  var n = (LS_WARN_COUNT[where] || 0) + 1
  LS_WARN_COUNT[where] = n
  if (n > LS_WARN_MAX) return
  console.log('[LS-WARN] ' + where + ': ' + e + (n === LS_WARN_MAX ? '  (이후 이 자리는 생략)' : ''))
}

// ── 도전과제 지급 (2026-08-06) ──
// 도전과제 27종은 `lsrelics` jar 안에 있다(`data/lsrelics/advancement/`, 표는
// `tools/gen_advancements.py`). 전부 `minecraft:impossible` 트리거라 **여기서 주는 것이
// 유일한 경로**다 — 조건 판정은 이미 각 스크립트가 하고 있고, 그 자리에서 부르면 된다.
// 판정을 도전과제 쪽에 한 벌 더 만들지 않는다.
//
// ⚠️ **id 를 화이트리스트로 거른다.** `/advancement grant` 는 없는 id 를 받으면 조용히
//    아무것도 안 한다(runCommandSilent 는 반환값도 void 다). 그러면 그 도전과제는 영영
//    안 열리고, 알아챌 방법은 몇 주 뒤 「왜 이건 안 뜨지」뿐이다.
//    여기서 거르면 오타가 로그에 한 줄로 남는다.
//
// ※ 이 목록은 `py tools/gen_advancements.py` 가 자기 표와 대조한다 — 어긋나면 알려준다.
var LS_ADV = [
  'root',
  'relic', 'star2', 'star3', 'star4', 'star5',
  'siege_first', 'siege_grand', 'wall_break', 'threat_max',
  'gate1', 'gate2', 'gate3', 'gate4', 'finale',
  'town_ramparts', 'town_workshop', 'town_sanctum', 'town_districts', 'town_all',
  'rescue_first', 'rescue_all', 'beacon_first', 'beacon_four',
  'bounty_first', 'casino_win', 'wipe'
]
var LS_ADV_SET = {}
LS_ADV.forEach(function (a) { LS_ADV_SET[a] = true })

// target = 플레이어 이름 또는 셀렉터(`@a`).
//   개인의 것(가호·유물·각성)은 그 사람에게, 서버 전체가 이룬 것(관문·공성·마을)은 `@a` 로.
// 이미 가진 사람에게 다시 줘도 무해하다 — 마인크래프트가 알아서 넘긴다(토스트도 안 뜬다).
function lsAdv(server, target, id) {
  if (!LS_ADV_SET[id]) { lsWarn('lsAdv:없는-id', id); return }
  if (!target) { lsWarn('lsAdv:대상-없음', id); return }
  // 아무도 없으면 조용히 넘어간다. `@a` 로 부르는 자리 중에는 틱 루프 안에 있는 것도 있어
  // (위협도 최대 등), 접속자가 0 일 때 「No player was found」가 반복될 수 있다.
  // 접속했을 때 다시 주는 경로가 필요한 도전과제는 없다 — 전부 사람이 있어야 일어나는 일이다.
  try { if (!server.players.length) return } catch (e) { lsWarn('lsAdv:players', e); return }
  try { server.runCommandSilent('advancement grant ' + target + ' only lsrelics:' + id) }
  catch (e) { lsWarn('lsAdv:' + id, e) }
}

// ── 접속 시 도전과제 맞추기 ──
// **두 가지를 푼다. 둘 다 안 하면 나무가 거짓말을 한다:**
//   ① 도전과제는 2026-08-06 에 생겼다. 그 전에 진행한 사람은 가호도 각성도 있는데 나무가 비어 있다.
//   ② 관문 2개가 깨진 서버에 새로 들어온 사람은 `gate1`·`gate2` 를 영영 못 받는다 —
//      그건 **서버가 이룬 것**이라 그 자리에 없었어도 기록에 남아야 한다.
//
// 지급 자체가 멱등이라(이미 가진 것은 마인크래프트가 넘긴다) 접속마다 돌려도 무해하다.
// 그래서 「했는가」 플래그를 따로 두지 않는다 — 플래그가 있으면 그게 또 어긋날 자리가 된다.
//
// ※ 여기 없는 것들(`wipe`·`casino_win`·`bounty_first`·`wall_break`·`threat_max`)은
//   **일어난 순간이 곧 조건**이라 나중에 되짚을 방법이 없다. 그 자리에서만 준다.
function lsAdvSync(server, player) {
  var name = player.username
  try {
    if (!String(LS.fate(server, name) || '')) return   // 가호가 없으면 나무 자체가 안 열린다
    lsAdv(server, name, 'root')

    if (LS.hasRelic(server, name)) lsAdv(server, name, 'relic')
    var st = LS.star(server, name) | 0
    for (var i = 2; i <= 5; i++) { if (st >= i) lsAdv(server, name, 'star' + i) }

    // ── 여기부터는 서버가 이룬 것 ──
    var pg = LS.progress(server) | 0
    for (var g = 1; g <= 4; g++) { if (pg >= g) lsAdv(server, name, 'gate' + g) }
    if (LS.firstSiegeDone(server)) lsAdv(server, name, 'siege_first')

    var pop = LS.population(server) | 0
    if (pop >= 1) lsAdv(server, name, 'rescue_first')
    if (pop >= 6) lsAdv(server, name, 'rescue_all')

    var bc = LS.beaconCount(server) | 0
    if (bc >= 1) lsAdv(server, name, 'beacon_first')
    if (bc >= 4) lsAdv(server, name, 'beacon_four')

    // 마을 — 트랙 최대 단계(4)는 `TownCatalog` 가 갖고 있지만, 여기서 물어볼 통로가 없다.
    // 4 를 그대로 쓴다. 카탈로그가 늘면 이 줄이 낡는데, 그때는 덜 주는 쪽으로 틀린다(안전한 방향).
    var tracks = ['ramparts', 'workshop', 'sanctum', 'districts']
    var full = 0
    tracks.forEach(function (t) {
      if ((LS.townLevel(server, t) | 0) >= 4) { lsAdv(server, name, 'town_' + t); full++ }
    })
    if (full === tracks.length) lsAdv(server, name, 'town_all')
  } catch (e) { lsWarn('lsAdvSync', e) }
}

PlayerEvents.loggedIn(event => {
  var p = event.player
  var s = event.server
  if (!p || !s) return
  // 다른 접속 훅(가호 재적용·칭호 이름표)이 먼저 정리되도록 뒤에 선다.
  s.scheduleInTicks(70, () => { try { lsAdvSync(s, p) } catch (e) { lsWarn('lsAdvSync:hook', e) } })
})

// 지표면 Y — 몹·제단·구출 지점을 지형에 맞춰 놓기 위한 것.
// 실패하면 fallback 을 준다.
//
// ── 왜 공용으로 올렸나 ──
// 이 함수가 ls_siege·ls_rescue·ls_rift 에 **똑같이 복붙돼 있었고, 셋 다 안에서 `const y` 를
// 선언했다.** 이름이 겹치니 Rhino 가 'redeclaration of var y' 로 매번 터졌고, 전부 fallback 만
// 돌려줘서 **막으려던 "지형 무시 배치"가 그대로 일어났다** (공성 몹은 성역 높이에, 제단·구출
// 지점도 엉뚱한 Y 에). 복사본이 셋이면 같은 실수도 셋이 된다 — 한 곳으로 합친다.
function lsSurfaceY(server, x, z, fallback) {
  // ※ `var` 다. `const`/`let` 을 try 안에 쓰면 Rhino 가 'redeclaration of var' 로 매번 터진다 —
  //   이름을 유일하게 바꿔도 안 낫는다(고유 이름 lsSy 로도 터졌다). `var` 는 재선언이 합법이라 안전하다.
  try {
    var lsSy = server.overworld().getHeight('MOTION_BLOCKING_NO_LEAVES', Math.floor(x), Math.floor(z))
    if (lsSy > -60 && lsSy < 320) return lsSy
  } catch (e) { lsWarn('ls_util:surface-y', e) }
  return fallback
}

// 이름으로 접속 중인 플레이어 찾기. 없으면 null.
//
// ── 왜 server.getPlayer(name) 을 쓰면 안 되나 ──
// 이름을 받아줄 것처럼 생겼지만 **UUID 전용**이다. 이름을 넣으면 파싱 단계에서
//     UUID string must be 32 or 36 characters long, got 'a_ka1128'
// 로 터지고, 명령이 통째로 죽는다(도박장 홀짝이 이 상태였다).
// 예외라서 조용히 넘어가지도 않고, 아이템·판돈을 건드리기 전에 죽는 게 그나마 다행인 경우다.
function lsPlayerByName(server, name) {
  var found = null
  try {
    server.players.forEach(p => { if (String(p.username) === String(name)) found = p })
  } catch (e) { console.log('[LS-UTIL] find fail: ' + e); return null }
  return found
}

// ── 관리자 명령의 "대상 플레이어" 인자 ──
// 접속자 이름을 탭 완성으로 띄우는 문자열 인자. ls_fate / ls_relic / ls_ascend 가 같이 쓴다.
//
// ※ EntityArgument 를 쓰면 탭 완성이 공짜지만 **접속자만** 잡는다. 가호·유물·각성은 전부
//   <키>_<이름> 문자열로 저장돼 있어 오프라인 대상도 지정·해제할 수 있어야 한다.
//   그래서 문자열 인자에 제안만 얹는다 — 탭에 안 떠도 직접 입력하면 동작한다.
function lsTargetArg(event, Commands, Arguments, argName) {
  return Commands.argument(argName || 'target', Arguments.STRING.create(event))
    .suggests((ctx, b) => {
      try { ctx.source.server.players.forEach(p => b.suggest(String(p.username))) }
      catch (e) { lsWarn('ls_util:target-suggest', e) }
      return b.buildFuture()
    })
}

console.log('[Last Stardust] 공용 유틸 로드됨 — 인벤토리 개수/회수 · 이름으로 플레이어 찾기 · 대상 인자')
