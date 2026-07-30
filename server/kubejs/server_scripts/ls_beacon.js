// Last Stardust — 정화 봉화 (Purifier Beacon)  [영토 수복 가시화]
// 실제 신호기(beacon) 블록 위에서 등록하면 그 일대가 "정화된 땅"이 된다:
//   · 반경 내 어둠의 잡몹이 주기적으로 소멸(연기와 함께)
//   · 봉화 2기당 위협도 하한 -1 (ls_siege.js threatFloor가 pb_names를 읽음)
// 수복이 지도 위에 보인다 — 세상을 되찾는 감각. SRP의 양방향 위협 미터 + Winter Rescue의 온기 반경.
// 저장: pb_names(CSV) · pb_<name>_x/y/z

function pbStore(server) { return server.overworld().persistentData }
function pbSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function pbPlay(server, s, v, p) { server.runCommandSilent(`execute as @a at @s run playsound ${s} master @s ~ ~ ~ ${v} ${p}`) }
function pbNames(server) { const s = String(pbStore(server).getString('pb_names') || ''); return s ? s.split(',') : [] }

const PB_COST = 150       // 등록 비용 (공동 금고 Ducat)
const PB_RADIUS = 24      // 정화 반경

// ── 봉화끼리 최소 거리 ──
// 봉화 2기당 위협도 하한이 1 내려간다(`ls_siege.js threatFloor`). 그 설계 의도는
// **영토 수복이 세상을 진정시킨다** 인데, 여태 위치 조건이 하나도 없었다 —
// 성역 앞마당에 여덟 기를 나란히 세워도 위협 하한이 4 내려갔다. 그건 수복이 아니라
// 금고를 위협도로 바꾸는 환전이다.
//
// 반경(24)의 4배로 잡았다. 정화 범위가 서로 닿지 않을 만큼은 떨어지되, 하루치 원정으로
// 닿는 거리여야 한다 — 너무 멀면 봉화가 「가능하지만 아무도 안 하는」 시스템이 된다.
const PB_MIN_DIST = 96
// ── 정화 대상은 태그 하나가 유일한 출처다 ──
//   kubejs/data/last_stardust/tags/entity_type/purge.json  (#last_stardust:purge)
// 흔한 어둠의 잡몹만 넣는다. 공성 몹은 - 안 넣는다 - — 공성은 정면으로 막아야 한다.
//
// ※ 2026-07-31: 여기 같은 목록을 담은 PB_PURGE 배열이 «문서용 원본»으로 남아 있었다.
//   쓰는 곳은 없고 태그와 따로 관리해야 하는 상태 = TODO D-2 가 지적한 드리프트 위험 그대로였다.
//   목록이 두 벌이면 언젠가 갈린다. 지웠다 — 대상을 바꾸려면 위 태그 파일만 고친다.

// ── 점화 물결 ──
// RESEARCH 4 가 ★★ 로 꼽은 자리. 봉화를 켜는 순간이 여태 «타이틀 한 줄 + 소리 하나»였다.
// 150 Ducat 을 모아 세운 것치고는 아무 일도 안 일어나는 것처럼 보였고, 무엇보다
// - 반경 24m 가 어디까지인지 - 를 볼 방법이 없어서 «내 집이 안에 들어가나»를 알 수 없었다.
//
// 그래서 빛이 중심에서 바깥으로 퍼져나가게 한다. 세 가지가 같이 간다:
//   ① 고리가 실제 반경까지 자라며 멈춘다  → 정화 범위가 눈으로 그어진다
//   ② 그 고리가 지나간 자리의 잡몹이 죽는다 → 연출이 아니라 - 실제로 일어나는 일 - 이다
//   ③ 소리가 단계마다 반음씩 오른다        → 물결이 어디쯤인지 소리로도 안다
//
// ②가 핵심이다. 파티클만 뿌리면 그건 장식이고, 장식은 두 번째부터 안 본다.
// 여기서는 물결이 - 도착해야 - 그 자리가 정화되므로, 보고 있으면 무슨 일이 일어나는지 알게 된다.
//
// ※ 블록 치환은 안 한다. TODO 의 원안은 «반경별 딜레이 블록 치환»이었는데,
//   지울 오염 블록이 아직 없고(균열 침식은 미구현) 지형을 실제로 바꾸면 남의 건축을
//   부술 수 있다. 되돌릴 수 없는 연출은 연출이 아니다.
const PB_WAVE_STEPS = 8       // 고리 개수 (반경을 이만큼으로 나눈다)
const PB_WAVE_GAP = 5         // 고리 사이 간격(틱) — 8단계 × 5 = 2초
const PB_WAVE_POINTS = 28     // 고리 하나를 이루는 점 개수

