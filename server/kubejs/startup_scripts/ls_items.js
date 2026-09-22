// Last Stardust — 커스텀 재화 아이템
// ※ 아이템 등록은 startup이라 적용에 서버 재시작 필요(‪/reload로는 안 됨).
//
// ── 재화가 둘인 이유 (2026-08-08) ──
// 두 축이 «서로 다른 플레이»를 요구하게 갈랐다.
//   별의 파편 — 공성·보스에서 나온다 → 방어·전투. 각성·관문·마을·축복 «종류» 리롤
//   별먼지     — 구조물 상자에서 나온다 → 탐험.     축복 «수치» 리롤
// 수치 리롤을 광물로 하면 Create 자동화로 후반에 사실상 무한이 되어 다들 최댓값으로
// 수렴한다. 구조물은 자동화가 안 되므로 비용이 «자원»이 아니라 «이동 시간»이 된다.
// 그리고 별먼지는 아무리 모아도 성급 곡선 이상은 못 뽑아서, 파밍이 진행을 앞지를 수 없다.
//
// ⚠️ **ID 는 `rift_essence` 그대로다.** 표시 이름만 「별의 파편」→「별의 파편」으로 바꿨다.
//    이 ID 가 Java(TownCatalog)·모드 어드밴스먼트 JSON·게이트웨이 JSON 등 27곳에 박혀 있어서,
//    바꾸면 모드 재빌드까지 딸려오고 한 곳만 놓쳐도 조용히 깨진다. 플레이어는 ID 를 볼 일이 없다.

StartupEvents.registry('item', event => {
  event.create('rift_essence')
    .displayName('별의 파편')
    .tooltip('§7어둠을 물리치고 되찾은 별빛 조각.')
    .tooltip('§8각성 · 관문 개봉 · 마을 발전에 사용')
    // 텍스처는 네더의 별을 금색으로 갈아끼운 것(`assets/kubejs/textures/item/rift_essence.png`).
    // **클라에 리소스팩이 있어야 보인다** — 없으면 보라/검정 누락 텍스처가 뜬다.
    .rarity('rare')
    .glow(true)
    .maxStackSize(64)

  // ── 별먼지: 탐험 재화 ──
  // 예전에 별의 파편이 쓰던 echo_shard 를 물려받는다. 바닐라 텍스처라 리소스팩 없이도 보인다.
  event.create('stardust')
    .displayName('별먼지')
    .tooltip('§7흩어진 별빛. 손에 쥐면 아직 미지근하다.')
    .tooltip('§8구조물 상자에서 발견된다')
    .tooltip('§8축복의 «수치»를 다시 굴리는 데 사용')
    .texture('minecraft:item/echo_shard')
    .rarity('uncommon')
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
