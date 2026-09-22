// Last Stardust — 현상금 게시판 (Bounty Board)  [일일 콘텐츠 루프]
// 매일 새벽 3건 자동 롤(사냥/납품/정예). 완료 시 공동 금고 + 개인 기여도 적립.
// 할 게 없는 날을 없앤다 — Vault Hunters의 Bounty Table 패턴.
// 기여도의 주인은 모드다(LS.addContribution → TownData). 마을 화면의 명예 보드가 그 장부다.
//
// ── 저장: 이관 5단계로 모드가 소유한다 (2026-08-06) ──
// 옛 저장은 한 칸이 `'hunt|minecraft:zombie|좀비|25|60'` 이었다. 읽는 쪽은 조각이 다섯인지만 봤고,
// **이름에 `|` 가 하나 들어가면 그 현상금이 조용히 사라졌다** — `btDec` 가 null 을 주고 호출부가
// `continue` 하기 때문이다. 예외도 로그도 없다. 한글 이름은 우리가 계속 늘리는 값이라 시간 문제였다.
// 이제 필드는 각자 자리를 갖는다(`LS.postBounty` / `LS.bountyName` …). 이어붙이는 곳이 없다.
//
// ※ 후보 목록(아래 BT_HUNT·BT_SUPPLY·BT_ELITE)과 보상 배율은 여기 남는다 — `/reload` 로 고치는 값이다.

function btSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function btPlay(server, s, v, p) { server.runCommandSilent(`execute as @a at @s run playsound ${s} master @s ~ ~ ~ ${v} ${p}`) }
// ※ 2026-07-30: persistentData 의 town_cnames 에 이름을 같이 남기던 코드를 지웠다.
//   ls_stats.js 가 그 목록을 읽던 유일한 곳이었는데 이제 모드 장부를 직접 읽는다 — 아무도 안 보는 키였다.
function btAddContribution(server, name, pts) {
  LS.addContribution(server, name, pts)
}

const BT_COUNT = 3
const BT_EVERY = 3      // 갱신 주기(일) — 하루가 실시간 18분이라 매일 롤이면 완료가 불가능하다
const BT_REWARD_MULT = 1.5  // 보상 배율 (ls_siege.js REWARD_MULT와 동일 기조)
const BT_ESS = 'kubejs:rift_essence'
const BT_ELITE_ESS = 1      // 정예(토벌) 현상금 완료 시 지급하는 별의 파편
const BT_ELITE_DUCAT = 25   // 같은 완료에 개인 지갑으로 주는 Ducat

// ── 현상금 풀 ──
// 사냥: 흔한 적 다수 처치. 납품: 자원 제출. 정예: 위험 몹 소수 처치(고보상).
const BT_HUNT = [
  { target: 'minecraft:zombie', name: '좀비', need: 25, reward: 60 },
  { target: 'minecraft:skeleton', name: '스켈레톤', need: 20, reward: 60 },
  { target: 'minecraft:spider', name: '거미', need: 15, reward: 50 },
  // ※ 크리퍼는 뺐다 (2026-08-08 유저 결정). 사냥 현상금은 일반 균열에서 돌게 만들었는데,
  //   크리퍼를 좁은 투기장에 넣으면 자폭이 성역·전리품 상자를 부순다(mobGriefing ON —
  //   공성 웨이브에서 크리퍼를 뺀 것과 같은 이유, ls_siege.js buildWave 주석 참고).
  { target: 'minecraft:husk', name: '허스크', need: 15, reward: 65 },
  { target: 'minecraft:stray', name: '스트레이', need: 12, reward: 65 }
  // ※ 엔더맨·팬텀도 뺐다 (2026-08-08 유저 결정). 사냥 현상금은 일반 균열에서 도는데,
  //   순간이동(엔더맨)과 비행(팬텀)은 투기장 밖으로 새면 웨이브가 안 끝날 수 있다.
  //   「될 수도 있다」에 기대는 것보다 목록에서 빼는 편이 확실하다.
]
const BT_SUPPLY = [
  { target: 'minecraft:iron_ingot', name: '철괴', need: 24, reward: 55 },
  { target: 'minecraft:leather', name: '가죽', need: 20, reward: 50 },
  { target: 'minecraft:bone', name: '뼈', need: 32, reward: 45 },
  { target: 'minecraft:string', name: '실', need: 32, reward: 45 },
  { target: 'minecraft:coal', name: '석탄', need: 48, reward: 50 },
  { target: 'minecraft:gunpowder', name: '화약', need: 16, reward: 60 },
  { target: 'minecraft:copper_ingot', name: '구리괴', need: 32, reward: 45 },
  { target: 'minecraft:bread', name: '빵', need: 24, reward: 40 }
]
const BT_ELITE = [
  { target: 'minecraft:wither_skeleton', name: '위더 스켈레톤', need: 5, reward: 120 },
  { target: 'minecraft:vindicator', name: '변명자', need: 4, reward: 110 },
  { target: 'minecraft:witch', name: '마녀', need: 3, reward: 100 },
  { target: 'minecraft:piglin_brute', name: '피글린 난폭자', need: 3, reward: 130 },
  { target: 'minecraft:ravager', name: '파괴수', need: 1, reward: 150 },
  { target: 'minecraft:evoker', name: '소환사', need: 1, reward: 140 }
]