// 고리 하나. 명령 하나로 원을 그릴 방법이 없어서 점을 돌려 찍는다.
// 점 개수를 반경에 비례시키지 않는 이유: 바깥 고리일수록 성기게 보이는 게
// «퍼져나간다»는 인상에 오히려 맞고, 명령 수도 단계마다 일정해진다.
function pbWaveRing(server, x, y, z, r, step) {
  for (var pwI = 0; pwI < PB_WAVE_POINTS; pwI++) {
    var pwA = (pwI / PB_WAVE_POINTS) * Math.PI * 2
    var pwX = x + Math.cos(pwA) * r
    var pwZ = z + Math.sin(pwA) * r
    server.runCommandSilent(`particle minecraft:end_rod ${pwX} ${y + 1} ${pwZ} 0.05 0.6 0.05 0.01 2 force`)
  }
  // 물결이 지나간 껍질만 정화한다. 안쪽은 이미 앞 단계가 지웠다.
  var pwIn = Math.max(0, r - (PB_RADIUS / PB_WAVE_STEPS))
  server.runCommandSilent(
    `execute positioned ${x} ${y} ${z} run kill @e[distance=${pwIn.toFixed(1)}..${r.toFixed(1)},`
    + 'type=#last_stardust:purge,tag=!ls_siege,tag=!ls_captor,tag=!ls_rift_boss]')
  // 반음씩 올라간다. 마지막 고리가 가장 높은 음이라 «다 퍼졌다»가 소리로도 닫힌다.
  pbPlay(server, 'minecraft:block.amethyst_block.chime', 0.7, 0.8 + step * 0.12)
}

// 점화 순간의 물결. 스케줄을 8개 걸어 두고 각자 자기 고리를 그린다.
// ※ 콜백마다 try 를 따로 두는 이유: 하나가 실패해도 나머지 고리는 계속 퍼진다.
//   중간에 끊긴 물결은 «봉화가 고장났다»로 읽히는데, 실제로는 정화가 멀쩡히 도는 경우가 있다.
function pbIgniteWave(server, x, y, z) {
  for (var pwS = 1; pwS <= PB_WAVE_STEPS; pwS++) {
    (function (step) {
      server.scheduleInTicks(step * PB_WAVE_GAP, () => {
        try {
          pbWaveRing(server, x, y, z, (PB_RADIUS / PB_WAVE_STEPS) * step, step)
        } catch (err) { lsWarn('ls_beacon:wave', err) }
      })
    })(pwS)
  }
}

