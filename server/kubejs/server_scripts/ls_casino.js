// Last Stardust — 도박장 (Casino)  [소셜 미니게임 / 판돈의 밤]
// 악놀2 최고 명장면은 960만 골드 섯다 한 판이었다 — 판돈 걸린 소셜 게임이 하이라이트를 만든다.
// 칩 = 에메랄드 실물 (별도 화폐 없음, /clear로 소모·/give로 지급). 게임 3종:
//   홀짝: 즉석 2배 (/casino oddeven <odd|even> <bet>)
//   주사위 결투: 친구에게 도전, 판돈 걸고 2d6 (/casino dice <상대> <bet> → 상대가 accept)
//   별똥말 경마: 황두런 오마주 — 베팅 30초 → 텍스트 중계 레이스, 적중 4배 (/casino race → bet <1~5> <n>)
// 저장: cs_* (레이스 상태) · 결투는 메모리(서버 재시작 시 초기화 = 무해)

function csStore(server) { return server.overworld().persistentData }
function csSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function csPlay(server, s, v, p) { server.runCommandSilent(`execute as @a at @s run playsound ${s} master @s ~ ~ ~ ${v} ${p}`) }
const CS_CHIP = 'minecraft:emerald'
// 개수는 인벤토리를 직접 읽는다 (ls_util.js) — /clear 반환값으로 세는 건 애초에 불가능하다.
// (예전엔 칩이 0개일 때 undefined 가 돌아와 `csCount(...) < bet` 이 false 가 되어 공짜 베팅이 됐다)
function csCount(server, name) {
  // ※ server.getPlayer(name) 은 UUID 전용이라 이름을 넣으면 예외로 명령이 죽는다 (ls_util.js 참고)
  const p = lsPlayerByName(server, name)
  return p ? lsCountItem(p, CS_CHIP) : 0
}
function csTake(server, name, n) { server.runCommandSilent(`clear ${name} ${CS_CHIP} ${n}`) }
function csGive(server, name, n) { server.runCommandSilent(`give ${name} ${CS_CHIP} ${n}`) }
function csRoll(n) { return 1 + Math.floor(Math.random() * n) }

const CS_MAX_BET = 64 // 1회 베팅 상한 (악놀1 교훈: 상한 없으면 도박 서버가 된다)

// ── 홀짝 ──
function csOddEven(server, player, pick, bet) {
  const name = player.username
  if (bet < 1 || bet > CS_MAX_BET) { player.tell(Text.of(`§c베팅은 1~${CS_MAX_BET} 에메랄드`)); return 0 }
  if (csCount(server, name) < bet) { player.tell(Text.of('§c에메랄드가 부족합니다.')); return 0 }
  csTake(server, name, bet)
  const n = csRoll(6)
  const isOdd = n % 2 === 1
  const win = (pick === 'odd') === isOdd
  csPlay(server, 'minecraft:block.note_block.hat', 0.8, 1.5)
  if (win) {
    csGive(server, name, bet * 2)
    csSay(server, `§a⚀ ${name} 홀짝 §e${n}§a(${isOdd ? '홀' : '짝'}) — 적중! §6+${bet * 2} 에메랄드`)
    csPlay(server, 'minecraft:entity.player.levelup', 0.6, 1.5)
  } else {
    csSay(server, `§7⚀ ${name} 홀짝 §e${n}§7(${isOdd ? '홀' : '짝'}) — 꽝... §c-${bet}`)
  }
  return 1
}

// ── 주사위 결투 (메모리 보관) ──
let CS_DUEL = null // { from, to, bet, expire(gametime) }
function csDuelChallenge(server, player, target, bet) {
  const name = player.username
  if (bet < 1 || bet > CS_MAX_BET) { player.tell(Text.of(`§c베팅은 1~${CS_MAX_BET} 에메랄드`)); return 0 }
  if (target === name) { player.tell(Text.of('§c자신에게는 도전할 수 없습니다.')); return 0 }
  let found = false
  server.players.forEach(p => { if (p.username === target) found = true })
  if (!found) { player.tell(Text.of('§c접속 중인 플레이어가 아닙니다.')); return 0 }
  if (csCount(server, name) < bet) { player.tell(Text.of('§c에메랄드가 부족합니다.')); return 0 }
  CS_DUEL = { from: name, to: target, bet: bet, expire: Date.now() + 60000 } // 60초 (실제 시간)
  csSay(server, `§6⚄ ${name}§7이(가) §6${target}§7에게 §e${bet} 에메랄드§7 주사위 결투 신청! §8(${target}: /casino dice accept · 60초)`)
  csPlay(server, 'minecraft:block.note_block.pling', 0.8, 0.8)
  return 1
}
function csDuelAccept(server, player) {
  const name = player.username
  if (!CS_DUEL || CS_DUEL.to !== name) { player.tell(Text.of('§7수락할 결투가 없습니다.')); return 0 }
  if (Date.now() > CS_DUEL.expire) { CS_DUEL = null; player.tell(Text.of('§7결투 신청이 만료됐습니다.')); return 0 }
  const d = CS_DUEL; CS_DUEL = null
  if (csCount(server, d.from) < d.bet) { csSay(server, `§7결투 무산 — ${d.from}의 에메랄드 부족`); return 0 }
  if (csCount(server, name) < d.bet) { player.tell(Text.of('§c에메랄드가 부족합니다.')); return 0 }
  csTake(server, d.from, d.bet); csTake(server, name, d.bet)
  const r1 = csRoll(6) + csRoll(6), r2 = csRoll(6) + csRoll(6)
  csSay(server, `§6⚄ 주사위 결투! §f${d.from} §e${r1} §7vs §e${r2} §f${name}`)
  csPlay(server, 'minecraft:block.note_block.xylophone', 1, 1)
  if (r1 === r2) {
    csGive(server, d.from, d.bet); csGive(server, name, d.bet)
    csSay(server, '§7무승부 — 판돈 반환')
  } else {
    const winner = r1 > r2 ? d.from : name
    csGive(server, winner, d.bet * 2)
    csSay(server, `§a★ ${winner} 승리! §6+${d.bet * 2} 에메랄드`)
    csPlay(server, 'minecraft:entity.player.levelup', 0.7, 1.3)
  }
  return 1
}

