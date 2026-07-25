// Last Stardust — 균열 원정 (Rift Expedition)  [진행 척추]
// 성역을 중심으로 바깥으로 뻗어나가는 "핵심 진행 보스" 제단 체인.
// 플레이어 주도: 성역에서 정수를 바쳐 다음 봉인을 해제하면, 링 거리(2000~5000m) 랜덤 좌표에
// 제단이 예고된다. 그 좌표로 원정 → 접근하면 제단이 출현 → 제물대 우클릭으로 보스 소환 → 처치.
// 처치 시 월드티어 상승 + 금고 보상 + 다음 봉인 개방. 마지막(T4) 클리어 → 피날레 자동 무장.
// ※ 자연 생성 구조물 보스(TF/Aether 등)는 그대로 탐험 보상으로 남는다 — 이건 "진행 게이트"만 담당.
// 저장(오버월드 persistentData, ls_siege/ls_town과 공유): ls_finale_armed
// ※ 성역 좌표와 금고는 모드(LSData)가 소유한다 — 브릿지 `LS` 로 읽고 쓴다. 여기에 사본은 없다.
//   전용: rf_active/rf_pending/rf_engaged · rf_tier · rf_ax/rf_ay/rf_az
//   ※ 클리어 수(진행도)는 모드(LSData)가 소유 — `LS.progress()` / `LS.setProgress()`

function rfStore(server) { return server.overworld().persistentData }
function rfGetI(server, k) { return rfStore(server).getInt(k) }
function rfSetI(server, k, v) { rfStore(server).putInt(k, v) }
function rfGetB(server, k) { return rfStore(server).getBoolean(k) }
function rfSetB(server, k, v) { rfStore(server).putBoolean(k, v) }

const RF_ESS = 'kubejs:rift_essence'
const APPROACH = 64          // 이 거리 이내로 다가오면 제단이 출현(청크 로드 보장)
const NEAR_SANCTUARY = 96    // 봉인 해제는 성역 근처에서만(의식)

// ── 진행 게이트 정의 (핵심 보스 4종, 전부 오버월드 소환 가능·정상 작동) ──
// minR~maxR: 성역 기준 링 거리(m). cost: 개봉에 바칠 균열 정수. reward: 클리어 금고 보상.
// tier: 클리어 시 부여될 Apotheosis 월드티어. (haven → frontier → ascent → summit → pinnacle)
const TIERS = [
  { key: 't1', name: '개척', boss: 'mowziesmobs:ferrous_wroughtnaut', bossName: '강철거인',
    minR: 2000, maxR: 2800, cost: 0, reward: 180, tier: 'frontier',
    blurb: '입문 관문 — 잠든 강철거인이 첫 봉인을 지킨다.' },
  { key: 't2', name: '심층', boss: 'cataclysm:ignis', bossName: '이그니스',
    minR: 2800, maxR: 3600, cost: 2, reward: 300, tier: 'ascent',
    blurb: '화염의 시련 — 불의 군주가 두 번째 봉인을 태운다.' },
  { key: 't3', name: '정점', boss: 'bosses_of_mass_destruction:gauntlet', bossName: '건틀렛',
    minR: 3600, maxR: 4400, cost: 2, reward: 450, tier: 'summit',
    blurb: '심연의 주먹 — 떠도는 건틀렛이 하늘을 짓누른다.' },
  { key: 't4', name: '균열핵', boss: 'cataclysm:netherite_monstrosity', bossName: '네더라이트 괴물',
    minR: 4400, maxR: 5000, cost: 3, reward: 750, tier: 'pinnacle',
    blurb: '최후의 관문 — 균열핵의 수호자를 넘어야 어둠의 심장에 닿는다.' }
]
const MAX_TIER = TIERS.length

