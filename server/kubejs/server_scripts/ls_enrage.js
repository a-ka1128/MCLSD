// Last Stardust — 소프트 격노 (Soft Enrage)
//
// 조사(docs/RESEARCH.md 「2. 전투·보스전」)에서 가져온 원칙:
//   - 하드 격노(시간 초과 즉사) 금지, 소프트 격노(점점 뜨거워지는 바닥) - 가 캐주얼 서버의 정답이다.
//
// 왜 하드 격노를 안 쓰나: 인원이 들쭉날쭉하고 숙련도가 제각각인 친구 서버에서 "8분 안에 못 죽이면
// 전멸"은 그날 밤을 통째로 날린다. 반면 아무 제한이 없으면 40분짜리 소모전이 되어 지루해진다.
// 그래서 - 끝내라고 밀어붙이되 문을 닫지는 않는다 - .
//
// 동작
//   **교전으로 인정된 시간** 8분 후부터 30초마다 그 보스의 공격력 +10% 누적.
//   경고를 같이 띄운다 — 갑자기 아파지면 "왜 죽었는지 모르겠다"가 되고, 그건 실패한 설계다.
//
//   「교전으로 인정된」이 핵심이다. 벽시계로 세면 아무도 안 싸우는 보스가 저절로 +200% 가 되고,
//   나중에 그 앞을 지나간 사람이 영문 모르고 한 방에 죽는다 — 바로 윗줄이 금지한 그 실패다.
//   실제로 2026-07-31 까지 그 상태였다(아래 EN_RANGE 주석).
//
// 대상: ls_bossdiff.js 의 BOSS_LIST 와 같은 보스들. 그 파일이 스폰 시 기본 배율을 잡고,
//       여기서는 - 전투 중 시간에 따른 가산 - 만 얹는다. 모디파이어 ID 가 달라 서로 안 겹친다.
//
// 저장: 없음 (전투 상태는 메모리. 서버 재시작 시 격노도 리셋 = 정상)

const EN_GRACE_TICKS = 8 * 60 * 20    // 8분 유예 — **교전으로 인정된 시간** 기준 (벽시계 아님)
const EN_STEP_TICKS = 30 * 20         // 30초마다
const EN_STEP_PCT = 0.10              // 한 단계 +10%
const EN_MAX_STACK = 20               // 상한 (+200%) — 무한히 오르면 하드 격노와 다를 게 없다
const EN_MOD = 'last_stardust:enrage'

// ── 교전 판정 (2026-07-31 추가) ──
// **이게 없어서 아무도 안 싸우는 보스가 +200% 까지 올라갔다.**
// 위 훑기의 주석은 처음부터 「교전 중인 보스 찾기」라고 적혀 있었지만, 실제로 한 일은
// «오버월드에 살아 있는 보스 찾기» 였다 — 엔티티를 본 순간부터 벽시계가 돌았다.
//
// 07-31 시험 서버 로그가 그대로 증명한다. **접속자 0명**인 채로:
//     06:33 격노 시작 +10%  →  06:42 +200%
// 청크에 남아 있던 시험 소환 보스 다섯이 10분 만에 상한까지 갔다.
//
// 실전에서 이게 무슨 일인가: 트와일라잇 구조물 보스나 파티가 도망친 보스가 로드된 청크에
// 서 있기만 해도 조용히 +200% 가 된다. 나중에 누가 그 앞을 지나가면 **영문 모르고 한 방에
// 죽는다.** 이 파일 머리말이 «갑자기 아파지면 "왜 죽었는지 모르겠다"가 되고, 그건 실패한
// 설계다» 라고 적어둔 바로 그것이다.
//
// 규칙은 자바 쪽 `BossFightTracker` 에서 그대로 가져온다 — 거긴 처음부터 제대로 하고 있었다:
//     근처에 사람이 있고 **그리고** 보스가 누군가를 노리고 있을 때만 시계가 돈다.
// 둘 중 하나만으로는 부족하다. 거리만 보면 자는 보스 옆을 지나가는 것도 교전이 되고,
// 표적만 보면 보스가 멀리서 다른 걸 쫓는 동안에도 시계가 돈다.
const EN_RANGE = 48                   // 교전으로 볼 거리 — 원거리 유물(시리우스·솔라리스) 사거리 + 여유
const EN_IDLE_RESET = 2 * 60 * 20     // 이만큼 교전이 끊기면 «전투가 끝났다»로 보고 되감는다

