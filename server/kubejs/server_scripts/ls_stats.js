// Last Stardust — 통계 + 폐막 시상식 (Stats & Closing Ceremony)
// 악놀2 라스트 콘서트·수니문 섭종 콘서트의 교훈: "끝맺음 의식"이 서버를 레전드로 기억하게 한다.
// 상시 추적: 킬/사망/공성 처치/보스 처치 (개인별).
// ※ 마을 레벨·기여도는 lsrelics 모드(LSData)가 소유한다 — 여기서는 LS 바인딩으로 읽기만 한다.
// 피날레 승리(ls_finale=100) 15초 후 자동 폐막식 — 3초 간격 시상 연출 + 성역 불꽃놀이. /ceremony(OP)로 수동 개막도.
// 저장: st_k_/st_d_/st_sg_/st_b_<user> · st_names(CSV) · cer_* (연출 상태)

function stStore(server) { return server.overworld().persistentData }
function stSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function stPlay(server, s, v, p) { server.runCommandSilent(`execute as @a at @s run playsound ${s} master @s ~ ~ ~ ${v} ${p}`) }
function stAdd(server, cat, name, n) {
  const p = stStore(server)
  p.putInt('st_' + cat + '_' + name, p.getInt('st_' + cat + '_' + name) + n)
  const csv = String(p.getString('st_names') || '')
  const names = csv ? csv.split(',') : []
  if (names.indexOf(name) < 0) { names.push(name); p.putString('st_names', names.join(',')) }
}
function stNames(server) { const s = String(stStore(server).getString('st_names') || ''); return s ? s.split(',') : [] }
function stGet(server, cat, name) { return stStore(server).getInt('st_' + cat + '_' + name) }
function stTop(server, cat) {
  let best = null, bv = -1
  stNames(server).forEach(n => { const v = stGet(server, cat, n); if (v > bv) { bv = v; best = n } })
  return best ? { name: best, v: bv } : null
}
// 기여도 1위. 장부의 주인은 모드(TownData)다 — LS 바인딩으로 읽는다.
//
// ※ 2026-07-30 수정: 예전에는 persistentData 의 town_c_<name> 을 읽었는데, 그 키를 채우던
//   ls_town.js 가 모드로 이관되며 .disabled 됐다(ARCHITECTURE.md). 쓰는 쪽만 사라지고 읽는 쪽이
//   남아서 전원 0점으로 읽혔고, bv 가 -1 에서 시작하니 - CSV 첫 사람이 항상 1위 - 였다.
//   조용히 틀린 값을 내보내는 종류의 고장이라 아무도 눈치채지 못했다.
function stTopContrib(server) {
  try {
    const name = String(LS.topContributorName(server) || '')
    if (!name) return null
    return { name: name, v: LS.topContributorPoints(server) }
  } catch (err) { lsWarn('ls_stats:topContrib', err); return null }
}

// ── 상시 추적 ──
EntityEvents.death(event => {
  const e = event.entity
  if (!e) return
  const server = e.server
  if (!server) return
  const type = String(e.type)
  // 플레이어 사망
  if (type === 'minecraft:player') {
    try { stAdd(server, 'd', String(e.username), 1) } catch (err) { lsWarn('ls_stats:42', err) }
    return
  }
  // 플레이어 킬
  let killer = null
  // `src` 는 ls_bounty.js 와 겹쳐 매번 터졌다 → killer 가 null → **킬 통계가 하나도 안 쌓였다.**
  // 고유 접두사 규칙을 블록 안 지역 변수에도 지킨다.
  try { var stSrc = event.source; if (stSrc && stSrc.player) killer = stSrc.player } catch (err) { lsWarn('ls_stats:kill-src', err) }
  if (!killer) return
  const name = String(killer.username)
  stAdd(server, 'k', name, 1)
  if (e.tags && (`${e.tags}`).includes('ls_siege')) stAdd(server, 'sg', name, 1)
  try { if (typeof BOSS_SET !== 'undefined' && BOSS_SET[type]) stAdd(server, 'b', name, 1) } catch (err) { lsWarn('ls_stats:52', err) }
})

