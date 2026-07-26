// Last Stardust — 시그니처: 밤 공성 (성역 방어)  [Phase 3]
// 균열 노드(어둠의 근원)가 살아있는 한 매일 위협도가 차오르고, 밤마다 공성이 온다.
// 10일마다 대공세(예고됨). 위협도 단계마다 몹이 진화. 노드를 원정 파괴해야 근본 해결.
// 노드 장부: /riftnode (실제 관문 배치는 리셋 후 인게임 — 배치 후 add로 등록)

// ── 저장소 (오버월드 persistentData) ──
function sfStore(server) { return server.overworld().persistentData }
function sfGetI(server, k) { return sfStore(server).getInt(k) }
function sfSetI(server, k, v) { sfStore(server).putInt(k, v) }
function sfGetB(server, k) { return sfStore(server).getBoolean(k) }
function sfSetB(server, k, v) { sfStore(server).putBoolean(k, v) }
function sfGetS(server, k) { return String(sfStore(server).getString(k) || '') }
function sfSetS(server, k, v) { sfStore(server).putString(k, v) }
// 마을 발전 레벨 읽기 (ls_town.js가 씀)
// ※ 금고·마을은 lsrelics 모드(LSData)가 소유한다. 전역 바인딩 LS 를 통해 접근한다 —
//   persistentData 의 'ls_treasury' 를 직접 건드리면 모드의 금고와 갈라져 보상이 도착하지 않는다.
function townLvl(server, t) { return LS.townLevel(server, t) }

// ── 튜닝 상수 ──
const SPAWN_RING = 32        // 성역 중심에서 스폰 거리
const SANCTUARY_RADIUS = 64
const MAX_WAVES = 5          // 일반 공성 최대 웨이브
const SIEGE_EVERY = 3        // 공성 주기(일) — 커스텀 낮밤 기준 실시간 약 1시간에 1회
const GRAND_EVERY = 12       // 대공세 주기(일) — SIEGE_EVERY의 배수라 반드시 공성일에 겹친다
const REWARD_MULT = 1.5      // 보상 배율 — Ducat이 거래에도 쓰이므로 유입 상향
const GRAND_ESS = 2          // 대공세 격퇴 시 균열 정수
const HIGH_THREAT_ESS = 7    // 이 위협도 이상에서 일반 공성을 격퇴하면 정수를 준다
const HIGH_THREAT_ESS_AMT = 1
// 첫 공세 — 유물 없이 맨몸(철제 장비)으로 막는 밤.
// 이걸 버텨야 정수가 나오고, 그 정수로 제단에서 유물이 깨어난다(ls_relic.js).
// 그래서 첫 밤만 의도적으로 약하게 잡는다.
const FIRST_SIEGE_THREAT_CAP = 2  // 첫 공성에 쓰는 위협도 상한 (웨이브 1개 · 5기 수준)
const FIRST_SIEGE_ESS = 1         // 첫 공세 격퇴 시 각 참여자에게 주는 정수
const MAX_THREAT = 15        // 위협도 상한
const NODE_FLOOR = 2         // 노드 1개당 위협도 하한 (+2)
const FINALE_NIGHTS = 3      // 최종장: 연속 방어 밤 수 (마지막 밤 = 최종 보스)
const FINAL_BOSS_ID = 'bosses_of_mass_destruction:lich' // 어둠의 심장 (나이트 리치)
const NIGHT_T0 = 13200       // 보스 체력↔밤 진행 매핑: 시작(초저녁)
const NIGHT_T1 = 22800       //                       끝(새벽 직전)

// 보스 처치 시 균열 정수(kubejs:rift_essence) 드롭 수 = 마을 재건 재화 (콘텐츠 게이팅)
const ESSENCE_DROP = {
  'cataclysm:the_harbinger': 2, 'cataclysm:ignis': 3, 'cataclysm:netherite_monstrosity': 3,
  'cataclysm:ender_guardian': 3, 'cataclysm:the_leviathan': 3, 'cataclysm:ancient_remnant': 3,
  'cataclysm:maledictus': 3, 'cataclysm:scylla': 2,
  'bosses_of_mass_destruction:lich': 3, 'bosses_of_mass_destruction:obsidilith': 3,
  'bosses_of_mass_destruction:gauntlet': 2, 'bosses_of_mass_destruction:void_blossom': 2,
  'twilightforest:naga': 1, 'twilightforest:lich': 2, 'twilightforest:hydra': 2,
  'twilightforest:ur_ghast': 2, 'twilightforest:snow_queen': 2, 'twilightforest:minoshroom': 1,
  'twilightforest:knight_phantom': 1, 'twilightforest:alpha_yeti': 1, 'twilightforest:plateau_boss': 2,
  'aether:slider': 1, 'aether:valkyrie_queen': 2, 'aether:sun_spirit': 2,
  'mowziesmobs:ferrous_wroughtnaut': 1, 'mowziesmobs:frostmaw': 1, 'mowziesmobs:umvuthi': 1,
  'alexsmobs:void_worm': 2, 'undergarden:forgotten_guardian': 1, 'deeperdarker:stalker': 1,
  'minecraft:ender_dragon': 3, 'minecraft:wither': 2, 'minecraft:warden': 2
}

// ── 균열 노드 (어둠의 근원) ──
function nodeNames(server) { const s = sfGetS(server, 'ls_nodes_names'); return s ? s.split(',') : [] }
function nodeCount(server) { return nodeNames(server).length }
function threatFloor(server) {
  let f = nodeCount(server) * NODE_FLOOR
  if (townLvl(server, 'ramparts') >= 3) f -= 1 // 방벽 Lv3: 위협 하한 완화
  // 정화 봉화 (ls_beacon.js): 2기당 위협 하한 -1 — 영토 수복이 세상을 진정시킨다
  const pbCsv = String(sfStore(server).getString('pb_names') || '')
  const pbCount = pbCsv ? pbCsv.split(',').length : 0
  f -= Math.floor(pbCount / 2)
  return Math.max(0, Math.min(MAX_THREAT, f))
}

// ── 위협도 (노드 하한 반영) ──
function getThreat(server) { return sfGetI(server, 'ls_threat') }
function setThreat(server, v) {
  let nv = Math.max(threatFloor(server), Math.min(MAX_THREAT, v))
  if (nv < 0) nv = 0
  sfSetI(server, 'ls_threat', nv)
}
function getTreasury(server) { return LS.treasury(server) }
function addTreasury(server, amt) { LS.addTreasury(server, amt) }

// ── 성역 좌표는 모드(LSData)가 유일하게 소유한다 ──
// 예전엔 여기 persistentData 에도 한 벌 있었다. 두 벌이 있으니 한쪽만 갱신되는 일이
// 벌어졌고(귀환석 먹통), 그걸 메꾸는 동기화 코드가 또 조용히 죽었다.
// 이제 사본을 두지 않고 브릿지로 읽는다 — 어긋날 수 있는 두 번째 값이 아예 없다.
function sancIsSet(server) { return LS.hasSanctuary(server) }
function sancPos(server) { return { x: LS.sanctuaryX(server), y: LS.sanctuaryY(server), z: LS.sanctuaryZ(server) } }
// 성역 좌표는 **두 곳이 다 알아야 한다.**
// 공성(이 스크립트)은 persistentData 를 보고, 귀환석·전송석(모드)은 LSData 를 본다.
// 여기서 모드 쪽으로 밀어주지 않으면 공성은 멀쩡히 도는데 귀환석만 조용히 실패한다 —
// 게다가 실패 안내가 액션바로만 떠서 "아무 반응이 없다"로 보인다. 반드시 같이 쓴다.
// 모드에만 쓴다. persistentData 사본은 없앴다 — 두 곳에 쓰면 언젠가 한 곳만 쓰게 된다.
function setSanc(server, x, y, z) { LS.setSanctuary(server, x, y, z) }