function btPick(arr) { return arr[Math.floor(Math.random() * arr.length)] }

// 장부에서 한 칸을 통째로 읽어 온다. 게시된 게 없으면 null — 호출부의 `if (!b)` 가 그대로 산다.
// 다리 호출을 감싸는 이유: 이 함수는 킬 이벤트(EntityEvents.death) 안에서 도는데, 거기서
// 예외가 나면 **핸들러가 통째로 죽어 현상금이 다시 안 오른다**(2026-07-25에 실제로 겪은 모양).
function btSlot(server, i) {
  try {
    if (!LS.bountyPosted(server, i)) return null
    return {
      kind: String(LS.bountyKind(server, i)),
      target: String(LS.bountyTarget(server, i)),
      name: String(LS.bountyName(server, i)),
      need: LS.bountyNeed(server, i) | 0,
      reward: LS.bountyReward(server, i) | 0,
      have: LS.bountyHave(server, i) | 0,
      done: !!LS.bountyDone(server, i)
    }
  } catch (e) { lsWarn('ls_bounty:slot', e); return null }
}

// ── 주기마다 롤 ──
function btRoll(server) {
  const picks = [['hunt', btPick(BT_HUNT)], ['supply', btPick(BT_SUPPLY)], ['elite', btPick(BT_ELITE)]]
  try {
    picks.forEach((row, idx) => {
      var d = row[1]
      // 진행도 초기화는 postBounty 안에 들어 있다 — 굴리기와 초기화를 나누면 한쪽만 도는 날이 온다.
      LS.postBounty(server, idx + 1, row[0], d.target, d.name, d.need, d.reward)
    })
  } catch (e) { lsWarn('ls_bounty:roll', e); return }
  const b1 = picks[0][1], b2 = picks[1][1], b3 = picks[2][1]
  btSay(server, `§6✎ 새 현상금 §7(${BT_EVERY}일간 유효) — §f${b1.name} ${b1.need}§7 사냥 · §f${b2.name} ${b2.need}§7 납품 · §c${b3.name} ${b3.need}§7 토벌 §8(/bounty)`)
  btPlay(server, 'minecraft:block.note_block.pling', 0.7, 1.4)
  console.log(`[LS-BOUNTY] rolled: ${b1.target}×${b1.need} / ${b2.target}×${b2.need} / ${b3.target}×${b3.need}`)
}

