// Last Stardust — 봉화 폐허 (Beacon Ruin)  [탐험 → 영토 수복]
//
// ── 왜 생겼나 ──
// 정화 봉화(`ls_beacon.js`)는 여태 **플레이어가 신호기를 직접 만들어 세우는** 것이었다.
// 그러려면 네더의 별(위더) + 150 Ducat 이 필요해서, 실제로는 **한 기도 안 세워졌다**
// (2026-08-13 점검: 봉화 0기 · 금고 96 < 비용 150). 「영토 수복」이 설계의 중심인데
// 그 입구가 후반 콘텐츠 뒤에 잠겨 있었던 것이다.
//
// 여기가 그 입구를 앞으로 당긴다 — **세상 곳곳에 이미 폐허가 있고, 찾아가서 되살린다.**
// 봉화는 만드는 게 아니라 **꺼져 있던 것**이고, 플레이어가 하는 일은 제작이 아니라 **복원**이다.
//
// ── 균열 노드와 짝이다 (`ls_rnode.js`) ──
//   어둠(노드)  = **예고한다.** 방위와 거리를 알려주고, 찾아가 부순다.
//   빛(폐허)    = **예고하지 않는다.** 탐험하다 마주친다.
// 그래서 폐허의 출현 반경(128)이 노드(64)보다 넓다 — 좌표를 모르고 지나가도 걸려야 한다.
//
// ── 장부는 `ls_beacon.js` 와 «같은 것»을 쓴다 ──
// 점화하면 `LS.addBeacon` 으로 **기존 봉화 장부에 들어간다.** 위협 하한(2기당 -1)·정화 반경·
// 희망 게이지가 전부 그 장부를 세고 있어서, 여기서 따로 세면 **세 곳이 조용히 갈린다**
// (`ls_beacon.js` 헤더가 경고하는 바로 그 사고). 이 파일은 «폐허의 위치와 상태»만 갖는다.
//
// ⚠️ 이 파일의 상태는 KubeJS persistentData 다 — `ls_rift.js` 가 제단 좌표를 두는 것과 같다.
//    모드(LSData)를 안 건드리므로 **jar 재빌드가 필요 없다.**

function ruStore(server) { return server.overworld().persistentData }
function ruGetI(server, k) { return ruStore(server).getInt(k) }
function ruSetI(server, k, v) { ruStore(server).putInt(k, v) }
function ruGetB(server, k) { return ruStore(server).getBoolean(k) }
function ruSetB(server, k, v) { ruStore(server).putBoolean(k, v) }
function ruGetS(server, k) { return String(ruStore(server).getString(k) || '') }
function ruSetS(server, k, v) { ruStore(server).putString(k, v) }

// ⚠️ 상수는 **전부 핸들러 위**에 둔다. Rhino 는 아래에 선언된 top-level const 를
//    핸들러가 못 보는 경우가 있다(`ls_siege.js` 헤더에 기록된 함정).
const RU_COUNT = 6           // 폐허 수. 봉화 2기당 위협 하한 -1 → 전부 밝히면 -3
const RU_MIN_R = 400         // 성역에서 최소 거리(m)
const RU_MAX_R = 2000        // 최대 거리. 균열 원정 제단(2000~5000)보다 «안쪽»이다 —
                             // 폐허는 원정의 목적지가 아니라 원정 «길목»에서 마주치는 것이다.
const RU_GAP = 128           // 폐허끼리 최소 거리. `ls_beacon.js` 의 PB_MIN_DIST(96)보다 넉넉히 —
                             // 좌표를 굴려 놓고 나중에 등록이 거부되면 폐허가 영영 안 켜진다.
