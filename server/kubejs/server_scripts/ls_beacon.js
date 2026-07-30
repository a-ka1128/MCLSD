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
// ── 정화 대상은 태그 하나가 유일한 출처다 ──
//   kubejs/data/last_stardust/tags/entity_type/purge.json  (#last_stardust:purge)
// 흔한 어둠의 잡몹만 넣는다. 공성 몹은 - 안 넣는다 - — 공성은 정면으로 막아야 한다.
//
// ※ 2026-07-31: 여기 같은 목록을 담은 PB_PURGE 배열이 «문서용 원본»으로 남아 있었다.
//   쓰는 곳은 없고 태그와 따로 관리해야 하는 상태 = TODO D-2 가 지적한 드리프트 위험 그대로였다.
//   목록이 두 벌이면 언젠가 갈린다. 지웠다 — 대상을 바꾸려면 위 태그 파일만 고친다.

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
