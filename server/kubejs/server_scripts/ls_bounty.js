// Last Stardust — 현상금 게시판 (Bounty Board)  [일일 콘텐츠 루프]
// 매일 새벽 3건 자동 롤(사냥/납품/정예). 완료 시 공동 금고 + 개인 기여도 적립.
// 할 게 없는 날을 없앤다 — Vault Hunters의 Bounty Table 패턴.
// 저장: bt_day · bt<n> (인코딩 "type|target|need|reward") · bt<n>_have · bt<n>_done
// 기여도는 ls_town.js와 같은 키(town_c_<name>/town_cnames)에 적립 = 하나의 명예 보드.

function btStore(server) { return server.overworld().persistentData }
function btGetI(server, k) { return btStore(server).getInt(k) }
function btSetI(server, k, v) { btStore(server).putInt(k, v) }
function btGetS(server, k) { return String(btStore(server).getString(k) || '') }
function btSetS(server, k, v) { btStore(server).putString(k, v) }
function btSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function btPlay(server, s, v, p) { server.runCommandSilent(`execute as @a at @s run playsound ${s} master @s ~ ~ ~ ${v} ${p}`) }
function btAddContribution(server, name, pts) { // ls_town과 동일 키 사용
  const p = btStore(server)
  LS.addContribution(server, name, pts)   // 기여도는 모드가 소유 (마을 화면의 명예 보드와 같은 장부)
  const csv = String(p.getString('town_cnames') || '')
  const names = csv ? csv.split(',') : []
  if (names.indexOf(name) < 0) { names.push(name); p.putString('town_cnames', names.join(',')) }
}

const BT_COUNT = 3
const BT_EVERY = 3      // 갱신 주기(일) — 하루가 실시간 18분이라 매일 롤이면 완료가 불가능하다
const BT_REWARD_MULT = 1.5  // 보상 배율 (ls_siege.js REWARD_MULT와 동일 기조)
const BT_ESS = 'kubejs:rift_essence'
const BT_ELITE_ESS = 1      // 정예(토벌) 현상금 완료 시 지급하는 균열 정수

