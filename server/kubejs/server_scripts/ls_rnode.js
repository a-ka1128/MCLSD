// Last Stardust — 균열 노드 실물화 (Rift Node)  [어둠의 근원을 «찾아가 부순다»]
//
// ── 왜 생겼나 ──
// `/riftnode add <이름>` 은 여태 **장부일 뿐**이었다. 이름 한 줄이면 세계가 어두워지는데
// (노드 1개 = 위협 하한 +2 · 노드가 있으면 매일 위협도 +1) 실물이 없어서
// **플레이어가 할 수 있는 게 없었다.** 없애는 길도 OP 의 `/riftnode remove` 뿐이라,
// 「어둠은 OP 가 놓고 OP 가 치운다」였다.
//
// 2026-08-13 점검에서 이게 **실물 검증이 없는 유일한 시스템**으로 드러났다 —
// 제단(`/relic altar`)은 자석석을, 봉화(`/purifier light`)는 신호기를 확인하는데
// 노드만 이름을 그대로 믿었다. 여기가 그 구멍을 막는다.
//
// ── 흐름 ──
//   spawn/auto  →  링 좌표 롤 + **장부 등록**(어둠은 예고 즉시 진짜다)
//               →  방위·거리 예고
//               →  플레이어가 64칸 접근  →  균열이 열린다(구조물 + 수호자)
//               →  수호자를 정리하고 **균열핵**을 부순다
//               →  기존 `/riftnode remove` 로 흘려보낸다 (보상·최종장 판정이 한 곳)
//
// ── 봉화 폐허(`ls_ruin.js`)와 짝이다 ──
//   어둠 = **예고한다.** 방위와 거리를 알려준다. 찾아가는 것이 목적이다.
//   빛   = **예고하지 않는다.** 탐험하다 마주친다.
//
// ⚠️ 파괴 보상·최종장 개막은 **여기서 다시 계산하지 않는다.** `ls_siege.js` 의
//    `/riftnode remove` 가 별의 파편 2~3 · 공동 금고 · beginFinale 을 전부 갖고 있다.
//    같은 걸 두 곳에 두면 언젠가 한쪽만 고치게 된다.
//
// ── 2026-08-14: 한 개 → 최대 세 개 ──
// 상태를 «슬롯»으로 바꿨다(`rn_<i>_*`). 예전엔 `rn_name`/`rn_x` 처럼 한 벌뿐이라
// 두 번째 노드를 예고하면 첫 번째가 **조용히 덮여** 실물만 세계에 남았을 것이다.

function rnStore(server) { return server.overworld().persistentData }
function rnGetI(server, k) { return rnStore(server).getInt(k) }
function rnSetI(server, k, v) { rnStore(server).putInt(k, v) }
function rnGetB(server, k) { return rnStore(server).getBoolean(k) }
function rnSetB(server, k, v) { rnStore(server).putBoolean(k, v) }
function rnGetS(server, k) { return String(rnStore(server).getString(k) || '') }
function rnSetS(server, k, v) { rnStore(server).putString(k, v) }

// ⚠️ 상수는 전부 핸들러 «위»에 (Rhino top-level const 함정 — `ls_siege.js` 헤더 참고)
const RN_MAX = 3             // 동시에 서 있을 수 있는 균열 수
const RN_MIN_R = 800         // 성역에서 최소 거리(m). 봉화 폐허(400~2000)보다 멀다 —
const RN_MAX_R = 2500        // 어둠의 근원이 마을 코앞에 있으면 「원정」이 아니다.
const RN_GAP = 400           // 균열끼리 최소 거리. 셋이 한 골짜기에 몰리면 원정이 아니라 소풍이다.
const RN_APPROACH = 64       // 출현 거리. 예고를 받고 «찾아가는» 것이라 좁아도 된다.
const RN_ROLL_TRIES = 60
// 월드 스폰 금지 반경 — 봉화 폐허(`ls_ruin.js` RU_SPAWN_KEEP)와 같은 이유다.
// 첫 접속 자리에 어둠의 근원이 서 있으면 안 된다. 하물며 수호자가 딸려 나온다.
const RN_SPAWN_KEEP = 1500
const RN_GUARD_RADIUS = 64   // 이 안에 수호자가 남아 있으면 핵이 안 부서진다
const RN_CORE = 'minecraft:crying_obsidian'
// 자리가 빈 채로 이만큼 날이 지나면 새 균열이 하나 예고된다.
// 공성이 3일에 한 번이라(SIEGE_EVERY) 4일이면 「한 번은 편하게 넘어간다」가 된다 —
// 걷어낸 보람은 남기되 영원히 조용하지는 않게. **한 번에 하나씩만** 채운다.
const RN_RESPAWN_DAYS = 4
// 자동 예고에는 명령을 친 사람이 없다. rnSpawn 이 실패 사유를 돌려줄 곳만 있으면 되므로
// 삼켜 버리는 스텁을 준다 — 실패는 어차피 console.log 로도 남는다.
const RN_AUTO_SRC = { sendSystemMessage: function () {} }

