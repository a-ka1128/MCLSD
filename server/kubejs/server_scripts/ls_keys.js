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
  // ── 일반만 «별먼지» 로 만든다 (2026-08-08) ──
  // 일반 균열은 사냥 현상금을 도는 자리라 매일 돌게 된다. 파편으로 값을 매기면
  // 정예·황금(파편 순 −1)과 같은 무게가 되어 「연습용이 제일 비싼」 모양이 된다.
  // 별먼지는 탐험 재화라 축이 다르고, **1개**로 잡아 축복 리롤(3~8개)과 경쟁하지 않게 한다.
  // 2개만 돼도 「열쇠 하나 = 리롤 반 번」이 되어 아무도 안 쓴다.
  event.shapeless('kubejs:rift_key', ['kubejs:stardust', 'minecraft:gold_ingot', 'minecraft:gold_ingot', 'minecraft:amethyst_shard', 'minecraft:amethyst_shard'])
  event.shapeless('kubejs:rift_key_elite', ['kubejs:rift_essence', 'minecraft:diamond', 'minecraft:diamond', 'minecraft:amethyst_shard'])
  // 황금만 두 재화를 다 요구한다 — 전투(파편)와 탐험(별먼지)을 둘 다 한 사람만 여는 자리.
  // 에메랄드블록은 뺐다: 보상에서 에메랄드를 없앤 마당에 원가로만 남기면
  // 균열이 도박장 칩을 «빨아먹는» 반대 방향이 된다.
  event.shapeless('kubejs:rift_key_gold', ['kubejs:rift_essence', 'kubejs:rift_essence', 'kubejs:stardust', 'minecraft:gold_block', 'minecraft:diamond_block'])
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