const RU_REVEAL = 128        // 이 거리 안에 들어오면 출현. 노드(64)의 두 배 — 예고가 없으니까.
const RU_ROLL_TRIES = 60     // 좌표 롤 재시도
// ── 월드 스폰 금지 반경 ──
// 첫 접속이 스폰이고, 거기서 **비행선을 타고 성역으로 떠나는** 것이 이 서버의 시작이다
// (`docs/LAUNCH.md` A-2·B-3). 그 자리에 폐허가 서 있으면 「출발지」가 「목적지」처럼 보여서,
// 아직 아무 설명도 못 들은 사람이 비행선 대신 폐허로 걸어간다.
//
// 지금 수치로는 어차피 못 간다 — 성역이 스폰에서 8371m 떨어져 있고 폐허는 최대 2000m 다.
// 그래도 명시적으로 막는다. 성역을 옮기거나 RU_MAX_R 을 올리는 날 이 보장이 **조용히**
// 사라지는데, 그때 이 줄이 없으면 사라진 줄도 모른다.
const RU_SPAWN_KEEP = 1500

// 폐허 이름. 장부 키라 **서로 달라야 한다.** 순서대로 쓴다 — 무작위로 뽑으면 겹친다.
const RU_NAMES = [
  '무너진 초소', '잊힌 사당', '재의 망루', '검은 이정표', '가라앉은 제단', '부서진 등대',
  '이끼 낀 첨탑', '말라붙은 우물'
]

// 폐허 팔레트
const RU_FLOOR = 'minecraft:mossy_cobblestone'
const RU_WALL  = 'minecraft:mossy_stone_bricks'
const RU_BROKE = 'minecraft:cracked_stone_bricks'
const RU_PEDE  = 'minecraft:iron_block'

function ruSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function ruPlay(server, s, v, p) { server.runCommandSilent(`execute as @a at @s run playsound ${s} master @s ~ ~ ~ ${v} ${p}`) }

function ruSanc(server) {
  return { x: LS.sanctuaryX(server), y: LS.sanctuaryY(server), z: LS.sanctuaryZ(server) }
}

// ── 상태 ──
// 0 = 미발견(좌표만 있음) · 1 = 출현(꺼진 폐허) · 2 = 점화됨
function ruState(server, i) { return ruGetI(server, `ru_${i}_s`) }
function ruSetState(server, i, v) { ruSetI(server, `ru_${i}_s`, v) }
function ruX(server, i) { return ruGetI(server, `ru_${i}_x`) }
function ruY(server, i) { return ruGetI(server, `ru_${i}_y`) }
function ruZ(server, i) { return ruGetI(server, `ru_${i}_z`) }
function ruName(server, i) { return ruGetS(server, `ru_${i}_n`) }

function ruCount(server, want) {
  var n = 0
  for (var ruCi = 0; ruCi < RU_COUNT; ruCi++) { if (ruState(server, ruCi) === want) n++ }
  return n
}

// ── 좌표 굴리기 (최초 1회) ──
// 성역이 정해진 뒤에만 돈다 — 성역이 기준점이라 그 전에 굴리면 0,0 근처에 몰린다.
// 리셋 직후에는 성역이 비어 있으므로 **매 틱 확인하다가 생기면 그때 굴린다.**
// 좌표 하나를 굴린다. 조건을 못 맞추면 null.
//
// ⚠️ **배치 판정은 여기 하나뿐이어야 한다.** 예전 구조에서는 `ruInit` 과 `/ruin reroll` 이
//    각자 굴렸는데, 그러면 한쪽에만 조건을 추가하는 날 둘이 갈린다 — 실제로 스폰 금지
//    반경을 넣을 때 reroll 쪽을 빠뜨릴 뻔했다.
function ruRoll(server, avoid) {
  const c = ruSanc(server)
  for (var ruRt = 0; ruRt < RU_ROLL_TRIES; ruRt++) {
    var ang = Math.random() * Math.PI * 2
    var dist = RU_MIN_R + Math.random() * (RU_MAX_R - RU_MIN_R)
    var rx = Math.floor(c.x + Math.cos(ang) * dist)
    var rz = Math.floor(c.z + Math.sin(ang) * dist)
    if (lsNearSpawn(server, rx, rz, RU_SPAWN_KEEP)) continue
    var ok = true
    for (var ruRj = 0; ruRj < avoid.length && ok; ruRj++) {
      var ddx = rx - avoid[ruRj][0], ddz = rz - avoid[ruRj][1]
      if (ddx * ddx + ddz * ddz < RU_GAP * RU_GAP) ok = false
    }
    if (ok) return [rx, rz]
  }
  return null
}

