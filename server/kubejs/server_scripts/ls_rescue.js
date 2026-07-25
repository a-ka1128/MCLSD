// Last Stardust — 생존자 구출 (Survivor Rescue)  [마을 인구]
// 폐허 세계에 흩어진 생존자들을 찾아 구출하면 성역에 "실제로" 정착한다(주민 엔티티 이송).
// 구출 흐름: /rescue scout (금고 소모) → 800~1600m 랜덤 좌표에 감금지 예고 → 원정
//   → 접근 시 폐허 캠프 출현(우리 안 생존자 + 어둠의 간수들) → 간수 전멸 = 구출
//   → 생존자가 성역으로 이송·정착, 인구 +1, 매일 인구×5 Ducat 수입.
// 리셋 후: town_npc_<key> 플래그를 읽어 Easy NPC 상인/스토리텔러로 승격(수동 연동).
// 저장: rs_active/rs_built/rs_x/rs_y/rs_z/rs_idx/rs_guards · rs_done_<key> · town_pop · rs_day

function rsStore(server) { return server.overworld().persistentData }
function rsGetI(server, k) { return rsStore(server).getInt(k) }
function rsSetI(server, k, v) { rsStore(server).putInt(k, v) }
function rsGetB(server, k) { return rsStore(server).getBoolean(k) }
function rsSetB(server, k, v) { rsStore(server).putBoolean(k, v) }
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

function rsDone(server, key) { return rsGetB(server, 'rs_done_' + key) }
function rsNextIdx(server) {
  for (let i = 0; i < SURVIVORS.length; i++) { if (!rsDone(server, SURVIVORS[i].key)) return i }
  return -1
}
function rsPop(server) { return rsGetI(server, 'town_pop') }

// ── 정찰: 감금지 좌표 롤 ──
function rsScout(server, player) {
  if (rsGetB(server, 'rs_active')) { player.tell(Text.of('§7이미 추적 중인 생존자가 있습니다. §e/rescue locate')); return 0 }
  const idx = rsNextIdx(server)
  if (idx < 0) { player.tell(Text.of('§6모든 생존자를 구출했습니다. §7성역에 사람이 돌아왔다.')); return 0 }
  if (!rsSancSet(server)) { player.tell(Text.of('§c성역이 지정되지 않았습니다. OP가 /sanctuary here')); return 0 }
  const tre = LS.treasury(server)
  if (tre < RS_SCOUT_COST) { player.tell(Text.of(`§c정찰 비용 부족: 공동 금고 ${tre}/${RS_SCOUT_COST} Ducat`)); return 0 }
  LS.spendTreasury(server, RS_SCOUT_COST)
  const c = rsSancPos(server)
  const ang = Math.random() * Math.PI * 2
  const dist = RS_MIN + Math.random() * (RS_MAX - RS_MIN)
  const x = Math.floor(c.x + Math.cos(ang) * dist)
  const z = Math.floor(c.z + Math.sin(ang) * dist)
  rsSetB(server, 'rs_active', true)
  rsSetB(server, 'rs_built', false)
  rsSetI(server, 'rs_idx', idx)
  rsSetI(server, 'rs_x', x); rsSetI(server, 'rs_z', z); rsSetI(server, 'rs_y', 0)
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
  const idx = rsGetI(server, 'rs_idx')
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
  rsSetI(server, 'rs_guards', g.length)
}

function rsRevealIfNear(server) {
  if (!rsGetB(server, 'rs_active') || rsGetB(server, 'rs_built')) return
  const x = rsGetI(server, 'rs_x'), z = rsGetI(server, 'rs_z')
  let near = null
  server.players.forEach(p => {
    const dx = p.x - x, dz = p.z - z
    if (!near && dx * dx + dz * dz <= RS_APPROACH * RS_APPROACH) near = p
  })
  if (!near) return
  const y = rsSurfaceY(server, x, z, Math.floor(near.y))
  rsSetI(server, 'rs_y', y)
  rsSetB(server, 'rs_built', true)
  rsBuildCamp(server, x, y, z)
  const s = SURVIVORS[rsGetI(server, 'rs_idx')]
  server.runCommandSilent(`title @a title {"text":"감금지 발견","color":"red","bold":true}`)
  server.runCommandSilent(`title @a subtitle {"text":"간수를 모두 처치하고 ${s.name}을(를) 구출하라","color":"gold"}`)
  rsPlay(server, 'minecraft:entity.vindicator.ambient', 1, 0.7)
  rsSay(server, `§c⚔ 감금지 발견! §7빛나는 간수 §c${rsGetI(server, 'rs_guards')}기§7를 모두 처치하라 — §b${s.name}§7이(가) 우리에 갇혀 있다.`)
  console.log(`[LS-RESCUE] camp revealed at ${x},${y},${z}`)
}