// ── 현상금 풀 ──
// 사냥: 흔한 적 다수 처치. 납품: 자원 제출. 정예: 위험 몹 소수 처치(고보상).
const BT_HUNT = [
  { target: 'minecraft:zombie', name: '좀비', need: 25, reward: 60 },
  { target: 'minecraft:skeleton', name: '스켈레톤', need: 20, reward: 60 },
  { target: 'minecraft:spider', name: '거미', need: 15, reward: 50 },
  { target: 'minecraft:creeper', name: '크리퍼', need: 10, reward: 70 },
  { target: 'minecraft:husk', name: '허스크', need: 15, reward: 65 },
  { target: 'minecraft:stray', name: '스트레이', need: 12, reward: 65 },
  { target: 'minecraft:enderman', name: '엔더맨', need: 6, reward: 80 },
  { target: 'minecraft:phantom', name: '팬텀', need: 8, reward: 70 }
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

function btEnc(kind, def) { return kind + '|' + def.target + '|' + def.name + '|' + def.need + '|' + def.reward }
function btDec(s) {
  const p = s.split('|')
  return p.length === 5 ? { kind: p[0], target: p[1], name: p[2], need: parseInt(p[3]), reward: parseInt(p[4]) } : null
}
function btPick(arr) { return arr[Math.floor(Math.random() * arr.length)] }

// ── 매일 롤 ──
function btRoll(server) {
  btSetS(server, 'bt1', btEnc('hunt', btPick(BT_HUNT)))
  btSetS(server, 'bt2', btEnc('supply', btPick(BT_SUPPLY)))
  btSetS(server, 'bt3', btEnc('elite', btPick(BT_ELITE)))
  for (let i = 1; i <= BT_COUNT; i++) { btSetI(server, 'bt' + i + '_have', 0); btSetI(server, 'bt' + i + '_done', 0) }
  const b1 = btDec(btGetS(server, 'bt1')), b2 = btDec(btGetS(server, 'bt2')), b3 = btDec(btGetS(server, 'bt3'))
  btSay(server, `§6✎ 새 현상금 §7(${BT_EVERY}일간 유효) — §f${b1.name} ${b1.need}§7 사냥 · §f${b2.name} ${b2.need}§7 납품 · §c${b3.name} ${b3.need}§7 토벌 §8(/bounty)`)
  btPlay(server, 'minecraft:block.note_block.pling', 0.7, 1.4)
  console.log('[LS-BOUNTY] rolled: ' + btGetS(server, 'bt1') + ' / ' + btGetS(server, 'bt2') + ' / ' + btGetS(server, 'bt3'))
}

function btComplete(server, i, b, playerName) {
  btSetI(server, 'bt' + i + '_done', 1)
  const pay = Math.round(b.reward * BT_REWARD_MULT)
  LS.addTreasury(server, pay)
  btAddContribution(server, playerName, Math.ceil(pay / 10))
  btPlay(server, 'minecraft:entity.player.levelup', 0.8, 1.3)
  btSay(server, `§6✎ 현상금 완료! §f${b.name} §7(${playerName} 마무리) — §e공동 금고 +${pay} §7· 기여도 +${Math.ceil(pay / 10)}`)
  // 정예(토벌) 현상금 = 반복 가능한 정수 공급처.
  // 정수 공급이 관문·노드 같은 유한한 풀에만 묶여 있으면, 열쇠 던전(장비 파밍)을 돌릴수록
  // 마을 재건 재료가 말라붙는다. 3일마다 1건씩 도는 정예 현상금이 그 완충 역할을 한다.
  if (b.kind === 'elite') {
    // 마무리한 사람은 방금 처치/납품을 했으므로 반드시 접속 중 — give로 충분하다
    server.runCommandSilent(`give ${playerName} ${BT_ESS} ${BT_ELITE_ESS}`)
    btSay(server, `§5✦ 균열 정수 +${BT_ELITE_ESS} §7— 정예 토벌의 대가 (${playerName})`)
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
    if (btGetI(server, 'bt' + i + '_done')) continue
    var b = btDec(btGetS(server, 'bt' + i))
    if (!b || b.kind === 'supply' || b.target !== type) continue
    var have = btGetI(server, 'bt' + i + '_have') + 1
    btSetI(server, 'bt' + i + '_have', have)
    if (have >= b.need) btComplete(server, i, b, killer.username ? String(killer.username) : '누군가')
    else if (have % 5 === 0 || b.need - have <= 3) killer.tell(Text.of(`§7✎ ${b.name} ${have}/${b.need}`))
  }
})

// ── 납품: /bounty deliver ──
function btDeliver(server, player) {
  let any = false
  for (let i = 1; i <= BT_COUNT; i++) {
    if (btGetI(server, 'bt' + i + '_done')) continue
    var b = btDec(btGetS(server, 'bt' + i))
    if (!b || b.kind !== 'supply') continue
    var need = b.need - btGetI(server, 'bt' + i + '_have')
    if (need <= 0) continue
    var name = player.username
    var have = 0
    // 개수는 인벤토리를 직접 읽는다 (ls_util.js) — /clear 반환값으로 세는 건 애초에 불가능하다.
    have = lsCountItem(player, b.target)
    if (have <= 0) { player.tell(Text.of(`§7납품할 ${b.name}이(가) 없습니다. (${btGetI(server, 'bt' + i + '_have')}/${b.need})`)); continue }
    var take = lsTakeItem(player, b.target, Math.min(need, have))
    if (take <= 0) continue
    var now = btGetI(server, 'bt' + i + '_have') + take
    btSetI(server, 'bt' + i + '_have', now)
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
  if (cycle !== btGetI(server, 'bt_day')) {
    btSetI(server, 'bt_day', cycle)
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
        var b = btDec(btGetS(s, 'bt' + i))
        if (!b) { ctx.source.sendSystemMessage(Text.of('§8(내일 새벽에 게시됩니다)')); break }
        var done = btGetI(s, 'bt' + i + '_done')
        var have = btGetI(s, 'bt' + i + '_have')
        var st = done ? '§a✔ 완료' : `§e${have}/${b.need}`
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