// ── 공유 헬퍼 (ls_siege와 동일 방식) ──
// 성역 좌표는 모드(LSData)가 유일 소유 — 사본을 두지 않는다 (ls_siege.js 주석 참고)
function rfSancSet(server) { return LS.hasSanctuary(server) }
function rfSancPos(server) { return { x: LS.sanctuaryX(server), y: LS.sanctuaryY(server), z: LS.sanctuaryZ(server) } }
function rsay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function rplay(server, s, v, p) { server.runCommandSilent(`execute as @a at @s run playsound ${s} master @s ~ ~ ~ ${v} ${p}`) }
// ※ 금고·마을은 lsrelics 모드(LSData)가 소유한다. 전역 바인딩 LS 를 통해 접근한다 —
//   persistentData 의 'ls_treasury' 를 직접 건드리면 모드의 금고와 갈라져 보상이 도착하지 않는다.
function rfAddTreasury(server, amt) { LS.addTreasury(server, amt) }
function rfSurfaceY(server, x, z, fallback) { return lsSurfaceY(server, x, z, fallback) }   // ls_util.js
// 방위(8방) + 거리 표기
function bearing8(dx, dz) {
  let ang = Math.atan2(dx, -dz) * 180 / Math.PI // 0=북, 90=동
  if (ang < 0) ang += 360
  const dirs = ['북', '북동', '동', '남동', '남', '남서', '서', '북서']
  return dirs[Math.round(ang / 45) % 8]
}
function distTo(server, x, z) { const c = rfSancPos(server); const dx = x - c.x, dz = z - c.z; return Math.round(Math.sqrt(dx * dx + dz * dz)) }

// ── 상태 ──
// ── 관문 진행도는 모드(LSData)가 유일하게 소유한다 ──
// 6개 스크립트가 각자 persistentData 에서 읽던 값이다. 성역 좌표와 똑같은 구조였고,
// 성역이 실제로 그 구조 때문에 사고를 냈기 때문에(귀환석 먹통) 같은 일이 나기 전에 옮겼다.
// 상한(0~4)은 LSData 가 걸어주므로 여기서 다시 자르지 않는다.
function progress(server) { return LS.progress(server) }                   // 클리어한 게이트 수(0..4)
function setProgress(server, n) { LS.setProgress(server, n) }
function isActive(server) { return rfGetB(server, 'rf_active') }            // 개봉된 제단(미클리어)이 있는가
function activeTierIdx(server) { return rfGetI(server, 'rf_tier') }         // 현재 개봉된 티어 인덱스
function nextIdx(server) { return progress(server) }                        // 다음에 개봉 가능한 티어 인덱스

