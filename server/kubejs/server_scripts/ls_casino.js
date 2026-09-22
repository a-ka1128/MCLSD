// Last Stardust — 도박장 (Casino)  [소셜 미니게임 / 판돈의 밤]
// 악놀2 최고 명장면은 960만 골드 섯다 한 판이었다 — 판돈 걸린 소셜 게임이 하이라이트를 만든다.
// 칩 = 에메랄드 실물 (별도 화폐 없음, /clear로 소모·/give로 지급). 게임 3종:
//   홀짝: 즉석 2배 (/casino oddeven <odd|even> <bet>)
//   주사위 결투: 친구에게 도전, 판돈 걸고 2d6 (/casino dice <상대> <bet> → 상대가 accept)
//   별똥말 경마: 황두런 오마주 — 베팅 30초 → 텍스트 중계 레이스, 적중 4배 (/casino race → bet <1~5> <n>)
//
// ── 저장: 이관 5단계로 모드가 소유한다 (2026-08-06) ──
// 옛 저장의 `cs_bet_<이름>` 은 경마가 끝날 때 **빈 문자열로 덮였을 뿐 사라지지 않았다.**
// 서버에 다녀간 사람 수만큼 빈 키가 세이브에 영원히 쌓였고, 목록(`cs_betters`)과 따로 살아서
// 둘이 어긋나도 아무도 몰랐다. 봉화의 `pb_<이름>_x/y/z` 와 같은 모양이다.
// 이제 베팅은 Map 하나다 — 지우면 사라진다.
//
// ※ 주사위 결투는 그대로 메모리(`CS_DUEL`)다. 60초짜리 상태라 저장할 이유가 없고,
//   저장하면 「재시작했더니 옛 결투 신청이 살아 있는」 쪽이 오히려 버그다.
// ※ 말 이름·배당·목표 거리는 여기 남는다 — `/reload` 로 고치는 값이다.

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
    var winner = r1 > r2 ? d.from : name
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
// 상태(모드 소유): 단계(0없음/1베팅/2주행) · 타이머 · 말 위치 5칸 · 베팅
const CS_BET_SECONDS = 30
// 다리 호출은 감싼다 — 이 값은 매초 도는 틱 핸들러가 읽는다. 여기서 터지면 경마가 아니라
// **핸들러 전체**가 멈춘다. 0(=경마 없음)으로 내려가면 조용히 아무것도 안 한다.
function csRacePhase(server) {
  try { return LS.racePhase(server) | 0 } catch (e) { lsWarn('ls_casino:phase', e); return 0 }
}
function csRaceOpen(server, player) {
  if (csRacePhase(server) !== 0) { player.tell(Text.of('§7이미 경마가 진행 중입니다.')); return 0 }
  // 단계·타이머·말 위치·베팅이 한 번에 선다. 예전엔 다섯 줄이 나란히 있었고,
  // 하나만 빠지면 **지난 경기 위치에서 출발하는** 경마가 됐다.
  try { LS.openRace(server, CS_BET_SECONDS) } catch (e) { lsWarn('ls_casino:open', e); return 0 }
  csSay(server, `§6⚘ 별똥말 경마 개장! §e${CS_BET_SECONDS}초§7간 베팅 — §f/casino race bet <1~5> <에메랄드>`)
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
  if (csCount(server, name) < bet) { player.tell(Text.of('§c에메랄드가 부족합니다.')); return 0 }
  // **베팅 등록을 먼저, 칩 회수를 나중에.** 중복 판정이 모드에 하나뿐이라 여기가 유일한 관문이고,
  // 반대로 두면 이미 건 사람이 한 번 더 쳤을 때 칩만 사라진다.
  var csOk = false
  try { csOk = !!LS.placeRaceBet(server, name, horse, bet) } catch (e) { lsWarn('ls_casino:bet', e) }
  if (!csOk) { player.tell(Text.of('§c이미 베팅했습니다.')); return 0 }
  csTake(server, name, bet)
  csSay(server, `§7⚘ ${name} → §e${horse}번 ${CS_HORSES[horse - 1]}§7에 ${bet} 에메랄드`)
  return 1
}