// ── 구출 성공: 생존자 성역 이송·정착 ──
function rsComplete(server) {
  const idx = rsGetI(server, 'rs_idx')
  const s = SURVIVORS[idx]
  const c = rsSancPos(server)
  rsSetB(server, 'rs_active', false)
  rsSetB(server, 'rs_built', false)
  rsSetB(server, 'rs_done_' + s.key, true)
  rsStore(server).putBoolean('town_npc_' + s.key, true) // 리셋 후 Easy NPC 승격용 플래그
  rsSetI(server, 'town_pop', rsPop(server) + 1)
  // 우리 해체 + 생존자를 성역으로 (NoAI 해제 — 성역을 거닌다)
  const x = rsGetI(server, 'rs_x'), y = rsGetI(server, 'rs_y'), z = rsGetI(server, 'rs_z')
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
  const s = SURVIVORS[rsGetI(server, 'rs_idx')]
  rsSetB(server, 'rs_active', false)
  rsSetB(server, 'rs_built', false)
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
    if (!rsGetB(server, 'rs_active') || !rsGetB(server, 'rs_built')) return
    var g = rsGetI(server, 'rs_guards') - 1
    if (g < 0) g = 0
    rsSetI(server, 'rs_guards', g)
    if (g > 0) rsSay(server, `§7간수 처치 — 남은 간수 §c${g}기`)
    else rsComplete(server)
    return
  }
  if (tagStr.includes('ls_survivor')) {
    if (rsGetB(server, 'rs_active')) rsFail(server)
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
  if (day !== rsGetI(server, 'rs_day')) {
    rsSetI(server, 'rs_day', day)
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
      SURVIVORS.forEach(sv => {
        const done = rsDone(s, sv.key)
        const cur = rsGetB(s, 'rs_active') && SURVIVORS[rsGetI(s, 'rs_idx')].key === sv.key
        const mark = done ? '§a✔' : cur ? '§e▶' : '§8?'
        ctx.source.sendSystemMessage(Text.of(`${mark} §f${sv.name} §7— ${sv.job}${cur ? ' §e(추적 중)' : ''}`))
      })
      if (rsGetB(s, 'rs_active')) {
        ctx.source.sendSystemMessage(Text.of(`§7   신호: §e${rsGetI(s, 'rs_x')}, ~, ${rsGetI(s, 'rs_z')} §8· /rescue locate`))
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
      if (!rsGetB(s, 'rs_active')) { ctx.source.sendSystemMessage(Text.of('§7추적 중인 생존자가 없습니다.')); return 1 }
      const sv = SURVIVORS[rsGetI(s, 'rs_idx')]
      const x = rsGetI(s, 'rs_x'), z = rsGetI(s, 'rs_z')
      const c = rsSancPos(s)
      ctx.source.sendSystemMessage(Text.of(`§b${sv.name}: §e${x}, ~, ${z} §7— ${rsDir8(x - c.x, z - c.z)}쪽 ${rsGetB(s, 'rs_built') ? '§c(감금지 노출 — 간수 ' + rsGetI(s, 'rs_guards') + '기)' : '§8(미발견)'}`))
      return 1
    }))
    .then(Commands.literal('cancel').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      s.runCommandSilent('kill @e[tag=ls_captor]')
      s.runCommandSilent('kill @e[tag=ls_survivor]')
      rsSetB(s, 'rs_active', false); rsSetB(s, 'rs_built', false)
      ctx.source.sendSystemMessage(Text.of('§7구출 작전 취소 (비용 환불 없음)')); return 1
    }))
    .then(Commands.literal('grant').requires(s => s.hasPermission(2)).then(Commands.argument('key', Arguments.STRING.create(event))
      .suggests((ctx, b) => { SURVIVORS.forEach(sv => b.suggest(sv.key)); return b.buildFuture() })
      .executes(ctx => {
        const s = ctx.source.server; const key = Arguments.STRING.getResult(ctx, 'key')
        const sv = SURVIVORS.find(v => v.key === key)
        if (!sv) { ctx.source.sendSystemMessage(Text.of('§c없는 생존자 키')); return 0 }
        rsSetB(s, 'rs_done_' + key, true)
        rsStore(s).putBoolean('town_npc_' + key, true)
        rsSetI(s, 'town_pop', rsPop(s) + 1)
        ctx.source.sendSystemMessage(Text.of(`§a${sv.name} 구출 처리 (인구 ${rsPop(s)})`)); return 1
      }))))
})

console.log(`[Last Stardust] 생존자 구출 로드됨 — 생존자 ${SURVIVORS.length}명 (800~1600m 랜덤 감금지)`)