// ── 제단 개봉(플레이어 주도) ──
function openAltar(server, player) {
  if (isActive(server)) { player.tell(Text.of('§7이미 개봉된 제단이 있습니다. §e/expedition locate §7로 위치 확인.')); return 0 }
  const idx = nextIdx(server)
  if (idx >= MAX_TIER) {
    player.tell(Text.of('§6모든 균열 봉인이 해제되었습니다. §7이제 성역에서 가장 긴 밤을 준비하라. (/finale)'))
    return 0
  }
  if (!rfSancSet(server)) { player.tell(Text.of('§c성역이 지정되지 않았습니다. OP가 /sanctuary here')); return 0 }
  // 성역 근처에서만 의식 가능
  const c = rfSancPos(server)
  const ddx = player.x - c.x, ddz = player.z - c.z
  if (ddx * ddx + ddz * ddz > NEAR_SANCTUARY * NEAR_SANCTUARY) {
    player.tell(Text.of(`§7봉인 해제 의식은 §e성역 근처§7에서만 가능합니다. (성역: ${c.x}, ${c.z})`)); return 0
  }
  const t = TIERS[idx]
  // 정수 소모(/clear 로 확인 후 회수 — 인벤 API 대신 명령어)
  const name = player.username
  let have = 0
  // 개수는 인벤토리를 직접 읽는다 (ls_util.js) — /clear 반환값으로 세는 건 애초에 불가능하다.
  have = lsCountItem(player, RF_ESS)
  if (have < t.cost) { player.tell(Text.of(`§c균열 정수 부족: §e${have}/${t.cost} §7— 보스를 처치해 모으세요.`)); return 0 }
  if (lsTakeItem(player, RF_ESS, t.cost) < t.cost) { player.tell(Text.of('§c정수 회수에 실패했습니다.')); return 0 }
  // 링 좌표 롤
  const ang = Math.random() * Math.PI * 2
  const dist = t.minR + Math.random() * (t.maxR - t.minR)
  const ax = Math.floor(c.x + Math.cos(ang) * dist)
  const az = Math.floor(c.z + Math.sin(ang) * dist)
  rfSetB(server, 'rf_active', true)
  rfSetB(server, 'rf_pending', true)   // 아직 미건축(접근 시 출현)
  rfSetB(server, 'rf_engaged', false)  // 보스 미소환
  rfSetI(server, 'rf_tier', idx)
  rfSetI(server, 'rf_ax', ax)
  rfSetI(server, 'rf_az', az)
  rfSetI(server, 'rf_ay', 0)
  // 예고
  const dir = bearing8(ax - c.x, az - c.z)
  server.runCommandSilent(`title @a title {"text":"균열 봉인 해제","color":"dark_purple","bold":true}`)
  server.runCommandSilent(`title @a subtitle {"text":"${t.name}의 제단이 먼 곳에 나타났다","color":"light_purple"}`)
  rplay(server, 'minecraft:block.end_portal.spawn', 0.7, 0.6)
  rsay(server, `§5✦ ${t.name} 제단 개봉 §7(${name}) — §d${t.bossName}§7이(가) 기다린다.`)
  rsay(server, `§7   위치: §e${ax}, ~, ${az} §7· 성역에서 §b${Math.round(dist)}m §7· 방향 §b${dir}쪽`)
  rsay(server, `§8   ${t.blurb}`)
  rsay(server, `§8   그 좌표로 원정하라. 다가가면 제단이 모습을 드러낸다.`)
  console.log(`[LS-RIFT] open tier=${idx} boss=${t.boss} at ${ax},${az} dist=${Math.round(dist)}`)
  return 1
}

// ── 제단 건축 (플레이어 접근 시, 지형에 맞춰) ──
function buildAltar(server, ax, ay, az) {
  const cmd = (s) => server.runCommandSilent(s)
  // 1. 부지 정리 + 바닥
  cmd(`fill ${ax - 3} ${ay} ${az - 3} ${ax + 3} ${ay + 4} ${az + 3} minecraft:air`)
  cmd(`fill ${ax - 3} ${ay - 1} ${az - 3} ${ax + 3} ${ay - 1} ${az + 3} minecraft:polished_blackstone`)
  // 2. 테두리 벽(1높이)
  cmd(`fill ${ax - 3} ${ay} ${az - 3} ${ax + 3} ${ay} ${az + 3} minecraft:polished_blackstone_bricks hollow`)
  // 3. 중앙 제단 + 제물대(우클릭 소환)
  cmd(`fill ${ax - 1} ${ay} ${az - 1} ${ax + 1} ${ay} ${az + 1} minecraft:polished_blackstone`)
  cmd(`setblock ${ax} ${ay} ${az} minecraft:crying_obsidian`)
  cmd(`setblock ${ax} ${ay + 1} ${az} minecraft:lodestone`)
  // 4. 모서리 첨탑 + 소울 랜턴(야간 식별)
  const corners = [[ax - 3, az - 3], [ax - 3, az + 3], [ax + 3, az - 3], [ax + 3, az + 3]]
  corners.forEach(cc => {
    cmd(`fill ${cc[0]} ${ay} ${cc[1]} ${cc[0]} ${ay + 2} ${cc[1]} minecraft:polished_blackstone`)
    cmd(`setblock ${cc[0]} ${ay + 3} ${cc[1]} minecraft:soul_lantern`)
  })
}