function btComplete(server, i, b, playerName) {
  // **이미 완료였으면 아무것도 안 한다.** 판정이 모드에 하나뿐이라, 킬과 납품이 같은 틱에
  // 겹쳐도 보상이 두 번 나가지 않는다. 예전엔 done 플래그를 쓰기만 하고 확인은 호출부 몫이었다.
  var fresh = false
  try { fresh = !!LS.completeBounty(server, i) } catch (e) { lsWarn('ls_bounty:complete', e); return }
  if (!fresh) return
  const pay = Math.round(b.reward * BT_REWARD_MULT)
  LS.addTreasury(server, pay)
  btAddContribution(server, playerName, Math.ceil(pay / 10))
  btPlay(server, 'minecraft:entity.player.levelup', 0.8, 1.3)
  btSay(server, `§6✎ 현상금 완료! §f${b.name} §7(${playerName} 마무리) — §e공동 금고 +${pay} §7· 기여도 +${Math.ceil(pay / 10)}`)
  lsAdv(server, '@a', 'bounty_first')   // 도전과제 (ls_util.js) — 보상이 공동 금고라 @a
  // ── 주간 별빛 금고 3칸 (ls_vault.js) ──
  // 여기는 **마무리한 사람만** 센다. 공성과 달리 현상금은 「누가 끝냈나」가 분명하고,
  // 전원에게 주면 한 사람이 다섯 건을 도는 주에 나머지 칸이 공짜로 열린다.
  try { if (typeof vtBounty === 'function') vtBounty(server, String(playerName)) }
  catch (e) { lsWarn('ls_bounty:vault', e) }
  // 정예(토벌) 현상금 = 반복 가능한 정수 공급처.
  // 정수 공급이 관문·노드 같은 유한한 풀에만 묶여 있으면, 열쇠 던전(장비 파밍)을 돌릴수록
  // 마을 재건 재료가 말라붙는다. 3일마다 1건씩 도는 정예 현상금이 그 완충 역할을 한다.
  if (b.kind === 'elite') {
    // 마무리한 사람은 방금 처치/납품을 했으므로 반드시 접속 중 — give로 충분하다
    server.runCommandSilent(`give ${playerName} ${BT_ESS} ${BT_ELITE_ESS}`)
    btSay(server, `§5✦ 별의 파편 +${BT_ELITE_ESS} §7— 정예 토벌의 대가 (${playerName})`)
    // 개인 지갑도 같이. 파편은 «진행»(유물·각성·원정), Ducat 은 «편의»라 겹치지 않는다.
    try { LS.walletAdd(server, String(playerName), BT_ELITE_DUCAT) } catch (e) { lsWarn('ls_bounty:wage', e) }
    btPlay(server, 'minecraft:block.amethyst_block.chime', 0.8, 0.7)
  }
  console.log(`[LS-BOUNTY] #${i} complete by ${playerName}`)
}

// ── 사냥/정예: 킬 추적 ──
EntityEvents.death(event => {
  const e = event.entity
  if (!e) return
  const server = e.server
  if (!server) return
  // 플레이어 킬만 인정
  let killer = null
  // 이름이 `src` 였을 때 Rhino 가 매번 'redeclaration of var src' 로 터졌다 (ls_stats.js 도 같은 이름을
  // try 안에서 썼다). 그러면 killer 가 영원히 null 이라 바로 아래 return 에 걸려 **사냥형 현상금이
  // 전혀 진행되지 않았다.** 고유 접두사 규칙을 블록 안 지역 변수에도 지킨다.
  try { var btSrc = event.source; if (btSrc && btSrc.player) killer = btSrc.player } catch (err) { lsWarn('ls_bounty:kill-src', err) }
  if (!killer) return
  const type = String(e.type)
  // ※ 루프 본문의 선언은 반드시 `var` 다.
  //   Rhino 는 반복마다 새 블록 스코프를 만들지 않아서, `const`/`let` 을 쓰면
  //   **두 번째 순회에서 'redeclaration of var' 로 터진다.** 그러면 EntityEvents.death
  //   핸들러가 통째로 중단되어 현상금이 다시 안 오른다(2026-07-25 실측으로 확인).
  for (var i = 1; i <= BT_COUNT; i++) {
    var b = btSlot(server, i)
    if (!b || b.done || b.kind === 'supply' || b.target !== type) continue
    var have = 0
    try { have = LS.addBountyProgress(server, i, 1) | 0 } catch (e) { lsWarn('ls_bounty:kill-add', e); continue }
    if (have >= b.need) btComplete(server, i, b, killer.username ? String(killer.username) : '누군가')
    else if (have % 5 === 0 || b.need - have <= 3) killer.tell(Text.of(`§7✎ ${b.name} ${have}/${b.need}`))
  }
})

