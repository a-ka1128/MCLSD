// Last Stardust — 부활 규칙 (별빛 쇠약)  [죽음을 무겁게, 복귀는 가능하게]
// 부활 직후 5초 무적 + 60초 '별빛 쇠약'(최대 체력 -20% · 주는 피해 -25%).
// FF14 쇠약의 정신: 죽음이 무겁되 벽이 되지는 않게 (캐주얼 협동 그룹 표준).
//
// ── 역할 분담 ──
//   이 파일   부활 시점 감지 · 최대 체력 감소 · 안내문 · 해제 타이머
//   ReviveRules(자바)  무적 창 · 주는 피해 -25% 판정
//
// 피해 감소를 자바로 옮긴 이유: 예전엔 attack_damage 속성 모디파이어였는데,
// 그 속성을 보는 건 - 근접 평타뿐 - 이다. 원거리 4종은 투사체 피해를 직접 계산하고
// 8유물의 스킬은 전부 LsDamage 로 고정 수치를 넣어서, 죽음의 대가를 근접 평타만
// 치르고 있었다. 자세한 근거는 ReviveRules 주석에 있다.
//
// 저장: 없음 (무적·쇠약 판정은 자바 메모리, 체력 감소는 시간 후 자동 해제 모디파이어)

const RV_INVULN_SEC = 5         // 부활 직후 무적 (자바가 판정 — 공격하면 즉시 풀린다)
const RV_FRAIL_SEC = 60         // 별빛 쇠약 지속(초). ReviveRules.FRAILTY_TICKS 과 맞출 것
const RV_FRAIL_PCT = 0.20       // 최대 체력 감소율 (최종값 기준 = add_multiplied_total)
const RV_HP_MOD = 'last_stardust:frailty_health'

function rvClearFrailty(server, uname) {
  try { server.runCommandSilent(`attribute ${uname} minecraft:generic.max_health modifier remove ${RV_HP_MOD}`) } catch (e) { lsWarn('ls_revive:14', e) }
}

PlayerEvents.respawned(event => {
  const player = event.player
  const server = player ? player.server : null
  if (!player || !server) return
  const uname = player.username

  // 다른 부활 훅(ls_ascend/ls_fate)이 5틱 뒤 속성을 재적용하므로 그 다음(10틱)에 얹는다.
  server.scheduleInTicks(10, () => {
    try {
      // ① 무적 창과 ② 주는 피해 -25% 는 자바가 건다 (ReviveRules).
      LS.reviveRule(player)
      // ③ 최대 체력 -20% (add_multiplied_total = 각성치까지 포함한 최종값의 20%).
      rvClearFrailty(server, uname)
      server.runCommandSilent(`attribute ${uname} minecraft:generic.max_health modifier add ${RV_HP_MOD} -${RV_FRAIL_PCT} add_multiplied_total`)
      player.tell(Text.of(`§8[ §7별빛이 흐려졌습니다 — 별빛 쇠약 ${RV_FRAIL_SEC}초 §8(체력 -${Math.round(RV_FRAIL_PCT * 100)}% · 주는 피해 -25%) §8]`))
      player.tell(Text.of(`§8[ §7일어선 직후 §f${RV_INVULN_SEC}초 무적§7 — 공격하면 즉시 풀린다 §8]`))
    } catch (e) { console.log('[LS-REVIVE] apply fail: ' + e) }
  })

  // 쇠약 자동 해제. (60초 내 재사망 시 이 타이머가 새 쇠약을 조금 일찍 풀 수 있으나 — 관대한 쪽이라 무해)
  server.scheduleInTicks(10 + RV_FRAIL_SEC * 20, () => {
    try {
      rvClearFrailty(server, uname)
      player.tell(Text.of('§7별빛이 다시 또렷해졌다.'))
    } catch (e) { lsWarn('ls_revive:42', e) }
  })
})

console.log('[Last Stardust] 부활 규칙 로드됨 — ' + RV_INVULN_SEC + '초 무적 + 별빛 쇠약 ' + RV_FRAIL_SEC + '초(체력 -' + Math.round(RV_FRAIL_PCT * 100) + '% · 주는 피해 -25%)')