// 나(i)를 뺀 나머지 폐허 좌표 — reroll 이 다른 폐허를 피하게 한다.
function ruOthers(server, skip) {
  var out = []
  for (var ruOi = 0; ruOi < RU_COUNT; ruOi++) {
    if (ruOi === skip || ruState(server, ruOi) === 3) continue
    out.push([ruX(server, ruOi), ruZ(server, ruOi)])
  }
  return out
}

function ruInit(server) {
  if (ruGetB(server, 'ru_init')) return
  if (!LS.hasSanctuary(server)) return
  const c = ruSanc(server)
  var placed = []
  for (var ruIi = 0; ruIi < RU_COUNT; ruIi++) {
    var spot = ruRoll(server, placed)
    if (spot) {
      placed.push(spot)
      ruSetI(server, `ru_${ruIi}_x`, spot[0])
      ruSetI(server, `ru_${ruIi}_z`, spot[1])
      ruSetI(server, `ru_${ruIi}_y`, 0)
      ruSetState(server, ruIi, 0)
      ruSetS(server, `ru_${ruIi}_n`, RU_NAMES[ruIi % RU_NAMES.length])
    } else {
      // 자리를 못 찾으면 **그 폐허는 없는 것으로 둔다.** 억지로 겹쳐 놓으면
      // 등록이 거부돼 영영 안 켜지는 폐허가 생긴다 — 그게 더 나쁘다.
      ruSetState(server, ruIi, 3) // 3 = 자리 없음(비활성)
      console.log(`[LS-RUIN] ✘ ${ruIi}번 자리를 못 찾았다 (${RU_ROLL_TRIES}회 시도)`)
    }
  }
  ruSetB(server, 'ru_init', true)
  console.log(`[LS-RUIN] 폐허 ${RU_COUNT}곳 배치 완료 (성역 ${c.x},${c.z} 기준 ${RU_MIN_R}~${RU_MAX_R}m)`)
}