// 수호자. 공성 정예와 같은 것들을 쓴다 — 새 몹을 들이면 밸런스를 처음부터 다시 잡아야 한다.
// 숫자는 「파티가 한 번 붙어볼 만한」 선. 너무 세면 노드가 영영 안 사라져 위협도가 15에 눌러앉는다.
const RN_GUARDS = [
  { id: 'cataclysm:ignited_berserker', n: 2 },
  { id: 'cataclysm:ignited_revenant', n: 2 },
  { id: 'minecraft:wither_skeleton', n: 6 }
]

const RN_NAMES = [
  '잿빛협곡', '검은 균열', '스러진 첨탑', '재의 분지', '메아리 없는 골짜기', '별무덤',
  '식은 등불', '가라앉은 종탑'
]

function rnSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function rnPlay(server, s, v, p) { server.runCommandSilent(`execute as @a at @s run playsound ${s} master @s ~ ~ ~ ${v} ${p}`) }
function rnSanc(server) { return { x: LS.sanctuaryX(server), y: LS.sanctuaryY(server), z: LS.sanctuaryZ(server) } }

// ── 슬롯 ──
// 0 = 빈 자리 · 1 = 예고됨(미출현) · 2 = 열림(실물 있음)
function rnState(server, i) { return rnGetI(server, `rn_${i}_s`) }
function rnSetState(server, i, v) { rnSetI(server, `rn_${i}_s`, v) }
function rnName(server, i) { return rnGetS(server, `rn_${i}_n`) }
function rnX(server, i) { return rnGetI(server, `rn_${i}_x`) }
function rnY(server, i) { return rnGetI(server, `rn_${i}_y`) }
function rnZ(server, i) { return rnGetI(server, `rn_${i}_z`) }

function rnClearSlot(server, i) {
  rnSetState(server, i, 0)
  rnSetS(server, `rn_${i}_n`, '')
  rnSetI(server, `rn_${i}_x`, 0)
  rnSetI(server, `rn_${i}_y`, 0)
  rnSetI(server, `rn_${i}_z`, 0)
}

function rnActive(server) {
  var n = 0
  for (var rnAi = 0; rnAi < RN_MAX; rnAi++) { if (rnState(server, rnAi) > 0) n++ }
  return n
}

function rnFreeSlot(server) {
  for (var rnFi = 0; rnFi < RN_MAX; rnFi++) { if (rnState(server, rnFi) === 0) return rnFi }
  return -1
}

function rnSlotOf(server, name) {
  for (var rnOi = 0; rnOi < RN_MAX; rnOi++) {
    if (rnState(server, rnOi) > 0 && rnName(server, rnOi) === name) return rnOi
  }
  return -1
}

// 수호자 수를 세는 **한 곳**. 파괴 판정(`BlockEvents.broken`)과 `/riftnode site` 가
// 같은 규칙을 보게 한다 — 두 곳에 같은 셀렉터를 적으면 언젠가 반경이 갈린다.
function rnGuardCount(server, nx, ny, nz) {
  return Number(server.runCommandSilent(
    `execute positioned ${nx} ${ny} ${nz} if entity @e[tag=ls_rnode_guard,distance=..${RN_GUARD_RADIUS}]`)) || 0
}

function rnInLedger(server, name) {
  try { return String(LS.nodeCsv(server) || '').split(',').indexOf(name) >= 0 }
  catch (e) { lsWarn('ls_rnode:ledger', e); return false }
}

// 아직 장부에 없는 이름을 고른다. 같은 이름을 두 번 넣으면 `/riftnode add` 가 거부한다.
function rnPickName(server) {
  for (var rnNi = 0; rnNi < RN_NAMES.length; rnNi++) {
    if (!rnInLedger(server, RN_NAMES[rnNi])) return RN_NAMES[rnNi]
  }
  return '이름 없는 균열 ' + Math.floor(Math.random() * 900 + 100)
}

