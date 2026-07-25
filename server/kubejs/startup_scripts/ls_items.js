// Last Stardust — 커스텀 아이템: 균열 정수 (Rift Essence)
// 마을 재건 전용 재료. 보스 처치 시 드롭(ls_siege.js death 핸들러가 소환).
// 제작·장비와 전혀 안 겹치는 순수 마을 재화 → "물리친 어둠의 정수로 성역을 재건한다".
// ※ 아이템 등록은 startup이라 적용에 서버 재시작 필요(‪/reload로는 안 됨).

StartupEvents.registry('item', event => {
  event.create('rift_essence')
    .displayName('균열 정수')
    .tooltip('§7물리친 어둠의 정수. 성역을 재건하는 재료다.')
    .tooltip('§8/town 에서 마을 발전에 사용')
    .texture('minecraft:item/echo_shard')
    .rarity('rare')
    .glow(true)
    .maxStackSize(64)

  // ── 균열 열쇠 3종: 우클릭으로 반복 웨이브 던전(Gateways) 개방 ──
  event.create('rift_key')
    .displayName('균열 열쇠')
    .tooltip('§7우클릭 — 균열 시련 개방 (3웨이브)')
    .tooltip('§8어둠을 불러내 그 전리품을 빼앗는다')
    .texture('minecraft:item/trial_key')
    .rarity('uncommon')
    .maxStackSize(16)

  event.create('rift_key_elite')
    .displayName('정예 균열 열쇠')
    .tooltip('§7우클릭 — 정예 균열 시련 개방 (4웨이브)')
    .tooltip('§c강력한 어둠 — 정수와 보물을 노려라')
    .texture('minecraft:item/ominous_trial_key')
    .rarity('rare')
    .glow(true)
    .maxStackSize(16)

  event.create('rift_key_gold')
    .displayName('황금 균열 열쇠')
    .tooltip('§7우클릭 — 황금 균열 개방 (2웨이브 · 대량 보물)')
    .tooltip('§6짧고 굵게 — 시간 내에 쓸어담아라')
    .texture('minecraft:item/raw_gold')
    .rarity('epic')
    .glow(true)
    .maxStackSize(16)

  // ※ 별의 유물 4종은 이제 커스텀 모드(lsrelics)가 제공한다 — 진짜 활/방패/도끼/지팡이 하이브리드.
  //   여기(KubeJS)서는 더 이상 등록하지 않음.

  // ── 전설 열쇠 (히든 기믹 발동용 — 리셋 후 배치되는 퍼즐이 요구) ──
  event.create('star_seal')
    .displayName('§e별의 봉인')
    .tooltip('§8낡은 봉인. 어딘가의 잠긴 문이 이것을 기억한다.')
    .tooltip('§7히든 기믹의 열쇠')
    .texture('minecraft:item/heart_of_the_sea')
    .rarity('rare').glow(true).maxStackSize(16)
})
