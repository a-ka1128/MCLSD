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

console.log('[Last Stardust] 공용 유틸 로드됨 — 인벤토리 개수/회수 · 이름으로 플레이어 찾기')