// ── 성벽 내구도 — 공성의 판돈 ──
// 성벽 라인(반경) 안으로 공성 몹이 들어오려 하면 밖으로 밀려나며 성벽을 두드린다(HP 감소).
// HP 0 = 성벽 붕괴 → 더 이상 막지 못하고 몹이 안으로 쏟아진다(내부 백병전). 수리 전까지 뚫린 채 유지.
// 신호기 등 중심물은 장식 — 체력은 성벽 자체에 있다. 방벽(ramparts) 레벨 = 성벽 내구도.
const WALL_BASE_HP = 150      // 최대 HP = 150 + 방벽Lv×50
const WALL_DEFAULT_R = 24     // 성벽 반경 기본값 (실제 성벽에 맞춰 /wall radius 로 조정)
function wallR(server) { const v = sfGetI(server, 'wall_r'); return v > 0 ? v : WALL_DEFAULT_R }
// 방벽 레벨당 최대 내구도 +100 (기본 150 → 4레벨 550).
// 방벽 트랙의 가장 직관적인 보상이라 눈에 띄게 올린다.
function wallMax(server) { return WALL_BASE_HP + townLvl(server, 'ramparts') * 100 }
function wallHp(server) { const v = sfGetI(server, 'wall_hp'); return v > 0 ? Math.min(v, wallMax(server)) : (sfGetB(server, 'wall_init') ? 0 : wallMax(server)) }
function wallSetHp(server, v) { sfSetB(server, 'wall_init', true); sfSetI(server, 'wall_hp', Math.max(0, Math.min(wallMax(server), v))) }
function wallBroken(server) { return sfGetB(server, 'wall_init') && sfGetI(server, 'wall_hp') <= 0 }
function wallBar(server) {
  const hp = wallHp(server), mx = wallMax(server)
  const n = Math.max(0, Math.min(10, Math.round(hp * 10 / mx)))
  return `§6${'▮'.repeat(n)}§8${'▯'.repeat(10 - n)} §7${hp}/${mx}`
}
// 화면 상단 보스바 (엔더드래곤식) — 공성 중에만 표시
function wallBossbar(server, show) {
  const id = 'last_stardust:wall'
  if (!show) { server.runCommandSilent(`bossbar set ${id} visible false`); return }
  server.runCommandSilent(`bossbar add ${id} {"text":"성벽 내구도"}`) // 이미 있으면 조용히 실패 — 무해
  const hp = wallHp(server), mx = wallMax(server)
  const color = hp <= 0 ? 'red' : hp <= mx * 0.25 ? 'red' : hp <= mx * 0.5 ? 'yellow' : 'green'
  const label = hp <= 0 ? '▨ 성벽 붕괴 — 방어선 없음!' : `▨ 성벽 내구도 ${hp} / ${mx}`
  server.runCommandSilent(`bossbar set ${id} name {"text":"${label}","color":"${color === 'green' ? 'gold' : color}"}`)
  server.runCommandSilent(`bossbar set ${id} max ${Math.max(1, mx)}`)
  server.runCommandSilent(`bossbar set ${id} value ${Math.max(0, hp)}`)
  server.runCommandSilent(`bossbar set ${id} color ${color}`)
  server.runCommandSilent(`bossbar set ${id} style notched_10`)
  server.runCommandSilent(`bossbar set ${id} players @a`)
  server.runCommandSilent(`bossbar set ${id} visible true`)
}

// ── 최종장 (가장 긴 밤) ──
// ls_finale: 0=비활성 · 1..N-1=방어 밤 단계 · N=보스 밤 대기 · 90=보스 전투 중 · 100=승리(영구 평화)
function finaleStage(server) { return sfGetI(server, 'ls_finale') }
function setFinale(server, v) { sfSetI(server, 'ls_finale', v) }
let FINAL_BOSS_REF = null
let BOSS_LOST_SEC = 0

function beginFinale(server) {
  if (finaleStage(server) !== 0) return
  setFinale(server, 1)
  sfSetB(server, 'ls_true_spawned', false) // 진 형태 초기화
  setThreat(server, MAX_THREAT)
  sfStore(server).putInt('ls_nrate_pct', 70) // 1밤: 밤 길이 1.4배
  server.runCommandSilent('title @a title {"text":"가장 긴 밤","color":"dark_purple","bold":true}')
  server.runCommandSilent('title @a subtitle {"text":"근원을 잃은 어둠이 마지막 힘을 그러모은다","color":"red"}')
  playAll(server, 'minecraft:entity.wither.spawn', 1, 0.5)
  say(server, `§5☽ 최종장 개막 — §c${FINALE_NIGHTS}일 밤§7을 연속으로 버텨야 한다. 밤은 갈수록 길어진다...`)
  console.log('[LS-FINALE] begin')
}

function spawnFinalBoss(server) {
  const c = sancPos(server)
  try { server.runCommandSilent('enhancedcelestials setLunarEvent enhancedcelestials:blood_moon') } catch (err) { lsWarn('ls_siege:148', err) } // 마지막 밤 = 혈월
  server.runCommandSilent(`summon ${FINAL_BOSS_ID} ${c.x + 0.5} ${c.y + 12} ${c.z + 0.5} {Tags:["ls_final_boss"],PersistenceRequired:1b}`)
  setFinale(server, 90)
  sfStore(server).putBoolean('ls_time_locked', true) // 하늘이 멈춘다
  server.runCommandSilent('title @a title {"text":"어둠의 심장","color":"dark_red","bold":true}')
  server.runCommandSilent('title @a subtitle {"text":"놈이 죽기 전까지 아침은 오지 않는다","color":"gray"}')
  playAll(server, 'minecraft:entity.wither.spawn', 1, 0.4)
  playAll(server, 'minecraft:entity.ender_dragon.growl', 1, 0.5)
  say(server, '§4♥ 어둠의 심장이 강림했다. §7하늘이 멈췄다 — §c심장이 약해질수록 새벽이 가까워진다.')
  console.log('[LS-FINALE] boss spawned')
}

// 진(眞) 형태 — 1페이즈 격파 시 껍질을 벗고 강화형으로 재림 (더 강함 + 시간 재봉인)
function spawnTrueForm(server) {
  sfSetB(server, 'ls_true_spawned', true)
  const c = sancPos(server)
  server.runCommandSilent(`summon ${FINAL_BOSS_ID} ${c.x + 0.5} ${c.y + 10} ${c.z + 0.5} {Tags:["ls_final_boss_true"],PersistenceRequired:1b}`)
  server.runCommandSilent('effect give @e[tag=ls_final_boss_true] minecraft:strength 99999 1 true')
  server.runCommandSilent('effect give @e[tag=ls_final_boss_true] minecraft:speed 99999 0 true')
  FINAL_BOSS_REF = null // 틱 핸들러가 진 형태를 재탐색 (findFinalBoss가 ls_final_boss 부분일치로 잡음)
  server.runCommandSilent('title @a title {"text":"(진) 어둠의 심장","color":"dark_red","bold":true}')
  server.runCommandSilent('title @a subtitle {"text":"이것이 놈의 진짜 형태다 — 하늘이 다시 멈춘다","color":"red"}')
  playAll(server, 'minecraft:entity.ender_dragon.growl', 1, 0.4)
  playAll(server, 'minecraft:entity.wither.spawn', 1, 0.5)
  say(server, '§4☠ 쓰러진 줄 알았던 어둠이 껍질을 벗는다 — §c(진) 어둠의 심장 강림!')
  console.log('[LS-FINALE] TRUE FORM spawned')
}

function findFinalBoss(server) {
  let found = null
  try {
    server.getEntities().forEach(e => {
      if (!found && e && e.tags && (`${e.tags}`).includes('ls_final_boss') && e.isAlive()) found = e
    })
  } catch (err) { lsWarn('ls_siege:182', err) }
  return found
}

function finaleVictory(server) {
  setFinale(server, 100)
  setThreat(server, 0)
  sfSetB(server, 'ls_dawnbreak', true) // 여명 가속 시작
  sfStore(server).putInt('ls_nrate_pct', 0)
  server.runCommandSilent('title @a title {"text":"별빛이 돌아온다","color":"gold","bold":true}')
  server.runCommandSilent('title @a subtitle {"text":"긴 어둠이 끝났다 — 세상은 너희의 것이다","color":"yellow"}')
  playAll(server, 'minecraft:ui.toast.challenge_complete', 1, 1)
  playAll(server, 'minecraft:block.beacon.activate', 1, 1)
  say(server, '§6★ 어둠의 심장이 멎었다. §e공성은 영원히 끝났다 — Last Stardust, 세상을 되찾았다.')
  // 최종 승리 칭호 (ls_title.js — 공유 스코프)
  server.players.forEach(p => { try { ttGrant(server, p.username, 'night_lord') } catch (e) { lsWarn('ls_siege:197', e) } })
  console.log('[LS-FINALE] VICTORY')
}