// ── 폐허 건축 ──
// 명령어로 짓는다(`ls_rift.js` 의 buildAltar 와 같은 방식). 구조물 파일(.nbt)로 바꾸려면
// 이 함수 하나만 갈아끼우면 된다 — 부르는 쪽은 좌표만 준다.
//
// ⚠️ `hollow`/`outline` 을 안 쓴다. 2칸 높이 상자에 쓰면 **천장까지 막혀서** 안이 안 보인다.
//    벽 네 장을 따로 세우는 쪽이 결과가 눈에 그려진다.
function ruBuild(server, ax, ay, az) {
  const cmd = (s) => server.runCommandSilent(s)
  const R = 5
  // 1. 부지 정리 + 바닥
  cmd(`fill ${ax - R} ${ay} ${az - R} ${ax + R} ${ay + 5} ${az + R} minecraft:air`)
  cmd(`fill ${ax - R} ${ay - 1} ${az - R} ${ax + R} ${ay - 1} ${az + R} ${RU_FLOOR}`)
  // 2. 벽 네 장 (2칸 높이)
  cmd(`fill ${ax - R} ${ay} ${az - R} ${ax + R} ${ay + 1} ${az - R} ${RU_WALL}`)
  cmd(`fill ${ax - R} ${ay} ${az + R} ${ax + R} ${ay + 1} ${az + R} ${RU_WALL}`)
  cmd(`fill ${ax - R} ${ay} ${az - R} ${ax - R} ${ay + 1} ${az + R} ${RU_WALL}`)
  cmd(`fill ${ax + R} ${ay} ${az - R} ${ax + R} ${ay + 1} ${az + R} ${RU_WALL}`)
  // 3. 무너진 자리 — 사방에 입구를 낸다. 벽이 멀쩡하면 폐허가 아니라 «집»으로 보인다.
  cmd(`fill ${ax - 1} ${ay} ${az - R} ${ax + 1} ${ay + 1} ${az - R} minecraft:air`)
  cmd(`fill ${ax - 1} ${ay} ${az + R} ${ax + 1} ${ay + 1} ${az + R} minecraft:air`)
  cmd(`fill ${ax - R} ${ay + 1} ${az - 2} ${ax - R} ${ay + 1} ${az + 2} minecraft:air`)
  cmd(`fill ${ax + R} ${ay + 1} ${az - 3} ${ax + R} ${ay + 1} ${az + 1} minecraft:air`)
  // 4. 봉화 대좌 — **일부러 미완성이다.**
  //    신호기는 바로 아래 3×3 이 «전부» 채워져야 빛기둥이 선다. 십자 네 칸을 부서진 채로
  //    두면 블록은 있는데 **꺼져 있다.** 점화(ruIgnite)가 그 네 칸을 메워 되살린다.
  //    「부서진 것을 고친다」가 명령어 한 줄이 아니라 눈에 보이는 변화가 된다.
  cmd(`fill ${ax - 1} ${ay} ${az - 1} ${ax + 1} ${ay} ${az + 1} ${RU_PEDE}`)
  cmd(`setblock ${ax - 1} ${ay} ${az} ${RU_BROKE}`)
  cmd(`setblock ${ax + 1} ${ay} ${az} ${RU_BROKE}`)
  cmd(`setblock ${ax} ${ay} ${az - 1} ${RU_BROKE}`)
  cmd(`setblock ${ax} ${ay} ${az + 1} ${RU_BROKE}`)
  cmd(`setblock ${ax} ${ay + 1} ${az} minecraft:beacon`)
  // 5. 전송석 — 2칸 블록이라 아래/위를 따로 놓는다.
  //    (`waystones:waystone[facing=…,half=lower|upper]` — jar 의 blockstates 로 확인)
  //    이름은 «플레이어가» 활성화할 때 정한다. 여기서 넣지 않는다.
  cmd(`setblock ${ax + 3} ${ay} ${az} waystones:waystone[facing=west,half=lower]`)
  cmd(`setblock ${ax + 3} ${ay + 1} ${az} waystones:waystone[facing=west,half=upper]`)
  // 6. 모서리 기둥 + 소울 랜턴 (밤에 멀리서 보이는 표식)
  const corners = [[ax - R, az - R], [ax - R, az + R], [ax + R, az - R], [ax + R, az + R]]
  corners.forEach(cc => {
    cmd(`fill ${cc[0]} ${ay} ${cc[1]} ${cc[0]} ${ay + 2} ${cc[1]} ${RU_WALL}`)
    cmd(`setblock ${cc[0]} ${ay + 3} ${cc[1]} minecraft:soul_lantern`)
  })
}

// ── 폐허 철거 ──
// 지어 놓은 것을 지운다. 좌표를 다시 굴릴 때 **먼저** 불러야 한다 —
// 안 그러면 옛 자리에 건물만 남아 「누가 지었는지 모를 폐허」가 세계에 쌓인다.
// 점화된 폐허(state 2)에는 쓰지 않는다. 그건 장부에 든 진짜 봉화다.
function ruClear(server, i) {
  const ax = ruX(server, i), ay = ruY(server, i), az = ruZ(server, i)
  const R = 5
  try {
    server.runCommandSilent(`fill ${ax - R} ${ay - 1} ${az - R} ${ax + R} ${ay + 3} ${az + R} minecraft:air`)
  } catch (e) { lsWarn('ls_ruin:clear', e) }
  console.log(`[LS-RUIN] clear ${i} at ${ax},${ay},${az}`)
}