// 방위 8방 — `ls_rift.js` 의 bearing8 과 같은 규칙(0=북, 시계방향).
function rnBearing(dx, dz) {
  var dirs = ['북', '북동', '동', '남동', '남', '남서', '서', '북서']
  var a = Math.atan2(dx, -dz) * 180 / Math.PI
  if (a < 0) a += 360
  return dirs[Math.round(a / 45) % 8]
}

// 좌표 하나를 굴린다. 못 찾으면 null.
// ⚠️ 판정이 여기 한 곳뿐이어야 한다 — 수동 spawn 과 자동 재등장이 각자 굴리면 언젠가 갈린다.
function rnRoll(server) {
  const c = rnSanc(server)
  for (var rnRt = 0; rnRt < RN_ROLL_TRIES; rnRt++) {
    var ang = Math.random() * Math.PI * 2
    var dist = RN_MIN_R + Math.random() * (RN_MAX_R - RN_MIN_R)
    var nx = Math.floor(c.x + Math.cos(ang) * dist)
    var nz = Math.floor(c.z + Math.sin(ang) * dist)
    if (lsNearSpawn(server, nx, nz, RN_SPAWN_KEEP)) continue
    var ok = true
    for (var rnRj = 0; rnRj < RN_MAX && ok; rnRj++) {
      if (rnState(server, rnRj) === 0) continue
      var ddx = nx - rnX(server, rnRj), ddz = nz - rnZ(server, rnRj)
      if (ddx * ddx + ddz * ddz < RN_GAP * RN_GAP) ok = false
    }
    if (ok) return { x: nx, z: nz, d: Math.round(dist) }
  }
  return null
}

// ── 예고 ──
//
// `adopt` = 장부에 **이미 있는** 노드에 실물만 붙인다.
//
// ── 왜 필요한가 ──
// 옛 `/riftnode add` 로 들어간 «실물 없는 노드»를 고치는 길이다. `remove` 후 `spawn` 으로도
// 되지만, `remove` 는 **별의 파편 2~3개와 금고를 뱉는다.** 그걸 정리용으로 쓰면
// 첫 공세를 치르기도 전에 유물을 열 수 있는 파편이 굴러다니게 된다 —
// 「첫 공세 → 파편 → 제단」 연쇄가 그 자리에서 깨진다. 오물을 치우려고 오물을 만드는 셈이다.
function rnSpawn(src, server, wanted, adopt) {
  if (!LS.hasSanctuary(server)) {
    src.sendSystemMessage(Text.of('§c성역이 먼저다 — §e/sanctuary here')); return 0
  }
  const slot = rnFreeSlot(server)
  if (slot < 0) {
    src.sendSystemMessage(Text.of(`§c균열이 이미 ${RN_MAX}곳입니다. §7하나를 부순 뒤에 늘릴 수 있습니다. (/riftnode site)`))
    return 0
  }
  const name = wanted && wanted.length ? wanted : rnPickName(server)
  if (rnSlotOf(server, name) >= 0) {
    src.sendSystemMessage(Text.of(`§c이미 열려 있는 균열입니다: ${name}`)); return 0
  }
  if (adopt) {
    if (!rnInLedger(server, name)) {
      src.sendSystemMessage(Text.of(`§c장부에 없는 이름입니다: ${name} §7— 새로 만들려면 /riftnode spawn`))
      return 0
    }
  } else {
    // 장부에 «먼저» 넣는다 — 예고된 순간부터 어둠은 진짜다(위협 하한이 즉시 오른다).
    // `/riftnode add` 를 그대로 부르는 이유: 관측 알림·위협 하한 재적용이 거기 다 있다.
    // ⚠️ `var` 다. 블록 안의 `const` 는 Rhino 가 두 번째 실행에서
    //    'redeclaration of var added' 로 터뜨린다 (tools/scan_try_decls.py 가 잡았다).
    var added = server.runCommandSilent(`riftnode add ${name}`)
    if (!added) {
      src.sendSystemMessage(Text.of(`§c장부 등록 실패 — 이미 있는 이름인지 확인: ${name} §7(장부에 있는 걸 살리려면 /riftnode adopt)`))
      return 0
    }
  }

  const spot = rnRoll(server)
  if (!spot) {
    // 방금 장부에 넣은 경우에만 되돌린다. adopt 는 원래 있던 것이라 **건드리면 안 된다** —
    // 되돌린답시고 지우면 보상을 뱉으면서 어둠이 공짜로 사라진다.
    if (!adopt) {
      try { server.runCommandSilent(`riftnode remove ${name}`) } catch (e) { lsWarn('ls_rnode:rollback', e) }
    }
    src.sendSystemMessage(Text.of(`§c자리를 못 찾았습니다 (${RN_ROLL_TRIES}회)${adopt ? '' : ' — 장부도 되돌렸습니다'}.`))
    return 0
  }

  rnSetS(server, `rn_${slot}_n`, name)
  rnSetI(server, `rn_${slot}_x`, spot.x)
  rnSetI(server, `rn_${slot}_z`, spot.z)
  rnSetI(server, `rn_${slot}_y`, 0)
  rnSetState(server, slot, 1)

  const c = rnSanc(server)
  const dir = rnBearing(spot.x - c.x, spot.z - c.z)
  server.runCommandSilent('title @a title {"text":"균열 관측","color":"dark_purple","bold":true}')
  server.runCommandSilent(`title @a subtitle {"text":"${name} — 어둠이 자리를 잡았다","color":"light_purple"}`)
  rnPlay(server, 'minecraft:block.end_portal.spawn', 0.7, 0.5)
  rnSay(server, `§5⚠ §d${name}§5 이(가) 세상을 갉아먹기 시작했다. §8(균열 ${rnActive(server)}/${RN_MAX})`)
  rnSay(server, `§7   위치: §e${spot.x}, ~, ${spot.z} §7· 성역에서 §b${spot.d}m §7· 방향 §b${dir}쪽`)
  rnSay(server, '§8   그 좌표로 향하라. 다가가면 균열이 열린다.')
  console.log(`[LS-RNODE] spawn[${slot}] ${name} at ${spot.x},${spot.z} dist=${spot.d}`)
  return 1
}