// uuid -> { eng, idle, stacks, name }
//   eng  = 교전으로 인정된 누적 틱 (벽시계가 아니다)
//   idle = 교전이 끊긴 연속 틱
const EN_ACTIVE = {}

// 근처에 사람이 있고 + 보스가 표적을 갖고 있는가.
// `getTarget()` 을 못 읽는 개체가 있을 수 있어(모드마다 다르다) 그때는 거리만으로 인정한다 —
// 여기서 false 로 떨어뜨리면 격노가 **영영 안 걸리는** 쪽으로 조용히 죽는다.
function enEngaged(server, e) {
  var enNear = false
  try {
    server.players.forEach(p => {
      if (enNear) return
      var ex = p.x - e.x, ey = p.y - e.y, ez = p.z - e.z
      if (ex * ex + ey * ey + ez * ez <= EN_RANGE * EN_RANGE) enNear = true
    })
  } catch (err) { lsWarn('ls_enrage:near', err) }
  if (!enNear) return false

  try {
    if (!e.getTarget) return true
    var enT = e.getTarget()
    return !!enT
  } catch (err) { return true }   // 읽을 수 없는 개체 — 거리만으로 인정
}

// 격노를 걷는다(모디파이어 제거). 장부는 건드리지 않는다.
function enStrip(server, e) {
  try {
    server.runCommandSilent(`attribute ${e.getUuid()} minecraft:generic.attack_damage modifier remove ${EN_MOD}`)
  } catch (err) { /* 안 붙어 있으면 정상 */ }
}

function enIsBoss(id) {
  // ls_bossdiff.js 가 먼저 로드되어 BOSS_SET 을 만들어 둔다(파일명 알파벳 순: bossdiff < enrage).
  return typeof BOSS_SET !== 'undefined' && !!BOSS_SET[id]
}

function enApply(server, e, stacks) {
  const u = e.getUuid()
  // 같은 ID 로 다시 add 하면 중복이 아니라 교체가 되도록 먼저 지운다.
  try { server.runCommandSilent(`attribute ${u} minecraft:generic.attack_damage modifier remove ${EN_MOD}`) } catch (err) { /* 처음엔 없다 */ }
  const amt = (EN_STEP_PCT * stacks).toFixed(3)
  try {
    server.runCommandSilent(`attribute ${u} minecraft:generic.attack_damage modifier add ${EN_MOD} ${amt} add_multiplied_total`)
  } catch (err) { lsWarn('ls_enrage:apply', err) }
}