// ── 발견 ──
function ruRevealIfNear(server) {
  // ⚠️⚠️ **배치 «전»에는 한 번도 돌면 안 된다.**
  // persistentData 의 getInt 는 «없는 키»에 0 을 돌려준다. 그래서 이 줄이 없으면
  // 성역을 정하기 전(= ruInit 이 아직 안 돈 상태)에 폐허 여섯의 좌표가 전부 0,0 으로
  // 읽히고, **스폰 근처에 있는 사람이 여섯 개를 0,0 에 통째로 쌓는다.**
  // 이름도 빈 문자열로 나오고("§f 을(를) 발견했다"), Y 는 앞 폐허 위로 2칸씩 올라간다.
  // 2026-08-13 인게임에서 실제로 그렇게 됐다 — 「없는 값은 0」이 조용히 유효한 좌표가 된다.
  if (!ruGetB(server, 'ru_init')) return
  for (var ruRi = 0; ruRi < RU_COUNT; ruRi++) {
    if (ruState(server, ruRi) !== 0) continue
    var rx = ruX(server, ruRi), rz = ruZ(server, ruRi)
    // 배치가 끝났는데도 0,0 이면 그 항목은 굴려지지 않은 것이다(스폰 금지 반경이 0,0 을
    // 이미 막으므로 정상 배치로는 나올 수 없는 값이다). 조용히 건너뛴다.
    if (rx === 0 && rz === 0) continue
    var who = null
    server.players.forEach(p => {
      if (who) return
      var dx = p.x - rx, dz = p.z - rz
      if (dx * dx + dz * dz <= RU_REVEAL * RU_REVEAL) who = p
    })
    if (!who) continue
    var ry = lsSurfaceY(server, rx, rz, Math.floor(who.y))
    ruBuild(server, rx, ry, rz)
    ruSetI(server, `ru_${ruRi}_y`, ry)
    ruSetState(server, ruRi, 1)
    var nm = ruName(server, ruRi)
    server.runCommandSilent(`title @a title {"text":"폐허 발견","color":"aqua","bold":true}`)
    server.runCommandSilent(`title @a subtitle {"text":"${nm} — 꺼진 봉화가 있다","color":"gray"}`)
    ruPlay(server, 'minecraft:block.beacon.deactivate', 0.8, 0.7)
    ruSay(server, `§b✦ §f${who.username}§b 이(가) §f${nm}§b 을(를) 발견했다. §7좌표 §e${rx}, ${ry + 1}, ${rz}`)
    ruSay(server, '§8   신호기를 우클릭하면 되살아난다. 옆에 전송석도 있다.')
    console.log(`[LS-RUIN] reveal ${ruRi} (${nm}) at ${rx},${ry},${rz} by ${who.username}`)
    return // 한 틱에 하나만 — 여러 개가 동시에 뜨면 알림이 서로를 덮는다
  }
}

// ── 점화 ──
// 대좌를 메우고 기존 봉화 장부에 넣는다. **비용은 없다** — 찾아온 것이 대가다
// (`/purifier light` 의 150 Ducat 은 「아무 데나 세우는」 경로의 값이라 그대로 둔다).
//
// ⚠️ `pbRegister` 의 «96m 안에 다른 봉화가 있으면 거부» 를 여기서는 **안 본다.**
//    폐허는 서로 128m 이상 떨어지게 굴리므로 폐허끼리는 문제가 없고, 사람이 폐허 옆에
//    손으로 봉화를 세운 경우에는 **폐허가 이긴다** — 먼저 있던 쪽이니까.
//    여기에 거리 검사를 넣으면 「영영 못 켜는 폐허」가 생긴다. 그게 훨씬 나쁘다.
function ruIgnite(server, i, player) {
  const nm = ruName(server, i)
  const rx = ruX(server, i), ry = ruY(server, i), rz = ruZ(server, i)
  var already = true
  try { already = !!LS.hasBeacon(server, nm) } catch (e) { lsWarn('ls_ruin:has', e) }
  if (already) { player.tell(Text.of(`§c이미 장부에 있는 이름: ${nm}`)); return false }

  var added = false
  try { added = !!LS.addBeacon(server, nm, rx, ry + 1, rz) } catch (e) { lsWarn('ls_ruin:add', e) }
  if (!added) { player.tell(Text.of('§c봉화 등록 실패 §7— 로그를 확인하세요.')); return false }

  // 부서진 십자 네 칸을 메운다 → 3×3 이 완성되면서 빛기둥이 선다
  server.runCommandSilent(`fill ${rx - 1} ${ry} ${rz - 1} ${rx + 1} ${ry} ${rz + 1} ${RU_PEDE}`)
  ruSetState(server, i, 2)

  var total = 0
  try { total = LS.beaconCount(server) } catch (e) { lsWarn('ls_ruin:count', e) }
  server.runCommandSilent('title @a title {"text":"정화 봉화 점화","color":"aqua","bold":true}')
  server.runCommandSilent(`title @a subtitle {"text":"${nm} — 이 땅은 되찾았다","color":"gray"}`)
  ruPlay(server, 'minecraft:block.beacon.activate', 1, 1)
  ruPlay(server, 'minecraft:ui.toast.challenge_complete', 0.7, 1.2)
  ruSay(server, `§b✦ §f${nm}§b 의 봉화가 되살아났다 §7(${player.username}) — 봉화 ${total}기 §8(2기당 위협 하한 -1)`)
  try { lsAdv(server, '@a', 'beacon_first'); if (total >= 4) lsAdv(server, '@a', 'beacon_four') }
  catch (e) { lsWarn('ls_ruin:adv', e) }
  console.log(`[LS-RUIN] ignite ${i} (${nm}) at ${rx},${ry},${rz} total=${total}`)
  return true
}