// ── 균열 건축 ──
// 명령어로 짓는다(`ls_rift.js` buildAltar 와 같은 방식). 구조물 파일로 바꾸려면 여기만 고친다.
function rnBuild(server, ax, ay, az) {
  const cmd = (s) => server.runCommandSilent(s)
  const R = 6
  // 1. 부지 정리 + 바닥
  cmd(`fill ${ax - R} ${ay} ${az - R} ${ax + R} ${ay + 6} ${az + R} minecraft:air`)
  cmd(`fill ${ax - R} ${ay - 1} ${az - R} ${ax + R} ${ay - 1} ${az + R} minecraft:blackstone`)
  cmd(`fill ${ax - 2} ${ay - 1} ${az - 2} ${ax + 2} ${ay - 1} ${az + 2} minecraft:polished_blackstone`)
  // 2. 부서진 외곽 — 벽이 아니라 «기둥 잔해»다. 균열은 지어진 게 아니라 «생긴» 것이다.
  const ring = [[-R, 0], [R, 0], [0, -R], [0, R], [-4, -4], [4, -4], [-4, 4], [4, 4]]
  ring.forEach(rr => {
    cmd(`fill ${ax + rr[0]} ${ay} ${az + rr[1]} ${ax + rr[0]} ${ay + 2} ${az + rr[1]} minecraft:polished_blackstone`)
    cmd(`setblock ${ax + rr[0]} ${ay + 3} ${az + rr[1]} minecraft:soul_lantern`)
  })
  // 3. 중앙 기둥 + 균열핵
  cmd(`fill ${ax} ${ay} ${az} ${ax} ${ay + 2} ${az} minecraft:polished_blackstone`)
  cmd(`setblock ${ax} ${ay + 3} ${az} ${RN_CORE}`)
  // 4. 혼불 — 밤에 멀리서 보이고, 「여기가 근원이다」를 말한다
  cmd(`setblock ${ax + 1} ${ay} ${az} minecraft:soul_fire`)
  cmd(`setblock ${ax - 1} ${ay} ${az} minecraft:soul_fire`)
  cmd(`setblock ${ax} ${ay} ${az + 1} minecraft:soul_fire`)
  cmd(`setblock ${ax} ${ay} ${az - 1} minecraft:soul_fire`)
}

function rnSummonGuards(server, ax, ay, az) {
  RN_GUARDS.forEach(g => {
    for (var rnGi = 0; rnGi < g.n; rnGi++) {
      var oa = Math.random() * Math.PI * 2
      var od = 3 + Math.random() * 4
      var gx = ax + Math.cos(oa) * od
      var gz = az + Math.sin(oa) * od
      try {
        server.runCommandSilent(`summon ${g.id} ${gx.toFixed(1)} ${ay + 1} ${gz.toFixed(1)} {Tags:["ls_rnode_guard"],PersistenceRequired:1b}`)
      } catch (e) { lsWarn('ls_rnode:summon', e) }
    }
  })
}