function revealIfNear(server) {
  if (!rfGetB(server, 'rf_pending')) return
  const ax = rfGetI(server, 'rf_ax'), az = rfGetI(server, 'rf_az')
  let near = null
  server.players.forEach(p => {
    const dx = p.x - ax, dz = p.z - az
    if (!near && dx * dx + dz * dz <= APPROACH * APPROACH) near = p
  })
  if (!near) return
  const ay = rfSurfaceY(server, ax, az, Math.floor(near.y))
  buildAltar(server, ax, ay, az)
  rfSetI(server, 'rf_ay', ay)
  rfSetB(server, 'rf_pending', false)
  const t = TIERS[activeTierIdx(server)]
  server.runCommandSilent(`title @a title {"text":"제단 출현","color":"dark_purple","bold":true}`)
  server.runCommandSilent(`title @a subtitle {"text":"제물대에 손을 얹어 ${t.bossName}을(를) 불러내라","color":"light_purple"}`)
  rplay(server, 'minecraft:block.beacon.activate', 0.8, 0.6)
  rplay(server, 'minecraft:ambient.cave', 1, 0.5)
  rsay(server, `§5✦ ${t.name} 제단이 모습을 드러냈다. §7좌표 §e${ax}, ${ay + 1}, ${az} §7— 중앙 제물대를 우클릭.`)
  console.log(`[LS-RIFT] altar revealed tier=${activeTierIdx(server)} at ${ax},${ay},${az}`)
}

// ── 보스 소환 (제물대 우클릭) ──
function summonAltarBoss(server) {
  if (!isActive(server) || rfGetB(server, 'rf_pending') || rfGetB(server, 'rf_engaged')) return false
  const idx = activeTierIdx(server)
  const t = TIERS[idx]
  const ax = rfGetI(server, 'rf_ax'), ay = rfGetI(server, 'rf_ay'), az = rfGetI(server, 'rf_az')
  server.runCommandSilent(`summon ${t.boss} ${ax + 0.5} ${ay + 2} ${az + 0.5} {Tags:["ls_rift_boss"],PersistenceRequired:1b}`)
  rfSetB(server, 'rf_engaged', true)
  server.runCommandSilent(`title @a title {"text":"${t.bossName}","color":"dark_red","bold":true}`)
  server.runCommandSilent(`title @a subtitle {"text":"${t.name}의 봉인을 지키는 자","color":"red"}`)
  rplay(server, 'minecraft:entity.wither.spawn', 1, 0.6)
  rplay(server, 'minecraft:entity.ender_dragon.growl', 0.6, 0.6)
  rsay(server, `§4⚔ ${t.bossName} 강림! §7${t.name} 봉인의 수호자를 쓰러뜨려라.`)
  console.log(`[LS-RIFT] boss summoned tier=${idx} ${t.boss}`)
  return true
}

// ── 게이트 클리어 ──
function setWorldTier(server, tierName) {
  server.players.forEach(p => { try { server.runCommandSilent(`apoth set_world_tier ${p.username} ${tierName}`) } catch (e) { lsWarn('ls_rift:178', e) } })
}
function completeTier(server) {
  if (!isActive(server)) return
  const idx = activeTierIdx(server)
  const t = TIERS[idx]
  rfSetB(server, 'rf_active', false)
  rfSetB(server, 'rf_engaged', false)
  rfSetB(server, 'rf_pending', false)
  setProgress(server, idx + 1)
  // 보상: 월드티어 상승 + 공동 금고
  setWorldTier(server, t.tier)
  rfAddTreasury(server, t.reward)
  server.runCommandSilent(`title @a title {"text":"봉인 해방","color":"gold","bold":true}`)
  server.runCommandSilent(`title @a subtitle {"text":"${t.name}의 균열이 닫혔다 — 세계 등급 상승","color":"yellow"}`)
  rplay(server, 'minecraft:ui.toast.challenge_complete', 1, 1)
  rplay(server, 'minecraft:block.beacon.power_select', 1, 1.2)
  rsay(server, `§6★ ${t.name} 봉인 해방! §e세계 등급 → ${t.tier} §7· 공동 금고 +${t.reward}`)
  const done = idx + 1
  if (done >= MAX_TIER) {
    // 마지막 게이트 → 피날레 무장 (남은 균열 노드를 파괴하면 '가장 긴 밤' 개막)
    rfSetB(server, 'ls_finale_armed', true)
    server.players.forEach(p => { try { ttGrant(server, p.username, 'rift_conqueror') } catch (e) { lsWarn('ls_rift:200', e) } }) // 칭호 (ls_title.js)
    rsay(server, '§5✧ 모든 균열 봉인이 풀렸다. §7이제 남은 §d균열 노드§7를 파괴하면 — §c어둠의 심장§7이 강림한다.')
    rsay(server, '§8   (성역에 집결하라. 가장 긴 밤이 다가온다.)')
    rplay(server, 'minecraft:entity.wither.spawn', 0.8, 0.4)
  } else {
    var nt = TIERS[done]
    rsay(server, `§7다음 봉인: §e${nt.name} §7(정수 ${nt.cost}개) — 준비되면 성역에서 §e/expedition open`)
  }
  console.log(`[LS-RIFT] tier ${idx} cleared, progress=${done}`)
}

