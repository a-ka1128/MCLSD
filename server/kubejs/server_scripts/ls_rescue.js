// Last Stardust — 생존자 구출 (Survivor Rescue)  [마을 인구]
// 폐허 세계에 흩어진 생존자들을 찾아 구출하면 성역에 "실제로" 정착한다(주민 엔티티 이송).
// 구출 흐름: /rescue scout (금고 소모) → 800~1600m 랜덤 좌표에 감금지 예고 → 원정
//   → 접근 시 폐허 캠프 출현(우리 안 생존자 + 어둠의 간수들) → 간수 전멸 = 구출
//   → 생존자가 성역으로 이송·정착, 인구 +1, 매일 인구×5 Ducat 수입.
// 리셋 후: town_npc_<key> 플래그를 읽어 Easy NPC 상인/스토리텔러로 승격(수동 연동).
//
// ── 저장: 이관 5단계로 모드가 소유한다 (2026-08-06) ──
// 옛 저장은 «구출 완료 표식»(`rs_done_<키>`)과 «인구»(`town_pop`)가 **따로 사는 두 값**이었다.
// 올리는 곳이 둘(`rsComplete` · `/rescue grant`)이고 둘 다 표식을 확인하지 않아서, 이미 구출한
// 생존자에게 `/rescue grant` 를 한 번 더 쓰면 **인구만 늘었다** — 그러면 매일 들어오는 금고
// 수입(인구×8)이 영구히 부풀고 아무 오류도 안 난다.
// 이제 인구는 저장되는 값이 아니라 **구출 명부의 크기**다. 두 번 셀 방법이 없다.
//
// ⚠️ `town_pop` 은 `ls_stats.js` 도 읽는다. 쓰는 쪽만 옮겼으면 거기가 조용히 0 을 읽는다 —
//   그쪽도 같이 `LS.population` 으로 옮겼다.
//
// ※ `town_npc_<키>`(리셋 후 Easy NPC 승격 표식)는 persistentData 에 남긴다 — 아무도 안 읽는
//   «다음 월드를 위한 메모»라서 장부에 넣을 이유가 없다.
// ※ 생존자 6명의 명부(이름·직업·간수)는 여기 남는다 — `/reload` 로 고치는 값이다.

function rsStore(server) { return server.overworld().persistentData }
function rsSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function rsPlay(server, s, v, p) { server.runCommandSilent(`execute as @a at @s run playsound ${s} master @s ~ ~ ~ ${v} ${p}`) }
// 성역 좌표는 모드(LSData)가 유일 소유 — 사본을 두지 않는다 (ls_siege.js 주석 참고)
function rsSancSet(server) { return LS.hasSanctuary(server) }
function rsSancPos(server) { return { x: LS.sanctuaryX(server), y: LS.sanctuaryY(server), z: LS.sanctuaryZ(server) } }
function rsSurfaceY(server, x, z, fallback) { return lsSurfaceY(server, x, z, fallback) }   // ls_util.js
function rsDir8(dx, dz) {
  let a = Math.atan2(dx, -dz) * 180 / Math.PI
  if (a < 0) a += 360
  return ['북', '북동', '동', '남동', '남', '남서', '서', '북서'][Math.round(a / 45) % 8]
}

const RS_SCOUT_COST = 30      // 정찰 비용 (공동 금고 Ducat)
const RS_MIN = 800, RS_MAX = 1600  // 감금지 거리 (원정 제단보다 가까움 — 중반 콘텐츠)
const RS_APPROACH = 48        // 접근 시 캠프 출현
const RS_POP_INCOME = 8       // 주민 1인당 매일 금고 수입 (Ducat 유입 상향)