// ── 출현 ──
function rnRevealIfNear(server) {
  for (var rnVi = 0; rnVi < RN_MAX; rnVi++) {
    if (rnState(server, rnVi) !== 1) continue
    var nx = rnX(server, rnVi), nz = rnZ(server, rnVi)
    var who = null
    server.players.forEach(p => {
      if (who) return
      var dx = p.x - nx, dz = p.z - nz
      if (dx * dx + dz * dz <= RN_APPROACH * RN_APPROACH) who = p
    })
    if (!who) continue
    var ny = lsSurfaceY(server, nx, nz, Math.floor(who.y))
    rnBuild(server, nx, ny, nz)
    rnSummonGuards(server, nx, ny, nz)
    rnSetI(server, `rn_${rnVi}_y`, ny)
    rnSetState(server, rnVi, 2)
    var name = rnName(server, rnVi)
    server.runCommandSilent('title @a title {"text":"균열이 열린다","color":"dark_purple","bold":true}')
    server.runCommandSilent(`title @a subtitle {"text":"${name} — 수호자를 넘어 균열핵을 부숴라","color":"light_purple"}`)
    rnPlay(server, 'minecraft:entity.wither.spawn', 0.8, 0.6)
    rnPlay(server, 'minecraft:ambient.cave', 1, 0.5)
    rnSay(server, `§5✦ §d${name}§5 이(가) 모습을 드러냈다. §7좌표 §e${nx}, ${ny + 3}, ${nz}`)
    rnSay(server, '§8   기둥 꼭대기의 §5균열핵§8 을 부수면 이 어둠은 사라진다. 수호자가 먼저다.')
    console.log(`[LS-RNODE] reveal[${rnVi}] ${name} at ${nx},${ny},${nz} by ${who.username}`)
    return // 한 틱에 하나만 — 여러 개가 동시에 열리면 알림이 서로를 덮는다
  }
}

// ── 균열핵 파괴 ──
// ⚠️ 수호자가 남아 있으면 못 부순다. 안 그러면 굴을 파고 들어가 핵만 캐고 나오는 게
//    최적 전략이 된다 — 그건 「어둠을 몰아냈다」가 아니다.
//    ※ 판정에 실패하면 **부술 수 있는 쪽으로** 흘린다. 못 부수는 노드가 남으면
//      위협도가 15에 눌러앉고 최종장이 영영 안 열린다 — 그게 훨씬 나쁜 고장이다.
BlockEvents.broken(event => {
  const b = event.block
  if (!b || b.id !== RN_CORE) return
  const p = event.player
  if (!p) return
  const server = p.server
  if (!server) return
  var hit = -1
  try {
    var bx = Number(b.x), by = Number(b.y), bz = Number(b.z)
    for (var rnBi = 0; rnBi < RN_MAX && hit < 0; rnBi++) {
      if (rnState(server, rnBi) !== 2) continue
      if (bx === rnX(server, rnBi) && by === rnY(server, rnBi) + 3 && bz === rnZ(server, rnBi)) hit = rnBi
    }
  } catch (e) { lsWarn('ls_rnode:pos', e); return }
  if (hit < 0) return

  const nx = rnX(server, hit), ny = rnY(server, hit), nz = rnZ(server, hit)
  var guards = 0
  try { guards = rnGuardCount(server, nx, ny, nz) }
  catch (e) { lsWarn('ls_rnode:guards', e); guards = 0 }

  if (guards > 0) {
    p.tell(Text.of(`§c균열핵이 꿈쩍도 하지 않는다 §7— 수호자 ${guards}기가 아직 살아 있다.`))
    // 캔슬은 «맨 마지막». KubeJS 는 cancel 을 예외로 구현해서 뒤 코드가 안 돈다.
    event.cancel()
    return
  }

  const name = rnName(server, hit)
  rnClearSlot(server, hit)
  rnSay(server, `§b✦ §f${p.username}§b 이(가) §d${name}§b 의 균열핵을 부쉈다.`)
  rnPlay(server, 'minecraft:entity.wither.death', 0.7, 0.8)
  // 보상·최종장 판정은 전부 여기 있다. 여기서 다시 계산하지 않는다.
  try { server.runCommandSilent(`riftnode remove ${name}`) }
  catch (e) { lsWarn('ls_rnode:remove', e) }
  // 잔해 정리 — 핵이 사라진 자리는 꺼져야 한다.
  // ⚠️ 수호자는 **이 균열 근처만** 지운다. `@e[tag=ls_rnode_guard]` 로 전부 지우면
  //    다른 균열의 수호자까지 사라져서, 거기 핵이 공짜로 열린다.
  try {
    server.runCommandSilent(`fill ${nx - 1} ${ny} ${nz - 1} ${nx + 1} ${ny} ${nz + 1} minecraft:air replace minecraft:soul_fire`)
    server.runCommandSilent(`execute positioned ${nx} ${ny} ${nz} run kill @e[tag=ls_rnode_guard,distance=..${RN_GUARD_RADIUS}]`)
  } catch (e) { lsWarn('ls_rnode:clean', e) }
  console.log(`[LS-RNODE] destroyed[${hit}] ${name} by ${p.username}`)
})