// 베팅자 목록. `''.split(',')` 이 길이 1 짜리 배열을 주는 함정 — 빈 문자열을 먼저 거른다.
function csBetters(server) {
  try { var s = String(LS.raceBettersCsv(server) || ''); return s ? s.split(',') : [] }
  catch (e) { lsWarn('ls_casino:betters', e); return [] }
}
function csTrack(server) {
  let out = []
  for (let i = 1; i <= 5; i++) {
    var pos = Math.min(CS_GOAL, LS.horsePos(server, i))
    var done = Math.floor(pos * 10 / CS_GOAL)
    out.push(`§8${i} §f${CS_HORSES[i - 1]} §a${'■'.repeat(done)}§8${'□'.repeat(10 - done)}`)
  }
  return out
}
function csRaceFinish(server, winner) {
  csSay(server, `§6⚑ 우승: §e${winner}번 ${CS_HORSES[winner - 1]}§6!`)
  csPlay(server, 'minecraft:ui.toast.challenge_complete', 1, 1.2)
  // 정산을 **먼저** 끝내고 폐장한다 — closeRace 가 베팅을 비우므로 순서가 뒤집히면
  // 아무에게도 배당이 안 나가고, 그게 «우승 방송은 떴는데 돈은 안 들어온» 상태가 된다.
  csBetters(server).forEach(n => {
    try {
      var h = LS.raceBetHorse(server, n) | 0
      var amt = LS.raceBetAmount(server, n) | 0
      if (h === winner && amt > 0) {
        csGive(server, n, amt * CS_PAYOUT)
        csSay(server, `§a  ★ ${n} 적중! +${amt * CS_PAYOUT} 에메랄드`)
        lsAdv(server, n, 'casino_win')   // 도전과제 — **맞힌 사람 개인의 것**이다
      }
    } catch (e) { lsWarn('ls_casino:payout:' + n, e) }
  })
  try { LS.closeRace(server) } catch (e) { lsWarn('ls_casino:close', e) }
}
let CS_TICK = 0
ServerEvents.tick(event => {
  CS_TICK++
  if (CS_TICK % 20 !== 0) return // 1초마다
  const server = event.server
  const phase = csRacePhase(server)
  if (phase === 0) return
  if (phase === 1) {
    var t = 0
    try { t = (LS.raceTimer(server) | 0) - 1; LS.setRaceTimer(server, t) }
    catch (e) { lsWarn('ls_casino:timer', e); return }
    if (t === 10) csSay(server, '§7⚘ 베팅 마감 10초 전!')
    if (t <= 0) {
      // 베팅자가 없으면 취소 — closeRace 가 단계와 베팅을 같이 되돌린다.
      if (LS.raceBetCount(server) <= 0) {
        csSay(server, '§7베팅자가 없어 경마가 취소됐습니다.')
        try { LS.closeRace(server) } catch (e) { lsWarn('ls_casino:cancel', e) }
        return
      }
      try { LS.setRacePhase(server, 2) } catch (e) { lsWarn('ls_casino:start', e); return }
      csSay(server, '§6⚘ 출발!')
      csPlay(server, 'minecraft:entity.firework_rocket.launch', 1, 1)
    }
    return
  }
  if (phase === 2) {
    // 매초 각 말 전진, 2초마다 중계
    var winner = 0
    for (var i = 1; i <= 5; i++) {
      var pos = 0
      try { pos = LS.advanceHorse(server, i, csRoll(3)) | 0 } catch (e) { lsWarn('ls_casino:advance', e); return }
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
        const s = ctx.source.server
        // 반환을 **먼저** 끝내고 폐장한다 — closeRace 가 베팅을 비운다.
        var refunded = 0
        csBetters(s).forEach(n => {
          try {
            var amt = LS.raceBetAmount(s, n) | 0
            if (amt > 0) { csGive(s, n, amt); refunded += amt }
          } catch (e) { lsWarn('ls_casino:refund:' + n, e) }
        })
        try { LS.closeRace(s) } catch (e) { lsWarn('ls_casino:cancel-cmd', e) }
        ctx.source.sendSystemMessage(Text.of(`§7경마 취소 (베팅금 ${refunded} 반환)`)); return 1
      }))))
})

console.log('[Last Stardust] 도박장 로드됨 — 홀짝/주사위 결투/별똥말 경마')
