// Last Stardust — 커스텀 낮밤 주기
// doDaylightCycle을 끄고 매 틱 구간별 속도로 시간을 수동 진행.
// 각 구간의 "실시간 길이(분)"를 직접 지정하고, 배율은 여기서 역산한다.
// (배율을 손으로 맞추면 하루 합계가 딱 안 떨어져서 분 단위로 바꿈)

// ── 구간별 실시간 길이(분) — 조절은 여기만 ──
const MIN_DAY = 5    // ☀️ 낮   — 원정·채굴·건설
const MIN_DUSK = 4   // ☾ 저녁 — 수성 준비
const MIN_NIGHT = 7  // ☽ 밤   — 공성 (고위협 5웨이브를 완전 클리어할 시간 확보)
const MIN_DAWN = 4   // ☼ 새벽 — 복구, 남으면 짧은 모험
// 하루 합계 = 20분 → 공성(3일 주기) = 정확히 60분

// dayTime 구간 길이(틱) — 바닐라 고정값
const T_DAY = 11500, T_DUSK = 2000, T_NIGHT = 8800, T_DAWN = 1700
// 구간 경계 (누적)
const B_DUSK = T_DAY                 // 11500
const B_NIGHT = B_DUSK + T_DUSK      // 13500
const B_DAWN = B_NIGHT + T_NIGHT     // 22300

// 배율 = 구간 틱 ÷ (분 × 60초 × 20틱)
function rateFor(ticks, minutes) { return ticks / (minutes * 1200) }

const RATE_DAY = rateFor(T_DAY, MIN_DAY)      // ≈ 1.597
const RATE_DUSK = rateFor(T_DUSK, MIN_DUSK)   // ≈ 0.417
const RATE_NIGHT = rateFor(T_NIGHT, MIN_NIGHT) // ≈ 1.222
const RATE_DAWN = rateFor(T_DAWN, MIN_DAWN)   // ≈ 0.354

function phaseRate(t) {
  if (t < B_DUSK) return RATE_DAY
  if (t < B_NIGHT) return RATE_DUSK
  if (t < B_DAWN) return RATE_NIGHT
  return RATE_DAWN
}
function phaseName(t) {
  if (t < B_DUSK) return '낮'
  if (t < B_NIGHT) return '저녁'
  if (t < B_DAWN) return '밤'
  return '새벽'
}
function phaseMinutes(t) {
  if (t < B_DUSK) return MIN_DAY
  if (t < B_NIGHT) return MIN_DUSK
  if (t < B_DAWN) return MIN_NIGHT
  return MIN_DAWN
}

let DN_INIT = false
let DN_ACC = 0

ServerEvents.tick(event => {
  const server = event.server
  const p = server.overworld().persistentData
  if (!DN_INIT) {
    DN_INIT = true
    if (!p.getBoolean('ls_cycle_off')) server.runCommandSilent('gamerule doDaylightCycle false')
    console.log(`[LS-DAYNIGHT] init dayTime=${Number(server.overworld().getDayTime()) % 24000}`)
  }
  if (p.getBoolean('ls_cycle_off')) return
  if (p.getBoolean('ls_time_locked')) return // 최종장: 시간 정지 (보스 체력↔시간은 공성 스크립트가 제어)
  const t = Number(server.overworld().getDayTime()) % 24000
  let rate = phaseRate(t)
  const pct = p.getInt('ls_nrate_pct')
  if (pct > 0 && t >= B_NIGHT && t < B_DAWN) rate = rate * pct / 100 // 최종장: 밤 길이 오버라이드 (70%→55%→…)
  DN_ACC += rate
  const add = Math.floor(DN_ACC)
  if (add > 0) { DN_ACC -= add; server.runCommandSilent(`time add ${add}`) }
})

ServerEvents.commandRegistry(event => {
  const { commands: Commands } = event
  event.register(Commands.literal('daynight')
    .then(Commands.literal('info').executes(ctx => {
      const s = ctx.source.server
      const t = Number(s.overworld().getDayTime()) % 24000
      const off = s.overworld().persistentData.getBoolean('ls_cycle_off')
      const total = MIN_DAY + MIN_DUSK + MIN_NIGHT + MIN_DAWN
      ctx.source.sendSystemMessage(Text.of(`§6낮밤: §e${phaseName(t)} §7— 이 구간 §e${phaseMinutes(t)}분 §8(dayTime ${t}, 배율 ${phaseRate(t).toFixed(3)}x)`))
      ctx.source.sendSystemMessage(Text.of(`§7하루 §e${total}분 §8(낮 ${MIN_DAY} · 저녁 ${MIN_DUSK} · 밤 ${MIN_NIGHT} · 새벽 ${MIN_DAWN}) §7· 커스텀 ${off ? '§c꺼짐' : '§a켜짐'}`))
      return 1
    }))
    .then(Commands.literal('on').requires(s => s.hasPermission(2)).executes(ctx => {
      ctx.source.server.overworld().persistentData.putBoolean('ls_cycle_off', false)
      ctx.source.server.runCommandSilent('gamerule doDaylightCycle false')
      ctx.source.sendSystemMessage(Text.of(`§a커스텀 낮밤 주기 켜짐 §7(하루 ${MIN_DAY + MIN_DUSK + MIN_NIGHT + MIN_DAWN}분)`)); return 1
    }))
    .then(Commands.literal('off').requires(s => s.hasPermission(2)).executes(ctx => {
      ctx.source.server.overworld().persistentData.putBoolean('ls_cycle_off', true)
      ctx.source.server.runCommandSilent('gamerule doDaylightCycle true')
      ctx.source.sendSystemMessage(Text.of('§7커스텀 낮밤 주기 꺼짐 (바닐라 복귀)')); return 1
    })))
})

console.log(`[Last Stardust] 커스텀 낮밤 주기 로드됨 — 하루 ${MIN_DAY + MIN_DUSK + MIN_NIGHT + MIN_DAWN}분 (낮 ${MIN_DAY} / 저녁 ${MIN_DUSK} / 밤 ${MIN_NIGHT} / 새벽 ${MIN_DAWN})`)