// ── 자동 재등장 ──
//
// ── 왜 필요한가 ──
// 노드를 다 부수면 위협도가 «매일 내려가» 0 으로 수렴한다(`ls_siege.js onNewDay`).
// 그러면 공성이 영원히 시시해지고 진행의 시계가 멈춘다. 어둠을 걷어낸 대가로
// 세상이 조용해지는 건 맞지만, **영원히** 조용해지면 그건 게임의 끝이지 보상이 아니다.
//
// 게임 안의 날짜로 세는 이유: 「몇 틱 뒤」로 재면 접속을 안 한 밤도 같이 흘러서,
// 하루 만에 들어왔는데 균열이 셋 생겨 있는 일이 난다.
// **한 번에 하나씩만** 채운다 — 자리가 둘 비어도 4일에 하나다.
function rnAutoCheck(server) {
  if (rnGetB(server, 'rn_auto_off')) return
  if (!LS.hasSanctuary(server)) return
  // 최종장이 돌고 있으면 손대지 않는다 — 그 며칠은 세계가 이미 결말을 향해 굴러가는 중이다.
  try { if (LS.finale(server) > 0) return } catch (e) { return }
  // 아무도 없을 때 조용히 생기면 「언제 생겼는지 모를 어둠」이 된다. 예고를 들을 사람이 있어야 한다.
  if (server.players.length <= 0) return

  var day = LS.siegeDay(server) | 0
  var from = rnGetI(server, 'rn_next_from')
  if (rnActive(server) >= RN_MAX) { rnSetI(server, 'rn_next_from', 0); return }
  if (from <= 0) { rnSetI(server, 'rn_next_from', day); return }
  if (day - from < RN_RESPAWN_DAYS) return

  rnSetI(server, 'rn_next_from', day)
  rnSay(server, '§8세상이 너무 오래 조용했다...')
  rnSpawn(RN_AUTO_SRC, server, '', false)
  console.log(`[LS-RNODE] auto spawn (빈 자리 ${RN_MAX - rnActive(server)}곳)`)
}

// ── 옛 단일 슬롯 → 슬롯 배열 이관 (2026-08-14) ──
// 예전 구조는 `rn_name`/`rn_x`/`rn_pending`/`rn_live` 한 벌이었다. 슬롯으로 바꾸면서
// 새 코드가 그 키를 안 읽으므로, 진행 중이던 균열이 **조용히 미아가 된다** —
// 세계에는 구조물이 서 있고 장부에도 이름이 있는데 시스템은 모르는 상태.
// 그건 이 파일이 없애려고 만든 「실물과 장부가 갈린다」 그 자체다.
//
// ※ pending/live 가 «둘 다 꺼져» 있으면 이미 `abandon` 으로 포기한 것이다 — 좌표만 남은
//   껍데기라 옮기지 않고 지운다. 옮기면 없는 균열이 되살아난다.
function rnMigrate(server) {
  if (rnGetB(server, 'rn_migrated')) return
  var old = rnGetS(server, 'rn_name')
  var pend = rnGetB(server, 'rn_pending')
  var live = rnGetB(server, 'rn_live')
  if (old && (pend || live)) {
    var i = rnFreeSlot(server)
    if (i >= 0) {
      rnSetS(server, `rn_${i}_n`, old)
      rnSetI(server, `rn_${i}_x`, rnGetI(server, 'rn_x'))
      rnSetI(server, `rn_${i}_y`, rnGetI(server, 'rn_y'))
      rnSetI(server, `rn_${i}_z`, rnGetI(server, 'rn_z'))
      rnSetState(server, i, live ? 2 : 1)
      console.log(`[LS-RNODE] 이관: ${old} → 슬롯 ${i} (${live ? '열림' : '예고됨'})`)
    } else {
      console.log(`[LS-RNODE] ✘ 이관 실패 — 빈 슬롯이 없다: ${old}`)
    }
  } else if (old) {
    console.log(`[LS-RNODE] 이관 건너뜀 — ${old} 은(는) 이미 포기된 상태였다 (장부에는 남아 있음)`)
  }
  rnSetS(server, 'rn_name', '')
  rnSetB(server, 'rn_pending', false)
  rnSetB(server, 'rn_live', false)
  rnSetB(server, 'rn_migrated', true)
}

