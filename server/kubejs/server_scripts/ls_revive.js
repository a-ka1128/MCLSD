// Last Stardust — 부활 규칙 (별빛 쇠약)  [죽음을 무겁게, 복귀는 가능하게]
// 부활 직후 3초 보호(무적급) + 60초 '별빛 쇠약'(최대 체력·공격력 -20%).
// FF14 쇠약의 정신: 죽음이 무겁되 벽이 되지는 않게 (캐주얼 협동 그룹 표준).
// 속성 모디파이어는 각성(ls_ascend)·가호(ls_fate)와 다른 ID라 공존한다 — 이중 적용 아님.
// 저장: 없음 (보호=효과, 쇠약=시간 후 자동 해제 모디파이어)

const RV_INVULN_SEC = 3         // 부활 직후 보호 시간(초)
const RV_FRAIL_SEC = 30         // 별빛 쇠약 지속(초)
const RV_FRAIL_PCT = 0.20       // 체력·공격력 감소율 (최종값 기준 = add_multiplied_total)
const RV_HP_MOD = 'last_stardust:frailty_health'
const RV_ATK_MOD = 'last_stardust:frailty_attack'

function rvClearFrailty(server, uname) {
  try { server.runCommandSilent(`attribute ${uname} minecraft:generic.max_health modifier remove ${RV_HP_MOD}`) } catch (e) { lsWarn('ls_revive:14', e) }
  try { server.runCommandSilent(`attribute ${uname} minecraft:generic.attack_damage modifier remove ${RV_ATK_MOD}`) } catch (e) { lsWarn('ls_revive:15', e) }
}

PlayerEvents.respawned(event => {
  const player = event.player
  const server = player ? player.server : null
  if (!player || !server) return
  const uname = player.username

  // 다른 부활 훅(ls_ascend/ls_fate)이 5틱 뒤 속성을 재적용하므로 그 다음(10틱)에 얹는다.
  server.scheduleInTicks(10, () => {
    try {
      // ① 3초 보호 — 저항 V(=amplifier 4)면 대부분 피해를 100% 감쇄한다.
      server.runCommandSilent(`effect give ${uname} minecraft:resistance ${RV_INVULN_SEC} 4 true`)
      // ② 별빛 쇠약 — 최대 체력·공격력 -20% (add_multiplied_total = 각성치까지 포함한 최종값의 20%).
      rvClearFrailty(server, uname)
      server.runCommandSilent(`attribute ${uname} minecraft:generic.max_health modifier add ${RV_HP_MOD} -${RV_FRAIL_PCT} add_multiplied_total`)
      server.runCommandSilent(`attribute ${uname} minecraft:generic.attack_damage modifier add ${RV_ATK_MOD} -${RV_FRAIL_PCT} add_multiplied_total`)
      player.tell(Text.of(`§8[ §7별빛이 흐려졌습니다 — 별빛 쇠약 ${RV_FRAIL_SEC}초 (체력·공격력 -${Math.round(RV_FRAIL_PCT * 100)}%) §8]`))
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

console.log('[Last Stardust] 부활 규칙 로드됨 — 3초 보호 + 별빛 쇠약 ' + RV_FRAIL_SEC + '초(-' + Math.round(RV_FRAIL_PCT * 100) + '%)')