// ── 등록: 신호기 블록 근처에서 ──
function pbRegister(server, player, name) {
  const names = pbNames(server)
  if (names.indexOf(name) >= 0) { player.tell(Text.of('§c이미 있는 봉화 이름')); return 0 }
  // 발밑 3×3×3 안에 beacon 블록 확인
  const px = Math.floor(player.x), py = Math.floor(player.y), pz = Math.floor(player.z)
  let found = false
  try {
    // 고유 접두사 — `dx`/`dy`/`dz` 그대로 두면 ls_siege·ls_towneffect 와 겹친다 (Rhino 재선언 함정)
    for (var bkDx = -1; bkDx <= 1 && !found; bkDx++) for (var bkDy = -2; bkDy <= 1 && !found; bkDy++) for (var bkDz = -1; bkDz <= 1 && !found; bkDz++) {
      var b = player.level.getBlock(px + bkDx, py + bkDy, pz + bkDz)
      if (b && b.id === 'minecraft:beacon') found = true
    }
  } catch (err) { lsWarn('ls_beacon:34', err) }
  if (!found) { player.tell(Text.of('§c근처에 신호기(beacon)가 없습니다. §7신호기를 먼저 세우고 그 위에서 등록하세요.')); return 0 }

  // ── 이미 밝힌 땅 옆에는 못 세운다 ──
  // 비용 검사보다 **먼저** 본다. 뒤에 두면 금고를 깎고 나서 거절하게 된다.
  // ※ 블록 안에서는 var — Rhino 재선언 함정 (docs/TODO.md 함정 #1)
  var pbNear = '', pbNearD = 0
  try {
    names.forEach(other => {
      if (pbNear) return
      var ox = pbStore(server).getInt('pb_' + other + '_x')
      var oz = pbStore(server).getInt('pb_' + other + '_z')
      var d = Math.sqrt((px - ox) * (px - ox) + (pz - oz) * (pz - oz))
      if (d < PB_MIN_DIST) { pbNear = other; pbNearD = Math.round(d) }
    })
  } catch (err) { lsWarn('ls_beacon:dist', err) }
  if (pbNear) {
    player.tell(Text.of(`§c너무 가깝습니다 §7— §f${pbNear}§7 봉화에서 ${pbNearD}m (최소 ${PB_MIN_DIST}m)`))
    player.tell(Text.of('§8   봉화는 이미 밝힌 땅이 아니라 §7아직 어두운 곳§8에 세운다.'))
    return 0
  }

  const tre = LS.treasury(server)
  if (tre < PB_COST) { player.tell(Text.of(`§c등록 비용 부족: 공동 금고 ${tre}/${PB_COST} Ducat`)); return 0 }
  LS.spendTreasury(server, PB_COST)
  names.push(name)
  pbStore(server).putString('pb_names', names.join(','))
  pbStore(server).putInt('pb_' + name + '_x', px)
  pbStore(server).putInt('pb_' + name + '_y', py)
  pbStore(server).putInt('pb_' + name + '_z', pz)
  server.runCommandSilent('title @a title {"text":"정화 봉화 점화","color":"aqua","bold":true}')
  server.runCommandSilent(`title @a subtitle {"text":"${name} — 이 땅은 되찾았다","color":"gray"}`)
  pbPlay(server, 'minecraft:block.beacon.activate', 1, 1)
  pbIgniteWave(server, px, py, pz)
  pbSay(server, `§b✦ 정화 봉화 점화: §f${name} §7(-${PB_COST} Ducat) — 반경 ${PB_RADIUS}m 정화 · 봉화 ${names.length}기 (2기당 위협 하한 -1)`)
  console.log(`[LS-BEACON] register ${name} at ${px},${py},${pz} total=${names.length}`)
  return 1
}