// ── 폐막식 연출 (3초 간격 단계 진행) ──
// cer_step: 0=비활성 · 1.. 진행 단계. cer_wait: 남은 초.
function cerFirework(server, big) {
  // 성역 좌표는 모드(LSData)가 유일 소유 — 사본을 두지 않는다 (ls_siege.js 주석 참고)
  if (!LS.hasSanctuary(server)) return
  const x = LS.sanctuaryX(server), y = LS.sanctuaryY(server), z = LS.sanctuaryZ(server)
  const n = big ? 6 : 2
  for (let i = 0; i < n; i++) {
    var dx = Math.floor(Math.random() * 21) - 10, dz = Math.floor(Math.random() * 21) - 10
    server.runCommandSilent(`summon minecraft:firework_rocket ${x + dx} ${y + 2} ${z + dz} {LifeTime:${20 + Math.floor(Math.random() * 20)}}`)
  }
}
function cerStart(server) {
  if (stStore(server).getInt('cer_step') > 0) return
  stStore(server).putInt('cer_step', 1)
  stStore(server).putInt('cer_wait', 0)
  console.log('[LS-CEREMONY] start')
}
function cerLine(server, step) {
  const K = stTop(server, 'k'), D = stTop(server, 'd'), SG = stTop(server, 'sg'), B = stTop(server, 'b'), C = stTopContrib(server)
  const pop = stStore(server).getInt('town_pop')
  switch (step) {
    case 1:
      server.runCommandSilent('title @a title {"text":"Last Stardust","color":"gold","bold":true}')
      server.runCommandSilent('title @a subtitle {"text":"— 폐막식 —","color":"yellow"}')
      stPlay(server, 'minecraft:ui.toast.challenge_complete', 1, 1)
      stSay(server, '§6═══════ ✦ Last Stardust 폐막식 ✦ ═══════')
      stSay(server, '§7별이 지던 세상에서, 너희는 새벽을 되찾았다.')
      return true
    case 2:
      stSay(server, `§c⚔ 학살자: §f${K ? K.name + ' §7— ' + K.v + '킬' : '§8기록 없음'}`)
      return true
    case 3:
      stSay(server, `§4☠ 불사조(최다 사망): §f${D ? D.name + ' §7— ' + D.v + '번 쓰러지고도 일어남' : '§8기록 없음'}`)
      return true
    case 4:
      stSay(server, `§5▦ 성벽의 수호자(공성 처치): §f${SG ? SG.name + ' §7— ' + SG.v + '킬' : '§8기록 없음'}`)
      return true
    case 5:
      stSay(server, `§6♛ 보스 사냥꾼: §f${B ? B.name + ' §7— ' + B.v + '보스' : '§8기록 없음'}`)
      return true
    case 6:
      stSay(server, `§b❖ 재건의 주역(기여도): §f${C ? C.name + ' §7— ' + C.v + '점' : '§8기록 없음'}`)
      return true
    case 7:
      stSay(server, `§a⌂ 되찾은 것들: §7주민 §f${pop}명§7 · 마을 발전 §f${['ramparts', 'workshop', 'sanctum', 'districts'].map(t => LS.townLevel(server, t)).reduce((a, b) => a + b, 0)}레벨§7 · 원정 §f${LS.progress(server)}/4 봉인`)
      return true
    case 8:
      server.runCommandSilent('title @a title {"text":"별빛이 돌아왔다","color":"aqua","bold":true}')
      server.runCommandSilent('title @a subtitle {"text":"함께해줘서 고마워 — Last Stardust","color":"gray"}')
      stSay(server, '§6═══════ ✦ 세상은 너희를 기억한다 ✦ ═══════')
      stPlay(server, 'minecraft:block.beacon.activate', 1, 1.2)
      return true
    default:
      return false
  }
}
let ST_TICK = 0
ServerEvents.tick(event => {
  ST_TICK++
  if (ST_TICK % 20 !== 0) return // 1초마다
  const server = event.server
  const p = stStore(server)
  // 피날레 승리 → 15초 후 자동 폐막식 (1회)
  if (p.getInt('ls_finale') === 100 && !p.getBoolean('cer_auto_done')) {
    var cd = p.getInt('cer_auto_cd')
    if (cd === 0) p.putInt('cer_auto_cd', 15)
    else if (cd === 1) { p.putBoolean('cer_auto_done', true); p.putInt('cer_auto_cd', 0); cerStart(server) }
    else p.putInt('cer_auto_cd', cd - 1)
  }
  // 폐막식 진행
  const step = p.getInt('cer_step')
  if (step > 0) {
    var wait = p.getInt('cer_wait')
    if (wait > 0) { p.putInt('cer_wait', wait - 1); return }
    var more = cerLine(server, step)
    cerFirework(server, step === 1 || step === 8)
    if (more) { p.putInt('cer_step', step + 1); p.putInt('cer_wait', 3) }
    else { p.putInt('cer_step', 0); console.log('[LS-CEREMONY] done') }
  }
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands } = event
  event.register(Commands.literal('stats')
    .executes(ctx => {
      const s = ctx.source.server
      const names = stNames(s)
      ctx.source.sendSystemMessage(Text.of('§6═══ ▤ 원정대 기록 ═══'))
      if (!names.length) { ctx.source.sendSystemMessage(Text.of('§8아직 기록이 없습니다.')); return 1 }
      names.forEach(n => {
        ctx.source.sendSystemMessage(Text.of(`§f${n} §7— 킬 §e${stGet(s, 'k', n)}§7 · 사망 §c${stGet(s, 'd', n)}§7 · 공성 §5${stGet(s, 'sg', n)}§7 · 보스 §6${stGet(s, 'b', n)}`))
      })
      return 1
    }))
  event.register(Commands.literal('ceremony').requires(s => s.hasPermission(2))
    .executes(ctx => { cerStart(ctx.source.server); return 1 }))
})

console.log('[Last Stardust] 통계+폐막식 로드됨 — 킬/사망/공성/보스 추적, 자동 시상')