// ── 신호기 우클릭 ──
// ⚠️ **`event.cancel()` 은 맨 마지막이다.** KubeJS 는 cancel 을 «예외»로 구현해서
//    그 뒤의 코드가 한 줄도 안 돈다. 순서를 바꾸면 「우클릭해도 아무 일이 없다」가 되고,
//    오류가 안 나서 고장으로도 안 보인다. (`ls_rift.js`·`ls_relic.js` 에서 두 번 밟았다.)
BlockEvents.rightClicked(event => {
  const b = event.block
  if (!b || b.id !== 'minecraft:beacon') return
  const p = event.player
  if (!p) return
  const server = p.server
  if (!server) return
  var hit = -1
  try {
    var bx = Number(b.x), by = Number(b.y), bz = Number(b.z)
    for (var ruHi = 0; ruHi < RU_COUNT && hit < 0; ruHi++) {
      if (ruState(server, ruHi) !== 1) continue
      if (bx === ruX(server, ruHi) && by === ruY(server, ruHi) + 1 && bz === ruZ(server, ruHi)) hit = ruHi
    }
    if (hit >= 0) ruIgnite(server, hit, p)
  } catch (e) { lsWarn('ls_ruin:click', e); hit = -1 }
  // 바닐라 신호기 GUI 를 막는다. 폐허의 봉화는 효과를 고르는 물건이 아니다.
  if (hit >= 0) event.cancel()
})