function say(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
// 지표면 Y 감지 (지형 경사에서 몹이 땅속/공중에 박히는 것 방지)
function surfaceY(server, x, z, fallback) {
  return lsSurfaceY(server, x, z, fallback)   // ls_util.js — 셋에 복붙돼 있던 것을 합쳤다
}
// 가장 가까운 플레이어 (공성 몹 진격 타겟)
function nearestPlayer(server, x, z) {
  let best = null, bd = 1.0e18
  server.players.forEach(p => {
    const dx = p.x - x, dz = p.z - z, d = dx * dx + dz * dz
    if (d < bd) { bd = d; best = p }
  })
  return best
}
function playAll(server, sound, vol, pitch) {
  server.runCommandSilent(`execute as @a at @s run playsound ${sound} master @s ~ ~ ~ ${vol} ${pitch}`)
}
function totalTicks(server) { return Number(server.overworld().getDayTime()) }
function worldDay(server) { return Math.floor(totalTicks(server) / 24000) }
function dayTime(server) { return totalTicks(server) % 24000 }
function isNight(server) { const t = dayTime(server); return t >= 13000 && t <= 23000 }
function isGrandDay(server) { const d = worldDay(server); return d >= GRAND_EVERY && d % GRAND_EVERY === 0 }

// ── 첫 공세 ──
// 유물이 없는 맨몸 상태로 맞는 유일한 밤. 버텨내면 정수가 나오고 그것으로 제단에서 유물이 깨어난다.
function isFirstSiege(server) { return !sfGetB(server, 'ls_first_siege_done') }
// 웨이브 구성에 쓰는 유효 위협도 — 첫 공세만 상한을 씌운다
function effThreat(server) {
  const t = Math.max(1, getThreat(server))
  return isFirstSiege(server) ? Math.min(t, FIRST_SIEGE_THREAT_CAP) : t
}

// ── 공성일 판정 ──
// 매일 밤 공성은 커스텀 낮밤(하루 ≈ 18분) 기준으로 너무 잦다. 3일에 한 번 = 실시간 약 1시간.
function isSiegeDay(day) { return day > 0 && day % SIEGE_EVERY === 0 }
function daysToSiege(day) {
  if (isSiegeDay(day)) return 0
  let d = 1
  while (!isSiegeDay(day + d)) d++
  return d
}

// ── 몹 진화 단계 ──
function threatTier(threat) { return threat >= 10 ? 4 : threat >= 7 ? 3 : threat >= 4 ? 2 : 1 }
const TIER_NEWS = {
  2: '§6⚠ 어둠이 짙어진다... §7메마른 자들과 얼어붙은 궁수가 공세에 섞여든다.',
  3: '§c⚠ 균열 깊은 곳에서 낯선 놈들이 기어나온다... §7불붙은 망령과 시든 해골.',
  4: '§4⚠ 어둠의 정예가 모습을 드러냈다. §7광전사가 공세를 이끈다.'
}

// 공성 방향 (밤마다 1방향에서만 몰려옴 — 방어선 구축이 의미있어짐)
const DIR8_KO = ['북', '북동', '동', '남동', '남', '남서', '서', '북서']
function sgDirName(angDeg) {
  // 각도(수학각: 0=동, 반시계) → 마인크래프트 방위 텍스트
  const dx = Math.cos(angDeg * Math.PI / 180), dz = Math.sin(angDeg * Math.PI / 180)
  let a = Math.atan2(dx, -dz) * 180 / Math.PI
  if (a < 0) a += 360
  return DIR8_KO[Math.round(a / 45) % 8]
}

// 위협도 → 한 웨이브 몹 목록 (단계별 진화, grand=대공세 규모)
function buildWave(threat, grand) {
  const mobs = []
  const cap = grand ? 16 : 12
  const total = Math.min(cap, (grand ? 5 : 3) + threat)
  const tier = threatTier(threat)
  for (let i = 0; i < total; i++) {
    if (tier >= 4 && i % 6 === 0) mobs.push('cataclysm:ignited_berserker')
    else if (tier >= 3 && i % 5 === 0) mobs.push('cataclysm:ignited_revenant')
    else if (tier >= 3 && i % 4 === 1) mobs.push('minecraft:wither_skeleton')
    else if (tier >= 2 && i % 4 === 0) mobs.push('minecraft:husk')
    else if (tier >= 2 && i % 5 === 2) mobs.push('minecraft:stray')
    else if (tier >= 2 && i % 7 === 3) mobs.push('minecraft:vindicator') // 크리퍼 금지: mobGriefing ON이라 성역이 부서짐 → 약탈자 처형인으로 대체
    else if (i % 3 === 1) mobs.push('minecraft:skeleton')
    else if (i % 5 === 4) mobs.push('minecraft:spider')
    else mobs.push('minecraft:zombie')
  }
  return mobs
}

// 웨이브 하나 소환
function spawnWave(server, waveNo) {
  const threat = effThreat(server)
  const grand = sfGetB(server, 'ls_siege_grand')
  const c = sancPos(server)
  const wave = buildWave(threat, grand)
  const n = wave.length
  // 단일 방향 공성: 이번 공성의 진격 방향(±35° 부채꼴)에서만 스폰
  const baseDeg = sfGetI(server, 'ls_siege_ang')
  for (let i = 0; i < n; i++) {
    var spread = n > 1 ? (i / (n - 1)) * 70 - 35 : 0
    var ang = (baseDeg + spread) * Math.PI / 180
    var x = Math.floor(c.x + Math.cos(ang) * SPAWN_RING) + 0.5
    var z = Math.floor(c.z + Math.sin(ang) * SPAWN_RING) + 0.5
    var sy = surfaceY(server, x, z, c.y)
    server.runCommandSilent(`summon ${wave[i]} ${x} ${sy} ${z} {Tags:["ls_siege"],PersistenceRequired:1b,Glowing:1b}`)
  }
  sfSetI(server, 'ls_siege_remaining', n)
  sfSetI(server, 'ls_siege_wave_no', waveNo)
  if (waveNo === 1) {
    if (grand) {
      server.runCommandSilent('title @a title {"text":"\\u2620 대공세","color":"dark_red","bold":true}')
      server.runCommandSilent('title @a subtitle {"text":"어둠의 총력이 성역을 노립니다 — 전원 방어하세요!","color":"red"}')
      playAll(server, 'minecraft:event.raid.horn', 1, 0.6)
      playAll(server, 'minecraft:entity.wither.spawn', 0.8, 0.7)
      say(server, `§4☠ 대공세 시작! §7위협도 ${threat} · 웨이브 ${sfGetI(server, 'ls_siege_waves') + 1}개 · 첫 물결 ${n}기 §c(보상 2배)`)
    } else {
      server.runCommandSilent('title @a title {"text":"\\u2694 성역 공성!","color":"red","bold":true}')
      server.runCommandSilent('title @a subtitle {"text":"어둠이 성역으로 몰려옵니다 — 막아주세요!","color":"gold"}')
      playAll(server, 'minecraft:event.raid.horn', 1, 0.8)
      playAll(server, 'minecraft:entity.ender_dragon.growl', 0.6, 0.6)
      say(server, `§c⚔ 공성 시작! §7위협도 ${threat} · 웨이브 ${sfGetI(server, 'ls_siege_waves') + 1}개 · 첫 물결 ${n}기`)
    }
    // 몹 진화 단계 뉴스 (새 단계 첫 공성 때 1회)
    var tier = threatTier(threat)
    if (tier > sfGetI(server, 'ls_ann_tier')) {
      sfSetI(server, 'ls_ann_tier', tier)
      if (TIER_NEWS[tier]) say(server, TIER_NEWS[tier])
    }
  } else {
    server.runCommandSilent(`title @a subtitle {"text":"${waveNo}번째 물결이 몰려온다!","color":"gold"}`)
    playAll(server, 'minecraft:event.raid.horn', 0.7, 1.0)
    say(server, `§6▶ ${waveNo}번째 물결! §7적 ${n}기`)
  }
  console.log(`[LS-SIEGE] wave ${waveNo} spawned count=${n} threat=${threat} grand=${grand}`)
}

function startSiege(server, auto, forceGrand) {
  if (!sancIsSet(server)) { if (!auto) say(server, '§c성역이 지정되지 않았습니다. OP가 /sanctuary here 로 지정하세요.'); return 0 }
  if (sfGetB(server, 'ls_siege_active')) { if (!auto) say(server, '§7이미 공성이 진행 중입니다.'); return 0 }
  const threat = effThreat(server)
  const grand = !!forceGrand || isGrandDay(server)
  let wavesTotal = Math.min(MAX_WAVES, 1 + Math.floor(threat / 3))
  if (grand) wavesTotal = Math.min(MAX_WAVES + 1, wavesTotal + 2)
  if (grand && townLvl(server, 'ramparts') >= 4) wavesTotal = Math.max(1, wavesTotal - 1) // 방벽 Lv4: 대공세 웨이브 -1
  sfSetB(server, 'ls_siege_active', true)
  sfSetB(server, 'ls_siege_grand', grand)
  sfSetI(server, 'ls_siege_waves', wavesTotal - 1)
  sfSetI(server, 'ls_siege_reward', 0)
  // 진격 방향 롤 (이번 공성 내내 유지) + 예고
  sfSetI(server, 'wall_warn', 4) // 성벽 경보 단계 초기화
  const dirDeg = Math.floor(Math.random() * 360)
  sfSetI(server, 'ls_siege_ang', dirDeg)
  say(server, `§c⚑ 어둠의 진격 방향: §e${sgDirName(dirDeg)}쪽 §7— 그쪽 방어선에 집결해 주세요!`)
  if (wallBroken(server)) say(server, '§4▨ 성벽이 무너진 채다 — 오늘 밤 방어선이 없습니다! (/wall repair)')
  else say(server, `§6▨ 성벽 내구도 ${wallBar(server)}`)
  wallBossbar(server, true) // 상단 보스바 표시
  // 대공세 밤 = 혈월 (Enhanced Celestials 연동 — 하늘이 붉게 물든다)
  if (grand) {
    try { server.runCommandSilent('enhancedcelestials setLunarEvent enhancedcelestials:blood_moon') } catch (err) { lsWarn('ls_siege:354', err) }
  }
  spawnWave(server, 1)
  console.log(`[LS-SIEGE] start auto=${!!auto} threat=${threat} waves=${wavesTotal} grand=${grand}`)
  return 1
}

function onWaveCleared(server) {
  const threat = getThreat(server)
  sfSetI(server, 'ls_siege_reward', sfGetI(server, 'ls_siege_reward') + 5 + threat)
  const wavesLeft = sfGetI(server, 'ls_siege_waves')
  if (wavesLeft > 0) {
    sfSetI(server, 'ls_siege_waves', wavesLeft - 1)
    playAll(server, 'minecraft:ui.toast.challenge_complete', 0.5, 1.4)
    say(server, `§a물결 격퇴! §7다음 물결 대기...`)
    spawnWave(server, sfGetI(server, 'ls_siege_wave_no') + 1)
  } else {
    finishSiege(server, 'win')
  }
}

// 첫 공세를 살아남았다 → 유물이 깨어난다
function firstSiegeCleared(server) {
  sfSetB(server, 'ls_first_siege_done', true)
  server.players.forEach(p => {
    server.runCommandSilent(`give ${p.username} kubejs:rift_essence ${FIRST_SIEGE_ESS}`)
  })
  server.runCommandSilent('title @a title {"text":"유물이 깨어난다","color":"aqua","bold":true}')
  server.runCommandSilent('title @a subtitle {"text":"첫 밤을 버텨낸 자에게 별이 응답했다","color":"gray"}')
  playAll(server, 'minecraft:block.beacon.power_select', 1, 1.2)
  playAll(server, 'minecraft:ui.toast.challenge_complete', 1, 1)
  say(server, `§b✦ 첫 공세를 버텨냈다 — §d균열 정수 +${FIRST_SIEGE_ESS}§7씩 주어졌다.`)
  say(server, '§7   §e제단§7에 정수를 바쳐 당신의 유물을 깨우세요. §8(/relic 로 확인)')
  console.log('[LS-SIEGE] first siege cleared — relics unlocked')
}

function finishSiege(server, outcome) {
  sfSetB(server, 'wall_last_stand', false)   // 불굴은 공성 1회당 한 번 — 여기서 되감는다
  wallBossbar(server, false) // 상단 보스바 숨김
  server.runCommandSilent('kill @e[tag=ls_siege]')
  const wasFirst = isFirstSiege(server)
  const grand = sfGetB(server, 'ls_siege_grand')
  sfSetB(server, 'ls_siege_active', false)
  sfSetB(server, 'ls_siege_grand', false)
  sfSetI(server, 'ls_siege_remaining', 0)
  sfSetI(server, 'ls_siege_waves', 0)
  const threat = getThreat(server)
  const accrued = sfGetI(server, 'ls_siege_reward')
  const fin = finaleStage(server)
  if (outcome === 'win' || outcome === 'dawn') {
    if (fin >= 1 && fin < FINALE_NIGHTS) sfSetB(server, 'ls_fn_ok', true) // 최종장: 이 밤 방어 성공
  }
  if (outcome === 'win') {
    var reward = accrued + 10 + threat * 2
    if (grand) reward *= 2
    if (townLvl(server, 'workshop') >= 2) reward = Math.round(reward * 1.2) // 공방 Lv2: 방어 보상 +20%
    reward = Math.round(reward * REWARD_MULT)
    addTreasury(server, reward)
    // 공성이 3일에 한 번이므로 승리 한 번이 3일치 상승분을 되돌린다
    setThreat(server, threat - SIEGE_EVERY)
    if (!wallBroken(server)) wallSetHp(server, wallHp(server) + 15) // 승리 시 성벽 소폭 보수
    server.runCommandSilent(`title @a title {"text":"${grand ? '대공세 격퇴!' : '성역 방어 성공!'}","color":"green","bold":true}`)
    playAll(server, 'minecraft:ui.toast.challenge_complete', 1, 1)
    playAll(server, 'minecraft:entity.player.levelup', 0.7, 1.2)
    say(server, `§a✔ ${grand ? '대공세를 격퇴했다!' : '성역 방어 성공!'} §e공동 금고 +${reward} §7· 위협도↓(${getThreat(server)})`)
    // 공성 격퇴 = 반복 가능한 정수 공급처 (무기 각성과 마을 재건을 동시에 굴려야 하므로).
    // 대공세는 항상, 일반 공성은 고위협(HIGH_THREAT_ESS 이상)에서 완전 격퇴했을 때만 —
    // "위협도를 낮게 깔면 안전하지만 정수가 안 나온다"는 선택지를 만든다.
    var ess = grand ? GRAND_ESS : (threat >= HIGH_THREAT_ESS ? HIGH_THREAT_ESS_AMT : 0)
    if (ess > 0) {
      var gc = sancPos(server)
      server.runCommandSilent(`summon item ${gc.x + 0.5} ${gc.y + 1} ${gc.z + 0.5} {Item:{id:"kubejs:rift_essence",count:${ess}}}`)
      say(server, `§5✦ 균열 정수 +${ess} §7— ${grand ? '대공세를' : `위협도 ${threat}의 공세를`} 격퇴한 대가 (성역에 떨어졌다)`)
      playAll(server, 'minecraft:block.amethyst_block.chime', 0.9, 0.7)
    }
    console.log(`[LS-SIEGE] WIN reward=${reward} grand=${grand}`)
  } else if (outcome === 'dawn') {
    // accrued 는 const 라 재할당하지 않는다 — 'win' 분기가 reward 를 따로 두는 것과 같은 구조.
    // (예전엔 여기서 accrued 를 직접 덮어써 Rhino 에서 런타임 오류가 났다. dawn 은 흔한 경로라 매번 터졌다.)
    var dawnReward = Math.round(accrued * REWARD_MULT)
    addTreasury(server, dawnReward)
    server.runCommandSilent('title @a title {"text":"동이 텄습니다","color":"yellow"}')
    server.runCommandSilent('title @a subtitle {"text":"어둠이 물러갑니다 — 성역은 버텨냈습니다","color":"gold"}')
    playAll(server, 'minecraft:block.beacon.activate', 0.8, 1)
    say(server, `§e☀ 동이 텄습니다 — 성역은 밤을 버텨냈습니다. §7공동 금고 +${dawnReward}`)
    console.log(`[LS-SIEGE] DAWN reward=${dawnReward} (accrued=${accrued})`)
  } else { // give_up
    setThreat(server, threat + 1)
    addTreasury(server, -5)
    server.runCommandSilent('title @a title {"text":"성역이 밀렸다...","color":"dark_red"}')
    playAll(server, 'minecraft:entity.ravager.roar', 1, 0.7)
    playAll(server, 'minecraft:block.bell.resonate', 1, 0.5)
    say(server, `§4성역이 밀렸다... §c위협도↑(${getThreat(server)}) · 공동 금고 -5`)
    console.log(`[LS-SIEGE] GIVE_UP`)
  }
  // 첫 공세는 "버텨내기만" 하면 된다(격퇴/아침) — 유물 해금이 여기 걸려 있어 밀리면 안 되기 때문
  if (wasFirst && (outcome === 'win' || outcome === 'dawn')) {
    firstSiegeCleared(server)
  }
}

function cancelSiege(server) {
  wallBossbar(server, false)
  server.runCommandSilent('kill @e[tag=ls_siege]')
  sfSetB(server, 'ls_siege_active', false)
  sfSetB(server, 'ls_siege_grand', false)
  sfSetI(server, 'ls_siege_remaining', 0)
  sfSetI(server, 'ls_siege_waves', 0)
  say(server, '§7공성이 취소되었습니다. (페널티 없음)')
  console.log('[LS-SIEGE] cancel')
}

// ── 공성 몹 진격 AI: 스폰 즉시 가장 가까운 플레이어를 타겟 ──
EntityEvents.spawned(event => {
  const e = event.entity
  if (!e || !e.tags) return
  if (!(`${e.tags}`).includes('ls_siege')) return
  // ※ try 안에서는 const/let 금지 (Rhino 가 'redeclaration of var' 로 터진다).
  //   특히 같은 파일의 다른 try 블록이 같은 이름을 쓰면 확실히 터진다 — 아래 재타겟팅과 겹쳤다.
  var tgt = null
  try {
    tgt = nearestPlayer(e.server, e.x, e.z)
    if (tgt) e.setTarget(tgt)
  } catch (err) { lsWarn('ls_siege:477', err) }
})

// ── 공성 몹 사망 추적 ──
EntityEvents.death(event => {
  const e = event.entity
  if (!e) return
  const srv = e.server
  // 보스 처치 → 균열 정수 드롭 (마을 재건 재화)
  if (srv) {
    var ec = ESSENCE_DROP[String(e.type)]
    if (ec) {
      srv.runCommandSilent(`summon item ${e.x} ${e.y + 0.5} ${e.z} {Item:{id:"kubejs:rift_essence",count:${ec}}}`)
      say(srv, `§5✦ 균열 정수 +${ec} §7— ${e.type} 격파 (마을 재건 재화)`)
    }
  }
  if (!e.tags) return
  const tagStr = `${e.tags}`
  // 진(眞) 형태 사망 = 진짜 승리 (부분문자열 주의: true 태그를 먼저 검사)
  if (tagStr.includes('ls_final_boss_true')) {
    if (srv && finaleStage(srv) === 90) finaleVictory(srv)
    return
  }
  if (tagStr.includes('ls_final_boss')) {
    if (srv && finaleStage(srv) === 90) {
      if (!sfGetB(srv, 'ls_trueform_off') && !sfGetB(srv, 'ls_true_spawned')) spawnTrueForm(srv)
      else finaleVictory(srv)
    }
    return
  }
  if (!tagStr.includes('ls_siege')) return
  const server = e.server
  if (!server || !sfGetB(server, 'ls_siege_active')) return
  let rem = sfGetI(server, 'ls_siege_remaining') - 1
  if (rem < 0) rem = 0
  sfSetI(server, 'ls_siege_remaining', rem)
  if (rem <= 0) onWaveCleared(server)
})

// ── 매일 처리: 노드 에스컬레이션 + 대공세 예고 ──
function onNewDay(server, day) {
  if (finaleStage(server) > 0) { console.log(`[LS-SIEGE] day=${day} (finale stage ${finaleStage(server)})`); return } // 최종장 중엔 일일 변화 정지
  const nodes = nodeCount(server)
  const threat = getThreat(server)
  // 노드가 살아있으면 어둠이 자란다; 전부 파괴됐으면 세상이 진정된다
  if (nodes > 0) {
    if (threat < MAX_THREAT) {
      setThreat(server, threat + 1)
      say(server, `§5균열이 어둠을 뿜어낸다... §7위협도 ${getThreat(server)} (활성 노드 ${nodes})`)
    }
  } else if (threat > 0) {
    setThreat(server, threat - 1)
    if (getThreat(server) < threat) say(server, `§b세상이 조금씩 숨을 되찾는다. §7위협도 ${getThreat(server)}`)
  }
  // 몹 진화 카운트다운 — 에스컬레이션을 데드라인으로 (노드가 살아있어 위협이 오르는 중일 때만)
  if (nodes > 0) {
    var th = getThreat(server)
    var NEXT_TIER_AT = { 1: 4, 2: 7, 3: 10 } // 현재 단계 → 다음 진화 위협도
    var nextAt = NEXT_TIER_AT[threatTier(th)]
    if (nextAt && th < nextAt) {
      var dleft = nextAt - th // 노드 활성 시 위협 +1/일
      if (dleft <= 2) {
        var preview = { 4: '메마른 자들이 섞여들 것이다', 7: '불붙은 망령이 깨어날 것이다', 10: '어둠의 정예가 출정할 것이다' }[nextAt]
        say(server, `§5⌛ 어둠의 진화 §cD-${dleft} §7— ${preview}... (위협도 ${th}/${nextAt})`)
      }
    }
  }
  // 공방 Lv3: 자동 생산 라인 — 매일 새벽 공동 금고 적립
  if (townLvl(server, 'workshop') >= 3) { addTreasury(server, 50); say(server, '§e⚙ 자동 생산 라인 가동 — 공동 금고 +50') }
  // 대공세 예고 (위협이 존재할 때만)
  if (getThreat(server) > 0 || nodes > 0) {
    var rem = day % GRAND_EVERY
    if (day >= GRAND_EVERY - 3 && rem === GRAND_EVERY - 3) {
      say(server, '§4☠ 사흘 뒤, 어둠이 총공세를 준비하고 있다... §7(대공세 D-3 — 방어를 강화하라)')  // GRAND_EVERY의 배수일에 발생
      playAll(server, 'minecraft:entity.wither.ambient', 0.5, 0.8)
    } else if (day >= GRAND_EVERY - 1 && rem === GRAND_EVERY - 1) {
      say(server, '§4☠ 내일 밤 — 대공세가 옵니다. §c마지막 준비를 마쳐주세요.')
      playAll(server, 'minecraft:entity.wither.ambient', 0.7, 0.7)
    } else if (day >= GRAND_EVERY && rem === 0) {
      say(server, '§4☠ 오늘 밤, 대공세입니다. §c성역에 집결하세요. (보상 2배)')
      playAll(server, 'minecraft:event.raid.horn', 0.8, 0.5)
    }
  }
  console.log(`[LS-SIEGE] day=${day} nodes=${nodes} threat=${getThreat(server)}`)
}

// ── 상단 상시 표시: 며칠차 · 다음 공성 · 위협도 ──
// 커스텀 낮밤이라 체감으로 날짜를 세기 어렵다 — 항상 보이게 띄운다.
let DAY_BAR_LAST = ''
function dayBossbar(server) {
  const id = 'last_stardust:day'
  const day = worldDay(server)
  const th = getThreat(server)
  const fin = finaleStage(server)
  let label, color, val, max
  if (fin === 100) {
    label = `★ ${day}일차 — 별빛이 돌아왔다`; color = 'yellow'; val = 1; max = 1
  } else if (fin > 0) {
    label = `☽ ${day}일차 · 최종장 — 가장 긴 밤`; color = 'purple'; val = 1; max = 1
  } else {
    var left = daysToSiege(day)
    var grand = isSiegeDay(day) && day % GRAND_EVERY === 0
    max = SIEGE_EVERY; val = SIEGE_EVERY - left
    if (sfGetB(server, 'ls_siege_active')) {
      label = `⚔ ${day}일차 · ${sfGetB(server, 'ls_siege_grand') ? '대공세' : '공성'} 진행 중 · 위협도 ${th}`; color = 'red'
    } else if (left === 0) {
      label = `⚔ ${day}일차 · 오늘 밤 ${grand ? '대공세' : '공성'} · 위협도 ${th}`; color = 'red'
    } else {
      label = `☾ ${day}일차 · 다음 공성 D-${left} · 위협도 ${th}`; color = 'white'
    }
  }
  const sig = label + '|' + color + '|' + val + '/' + max
  if (sig === DAY_BAR_LAST) { server.runCommandSilent(`bossbar set ${id} players @a`); return }
  DAY_BAR_LAST = sig
  server.runCommandSilent(`bossbar add ${id} {"text":"날짜"}`) // 이미 있으면 조용히 실패 — 무해
  server.runCommandSilent(`bossbar set ${id} name {"text":"${label}"}`)
  server.runCommandSilent(`bossbar set ${id} color ${color}`)
  server.runCommandSilent(`bossbar set ${id} max ${max}`)
  server.runCommandSilent(`bossbar set ${id} value ${val}`)
  server.runCommandSilent(`bossbar set ${id} style ${max > 1 ? 'notched_' + max : 'progress'}`)
  server.runCommandSilent(`bossbar set ${id} players @a`)
  server.runCommandSilent(`bossbar set ${id} visible true`)
}

// ── 매초 틱: 날짜 변화 + 밤 자동 공성 + 새벽 판정 + 경보음 ──
let LS_TICK = 0
ServerEvents.tick(event => {
  LS_TICK++
  if (LS_TICK % 20 !== 0) return // 1초마다
  const server = event.server

  // 날짜 변화 감지
  const day = worldDay(server)
  if (day !== sfGetI(server, 'ls_day')) {
    sfSetI(server, 'ls_day', day)
    onNewDay(server, day)
  }

  const night = isNight(server)
  const wasNight = sfGetB(server, 'ls_night')
  sfSetB(server, 'ls_night', night)
  const active = sfGetB(server, 'ls_siege_active')
  const fin = finaleStage(server)

  // 밤 시작
  if (night && !wasNight) {
    if (fin >= 1 && fin < FINALE_NIGHTS && !active && sancIsSet(server)) {
      sfSetB(server, 'ls_fn_ok', false)
      say(server, `§5☽ 최종장 ${fin}번째 밤이 내린다...`)
      startSiege(server, true, true) // 강제 대공세급
    } else if (fin === FINALE_NIGHTS && sancIsSet(server)) {
      say(server, '§4☽ 가장 긴 밤이 시작된다.')
      spawnFinalBoss(server)
    } else if (fin === 0 && !active && sancIsSet(server) && getThreat(server) > 0 && isSiegeDay(worldDay(server))) {
      startSiege(server, true)
    }
  }
  // 새벽
  if (!night && wasNight) {
    if (sfGetB(server, 'ls_siege_active')) finishSiege(server, 'dawn')
    if (fin >= 1 && fin < FINALE_NIGHTS) {
      if (sfGetB(server, 'ls_fn_ok')) {
        var next = fin + 1
        setFinale(server, next)
        sfSetB(server, 'ls_fn_ok', false)
        if (next === FINALE_NIGHTS) {
          sfStore(server).putInt('ls_nrate_pct', 40) // 마지막 밤: 2.5배 길이 (보스가 시간을 쥔다)
          say(server, '§4☽ 다음 밤이 마지막이다 — 어둠의 심장이 온다. §c만반의 준비를 해주세요.')
          playAll(server, 'minecraft:entity.wither.ambient', 0.8, 0.6)
        } else {
          sfStore(server).putInt('ls_nrate_pct', 55) // 2밤: 1.8배 길이
          say(server, `§a${fin}번째 밤을 버텨냈다. §7다음 밤은 더 길다...`)
        }
      } else {
        say(server, '§c어둠은 물러서지 않았다... §7같은 밤을 다시 버텨야 한다.')
      }
    }
  }
  // 상단 날짜/공성 표시 (2초마다)
  if (LS_TICK % 40 === 0) dayBossbar(server)

  // 공성 중 경보음 (15초마다)
  // ※ 예전엔 warden.heartbeat 였는데, 공성에 워든이 실제로 섞여 나온다(위협도 표 참고).
  //    그래서 워든을 잡고도 심장 소리가 계속 나면 "안 죽은 건가?" 하고 헷갈렸다.
  //    워든과 무관한 소리로 바꾼다 — 무거운 발소리 쪽이 "몰려온다"는 뜻도 더 맞다.
  if (sfGetB(server, 'ls_siege_active') && LS_TICK % 300 === 0) {
    playAll(server, 'minecraft:entity.ravager.step', 0.7, 0.6)
  }
  // 공성 중: 상단 보스바 갱신 (2초마다 — 피해/수리 실시간 반영)
  if (sfGetB(server, 'ls_siege_active') && LS_TICK % 40 === 0) wallBossbar(server, true)
  // 공성 중: 성벽 방어선 판정 (2초마다) — 성벽이 서 있는 동안 몹은 못 들어오고, 대신 성벽을 두드린다
  if (sfGetB(server, 'ls_siege_active') && sancIsSet(server) && LS_TICK % 40 === 0 && !wallBroken(server)) {
    var wc = sancPos(server)
    var R = wallR(server)
    var banging = 0
    try {
      server.getEntities().forEach(e => {
        if (!e || !e.tags || !(`${e.tags}`).includes('ls_siege') || !e.isAlive()) return
        const sfDx = e.x - wc.x, sfDz = e.z - wc.z   // 고유 접두사 (Rhino 재선언 함정)
        const d2 = sfDx * sfDx + sfDz * sfDz
        if (d2 < R * R) {
          // 성벽 안으로 침입 시도 → 밖으로 밀려남 (성벽을 두드리는 연출)
          banging++
          var d = Math.max(1, Math.sqrt(d2))
          var ox = wc.x + (sfDx / d) * (R + 2), oz = wc.z + (sfDz / d) * (R + 2)
          var oy = surfaceY(server, ox, oz, Math.floor(e.y))
          try { server.runCommandSilent(`tp ${e.getUuid()} ${ox.toFixed(1)} ${oy} ${oz.toFixed(1)}`) } catch (e2) { lsWarn('ls_siege:680', e2) }
          server.runCommandSilent(`particle minecraft:block minecraft:stone ${ox.toFixed(1)} ${oy + 1} ${oz.toFixed(1)} 0.4 0.6 0.4 0.1 12`)
        }
      })
    } catch (err) { lsWarn('ls_siege:684', err) }
    // ── 불굴 유지 판정 ──
    // 버티는 동안엔 아무리 두들겨도 HP 가 1 밑으로 내려가지 않는다.
    // 시간이 끝나면 그 순간 무너진다 — 버틴 15초 안에 손을 못 썼다는 뜻이니까.
    var standing = sfGetB(server, 'wall_last_stand') && LS_TICK < sfGetI(server, 'wall_stand_until')
    if (sfGetB(server, 'wall_last_stand') && !standing && wallHp(server) <= 1) {
      wallSetHp(server, 0)
      sfSetB(server, 'wall_last_stand', false)
      server.runCommandSilent('title @a title {"text":"성벽 붕괴!","color":"dark_red","bold":true}')
      server.runCommandSilent('title @a subtitle {"text":"버텨낼 시간이 끝났습니다","color":"red"}')
      playAll(server, 'minecraft:entity.generic.explode', 1, 0.5)
      playAll(server, 'minecraft:event.raid.horn', 1, 0.5)
      say(server, '§4▨ 성벽이 끝내 무너졌다.')
    }

    if (banging > 0) {
      if (standing) {
        // 버티는 중 — 피해 대신 불꽃만 튄다
        wallSetHp(server, 1)
        if (LS_TICK % 80 === 0) playAll(server, 'minecraft:block.anvil.land', 0.5, 1.6)
      } else
      wallSetHp(server, wallHp(server) - Math.min(banging, 6))
      playAll(server, 'minecraft:entity.zombie.attack_iron_door', 0.8, 0.7)
      var hp = wallHp(server), mx = wallMax(server)
      var stage = hp <= mx * 0.25 ? 1 : hp <= mx * 0.5 ? 2 : hp <= mx * 0.75 ? 3 : 4
      if (stage < sfGetI(server, 'wall_warn')) {
        sfSetI(server, 'wall_warn', stage)
        if (stage === 3) say(server, `§6▨ 성벽이 공격받고 있다! ${wallBar(server)}`)
        if (stage === 2) { say(server, `§c▨ 성벽에 금이 간다! ${wallBar(server)} §c— 성벽 밖의 적을 처치해 주세요!`); playAll(server, 'minecraft:block.bell.use', 1, 0.6) }
        if (stage === 1) { server.runCommandSilent('title @a title {"text":"▨ 성벽 위기","color":"red","bold":true}'); playAll(server, 'minecraft:entity.wither.hurt', 1, 0.5) }
      }
      // 방벽 Lv4「불굴의 성벽」— 무너지기 직전 HP 1 로 버티며 15초를 번다.
      // 그 15초가 "밖의 적을 정리하고 보수할 마지막 기회"다. 한 번의 공성에 한 번만 발동한다.
      if (hp <= 0 && townLvl(server, 'ramparts') >= 4 && !sfGetB(server, 'wall_last_stand')) {
        sfSetB(server, 'wall_last_stand', true)
        sfSetI(server, 'wall_stand_until', LS_TICK + 300)   // 15초
        wallSetHp(server, 1)
        server.runCommandSilent('title @a title {"text":"▨ 불굴의 성벽","color":"gold","bold":true}')
        server.runCommandSilent('title @a subtitle {"text":"15초간 무너지지 않는다 — 지금 밖을 정리하라","color":"yellow"}')
        playAll(server, 'minecraft:block.beacon.activate', 1, 0.7)
        playAll(server, 'minecraft:item.totem.use', 0.9, 1.2)
        say(server, '§6▨ 불굴의 성벽이 버틴다! §e15초 §7— 그 안에 적을 밀어내고 보수하라.')
      } else if (hp <= 0) {
        server.runCommandSilent('title @a title {"text":"성벽 붕괴!","color":"dark_red","bold":true}')
        server.runCommandSilent('title @a subtitle {"text":"적들이 안으로 쏟아져 들어옵니다!","color":"red"}')
        playAll(server, 'minecraft:entity.generic.explode', 1, 0.5)
        playAll(server, 'minecraft:event.raid.horn', 1, 0.5)
        say(server, `§4▨ 성벽이 무너졌다! §c적들이 성역 안으로 몰려온다 — 수리(/wall repair) 전까지 방어선이 없습니다.`)
      }
    }
  }
  // 공성 중 마을 발전 효과 (성역 반경 내)
  if (sfGetB(server, 'ls_siege_active') && sancIsSet(server)) {
    var c = sancPos(server)
    // 방벽 Lv1: 결계 — 재생+저항 (3초마다)
    if (townLvl(server, 'ramparts') >= 1 && LS_TICK % 60 === 0) {
      server.runCommandSilent(`execute positioned ${c.x} ${c.y} ${c.z} run effect give @a[distance=..${SANCTUARY_RADIUS}] minecraft:regeneration 4 0 true`)
      server.runCommandSilent(`execute positioned ${c.x} ${c.y} ${c.z} run effect give @a[distance=..${SANCTUARY_RADIUS}] minecraft:resistance 4 0 true`)
    }
    // 방벽 Lv2: 자동 방어 포화 — 가장 가까운 공성 몹 자동 사격 (2초마다)
    // 방벽 Lv3「방벽 강화」는 이 포화를 키운다: 피해 6→14, 동시 표적 2→4.
    // 새 시스템을 얹지 않고 이미 있는 것을 강하게 만드는 쪽이라, 2단계를 지은 보람이 이어진다.
    if (townLvl(server, 'ramparts') >= 2 && LS_TICK % 40 === 0) {
      var up = townLvl(server, 'ramparts') >= 3
      var tdmg = up ? 14 : 6
      var tcount = up ? 4 : 2
      server.runCommandSilent(`execute positioned ${c.x} ${c.y} ${c.z} as @e[tag=ls_siege,sort=nearest,limit=${tcount}] run damage @s ${tdmg} minecraft:magic`)
      server.runCommandSilent(`execute positioned ${c.x} ${c.y} ${c.z} at @e[tag=ls_siege,sort=nearest,limit=1] run playsound minecraft:entity.arrow.shoot master @a ~ ~ ~ ${up ? 0.8 : 0.5} ${up ? 1.1 : 1.4}`)
    }
    // 성소 Lv2: 경험의 제단 — 성역 내부 경험치 (10초마다)
    if (townLvl(server, 'sanctum') >= 2 && LS_TICK % 200 === 0) {
      server.runCommandSilent(`execute positioned ${c.x} ${c.y} ${c.z} run xp add @a[distance=..${SANCTUARY_RADIUS}] 6 points`)
    }
  }
  // 공성 중 진격 유지: 타겟 잃은 몹 재타겟팅 (10초마다)
  if (sfGetB(server, 'ls_siege_active') && LS_TICK % 200 === 0) {
    try {
      server.getEntities().forEach(e => {
        if (e && e.tags && (`${e.tags}`).includes('ls_siege') && e.isAlive() && !e.getTarget()) {
          var near = nearestPlayer(server, e.x, e.z)
          if (near) e.setTarget(near)
        }
      })
    } catch (err) { lsWarn('ls_siege:767', err) }
  }
  // 보스 전투: 체력 ↔ 밤 진행 (하늘이 보스 체력바)
  if (fin === 90) {
    var boss = FINAL_BOSS_REF
    if (!boss || !boss.isAlive()) { boss = findFinalBoss(server); FINAL_BOSS_REF = boss }
    if (boss) {
      BOSS_LOST_SEC = 0
      var frac = Math.max(0, Math.min(1, boss.getHealth() / boss.getMaxHealth()))
      var target = NIGHT_T0 + Math.floor((1 - frac) * (NIGHT_T1 - NIGHT_T0))
      var cur = dayTime(server)
      if (cur < target) server.runCommandSilent(`time add ${Math.min(target - cur, 240)}`)
      // 최종장 보스 심박 — 여기도 워든 소리를 피한다(위 경보음과 같은 이유)
      if (LS_TICK % 300 === 0) playAll(server, 'minecraft:entity.ravager.step', 0.9, 0.5)
    } else {
      BOSS_LOST_SEC++
      if (BOSS_LOST_SEC >= 12) { BOSS_LOST_SEC = 0; say(server, '§5흩어졌던 어둠이 다시 뭉친다...'); spawnFinalBoss(server) }
    }
  }
  // 승리 후 여명 가속
  if (sfGetB(server, 'ls_dawnbreak')) {
    var curDawn = dayTime(server)
    if (curDawn >= 23600 || curDawn < 12000) {
      sfSetB(server, 'ls_dawnbreak', false)
      sfStore(server).putBoolean('ls_time_locked', false)
      playAll(server, 'minecraft:block.beacon.activate', 1, 1.4)
    } else {
      server.runCommandSilent('time add 400')
    }
  }
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event

  event.register(Commands.literal('sanctuary')
    .then(Commands.literal('here').requires(s => s.hasPermission(2)).executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만 사용 가능')); return 0 }
      setSanc(ctx.source.server, Math.floor(p.x), Math.floor(p.y), Math.floor(p.z))
      ctx.source.sendSystemMessage(Text.of(`§a성역 중심 지정: §e${Math.floor(p.x)}, ${Math.floor(p.y)}, ${Math.floor(p.z)} §7(반경 ${SANCTUARY_RADIUS})`))
      return 1
    }))
    .then(Commands.literal('info').executes(ctx => {
      const s = ctx.source.server
      if (!sancIsSet(s)) { ctx.source.sendSystemMessage(Text.of('§7성역 미지정 — /sanctuary here')); return 1 }
      const c = sancPos(s)
      ctx.source.sendSystemMessage(Text.of(`§6성역: §e${c.x},${c.y},${c.z} §7· 위협도 ${getThreat(s)} · 노드 ${nodeCount(s)} · 금고 ${getTreasury(s)}`))
      return 1
    })))

  event.register(Commands.literal('wall')
    .executes(ctx => {
      const s = ctx.source.server
      ctx.source.sendSystemMessage(Text.of(`§6▨ 성벽 내구도 ${wallBar(s)} §8(반경 ${wallR(s)} · 최대 ${wallMax(s)} = 기본 ${WALL_BASE_HP} + 방벽Lv×50)`))
      if (wallBroken(s)) ctx.source.sendSystemMessage(Text.of('§c   붕괴됨 — 공성 몹이 그대로 들어온다. /wall repair <n> (1HP = 2 Ducat)'))
      return 1
    })
    .then(Commands.literal('repair').then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
      const s = ctx.source.server
      const want = Math.max(1, Arguments.INTEGER.getResult(ctx, 'n'))
      const missing = wallMax(s) - wallHp(s)
      if (missing <= 0) { ctx.source.sendSystemMessage(Text.of('§7성벽은 온전합니다.')); return 0 }
      const amt = Math.min(want, missing)
      const cost = amt * 2
      const tre = getTreasury(s)
      if (tre < cost) { ctx.source.sendSystemMessage(Text.of(`§c공동 금고 부족: ${tre}/${cost} Ducat (1HP=2)`)); return 0 }
      addTreasury(s, -cost)
      const wasBroken = wallBroken(s)
      wallSetHp(s, wallHp(s) + amt)
      say(s, `§6▨ 성벽 보수 +${amt} §7(-${cost} Ducat) ${wallBar(s)}${wasBroken && !wallBroken(s) ? ' §a— 방어선 복구!' : ''}`)
      playAll(s, 'minecraft:block.anvil.use', 0.8, 1)
      return 1
    })))
    .then(Commands.literal('radius').requires(s => s.hasPermission(2)).then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
      const n = Math.max(8, Math.min(60, Arguments.INTEGER.getResult(ctx, 'n')))
      sfSetI(ctx.source.server, 'wall_r', n)
      ctx.source.sendSystemMessage(Text.of(`§a성벽 반경 = ${n} §7(실제 성벽 크기에 맞춰 조정)`)); return 1
    })))
    .then(Commands.literal('set').requires(s => s.hasPermission(2)).then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
      wallSetHp(ctx.source.server, Arguments.INTEGER.getResult(ctx, 'n'))
      ctx.source.sendSystemMessage(Text.of(`§a성벽 HP = ${wallHp(ctx.source.server)}`)); return 1
    }))))

  event.register(Commands.literal('threat')
    .then(Commands.literal('get').executes(ctx => {
      const s = ctx.source.server
      ctx.source.sendSystemMessage(Text.of(`§6위협도: §e${getThreat(s)} §7(하한 ${threatFloor(s)} · 상한 ${MAX_THREAT})`)); return 1
    }))
    .then(Commands.literal('set').requires(s => s.hasPermission(2)).then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
      setThreat(ctx.source.server, Arguments.INTEGER.getResult(ctx, 'n'))
      ctx.source.sendSystemMessage(Text.of(`§a위협도 = ${getThreat(ctx.source.server)}`)); return 1
    })))
    .then(Commands.literal('add').requires(s => s.hasPermission(2)).then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
      setThreat(ctx.source.server, getThreat(ctx.source.server) + Arguments.INTEGER.getResult(ctx, 'n'))
      ctx.source.sendSystemMessage(Text.of(`§a위협도 = ${getThreat(ctx.source.server)}`)); return 1
    }))))

  event.register(Commands.literal('riftnode')
    .executes(ctx => {
      const s = ctx.source.server; const names = nodeNames(s)
      ctx.source.sendSystemMessage(Text.of(`§5균열 노드: §e${names.length}개 활성 §7${names.length ? '(' + names.join(', ') + ')' : '(세상이 잠잠하다)'} · 위협 하한 ${threatFloor(s)}`))
      return 1
    })
    .then(Commands.literal('list').executes(ctx => {
      const s = ctx.source.server; const names = nodeNames(s)
      ctx.source.sendSystemMessage(Text.of(`§5균열 노드 ${names.length}개: §7${names.join(', ') || '없음'}`))
      return 1
    }))
    .then(Commands.literal('add').requires(s => s.hasPermission(2)).then(Commands.argument('name', Arguments.STRING.create(event)).executes(ctx => {
      const s = ctx.source.server; const name = Arguments.STRING.getResult(ctx, 'name')
      const names = nodeNames(s)
      if (names.indexOf(name) >= 0) { ctx.source.sendSystemMessage(Text.of('§c이미 있는 노드 이름')); return 0 }
      names.push(name)
      sfSetS(s, 'ls_nodes_names', names.join(','))
      setThreat(s, getThreat(s)) // 하한 재적용
      say(s, `§5⚠ 균열 노드 관측: §d${name} §7— 어둠이 짙어진다 (활성 ${names.length}개)`)
      playAll(s, 'minecraft:block.end_portal.spawn', 0.6, 0.5)
      return 1
    })))
    .then(Commands.literal('remove').requires(s => s.hasPermission(2)).then(Commands.argument('name', Arguments.STRING.create(event))
      .suggests((ctx, b) => { nodeNames(ctx.source.server).forEach(x => b.suggest(x)); return b.buildFuture() })
      .executes(ctx => {
        const s = ctx.source.server; const name = Arguments.STRING.getResult(ctx, 'name')
        const names = nodeNames(s)
        const i = names.indexOf(name)
        if (i < 0) { ctx.source.sendSystemMessage(Text.of('§c없는 노드 이름')); return 0 }
        names.splice(i, 1)
        sfSetS(s, 'ls_nodes_names', names.join(','))
        const nodeReward = Math.round((30 + (townLvl(s, 'sanctum') >= 3 ? 30 : 0)) * REWARD_MULT) // 성소 Lv3: 노드 보상 강화
        addTreasury(s, nodeReward)
        const nodeEss = townLvl(s, 'sanctum') >= 3 ? 3 : 2
        const nc = sancPos(s)
        s.runCommandSilent(`summon item ${nc.x + 0.5} ${nc.y + 1} ${nc.z + 0.5} {Item:{id:"kubejs:rift_essence",count:${nodeEss}}}`)
        say(s, `§5✦ 균열 정수 +${nodeEss} §7— 어둠의 근원이 응결됐다 (성역에 떨어졌다)`)
        say(s, `§b✔ 균열 노드 파괴: §d${name} §7— 세상이 숨을 돌린다 (남은 ${names.length}개) §e공동 금고 +${nodeReward}`)
        playAll(s, 'minecraft:ui.toast.challenge_complete', 1, 0.8)
        playAll(s, 'minecraft:block.beacon.activate', 0.8, 1.2)
        // 마지막 노드 파괴 + 최종장 무장 상태 → 어둠의 발악 개막
        if (names.length === 0 && sfGetB(s, 'ls_finale_armed') && finaleStage(s) === 0) beginFinale(s)
        return 1
      }))))

  event.register(Commands.literal('finale')
    .executes(ctx => {
      const s = ctx.source.server; const f = finaleStage(s)
      const txt = f === 0 ? (sfGetB(s, 'ls_finale_armed') ? '대기 중 (마지막 노드 파괴 시 개막)' : '비활성 (/finale arm 으로 무장)')
        : f === 100 ? '§6승리 — 세상을 되찾았다 ★'
        : f === 90 ? '§4어둠의 심장 전투 중 (하늘 = 보스 체력)'
        : f === FINALE_NIGHTS ? `§5보스 밤 대기 (오늘 밤 강림)` : `§5방어 밤 ${f}/${FINALE_NIGHTS - 1} 진행 중`
      ctx.source.sendSystemMessage(Text.of(`§5최종장: §r${txt}`))
      return 1
    })
    .then(Commands.literal('arm').requires(s => s.hasPermission(2)).executes(ctx => {
      sfSetB(ctx.source.server, 'ls_finale_armed', true)
      ctx.source.sendSystemMessage(Text.of('§5최종장 무장됨 — 마지막 균열 노드가 파괴되면 자동 개막')); return 1
    }))
    .then(Commands.literal('start').requires(s => s.hasPermission(2)).executes(ctx => {
      if (finaleStage(ctx.source.server) !== 0) { ctx.source.sendSystemMessage(Text.of('§c이미 최종장 진행/완료 상태')); return 0 }
      beginFinale(ctx.source.server); return 1
    }))
    .then(Commands.literal('abort').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      s.runCommandSilent('kill @e[tag=ls_final_boss]')
      s.runCommandSilent('kill @e[tag=ls_final_boss_true]')
      setFinale(s, 0)
      sfSetB(s, 'ls_fn_ok', false)
      sfSetB(s, 'ls_dawnbreak', false)
      sfSetB(s, 'ls_true_spawned', false)
      sfStore(s).putBoolean('ls_time_locked', false)
      sfStore(s).putInt('ls_nrate_pct', 0)
      ctx.source.sendSystemMessage(Text.of('§7최종장 중단·초기화 (시간 잠금 해제)')); return 1
    }))
    .then(Commands.literal('trueform').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      const off = !sfGetB(s, 'ls_trueform_off')
      sfSetB(s, 'ls_trueform_off', off)
      ctx.source.sendSystemMessage(Text.of(off ? '§7진(眞) 보스 페이즈 OFF' : '§a진(眞) 보스 페이즈 ON (기본)')); return 1
    })))

  event.register(Commands.literal('siege')
    .then(Commands.literal('start').requires(s => s.hasPermission(2)).executes(ctx => startSiege(ctx.source.server, false)))
    // 대공세를 그날이 아니어도 강제로 연다. 원래는 GRAND_EVERY(12일)의 배수 밤에만 붙는데,
    // 테스트하려고 /time set 으로 날짜를 넘기는 건 다른 주기(공성일·현상금·시세)까지 흔든다.
    .then(Commands.literal('grand').requires(s => s.hasPermission(2)).executes(ctx => startSiege(ctx.source.server, false, true)))
    .then(Commands.literal('status').executes(ctx => {
      const s = ctx.source.server
      const d = worldDay(s)
      const untilGrand = (GRAND_EVERY - (d % GRAND_EVERY)) % GRAND_EVERY
      const grandTxt = untilGrand === 0 && d >= GRAND_EVERY ? '오늘!' : `${untilGrand || GRAND_EVERY}일 후`
      if (sfGetB(s, 'ls_siege_active'))
        ctx.source.sendSystemMessage(Text.of(`§c공성 진행 중${sfGetB(s, 'ls_siege_grand') ? ' §4[대공세]' : ''} §7· 물결 ${sfGetI(s, 'ls_siege_wave_no')} · 남은 적 ${sfGetI(s, 'ls_siege_remaining')}기 · 남은 물결 ${sfGetI(s, 'ls_siege_waves')}`))
      else
        ctx.source.sendSystemMessage(Text.of(`§7공성 없음 · §6${d}일차 §7· 위협도 ${getThreat(s)} · 노드 ${nodeCount(s)} · 대공세 ${grandTxt} · 금고 ${getTreasury(s)}`))
      return 1
    }))
    .then(Commands.literal('end').requires(s => s.hasPermission(2)).executes(ctx => {
      if (!sfGetB(ctx.source.server, 'ls_siege_active')) { ctx.source.sendSystemMessage(Text.of('§7진행 중인 공성이 없습니다.')); return 0 }
      finishSiege(ctx.source.server, 'give_up'); return 1
    }))
    .then(Commands.literal('cancel').requires(s => s.hasPermission(2)).executes(ctx => {
      if (!sfGetB(ctx.source.server, 'ls_siege_active')) { ctx.source.sendSystemMessage(Text.of('§7진행 중인 공성이 없습니다.')); return 0 }
      cancelSiege(ctx.source.server); return 1
    })))
})

console.log('[Last Stardust] 밤 공성 Phase 3+최종장 로드됨 — 노드/대공세/몹진화/가장 긴 밤')