ServerEvents.tick(event => {
  const server = event.server
  if (!server) return
  // 2초마다면 충분하다. 격노 단위가 30초라 더 자주 볼 이유가 없다.
  if (server.tickCount % 40 !== 0) return

  // 이번 훑기에서 살아 있다고 확인된 보스. 아래 정리 단계가 이걸 쓴다.
  const seen = {}

  // ── 알림은 보스마다가 아니라 «이번 훑기에 한 번» ──
  // 문구가 「보스 공격력 +N%」라는 전역 서술인데 개체 루프 안에서 방송하고 있었다.
  // 2026-07-31 시험 로그에 같은 줄이 한 초에 다섯 번 찍혔다 — 시험 소환한 다섯 기가
  // 나란히 유예를 넘긴 것이다. 실전에서도 공성 마지막 웨이브·T4 처럼 보스가 겹치면 그대로 겹친다.
  // 그래서 루프는 «있었다»만 적고, 방송은 루프가 끝난 뒤 한 번 한다.
  var enBegan = false   // 처음 격노에 든 보스가 있었나
  var enLoud = 0        // 4단계 배수를 새로 넘은 것 중 가장 높은 단계

  // ── 교전 중인 보스 찾기 ──
  // ※ 중첩 블록 안에서는 const/let 을 쓰지 않는다 — Rhino 가 매 실행마다
  //    redeclaration 으로 터진다(tools/scan_try_decls.py 가 검출). var + 고유 접두사.
  server.overworld().getEntities().forEach(e => {
    try {
      if (!e || !e.isAlive || !e.isAlive()) return
      var enId = String(e.type)
      if (!enIsBoss(enId)) return
      var enU = String(e.getUuid())
      seen[enU] = true

      if (!EN_ACTIVE[enU]) EN_ACTIVE[enU] = { eng: 0, idle: 0, stacks: 0, name: enId }
      var enSt = EN_ACTIVE[enU]

      // ── 시계는 교전 중에만 돈다 ──
      if (enEngaged(server, e)) {
        enSt.eng += 40      // 이 훑기 주기(2초)만큼
        enSt.idle = 0
      } else {
        enSt.idle += 40
        // 오래 끊기면 «전투가 끝났다». 되감고 붙인 것도 걷는다 —
        // 장부만 비우면 공격력이 오른 채 그대로 남는다.
        if (enSt.idle >= EN_IDLE_RESET && enSt.eng > 0) {
          enStrip(server, e)
          console.log(`[LS-ENRAGE] reset ${enSt.name} (교전 끊김 ${Math.floor(enSt.idle / 20)}초 · ${enSt.stacks}단계 해제)`)
          enSt.eng = 0
          enSt.stacks = 0
        }
        return
      }

      if (enSt.eng < EN_GRACE_TICKS) return

      var enWant = Math.min(EN_MAX_STACK, 1 + Math.floor((enSt.eng - EN_GRACE_TICKS) / EN_STEP_TICKS))
      if (enWant <= enSt.stacks) return

      enSt.stacks = enWant
      enApply(server, e, enWant)

      if (enWant === 1) enBegan = true
      // 매 단계 알리면 소음이 된다. 4단계(2분)마다만.
      else if (enWant % 4 === 0 && enWant > enLoud) enLoud = enWant
    } catch (err) { lsWarn('ls_enrage:scan', err) }
  })

  // 둘 다 걸릴 수 있다(한 기는 막 들어오고 다른 기는 8단계를 넘김). 그때는 두 줄이 맞다 —
  // 서로 다른 사실이고, 합치면 «시작인지 누적인지» 가 사라진다.
  if (enBegan) {
    try {
      server.runCommandSilent('title @a subtitle {"text":"보스가 격앙되기 시작한다","color":"gold"}')
      server.runCommandSilent('title @a times 5 40 10')
      server.runCommandSilent('title @a title {"text":""}')
      server.tell(Text.of(
        `§6⚠ 격노 §7— 시간이 길어지고 있습니다. 보스 공격력 §c+${Math.round(EN_STEP_PCT * 100)}%§7 (30초마다 누적)`))
    } catch (err) { lsWarn('ls_enrage:began', err) }
  }
  if (enLoud > 0) {
    try {
      server.tell(Text.of(`§c⚠ 격노 누적 §7— 보스 공격력 §c+${Math.round(enLoud * EN_STEP_PCT * 100)}%`))
    } catch (err) { lsWarn('ls_enrage:loud', err) }
  }

  // ── 죽었거나 사라진 보스 정리 ──
  // 위 훑기가 만든 seen 을 쓴다. 예전엔 장부의 보스마다 - 월드 전체를 다시 훑었다 - :
  // 추적 4기면 2초마다 엔티티 전수 조사가 5번(위 1 + 아래 4) 돌았다. 이제 1번이다.
  Object.keys(EN_ACTIVE).forEach(u => { if (!seen[u]) delete EN_ACTIVE[u] })
})