// ── 주기 정화: 반경 내 잡몹 소멸 ──
let PB_TICK = 0
ServerEvents.tick(event => {
  PB_TICK++
  if (PB_TICK % 200 !== 0) return // 10초마다
  const server = event.server
  const names = pbNames(server)
  if (!names.length) return
  names.forEach(n => {
    const x = pbStore(server).getInt('pb_' + n + '_x')
    const y = pbStore(server).getInt('pb_' + n + '_y')
    const z = pbStore(server).getInt('pb_' + n + '_z')
    server.runCommandSilent(`execute positioned ${x} ${y} ${z} run particle minecraft:end_rod ~ ~1 ~ 6 2 6 0.02 6`)
    // 정화 대상은 엔티티 타입 태그 #last_stardust:purge 로 묶어 명령 1개로 처리한다.
    //   (예전엔 PB_PURGE 9종을 각각 kill 명령으로 돌려서 봉화 1기당 9번 셀렉터 스캔이 돌았다 →
    //    봉화 수 × 9 명령. 태그로 합쳐 봉화 1기당 1번 스캔으로 줄임. 대상 목록은 purge.json 이 원본.)
    // 공성 몹(ls_siege)·간수(ls_captor)·게이트 보스(ls_rift_boss) 제외 — 콘텐츠 몹은 정화가 못 지운다.
    server.runCommandSilent(`execute positioned ${x} ${y} ${z} run kill @e[distance=..${PB_RADIUS},type=#last_stardust:purge,tag=!ls_siege,tag=!ls_captor,tag=!ls_rift_boss]`)
  })
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  event.register(Commands.literal('purifier')
    .executes(ctx => {
      const s = ctx.source.server
      const names = pbNames(s)
      ctx.source.sendSystemMessage(Text.of(`§6═══ ✦ 정화 봉화 §7(${names.length}기 · 2기당 위협 하한 -1) §6═══`))
      if (!names.length) { ctx.source.sendSystemMessage(Text.of(`§8아직 없음 — 신호기를 세우고 그 위에서 /purifier light <이름> (${PB_COST} Ducat)`)); return 1 }
      names.forEach(n => {
        ctx.source.sendSystemMessage(Text.of(`§b✦ ${n} §7— ${pbStore(s).getInt('pb_' + n + '_x')}, ${pbStore(s).getInt('pb_' + n + '_y')}, ${pbStore(s).getInt('pb_' + n + '_z')} (반경 ${PB_RADIUS})`))
      })
      return 1
    })
    .then(Commands.literal('light').then(Commands.argument('name', Arguments.STRING.create(event)).executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      return pbRegister(ctx.source.server, p, Arguments.STRING.getResult(ctx, 'name'))
    })))
    // 점화 물결만 따로 본다. 실전 점화는 150 Ducat 이 들고 되돌릴 수 없어서,
    // «연출이 제대로 보이는가»를 확인하려고 봉화를 세울 수는 없다.
    // 정화 kill 까지 그대로 돈다 — 물결이 실제로 무엇을 하는지가 이 연출의 전부라서다.
    .then(Commands.literal('wave').requires(s => s.hasPermission(2)).executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      pbIgniteWave(ctx.source.server, Math.floor(p.x), Math.floor(p.y), Math.floor(p.z))
      ctx.source.sendSystemMessage(Text.of(
        `§b점화 물결 시험 §7— ${PB_WAVE_STEPS}단계 · ${(PB_WAVE_STEPS * PB_WAVE_GAP / 20).toFixed(1)}초 · 반경 ${PB_RADIUS}m §8(정화도 같이 돈다)`))
      return 1
    }))
    .then(Commands.literal('remove').requires(s => s.hasPermission(2)).then(Commands.argument('name', Arguments.STRING.create(event))
      .suggests((ctx, b) => { pbNames(ctx.source.server).forEach(x => b.suggest(x)); return b.buildFuture() })
      .executes(ctx => {
        const s = ctx.source.server; const name = Arguments.STRING.getResult(ctx, 'name')
        const names = pbNames(s); const i = names.indexOf(name)
        if (i < 0) { ctx.source.sendSystemMessage(Text.of('§c없는 봉화 이름')); return 0 }
        names.splice(i, 1)
        pbStore(s).putString('pb_names', names.join(','))
        pbSay(s, `§7정화 봉화 소등: ${name} (남은 ${names.length}기)`)
        return 1
      }))))
})

console.log('[Last Stardust] 정화 봉화 로드됨 — 신호기 등록형 (반경 정화 + 위협 하한 완화)')