// ── 납품: /bounty deliver ──
function btDeliver(server, player) {
  let any = false
  for (let i = 1; i <= BT_COUNT; i++) {
    var b = btSlot(server, i)
    if (!b || b.done || b.kind !== 'supply') continue
    var need = b.need - b.have
    if (need <= 0) continue
    var name = player.username
    var have = 0
    // 개수는 인벤토리를 직접 읽는다 (ls_util.js) — /clear 반환값으로 세는 건 애초에 불가능하다.
    have = lsCountItem(player, b.target)
    if (have <= 0) { player.tell(Text.of(`§7납품할 ${b.name}이(가) 없습니다. (${b.have}/${b.need})`)); continue }
    var take = lsTakeItem(player, b.target, Math.min(need, have))
    if (take <= 0) continue
    var now = 0
    try { now = LS.addBountyProgress(server, i, take) | 0 } catch (e) { lsWarn('ls_bounty:deliver-add', e); continue }
    any = true
    player.tell(Text.of(`§a+${take} ${b.name} §7납품 (${now}/${b.need})`))
    if (now >= b.need) btComplete(server, i, b, String(name))
  }
  return any ? 1 : 0
}

// ── 일일 리롤 (자체 날짜 추적) ──
let BT_TICK = 0
ServerEvents.tick(event => {
  BT_TICK++
  if (BT_TICK % 20 !== 0) return
  const server = event.server
  const day = Math.floor(Number(server.overworld().getDayTime()) / 24000)
  const cycle = Math.floor(day / BT_EVERY)
  var prev = 0
  try { prev = LS.bountyCycle(server) | 0 } catch (e) { lsWarn('ls_bounty:cycle', e); return }
  if (cycle !== prev) {
    // 주기 번호를 **먼저** 올린다. 굴리기가 중간에 실패해도 매 틱 다시 시도하지 않게 —
    // 실패가 초당 한 번씩 로그를 뒤덮으면 원인을 못 찾는다.
    try { LS.setBountyCycle(server, cycle) } catch (e) { lsWarn('ls_bounty:cycle-set', e); return }
    btRoll(server)
  }
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands } = event
  event.register(Commands.literal('bounty')
    .executes(ctx => {
      const s = ctx.source.server
      ctx.source.sendSystemMessage(Text.of('§6═══ ✎ 오늘의 현상금 ═══'))
      const KIND_TXT = { hunt: '§7사냥', supply: '§b납품', elite: '§c토벌' }
      for (let i = 1; i <= BT_COUNT; i++) {
        var b = btSlot(s, i)
        if (!b) { ctx.source.sendSystemMessage(Text.of('§8(내일 새벽에 게시됩니다)')); break }
        var st = b.done ? '§a✔ 완료' : `§e${b.have}/${b.need}`
        ctx.source.sendSystemMessage(Text.of(`${KIND_TXT[b.kind]} §f${b.name} §7×${b.need} — ${st} §8· 보상 ${b.reward} Ducat`))
      }
      ctx.source.sendSystemMessage(Text.of('§8납품은 아이템 들고 /bounty deliver'))
      return 1
    })
    .then(Commands.literal('deliver').executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      return btDeliver(ctx.source.server, p)
    }))
    .then(Commands.literal('reroll').requires(s => s.hasPermission(2)).executes(ctx => {
      btRoll(ctx.source.server); return 1
    })))
})

console.log('[Last Stardust] 현상금 게시판 로드됨 — 매일 3건 (사냥/납품/토벌)')
