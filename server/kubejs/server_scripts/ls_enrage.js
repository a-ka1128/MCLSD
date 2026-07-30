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
//   전투 시작 8분 후부터 30초마다 그 보스의 공격력 +10% 누적.
//   경고를 같이 띄운다 — 갑자기 아파지면 "왜 죽었는지 모르겠다"가 되고, 그건 실패한 설계다.
//
// 대상: ls_bossdiff.js 의 BOSS_LIST 와 같은 보스들. 그 파일이 스폰 시 기본 배율을 잡고,
//       여기서는 - 전투 중 시간에 따른 가산 - 만 얹는다. 모디파이어 ID 가 달라 서로 안 겹친다.
//
// 저장: 없음 (전투 상태는 메모리. 서버 재시작 시 격노도 리셋 = 정상)

const EN_GRACE_TICKS = 8 * 60 * 20    // 8분 유예
const EN_STEP_TICKS = 30 * 20         // 30초마다
const EN_STEP_PCT = 0.10              // 한 단계 +10%
const EN_MAX_STACK = 20               // 상한 (+200%) — 무한히 오르면 하드 격노와 다를 게 없다
const EN_MOD = 'last_stardust:enrage'

// uuid -> { start, stacks, name }
const EN_ACTIVE = {}

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

  const now = server.tickCount

  // ── 교전 중인 보스 찾기 ──
  // ※ 중첩 블록 안에서는 const/let 을 쓰지 않는다 — Rhino 가 매 실행마다
  //    redeclaration 으로 터진다(tools/scan_try_decls.py 가 검출). var + 고유 접두사.
  server.overworld().getEntities().forEach(e => {
    try {
      if (!e || !e.isAlive || !e.isAlive()) return
      var enId = String(e.type)
      if (!enIsBoss(enId)) return
      var enU = String(e.getUuid())

      if (!EN_ACTIVE[enU]) {
        EN_ACTIVE[enU] = { start: now, stacks: 0, name: enId }
        return
      }
      var enSt = EN_ACTIVE[enU]
      var enElapsed = now - enSt.start
      if (enElapsed < EN_GRACE_TICKS) return

      var enWant = Math.min(EN_MAX_STACK, 1 + Math.floor((enElapsed - EN_GRACE_TICKS) / EN_STEP_TICKS))
      if (enWant <= enSt.stacks) return

      enSt.stacks = enWant
      enApply(server, e, enWant)

      var enPct = Math.round(enWant * EN_STEP_PCT * 100)
      if (enWant === 1) {
        server.runCommandSilent('title @a subtitle {"text":"보스가 격앙되기 시작한다","color":"gold"}')
        server.runCommandSilent('title @a times 5 40 10')
        server.runCommandSilent('title @a title {"text":""}')
        server.tell(Text.of(`§6⚠ 격노 §7— 시간이 길어지고 있습니다. 보스 공격력 §c+${enPct}%§7 (30초마다 누적)`))
      } else if (enWant % 4 === 0) {
        // 매 단계 알리면 소음이 된다. 4단계(2분)마다만.
        server.tell(Text.of(`§c⚠ 격노 누적 §7— 보스 공격력 §c+${enPct}%`))
      }
    } catch (err) { lsWarn('ls_enrage:scan', err) }
  })

  // ── 죽었거나 사라진 보스 정리 ──
  Object.keys(EN_ACTIVE).forEach(u => {
    var found = false
    try {
      server.overworld().getEntities().forEach(e => {
        if (e && e.isAlive && e.isAlive() && String(e.getUuid()) === u) found = true
      })
    } catch (err) { lsWarn('ls_enrage:prune', err) }
    if (!found) delete EN_ACTIVE[u]
  })
})

ServerEvents.commandRegistry(event => {
  const { commands: Commands } = event
  event.register(Commands.literal('enrage')
    .then(Commands.literal('status').executes(ctx => {
      const keys = Object.keys(EN_ACTIVE)
      if (keys.length === 0) { ctx.source.sendSystemMessage(Text.of('§7교전 중인 보스가 없습니다.')); return 1 }
      ctx.source.sendSystemMessage(Text.of('§6⚠ 격노 현황'))
      keys.forEach(u => {
        var enSt2 = EN_ACTIVE[u]
        var enSec = Math.floor((ctx.source.server.tickCount - enSt2.start) / 20)
        var enPct2 = Math.round(enSt2.stacks * EN_STEP_PCT * 100)
        ctx.source.sendSystemMessage(Text.of(`§7  ${enSt2.name} §8· §f${enSec}초 §8· §c+${enPct2}%`))
      })
      return 1
    })))
})

console.log('[Last Stardust] 소프트 격노 로드됨 — ' + (EN_GRACE_TICKS / 1200) + '분 후 30초마다 +'
  + Math.round(EN_STEP_PCT * 100) + '% (상한 +' + Math.round(EN_MAX_STACK * EN_STEP_PCT * 100) + '%)')