// 격노를 통째로 걷는다. 모디파이어는 엔티티에 붙어 있어서 장부만 비우면
// - 공격력이 오른 채 그대로 남는다 - . 붙인 것을 먼저 떼고 나서 장부를 비운다.
function enClear(server) {
  var enN = 0
  try {
    server.overworld().getEntities().forEach(e => {
      try {
        if (!e || !e.getUuid) return
        if (!EN_ACTIVE[String(e.getUuid())]) return
        server.runCommandSilent(`attribute ${e.getUuid()} minecraft:generic.attack_damage modifier remove ${EN_MOD}`)
        enN++
      } catch (err) { lsWarn('ls_enrage:clear-one', err) }
    })
  } catch (err) { lsWarn('ls_enrage:clear', err) }
  Object.keys(EN_ACTIVE).forEach(u => { delete EN_ACTIVE[u] })
  return enN
}

ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  event.register(Commands.literal('enrage')
    // ── 되돌리는 길 ──
    // 여태 status 뿐이었다. 8분 넘긴 전투에서 파티가 «다시 하자»를 하거나, 어딘가
    // 잘못되어 격노가 폭주하면 - 서버를 재시작하는 것 말고 방법이 없었다 - .
    // DawnCraft 교훈(docs/TODO.md D-3): 상태를 만드는 모든 것에 되돌리는 길을 둔다.
    .then(Commands.literal('clear').requires(s => s.hasPermission(2)).executes(ctx => {
      const n = enClear(ctx.source.server)
      ctx.source.sendSystemMessage(Text.of(`§a격노 해제 §7— 보스 ${n}기에서 공격력 보정을 걷었다.`))
      return 1
    }))
    // ── 시험하는 길 ──
    // 유예가 8분이라, 격노가 제대로 걸리는지 보려면 8분짜리 전투를 해야 했다.
    // 그래서 이 시스템은 만들어진 뒤로 한 번도 눈으로 확인된 적이 없다(SELF-CHECK 4-1).
    // 여기서 단계를 직접 얹으면 30초 안에 확인된다.
    .then(Commands.literal('now').requires(s => s.hasPermission(2))
      .then(Commands.argument('stacks', Arguments.INTEGER.create(event)).executes(ctx => {
        const s = ctx.source.server
        const want = Math.max(1, Math.min(EN_MAX_STACK, Arguments.INTEGER.getResult(ctx, 'stacks')))
        var enHit = 0
        try {
          s.overworld().getEntities().forEach(e => {
            try {
              if (!e || !e.isAlive || !e.isAlive()) return
              if (!enIsBoss(String(e.type))) return
              var enU2 = String(e.getUuid())
              if (!EN_ACTIVE[enU2]) EN_ACTIVE[enU2] = { eng: 0, idle: 0, stacks: 0, name: String(e.type) }
              EN_ACTIVE[enU2].stacks = want
              // 교전 누적도 같이 밀어준다 — 안 하면 다음 틱이 «누적에 맞는 단계»를
              // 다시 계산해 방금 얹은 값을 덮는다.
              EN_ACTIVE[enU2].eng = EN_GRACE_TICKS + (want - 1) * EN_STEP_TICKS
              EN_ACTIVE[enU2].idle = 0
              enApply(s, e, want)
              enHit++
            } catch (err) { lsWarn('ls_enrage:now-one', err) }
          })
        } catch (err) { lsWarn('ls_enrage:now', err) }
        ctx.source.sendSystemMessage(Text.of(
          `§6격노 §e${want}단계§6 강제 §7— 보스 ${enHit}기 · 공격력 §c+${Math.round(want * EN_STEP_PCT * 100)}%`))
        if (enHit === 0) ctx.source.sendSystemMessage(Text.of('§8오버월드에 살아 있는 보스가 없다.'))
        return 1
      })))
    .then(Commands.literal('status').executes(ctx => {
      const keys = Object.keys(EN_ACTIVE)
      if (keys.length === 0) { ctx.source.sendSystemMessage(Text.of('§7추적 중인 보스가 없습니다.')); return 1 }
      ctx.source.sendSystemMessage(Text.of(`§6⚠ 격노 현황 §8— 유예 ${EN_GRACE_TICKS / 1200}분(교전 시간 기준)`))
      keys.forEach(u => {
        var enSt2 = EN_ACTIVE[u]
        // «경과»가 아니라 «교전»이라고 쓴다. 벽시계와 다르다는 걸 이 줄에서 알 수 있어야
        // 「10분째인데 왜 격노가 안 걸리지」를 물어볼 데가 생긴다.
        var enSec = Math.floor(enSt2.eng / 20)
        var enPct2 = Math.round(enSt2.stacks * EN_STEP_PCT * 100)
        var enIdleS = Math.floor(enSt2.idle / 20)
        ctx.source.sendSystemMessage(Text.of(
          `§7  ${enSt2.name} §8· 교전 §f${enSec}초 §8· §c+${enPct2}%`
          + (enIdleS > 0 ? ` §8· §7대기 ${enIdleS}초` : ' §8· §a교전 중')))
      })
      ctx.source.sendSystemMessage(Text.of('§8/enrage now <단계> · clear'))
      return 1
    })))
})

console.log('[Last Stardust] 소프트 격노 로드됨 — ' + (EN_GRACE_TICKS / 1200) + '분 후 30초마다 +'
  + Math.round(EN_STEP_PCT * 100) + '% (상한 +' + Math.round(EN_MAX_STACK * EN_STEP_PCT * 100) + '%)')