// ── 생존자 명부 (구출 순서대로 등장, 뒤로 갈수록 간수가 강함) ──
const SURVIVORS = [
  { key: 'smith', name: '대장장이 무쇠', job: '무기·방어구 상인', guards: ['minecraft:zombie', 'minecraft:zombie', 'minecraft:skeleton', 'minecraft:zombie'] },
  { key: 'herb', name: '약초사 달래', job: '물약·식량 상인', guards: ['minecraft:zombie', 'minecraft:skeleton', 'minecraft:spider', 'minecraft:husk', 'minecraft:zombie'] },
  { key: 'farmer', name: '농부 이삭', job: '종자·농산물 상인', guards: ['minecraft:husk', 'minecraft:husk', 'minecraft:stray', 'minecraft:skeleton', 'minecraft:zombie'] },
  { key: 'bard', name: '음유시인 별하', job: '선술집 주인', guards: ['minecraft:stray', 'minecraft:wither_skeleton', 'minecraft:husk', 'minecraft:skeleton', 'minecraft:vindicator'] },
  { key: 'archive', name: '사서 먹글', job: '스토리텔러(기록관)', guards: ['minecraft:wither_skeleton', 'minecraft:vindicator', 'minecraft:stray', 'minecraft:husk', 'minecraft:wither_skeleton', 'minecraft:skeleton'] },
  { key: 'watch', name: '파수꾼 바람', job: '성역 경비대장', guards: ['cataclysm:ignited_revenant', 'minecraft:wither_skeleton', 'minecraft:vindicator', 'minecraft:vindicator', 'minecraft:wither_skeleton', 'minecraft:stray'] }
]

// 다리 호출은 감싼다 — 이 셋은 틱 핸들러와 킬 이벤트가 부른다. 여기서 터지면 핸들러가 죽는다.
function rsDone(server, key) {
  try { return !!LS.isRescued(server, key) } catch (e) { lsWarn('ls_rescue:done', e); return false }
}
function rsNextIdx(server) {
  for (let i = 0; i < SURVIVORS.length; i++) { if (!rsDone(server, SURVIVORS[i].key)) return i }
  return -1
}
// 인구 = 구출 명부의 크기. 따로 저장하지 않는다(머리말 참조).
function rsPop(server) {
  try { return LS.population(server) | 0 } catch (e) { lsWarn('ls_rescue:pop', e); return 0 }
}
function rsActive(server) {
  try { return !!LS.rescueActive(server) } catch (e) { lsWarn('ls_rescue:active', e); return false }
}

// ── 정찰: 감금지 좌표 롤 ──
function rsScout(server, player) {
  if (rsActive(server)) { player.tell(Text.of('§7이미 추적 중인 생존자가 있습니다. §e/rescue locate')); return 0 }
  const idx = rsNextIdx(server)
  if (idx < 0) { player.tell(Text.of('§6모든 생존자를 구출했습니다. §7성역에 사람이 돌아왔다.')); return 0 }
  if (!rsSancSet(server)) { player.tell(Text.of('§c성역이 지정되지 않았습니다. OP가 /sanctuary here')); return 0 }
  // `spendTreasury` 하나로 «확인 + 차감»이 끝난다(부족하면 아무것도 안 하고 false).
  var rsPaid = false
  try { rsPaid = !!LS.spendTreasury(server, RS_SCOUT_COST) } catch (e) { lsWarn('ls_rescue:pay', e) }
  if (!rsPaid) { player.tell(Text.of(`§c정찰 비용 부족: 공동 금고 ${LS.treasury(server)}/${RS_SCOUT_COST} Ducat`)); return 0 }
  const c = rsSancPos(server)
  const ang = Math.random() * Math.PI * 2
  const dist = RS_MIN + Math.random() * (RS_MAX - RS_MIN)
  const x = Math.floor(c.x + Math.cos(ang) * dist)
  const z = Math.floor(c.z + Math.sin(ang) * dist)
  // 다섯 값이 한 번에 선다 — 예전엔 여섯 줄이 나란히 있었고, 하나만 빠지면
  // «지난 원정의 좌표로 가는» 추적이 됐다.
  try { LS.beginScout(server, idx, x, z) }
  catch (e) {
    lsWarn('ls_rescue:scout', e)
    try { LS.addTreasury(server, RS_SCOUT_COST) } catch (e2) { lsWarn('ls_rescue:refund', e2) }
    player.tell(Text.of('§c정찰 실패 §7— 로그를 확인하세요. (비용은 되돌렸습니다)'))
    return 0
  }
  const s = SURVIVORS[idx]
  const dir = rsDir8(x - c.x, z - c.z)
  server.runCommandSilent('title @a title {"text":"생존자 신호 감지","color":"aqua","bold":true}')
  server.runCommandSilent(`title @a subtitle {"text":"${s.name}이(가) 어딘가에 갇혀 있다","color":"gray"}`)
  rsPlay(server, 'minecraft:block.bell.use', 1, 1.2)
  rsSay(server, `§b☀ 생존자 추적 개시 §7(정찰 -${RS_SCOUT_COST} Ducat) — §f${s.name} §8(${s.job})`)
  rsSay(server, `§7   신호 위치: §e${x}, ~, ${z} §7· 성역에서 §b${Math.round(dist)}m ${dir}쪽`)
  rsSay(server, `§8   서둘러라 — 다가가면 감금지가 드러난다.`)
  console.log(`[LS-RESCUE] scout idx=${idx} ${s.key} at ${x},${z}`)
  return 1
}