// ── 별똥말 경마 (황두런 오마주) ──
const CS_HORSES = ['새벽별', '북극성', '유성우', '초신성', '혜성']
const CS_GOAL = 30
const CS_PAYOUT = 4
// 상태: cs_phase(0없음/1베팅/2주행) · cs_timer · cs_pos_1..5 · cs_betters(CSV) · cs_bet_<user>="horse|amt"
function csRacePhase(server) { return csStore(server).getInt('cs_phase') }
function csRaceOpen(server, player) {
  if (csRacePhase(server) !== 0) { player.tell(Text.of('§7이미 경마가 진행 중입니다.')); return 0 }
  const st = csStore(server)
  st.putInt('cs_phase', 1)
  st.putInt('cs_timer', 30)
  st.putString('cs_betters', '')
  for (let i = 1; i <= 5; i++) st.putInt('cs_pos_' + i, 0)
  csSay(server, '§6⚘ 별똥말 경마 개장! §e30초§7간 베팅 — §f/casino race bet <1~5> <에메랄드>')
  CS_HORSES.forEach((h, i) => csSay(server, `§8  ${i + 1}번 §f${h}`))
  csSay(server, `§8  적중 시 ${CS_PAYOUT}배 배당`)
  csPlay(server, 'minecraft:entity.horse.ambient', 1, 1)
  return 1
}
function csRaceBet(server, player, horse, bet) {
  if (csRacePhase(server) !== 1) { player.tell(Text.of('§7지금은 베팅 시간이 아닙니다. §8/casino race 로 개장')); return 0 }
  if (horse < 1 || horse > 5) { player.tell(Text.of('§c말 번호는 1~5')); return 0 }
  if (bet < 1 || bet > CS_MAX_BET) { player.tell(Text.of(`§c베팅은 1~${CS_MAX_BET} 에메랄드`)); return 0 }
  const name = player.username
  const st = csStore(server)
  if (String(st.getString('cs_bet_' + name) || '')) { player.tell(Text.of('§c이미 베팅했습니다.')); return 0 }
  if (csCount(server, name) < bet) { player.tell(Text.of('§c에메랄드가 부족합니다.')); return 0 }
  csTake(server, name, bet)
  st.putString('cs_bet_' + name, horse + '|' + bet)
  const csv = String(st.getString('cs_betters') || '')
  st.putString('cs_betters', csv ? csv + ',' + name : name)
  csSay(server, `§7⚘ ${name} → §e${horse}번 ${CS_HORSES[horse - 1]}§7에 ${bet} 에메랄드`)
  return 1
}
function csTrack(server) {
  const st = csStore(server)
  let out = []
  for (let i = 1; i <= 5; i++) {
    var pos = Math.min(CS_GOAL, st.getInt('cs_pos_' + i))
    var done = Math.floor(pos * 10 / CS_GOAL)
    out.push(`§8${i} §f${CS_HORSES[i - 1]} §a${'■'.repeat(done)}§8${'□'.repeat(10 - done)}`)
  }
  return out
}
function csRaceFinish(server, winner) {
  const st = csStore(server)
  csSay(server, `§6⚑ 우승: §e${winner}번 ${CS_HORSES[winner - 1]}§6!`)
  csPlay(server, 'minecraft:ui.toast.challenge_complete', 1, 1.2)
  const csv = String(st.getString('cs_betters') || '')
  const names = csv ? csv.split(',') : []
  names.forEach(n => {
    const b = String(st.getString('cs_bet_' + n) || '').split('|')
    if (b.length === 2) {
      const h = parseInt(b[0]), amt = parseInt(b[1])
      if (h === winner) { csGive(server, n, amt * CS_PAYOUT); csSay(server, `§a  ★ ${n} 적중! +${amt * CS_PAYOUT} 에메랄드`) }
    }
    st.putString('cs_bet_' + n, '')
  })
  st.putString('cs_betters', '')
  st.putInt('cs_phase', 0)
}
let CS_TICK = 0
ServerEvents.tick(event => {
  CS_TICK++
  if (CS_TICK % 20 !== 0) return // 1초마다
  const server = event.server
  const st = csStore(server)
  const phase = st.getInt('cs_phase')
  if (phase === 0) return
  if (phase === 1) {
    const t = st.getInt('cs_timer') - 1
    st.putInt('cs_timer', t)
    if (t === 10) csSay(server, '§7⚘ 베팅 마감 10초 전!')
    if (t <= 0) {
      const csv = String(st.getString('cs_betters') || '')
      if (!csv) { csSay(server, '§7베팅자가 없어 경마가 취소됐습니다.'); st.putInt('cs_phase', 0); return }
      st.putInt('cs_phase', 2)
      csSay(server, '§6⚘ 출발!')
      csPlay(server, 'minecraft:entity.firework_rocket.launch', 1, 1)
    }
    return
  }
  if (phase === 2) {
    // 매초 각 말 전진, 2초마다 중계
    let winner = 0
    for (let i = 1; i <= 5; i++) {
      var pos = st.getInt('cs_pos_' + i) + csRoll(3)
      st.putInt('cs_pos_' + i, pos)
      if (pos >= CS_GOAL && !winner) winner = i
    }
    if (CS_TICK % 40 === 0 || winner) {
      csTrack(server).forEach(line => csSay(server, line))
      csPlay(server, 'minecraft:entity.horse.gallop', 0.6, 1.2)
    }
    if (winner) csRaceFinish(server, winner)
  }
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event

  event.register(Commands.literal('casino')
    .executes(ctx => {
      ctx.source.sendSystemMessage(Text.of('§6═══ ✦ 별빛 도박장 §7(칩: 에메랄드 · 상한 ' + CS_MAX_BET + ') §6═══'))
      ctx.source.sendSystemMessage(Text.of('§f홀짝 §7/casino oddeven <odd|even> <bet> — 2배'))
      ctx.source.sendSystemMessage(Text.of('§f주사위 결투 §7/casino dice <상대> <bet> → 상대: /casino dice accept'))
      ctx.source.sendSystemMessage(Text.of('§f별똥말 경마 §7/casino race → /casino race bet <1~5> <bet> — 4배'))
      return 1
    })
    .then(Commands.literal('oddeven')
      .then(Commands.argument('pick', Arguments.STRING.create(event))
        .suggests((ctx, b) => { b.suggest('odd'); b.suggest('even'); return b.buildFuture() })
        .then(Commands.argument('bet', Arguments.INTEGER.create(event)).executes(ctx => {
          const p = ctx.source.player
          if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
          const pick = Arguments.STRING.getResult(ctx, 'pick')
          if (pick !== 'odd' && pick !== 'even') { ctx.source.sendSystemMessage(Text.of('§codd 또는 even')); return 0 }
          return csOddEven(ctx.source.server, p, pick, Arguments.INTEGER.getResult(ctx, 'bet'))
        }))))
    .then(Commands.literal('dice')
      .then(Commands.literal('accept').executes(ctx => {
        const p = ctx.source.player
        if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
        return csDuelAccept(ctx.source.server, p)
      }))
      .then(Commands.argument('target', Arguments.STRING.create(event))
        .suggests((ctx, b) => { ctx.source.server.players.forEach(pl => b.suggest(pl.username)); return b.buildFuture() })
        .then(Commands.argument('bet', Arguments.INTEGER.create(event)).executes(ctx => {
          const p = ctx.source.player
          if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
          return csDuelChallenge(ctx.source.server, p, Arguments.STRING.getResult(ctx, 'target'), Arguments.INTEGER.getResult(ctx, 'bet'))
        }))))
    .then(Commands.literal('race')
      .executes(ctx => {
        const p = ctx.source.player
        if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
        return csRaceOpen(ctx.source.server, p)
      })
      .then(Commands.literal('bet')
        .then(Commands.argument('horse', Arguments.INTEGER.create(event))
          .then(Commands.argument('bet', Arguments.INTEGER.create(event)).executes(ctx => {
            const p = ctx.source.player
            if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
            return csRaceBet(ctx.source.server, p, Arguments.INTEGER.getResult(ctx, 'horse'), Arguments.INTEGER.getResult(ctx, 'bet'))
          }))))
      .then(Commands.literal('cancel').requires(s => s.hasPermission(2)).executes(ctx => {
        const st = csStore(ctx.source.server)
        // 베팅금 반환
        const csv = String(st.getString('cs_betters') || '')
        const names = csv ? csv.split(',') : []
        names.forEach(n => {
          const b = String(st.getString('cs_bet_' + n) || '').split('|')
          if (b.length === 2) csGive(ctx.source.server, n, parseInt(b[1]))
          st.putString('cs_bet_' + n, '')
        })
        st.putString('cs_betters', ''); st.putInt('cs_phase', 0)
        ctx.source.sendSystemMessage(Text.of('§7경마 취소 (베팅금 반환)')); return 1
      }))))
})

console.log('[Last Stardust] 도박장 로드됨 — 홀짝/주사위 결투/별똥말 경마')
