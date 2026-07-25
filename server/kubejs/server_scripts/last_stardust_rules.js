// Last Stardust — Layer A: 기본 서버 규칙
// 서버가 로드될 때마다 자동 적용 (run.bat로 켤 때마다)

ServerEvents.loaded(event => {
  const s = event.server

  // 죽음: 아이템 드롭 (Corpse 모드가 시체로 회수 → 가벼운 페널티)
  s.runCommandSilent('gamerule keepInventory false')

  // 팬텀: 안 자도 안 뜨게 (멀티 스트레스 방지)
  s.runCommandSilent('gamerule doInsomnia false')

  // 불 번짐: 끔 (실수/그리핑 방지)
  s.runCommandSilent('gamerule doFireTick false')

  // 몹 그리핑: 유지 (성역은 FTB Chunks 청크보호로 막을 예정)
  s.runCommandSilent('gamerule mobGriefing true')

  // 난이도: hard (server.properties와 함께 강제)
  s.runCommandSilent('difficulty hard')

  console.log('[Last Stardust] Layer A 기본 규칙 적용됨')
})