// ── 감금지 캠프 건축 + 간수·생존자 스폰 ──
function rsBuildCamp(server, x, y, z) {
  const cmd = (s) => server.runCommandSilent(s)
  const idx = LS.rescueIdx(server)
  const s = SURVIVORS[idx]
  // 부지 정리 + 폐허 바닥
  cmd(`fill ${x - 4} ${y} ${z - 4} ${x + 4} ${y + 4} ${z + 4} minecraft:air`)
  cmd(`fill ${x - 4} ${y - 1} ${z - 4} ${x + 4} ${y - 1} ${z + 4} minecraft:cobblestone`)
  cmd(`fill ${x - 4} ${y - 1} ${z - 4} ${x + 4} ${y - 1} ${z + 4} minecraft:mossy_cobblestone replace minecraft:cobblestone`)  // 일부만 이끼 — replace 실패해도 무해
  // 무너진 벽 모서리 (폐허 연출)
  cmd(`fill ${x - 4} ${y} ${z - 4} ${x - 4} ${y + 1} ${z - 2} minecraft:cracked_stone_bricks`)
  cmd(`fill ${x + 4} ${y} ${z + 2} ${x + 4} ${y + 1} ${z + 4} minecraft:cracked_stone_bricks`)
  cmd(`fill ${x + 2} ${y} ${z - 4} ${x + 4} ${y} ${z - 4} minecraft:mossy_stone_bricks`)
  // 감옥(철창 3×3, 안에 생존자)
  cmd(`fill ${x - 1} ${y} ${z - 1} ${x + 1} ${y + 2} ${z + 1} minecraft:iron_bars hollow`)
  cmd(`fill ${x} ${y + 3} ${z} ${x} ${y + 3} ${z} minecraft:dark_oak_slab`)
  cmd(`setblock ${x + 3} ${y} ${z + 3} minecraft:campfire`)
  cmd(`setblock ${x - 3} ${y} ${z + 3} minecraft:soul_lantern`)
  // 생존자 (우리 안, 이름표)
  cmd(`summon minecraft:villager ${x + 0.5} ${y} ${z + 0.5} {Tags:["ls_survivor"],CustomName:'{"text":"${s.name}","color":"aqua"}',CustomNameVisible:1b,PersistenceRequired:1b,NoAI:1b}`)
  // 간수들 (캠프 둘레)
  const g = s.guards
  for (let i = 0; i < g.length; i++) {
    var a = (i / g.length) * Math.PI * 2
    var gx = x + Math.cos(a) * 5, gz = z + Math.sin(a) * 5
    cmd(`summon ${g[i]} ${gx.toFixed(1)} ${y} ${gz.toFixed(1)} {Tags:["ls_captor"],PersistenceRequired:1b,Glowing:1b}`)
  }
}