// ── 제물대 우클릭 감지 ──
BlockEvents.rightClicked(event => {
  const b = event.block
  if (!b || b.id !== 'minecraft:lodestone') return
  const p = event.player
  const server = p ? p.server : null
  if (!server || !isActive(server) || rfGetB(server, 'rf_pending') || rfGetB(server, 'rf_engaged')) return
  const ax = rfGetI(server, 'rf_ax'), ay = rfGetI(server, 'rf_ay'), az = rfGetI(server, 'rf_az')
  if (b.x === ax && b.y === ay + 1 && b.z === az) {
    event.cancel()
    summonAltarBoss(server)
  }
})

// ── 게이트 보스 사망 ──
EntityEvents.death(event => {
  const e = event.entity
  if (!e || !e.tags) return
  if (!(`${e.tags}`).includes('ls_rift_boss')) return
  const server = e.server
  if (server) completeTier(server)
})

// ── 틱: 미건축 제단 접근 감지 ──
let RF_TICK = 0
ServerEvents.tick(event => {
  RF_TICK++
  if (RF_TICK % 20 !== 0) return // 1초마다
  revealIfNear(event.server)
})

// ── 명령어 ──
function statusLine(server) {
  const done = progress(server)
  const bar = '§a' + '■'.repeat(done) + '§8' + '□'.repeat(MAX_TIER - done)
  return bar
}
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event

  event.register(Commands.literal('expedition')
    .executes(ctx => {
      const s = ctx.source.server
      ctx.source.sendSystemMessage(Text.of(`§6═══ ✧ 균열 원정 §7${statusLine(s)} §7(${progress(s)}/${MAX_TIER}) §6═══`))
      if (isActive(s)) {
        var t = TIERS[activeTierIdx(s)]
        var ax = rfGetI(s, 'rf_ax'), az = rfGetI(s, 'rf_az')
        if (rfGetB(s, 'rf_pending')) {
          var dir = bearing8(ax - rfSancPos(s).x, az - rfSancPos(s).z)
          ctx.source.sendSystemMessage(Text.of(`§5▶ ${t.name} 제단 원정 중 §7— §e${ax}, ~, ${az} §7(${distTo(s, ax, az)}m ${dir}쪽) · §8미출현(접근 필요)`))
        } else if (!rfGetB(s, 'rf_engaged')) {
          ctx.source.sendSystemMessage(Text.of(`§5▶ ${t.name} 제단 출현 §7— §e${ax}, ${rfGetI(s, 'rf_ay') + 1}, ${az} §7· 제물대 우클릭으로 §d${t.bossName}§7 소환`))
        } else {
          ctx.source.sendSystemMessage(Text.of(`§4▶ ${t.name} — §d${t.bossName}§7 전투 중! §7좌표 §e${ax}, ~, ${az}`))
        }
        ctx.source.sendSystemMessage(Text.of('§8   /expedition locate 로 위치 재확인'))
      } else if (progress(s) >= MAX_TIER) {
        ctx.source.sendSystemMessage(Text.of('§6모든 봉인 해방 — §c어둠의 심장§6을 향하라. §7(남은 균열 노드 파괴 → /finale)'))
      } else {
        var nt = TIERS[nextIdx(s)]
        ctx.source.sendSystemMessage(Text.of(`§7다음 봉인: §e${nt.name} §7→ §d${nt.bossName} §7(${nt.minR}~${nt.maxR}m)`))
        ctx.source.sendSystemMessage(Text.of(`§8   개봉 비용 §5균열 정수 ${nt.cost}개§8 · 성역에서 §e/expedition open`))
      }
      return 1
    })
    .then(Commands.literal('open').executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      return openAltar(ctx.source.server, p)
    }))
    .then(Commands.literal('locate').executes(ctx => {
      const s = ctx.source.server
      if (!isActive(s)) { ctx.source.sendSystemMessage(Text.of('§7개봉된 제단이 없습니다. §e/expedition open')); return 1 }
      const t = TIERS[activeTierIdx(s)]
      const ax = rfGetI(s, 'rf_ax'), az = rfGetI(s, 'rf_az')
      const dir = bearing8(ax - rfSancPos(s).x, az - rfSancPos(s).z)
      const ytxt = rfGetB(s, 'rf_pending') ? '~' : (rfGetI(s, 'rf_ay') + 1)
      ctx.source.sendSystemMessage(Text.of(`§5${t.name} 제단: §e${ax}, ${ytxt}, ${az} §7— 성역에서 §b${distTo(s, ax, az)}m §b${dir}쪽`))
      return 1
    }))
    // ── 관리자 ──
    .then(Commands.literal('build').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      if (!isActive(s) || !rfGetB(s, 'rf_pending')) { ctx.source.sendSystemMessage(Text.of('§7미출현 제단이 없습니다.')); return 0 }
      const ax = rfGetI(s, 'rf_ax'), az = rfGetI(s, 'rf_az')
      const p = ctx.source.player
      const ay = rfSurfaceY(s, ax, az, p ? Math.floor(p.y) : 80)
      buildAltar(s, ax, ay, az); rfSetI(s, 'rf_ay', ay); rfSetB(s, 'rf_pending', false)
      ctx.source.sendSystemMessage(Text.of(`§a제단 강제 건축: ${ax}, ${ay}, ${az}`)); return 1
    }))
    .then(Commands.literal('skip').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      if (!isActive(s)) { ctx.source.sendSystemMessage(Text.of('§7개봉된 제단이 없습니다.')); return 0 }
      completeTier(s); ctx.source.sendSystemMessage(Text.of('§7현재 게이트 강제 클리어 처리')); return 1
    }))
    .then(Commands.literal('cancel').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      rfSetB(s, 'rf_active', false); rfSetB(s, 'rf_pending', false); rfSetB(s, 'rf_engaged', false)
      ctx.source.sendSystemMessage(Text.of('§7현재 제단 취소(진행도 유지, 정수 환불 없음)')); return 1
    }))
    .then(Commands.literal('reset').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      setProgress(s, 0); rfSetB(s, 'rf_active', false); rfSetB(s, 'rf_pending', false); rfSetB(s, 'rf_engaged', false)
      rfSetI(s, 'rf_tier', 0)
      ctx.source.sendSystemMessage(Text.of('§7균열 원정 진행도 초기화')); return 1
    }))
    .then(Commands.literal('setprogress').requires(s => s.hasPermission(2)).then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
      const s = ctx.source.server
      const n = Math.max(0, Math.min(MAX_TIER, Arguments.INTEGER.getResult(ctx, 'n')))
      setProgress(s, n); rfSetB(s, 'rf_active', false); rfSetB(s, 'rf_pending', false); rfSetB(s, 'rf_engaged', false)
      ctx.source.sendSystemMessage(Text.of(`§7진행도 = ${n}/${MAX_TIER}`)); return 1
    }))))
})

console.log(`[Last Stardust] 균열 원정 로드됨 — 진행 게이트 ${MAX_TIER}종 (제단 랜덤 배치 2000~5000m)`)