// ── 틱 ──
let RN_TICK = 0
ServerEvents.tick(event => {
  RN_TICK++
  if (RN_TICK % 20 !== 0) return // 1초마다
  try { rnMigrate(event.server) } catch (e) { lsWarn('ls_rnode:migrate', e) }
  try { rnRevealIfNear(event.server) } catch (e) { lsWarn('ls_rnode:tick', e) }
  if (RN_TICK % 600 !== 0) return // 30초마다 — 날짜 단위 판정이라 자주 볼 이유가 없다
  try { rnAutoCheck(event.server) } catch (e) { lsWarn('ls_rnode:auto', e) }
})

// ── 명령어 ──
//
// ⚠️⚠️ **`/riftnode` 에만 매달면 안 된다.**
// `ls_siege.js` 도 같은 리터럴을 등록한다. Brigadier 의 `CommandNode.addChild` 는 같은
// 이름을 «합치게» 돼 있지만, 실제로는 한쪽이 이겨서 여기 가지들이 통째로 사라졌다
// (2026-08-14, 인게임: 「위치 10: /riftnode <--[HERE]」 — abandon 이라는 가지가 아예 없음).
// 남의 파일 등록 순서에 목숨을 걸 이유가 없다. **독립 이름 `/lsnode` 를 같이 단다** —
// 병합이 되면 둘 다 되고, 안 되면 최소한 `/lsnode` 는 확실히 산다.
//
// 노드를 두 트리에 재사용할 수는 없다(빌드된 객체다). 그래서 만드는 함수를 두 번 부른다.
function rnTree(Commands, Arguments, event, root) {
  return Commands.literal(root)
    // 이름은 GREEDY_STRING 이다 — Arguments.STRING 은 따옴표 없는 한글을 통째로 못 읽는다
    // (`0-9A-Za-z_-.+` 만 받는다). 같은 함정을 `add`·`/lsonboard`·`/닉네임` 에서 이미 밟았다.
    .then(Commands.literal('spawn').requires(s => s.hasPermission(2))
      .executes(ctx => rnSpawn(ctx.source, ctx.source.server, '', false))
      .then(Commands.argument('name', Arguments.GREEDY_STRING.create(event)).executes(ctx =>
        rnSpawn(ctx.source, ctx.source.server, Arguments.STRING.getResult(ctx, 'name'), false))))
    // 장부에만 있는 노드(옛 `/riftnode add`)에 실물을 붙인다. 장부는 안 건드리므로
    // **보상이 안 나가고 위협도도 그대로다.**
    .then(Commands.literal('adopt').requires(s => s.hasPermission(2))
      .then(Commands.argument('name', Arguments.GREEDY_STRING.create(event))
        .suggests((ctx, b) => {
          String(LS.nodeCsv(ctx.source.server) || '').split(',').forEach(x => { if (x) b.suggest(x) })
          return b.buildFuture()
        })
        .executes(ctx =>
          rnSpawn(ctx.source, ctx.source.server, Arguments.STRING.getResult(ctx, 'name'), true))))
    // 자동 재등장 스위치. 세션 중에 「지금은 그만」이 필요할 때가 반드시 온다.
    .then(Commands.literal('auto').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      const off = !rnGetB(s, 'rn_auto_off')
      rnSetB(s, 'rn_auto_off', off)
      rnSetI(s, 'rn_next_from', 0)
      ctx.source.sendSystemMessage(Text.of(off
        ? '§7자동 재등장 §c꺼짐§7 — 이제 spawn 으로만 생깁니다.'
        : `§a자동 재등장 켜짐 §7— 자리가 비면 ${RN_RESPAWN_DAYS}일마다 하나씩 채웁니다 (최대 ${RN_MAX}곳).`))
      return 1
    }))
    // 실물이 어디 있는지. 장부(`/riftnode`)는 «이름만» 보여주므로 이게 따로 필요하다.
    .then(Commands.literal('site').executes(ctx => {
      const s = ctx.source.server
      ctx.source.sendSystemMessage(Text.of(`§5═══ ✦ 균열 §7${rnActive(s)}/${RN_MAX} §5═══`))
      var any = false
      for (var rnLi = 0; rnLi < RN_MAX; rnLi++) {
        var st = rnState(s, rnLi)
        if (st === 0) continue
        any = true
        var nm = rnName(s, rnLi), nx = rnX(s, rnLi), ny = rnY(s, rnLi), nz = rnZ(s, rnLi)
        if (st === 1) {
          ctx.source.sendSystemMessage(Text.of(`§5 ▶ §d${nm} §7— §e${nx}, ~, ${nz} §8미출현(${RN_APPROACH}칸 접근 필요)`))
        } else {
          var g = 0
          try { g = rnGuardCount(s, nx, ny, nz) } catch (e) { lsWarn('ls_rnode:sitecount', e) }
          ctx.source.sendSystemMessage(Text.of(
            `§5 ▶ §d${nm} §7— §e${nx}, ${ny + 3}, ${nz} §7· 수호자 §c${g}§7기 남음`))
        }
      }
      if (!any) ctx.source.sendSystemMessage(Text.of('§7실물로 열린 균열이 없습니다. §8OP: /lsnode spawn'))
      // ⚠️ 삼항을 겹쳐 쓰다 괄호가 어긋나 파일이 통째로 안 읽혔다(2026-08-14). 네 갈래는 if 로 푼다.
      var q = rnGetI(s, 'rn_next_from')
      var d = LS.siegeDay(s) | 0
      var tail
      if (rnGetB(s, 'rn_auto_off')) {
        tail = '§8   자동 재등장 꺼짐 (auto 로 켬)'
      } else if (rnActive(s) >= RN_MAX) {
        tail = '§8   자리가 다 찼습니다 — 하나를 부수면 다시 셉니다.'
      } else if (q > 0) {
        tail = `§8   빈 자리 ${RN_MAX - rnActive(s)}곳 · ${Math.max(0, RN_RESPAWN_DAYS - (d - q))}일 뒤 새 균열`
      } else {
        tail = `§8   자동 재등장 켜짐 (${RN_RESPAWN_DAYS}일마다 하나)`
      }
      ctx.source.sendSystemMessage(Text.of(tail))
      return 1
    }))
    // 실물을 포기하고 상태만 지운다. 구조물이 지형에 어긋나게 박혔을 때의 탈출구다.
    // ※ 장부는 안 건드린다 — 어둠을 «공짜로» 걷어내는 문을 만들면 안 된다.
    .then(Commands.literal('abandon').requires(s => s.hasPermission(2))
      .then(Commands.argument('name', Arguments.GREEDY_STRING.create(event))
        .suggests((ctx, b) => {
          for (var rnSi = 0; rnSi < RN_MAX; rnSi++) {
            if (rnState(ctx.source.server, rnSi) > 0) b.suggest(rnName(ctx.source.server, rnSi))
          }
          return b.buildFuture()
        })
        .executes(ctx => {
          const s = ctx.source.server
          const nm = Arguments.STRING.getResult(ctx, 'name')
          const i = rnSlotOf(s, nm)
          if (i < 0) { ctx.source.sendSystemMessage(Text.of('§c열려 있는 균열이 아닙니다: ' + nm)); return 0 }
          const nx = rnX(s, i), ny = rnY(s, i), nz = rnZ(s, i)
          try { s.runCommandSilent(`execute positioned ${nx} ${ny} ${nz} run kill @e[tag=ls_rnode_guard,distance=..${RN_GUARD_RADIUS}]`) }
          catch (e) { lsWarn('ls_rnode:abandon', e) }
          rnClearSlot(s, i)
          ctx.source.sendSystemMessage(Text.of(
            `§7${nm} 의 실물을 포기했습니다. §8장부에는 그대로 남아 있습니다 (/riftnode remove ${nm} 로 지움)`))
          return 1
        })))
}

ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  event.register(rnTree(Commands, Arguments, event, 'lsnode'))
  event.register(rnTree(Commands, Arguments, event, 'riftnode'))
})

console.log(`[Last Stardust] 균열 노드 로드됨 — 최대 ${RN_MAX}곳 · ${RN_RESPAWN_DAYS}일마다 자동 재등장 · /lsnode`)