function rsRevealIfNear(server) {
  if (!rsActive(server) || LS.rescueBuilt(server)) return
  const x = LS.rescueX(server), z = LS.rescueZ(server)
  let near = null
  server.players.forEach(p => {
    const dx = p.x - x, dz = p.z - z
    if (!near && dx * dx + dz * dz <= RS_APPROACH * RS_APPROACH) near = p
  })
  if (!near) return
  const y = rsSurfaceY(server, x, z, Math.floor(near.y))
  const s = SURVIVORS[LS.rescueIdx(server)]
  // **표식을 먼저 세우고 짓는다.** revealCamp 가 false 를 주면 이미 지어진 것이므로 그냥 돌아간다 —
  // 두 번 짓는 걸 막는 자리가 여기 하나다. 예전엔 표식 세우기와 짓기가 따로였다.
  var rsOk = false
  try { rsOk = !!LS.revealCamp(server, y, s.guards.length) } catch (e) { lsWarn('ls_rescue:reveal', e) }
  if (!rsOk) return
  rsBuildCamp(server, x, y, z)
  server.runCommandSilent(`title @a title {"text":"감금지 발견","color":"red","bold":true}`)
  server.runCommandSilent(`title @a subtitle {"text":"간수를 모두 처치하고 ${s.name}을(를) 구출하라","color":"gold"}`)
  rsPlay(server, 'minecraft:entity.vindicator.ambient', 1, 0.7)
  rsSay(server, `§c⚔ 감금지 발견! §7빛나는 간수 §c${s.guards.length}기§7를 모두 처치하라 — §b${s.name}§7이(가) 우리에 갇혀 있다.`)
  console.log(`[LS-RESCUE] camp revealed at ${x},${y},${z}`)
}

// ── 구출 성공: 생존자 성역 이송·정착 ──
function rsComplete(server) {
  const idx = LS.rescueIdx(server)
  const s = SURVIVORS[idx]
  const c = rsSancPos(server)
  const x = LS.rescueX(server), y = LS.rescueY(server), z = LS.rescueZ(server)
  // 좌표를 **읽고 나서** 원정을 끝낸다 — endRun 이 진행 상태를 되돌리므로 순서가 뒤집히면
  // 우리를 해체할 좌표를 잃는다(철창이 그대로 남고 생존자가 갇힌 채 텔레포트된다).
  try { LS.endRescue(server) } catch (e) { lsWarn('ls_rescue:end', e) }
  // 인구는 명부에 등록되면 자동으로 는다. 이미 구출한 사람이면 settleSurvivor 가 false 를 준다.
  try { LS.settleSurvivor(server, s.key) } catch (e) { lsWarn('ls_rescue:settle', e) }
  rsStore(server).putBoolean('town_npc_' + s.key, true) // 리셋 후 Easy NPC 승격용 플래그
  // 우리 해체 + 생존자를 성역으로 (NoAI 해제 — 성역을 거닌다)
  server.runCommandSilent(`fill ${x - 1} ${y} ${z - 1} ${x + 1} ${y + 2} ${z + 1} minecraft:air replace minecraft:iron_bars`)
  server.runCommandSilent(`execute as @e[tag=ls_survivor] run data merge entity @s {NoAI:0b}`)
  server.runCommandSilent(`tp @e[tag=ls_survivor] ${c.x + 2} ${c.y + 1} ${c.z + 2}`)
  server.runCommandSilent(`tag @e[tag=ls_survivor] add ls_resident`)
  server.runCommandSilent(`tag @e[tag=ls_survivor] remove ls_survivor`)
  server.runCommandSilent('title @a title {"text":"생존자 구출!","color":"aqua","bold":true}')
  server.runCommandSilent(`title @a subtitle {"text":"${s.name}이(가) 성역에 정착했다","color":"yellow"}`)
  rsPlay(server, 'minecraft:ui.toast.challenge_complete', 1, 1)
  rsPlay(server, 'minecraft:entity.villager.celebrate', 1, 1)
  rsSay(server, `§b★ ${s.name} 구출! §7성역으로 이송됐다 — §f인구 ${rsPop(server)}명 §7(매일 금고 +${rsPop(server) * RS_POP_INCOME})`)
  // 정수 공급처 ③ — 구출도 정수를 준다 (무기 각성과 마을 재건을 병행할 수 있게)
  try {
    var rc = rsSancPos(server)
    server.runCommandSilent(`summon item ${rc.x + 0.5} ${rc.y + 1} ${rc.z + 0.5} {Item:{id:"kubejs:rift_essence",count:1}}`)
    rsSay(server, '§5✦ 균열 정수 +1 §7— 구출된 자가 품고 있던 것 (성역에 떨어졌다)')
  } catch (err) { lsWarn('ls_rescue:161', err) }
  rsSay(server, `§8   ${s.job} — 세상이 리셋되면 정식 개업한다.`)
  const nxt = rsNextIdx(server)
  if (nxt >= 0) rsSay(server, `§7다음 신호: §f${SURVIVORS[nxt].name} §8— /rescue scout (${RS_SCOUT_COST} Ducat)`)
  else {
    rsSay(server, '§6모든 생존자가 돌아왔다. §7성역에 다시 사람 사는 소리가 난다.')
    server.players.forEach(p => { try { ttGrant(server, p.username, 'savior') } catch (e) { lsWarn('ls_rescue:167', e) } }) // 칭호 (ls_title.js)
  }
  console.log(`[LS-RESCUE] rescued ${s.key} pop=${rsPop(server)}`)
}

