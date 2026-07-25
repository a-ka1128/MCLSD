// Last Stardust — 균열 열쇠 (Rift Keys)  [반복 던전 루프]
// 정수를 소모해 열쇠를 제작 → 아무 곳에서나 우클릭 → 그 자리에 웨이브 던전(Gateways) 개방.
// Vault Hunters의 "열쇠를 만들어 던전을 연다" 패턴 — 반복 가능한 엔드게임.
// 관문 정의: kubejs/data/last_stardust/gateways/rift_trial*.json

const KEY_GATES = {
  'kubejs:rift_key': { gate: 'last_stardust:rift_trial', name: '균열 시련' },
  'kubejs:rift_key_elite': { gate: 'last_stardust:rift_trial_elite', name: '정예 균열 시련' },
  'kubejs:rift_key_gold': { gate: 'last_stardust:rift_trial_gold', name: '황금 균열' }
}

// ── 제작 레시피 (정수가 소모처를 얻는다 — 보스 킬 → 열쇠 → 던전 → 전리품 루프) ──
ServerEvents.recipes(event => {
  event.shapeless('kubejs:rift_key', ['kubejs:rift_essence', 'minecraft:gold_ingot', 'minecraft:gold_ingot', 'minecraft:amethyst_shard', 'minecraft:amethyst_shard'])
  event.shapeless('kubejs:rift_key_elite', ['kubejs:rift_essence', 'kubejs:rift_essence', 'minecraft:diamond', 'minecraft:diamond', 'minecraft:amethyst_shard'])
  event.shapeless('kubejs:rift_key_gold', ['kubejs:rift_essence', 'kubejs:rift_essence', 'kubejs:rift_essence', 'minecraft:gold_block', 'minecraft:emerald_block'])
})

// ── 우클릭 → 관문 개방 ──
// ※ 허공/블록/몹 조준이 각각 다른 이벤트로 빠지므로 3중 등록 + 10틱 가드(열쇠 이중 소모 방지)
function ksTryUse(server, player) {
  const held = player.mainHandItem
  const id = held ? String(held.id) : ''
  const def = KEY_GATES[id]
  if (!def) return
  const now = Date.now() // 실제 시간(ms) — getGameTime은 KubeJS 레벨 래퍼에 없음
  const last = Number(server.overworld().persistentData.getLong('ks_cd_' + player.username))
  if (last > 0 && now - last < 500) return // 같은 클릭 중복 발화 무시 (열쇠 이중 소모 방지)
  server.overworld().persistentData.putLong('ks_cd_' + player.username, now)
  // 소모 후 개방 (플레이어 위치 위 1블록)
  server.runCommandSilent(`clear ${player.username} ${id} 1`)
  server.runCommandSilent(`execute at ${player.username} run open_gateway ~ ~1 ~ ${def.gate}`)
  server.players.forEach(p => p.tell(Text.of(`§5⌘ ${player.username}§7이(가) §d${def.name}§7을(를) 열었습니다!`)))
  server.runCommandSilent(`execute as ${player.username} at @s run playsound minecraft:block.end_portal.spawn master @a ~ ~ ~ 0.8 0.7`)
  console.log(`[LS-KEYS] ${player.username} opened ${def.gate}`)
}
ItemEvents.rightClicked(event => {
  const player = event.player
  if (!player || !player.server) return
  ksTryUse(player.server, player)
})
ItemEvents.entityInteracted(event => {
  const player = event.player
  if (!player || !player.server) return
  ksTryUse(player.server, player)
})
BlockEvents.rightClicked(event => {
  const player = event.player
  if (!player || !player.server) return
  ksTryUse(player.server, player)
})

console.log('[Last Stardust] 균열 열쇠 로드됨 — 3종 (일반/정예/황금)')