// ── 틱 ──
let RU_TICK = 0
ServerEvents.tick(event => {
  RU_TICK++
  if (RU_TICK % 40 !== 0) return // 2초마다. 폐허는 급할 게 없다
  try {
    ruInit(event.server)
    ruRevealIfNear(event.server)
  } catch (e) { lsWarn('ls_ruin:tick', e) }
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event

  event.register(Commands.literal('ruin')
    .executes(ctx => {
      const s = ctx.source.server
      const op = ctx.source.hasPermission(2)
      var found = ruCount(s, 1) + ruCount(s, 2)
      var lit = ruCount(s, 2)
      ctx.source.sendSystemMessage(Text.of(`§6═══ ✦ 봉화 폐허 §7(발견 ${found} · 점화 ${lit} / ${RU_COUNT}) §6═══`))
      if (!ruGetB(s, 'ru_init')) {
        ctx.source.sendSystemMessage(Text.of('§8아직 배치 전 — §7성역이 정해지면 자동으로 흩어진다.'))
        return 1
      }
      for (var ruLi = 0; ruLi < RU_COUNT; ruLi++) {
        var st = ruState(s, ruLi)
        if (st === 3) continue
        var nm = ruName(s, ruLi)
        if (st === 2) {
          ctx.source.sendSystemMessage(Text.of(`§b ✦ §f${nm} §7${ruX(s, ruLi)}, ${ruZ(s, ruLi)} §8점화됨`))
        } else if (st === 1) {
          ctx.source.sendSystemMessage(Text.of(`§7 ○ §f${nm} §7${ruX(s, ruLi)}, ${ruZ(s, ruLi)} §8꺼져 있음 — 신호기를 우클릭`))
        } else if (op) {
          // 미발견 좌표는 **OP 에게만** 보인다. 목록에 띄우면 탐험이 사라진다.
          ctx.source.sendSystemMessage(Text.of(`§8 · ${nm} §8${ruX(s, ruLi)}, ${ruZ(s, ruLi)} §8(미발견)`))
        } else {
          ctx.source.sendSystemMessage(Text.of('§8 · §8??? — 아직 찾지 못했다'))
        }
      }
      return 1
    })
    // 자리를 다시 굴린다. 폐허가 누군가의 건축물 위에 떨어졌을 때의 탈출구다 —
    // 출현은 지형을 «덮어쓰므로» 되돌릴 방법이 이것뿐이다.
    .then(Commands.literal('reroll').requires(s => s.hasPermission(2))
      .then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
        const s = ctx.source.server
        const i = Arguments.INTEGER.getResult(ctx, 'n')
        if (i < 0 || i >= RU_COUNT) { ctx.source.sendSystemMessage(Text.of(`§c0 ~ ${RU_COUNT - 1} 사이`)); return 0 }
        if (ruState(s, i) === 2) { ctx.source.sendSystemMessage(Text.of('§c이미 점화된 폐허는 못 옮깁니다. §7/purifier remove 로 먼저 지우세요.')); return 0 }
        if (!LS.hasSanctuary(s)) { ctx.source.sendSystemMessage(Text.of('§c성역이 먼저다 — §e/sanctuary here')); return 0 }
        const spot = ruRoll(s, ruOthers(s, i))
        if (!spot) { ctx.source.sendSystemMessage(Text.of(`§c자리를 못 찾았습니다 (${RU_ROLL_TRIES}회 시도) — 다시 시도해 보세요.`)); return 0 }
        // 이미 세워진 것이면 «먼저» 철거한다. 안 그러면 옛 자리에 건물만 남는다.
        if (ruState(s, i) === 1) ruClear(s, i)
        ruSetI(s, `ru_${i}_x`, spot[0])
        ruSetI(s, `ru_${i}_z`, spot[1])
        ruSetI(s, `ru_${i}_y`, 0)
        ruSetState(s, i, 0)
        ctx.source.sendSystemMessage(Text.of(`§a${ruName(s, i)} → ${spot[0]}, ${spot[1]} §7(미발견으로 되돌림)`))
        console.log(`[LS-RUIN] reroll ${i} -> ${spot[0]},${spot[1]}`)
        return 1
      })))
    // 전부 다시 굴린다. 초기화 직후 성역을 옮겼을 때 쓴다.
    .then(Commands.literal('reset').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      if (!LS.hasSanctuary(s)) { ctx.source.sendSystemMessage(Text.of('§c성역이 먼저다 — §e/sanctuary here')); return 0 }
      // 점화된 폐허는 «장부에 든 진짜 봉화»다. 좌표만 옮기면 봉화가 허공을 가리키게 된다.
      if (ruCount(s, 2) > 0) {
        ctx.source.sendSystemMessage(Text.of('§c점화된 폐허가 있습니다. §7/purifier remove <이름> 으로 먼저 지우세요.'))
        return 0
      }
      // 세워 둔 것을 «먼저» 철거한다. 안 그러면 월드에 주인 없는 폐허가 남는다 —
      // 2026-08-13, 성역 지정 전에 0,0 에 여섯 개가 쌓였을 때 이게 없어서 손으로 지웠다.
      var wiped = 0
      for (var ruXi = 0; ruXi < RU_COUNT; ruXi++) {
        if (ruState(s, ruXi) === 1) { ruClear(s, ruXi); wiped++ }
        ruSetState(s, ruXi, 0)
      }
      ruSetB(s, 'ru_init', false)
      ruInit(s)
      ctx.source.sendSystemMessage(Text.of(`§a폐허 좌표를 전부 다시 굴렸습니다. §7(세워져 있던 ${wiped}곳은 철거)`))
      return 1
    })))
})