// ── 구출 실패 (생존자 사망) ──
function rsFail(server) {
  const s = SURVIVORS[LS.rescueIdx(server)]
  try { LS.endRescue(server) } catch (e) { lsWarn('ls_rescue:fail-end', e) }
  server.runCommandSilent('kill @e[tag=ls_captor]')
  server.runCommandSilent('title @a title {"text":"구출 실패...","color":"dark_red"}')
  rsPlay(server, 'minecraft:entity.villager.death', 1, 0.7)
  rsSay(server, `§4✖ ${s.name}을(를) 지키지 못했다... §7하지만 신호가 다시 잡힌다 — 다른 은신처로 옮겨진 모양이다. §8(/rescue scout 로 재시도)`)
  console.log(`[LS-RESCUE] FAILED ${s.key} (retry allowed)`)
}

// ── 간수 사망 추적 ──
EntityEvents.death(event => {
  const e = event.entity
  if (!e || !e.tags) return
  const tagStr = `${e.tags}`
  const server = e.server
  if (!server) return
  if (tagStr.includes('ls_captor')) {
    if (!rsActive(server) || !LS.rescueBuilt(server)) return
    // 읽고·빼고·쓰는 세 줄을 한 줄로 접었다 — 간수 둘이 같은 틱에 죽으면 예전엔 한쪽이 덮였다.
    var g = 0
    try { g = LS.killGuard(server) | 0 } catch (e) { lsWarn('ls_rescue:guard', e); return }
    if (g > 0) rsSay(server, `§7간수 처치 — 남은 간수 §c${g}기`)
    else rsComplete(server)
    return
  }
  if (tagStr.includes('ls_survivor')) {
    if (rsActive(server)) rsFail(server)
  }
})

// ── 틱: 접근 감지 + 일일 인구 수입 ──
let RS_TICK = 0
ServerEvents.tick(event => {
  RS_TICK++
  if (RS_TICK % 20 !== 0) return
  const server = event.server
  rsRevealIfNear(server)
  // 일일 인구 수입 (자체 날짜 추적 — ls_siege와 독립)
  const day = Math.floor(Number(server.overworld().getDayTime()) / 24000)
  var rsPrev = 0
  try { rsPrev = LS.rescueDay(server) | 0 } catch (e) { lsWarn('ls_rescue:day', e); return }
  if (day !== rsPrev) {
    // 날짜를 **먼저** 올린다 — 뒤에 두면 지급이 실패했을 때 매 틱 다시 시도해 로그를 뒤덮는다.
    try { LS.setRescueDay(server, day) } catch (e) { lsWarn('ls_rescue:day-set', e); return }
    var pop = rsPop(server)
    if (pop > 0) {
      LS.addTreasury(server, pop * RS_POP_INCOME)
      rsSay(server, `§b⌂ 주민 ${pop}명이 성역을 일군다 — 공동 금고 +${pop * RS_POP_INCOME}`)
    }
  }
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event

  event.register(Commands.literal('rescue')
    .executes(ctx => {
      const s = ctx.source.server
      const pop = rsPop(s)
      ctx.source.sendSystemMessage(Text.of(`§6═══ ☀ 생존자 구출 §7(인구 ${pop}/${SURVIVORS.length} · 매일 +${pop * RS_POP_INCOME} Ducat) §6═══`))
      const act = rsActive(s)
      const curIdx = act ? LS.rescueIdx(s) : -1
      SURVIVORS.forEach((sv, i) => {
        const done = rsDone(s, sv.key)
        const cur = i === curIdx
        const mark = done ? '§a✔' : cur ? '§e▶' : '§8?'
        ctx.source.sendSystemMessage(Text.of(`${mark} §f${sv.name} §7— ${sv.job}${cur ? ' §e(추적 중)' : ''}`))
      })
      if (act) {
        ctx.source.sendSystemMessage(Text.of(`§7   신호: §e${LS.rescueX(s)}, ~, ${LS.rescueZ(s)} §8· /rescue locate`))
      } else if (rsNextIdx(s) >= 0) {
        ctx.source.sendSystemMessage(Text.of(`§8   /rescue scout — 다음 생존자 추적 (${RS_SCOUT_COST} Ducat)`))
      }
      return 1
    })
    .then(Commands.literal('scout').executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      return rsScout(ctx.source.server, p)
    }))
    .then(Commands.literal('locate').executes(ctx => {
      const s = ctx.source.server
      if (!rsActive(s)) { ctx.source.sendSystemMessage(Text.of('§7추적 중인 생존자가 없습니다.')); return 1 }
      const sv = SURVIVORS[LS.rescueIdx(s)]
      const x = LS.rescueX(s), z = LS.rescueZ(s)
      const c = rsSancPos(s)
      ctx.source.sendSystemMessage(Text.of(`§b${sv.name}: §e${x}, ~, ${z} §7— ${rsDir8(x - c.x, z - c.z)}쪽 ${LS.rescueBuilt(s) ? '§c(감금지 노출 — 간수 ' + LS.rescueGuards(s) + '기)' : '§8(미발견)'}`))
      return 1
    }))
    .then(Commands.literal('cancel').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      s.runCommandSilent('kill @e[tag=ls_captor]')
      s.runCommandSilent('kill @e[tag=ls_survivor]')
      try { LS.endRescue(s) } catch (e) { lsWarn('ls_rescue:cancel', e) }
      ctx.source.sendSystemMessage(Text.of('§7구출 작전 취소 (비용 환불 없음)')); return 1
    }))
    .then(Commands.literal('grant').requires(s => s.hasPermission(2)).then(Commands.argument('key', Arguments.STRING.create(event))
      .suggests((ctx, b) => { SURVIVORS.forEach(sv => b.suggest(sv.key)); return b.buildFuture() })
      .executes(ctx => {
        const s = ctx.source.server; const key = Arguments.STRING.getResult(ctx, 'key')
        const sv = SURVIVORS.find(v => v.key === key)
        if (!sv) { ctx.source.sendSystemMessage(Text.of('§c없는 생존자 키')); return 0 }
        // 이미 구출한 생존자면 아무것도 안 한다. 예전엔 인구만 늘었고, 그러면 매일 들어오는
        // 수입(인구×8)이 영구히 부풀었다 — 오류도 로그도 없이.
        var rsNew = false
        try { rsNew = !!LS.settleSurvivor(s, key) } catch (e) { lsWarn('ls_rescue:grant', e) }
        if (!rsNew) { ctx.source.sendSystemMessage(Text.of(`§7${sv.name}은(는) 이미 구출됨 (인구 ${rsPop(s)})`)); return 0 }
        rsStore(s).putBoolean('town_npc_' + key, true)
        ctx.source.sendSystemMessage(Text.of(`§a${sv.name} 구출 처리 (인구 ${rsPop(s)})`)); return 1
      }))))
})

console.log(`[Last Stardust] 생존자 구출 로드됨 — 생존자 ${SURVIVORS.length}명 (800~1600m 랜덤 감금지)`)
