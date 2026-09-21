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

// ── 죽어도 «레벨»은 남는다 (2026-08-18) ──
//
// 이 서버는 keepInventory 가 꺼져 있다 — 죽으면 들고 있던 걸 잃는 게 죽음의 무게다.
// 그런데 «경험치»까지 같이 날리면 대가가 두 배로 붙는다:
//   ① 유물 각성은 파편으로 사지만, 인챈트·수리는 경험치로 산다
//   ② 그 경험치는 유물과 달리 **되찾을 방법이 없다** — 떨어진 구슬은 5분이면 사라지고,
//      죽은 자리가 밤의 성벽 밖이면 애초에 되돌아갈 수가 없다
// 아이템은 «다시 캐면» 되지만 레벨은 아니다. 그래서 레벨만 돌려준다.
//
// ⚠️ **죽을 때 0 으로 비우는 게 핵심이다.** 안 비우면 바닐라가 최대 100 포인트(≈7레벨)를
//    구슬로 흘리고, 부활 때 우리가 원래 레벨을 «다시» 채워서 **죽을수록 이득**이 된다.
//    비우는 시점은 사망 이벤트 — 바닐라가 구슬을 계산하기 «전»이다.
const RV_XP_KEY = 'rv_xp_'      // + 아이디 → 레벨
const RV_XPP_KEY = 'rv_xpp_'    // + 아이디 → 레벨 안 진행도(0~1)를 1000배 정수로

EntityEvents.death(event => {
  const e = event.entity
  if (!e || String(e.type) !== 'minecraft:player') return
  const server = e.server
  if (!server) return
  try {
    // ⚠️ `var` 다. 반복 실행되는 블록의 const 는 Rhino 가 재선언으로 터뜨린다
    //    (tools/scan_try_decls.py 가 잡았다).
    var uname = String(e.username)
    var lv = 0, prog = 0
    try { lv = e.experienceLevel | 0 } catch (err) { lsWarn('ls_revive:xp-read', err) }
    try { prog = Math.round((Number(e.experienceProgress) || 0) * 1000) } catch (err) { prog = 0 }
    var st = server.overworld().persistentData
    st.putInt(RV_XP_KEY + uname, lv)
    st.putInt(RV_XPP_KEY + uname, prog)
    // 구슬이 떨어지지 않게 비운다. 명령어로 하는 이유: 래퍼의 setter 이름이 버전마다
    // 갈리는데 `/xp set` 은 안 바뀐다. 여기서 실패하면 «죽을수록 이득»이 되므로 안전한 쪽을 쓴다.
    server.runCommandSilent(`xp set ${uname} 0 levels`)
    server.runCommandSilent(`xp set ${uname} 0 points`)
  } catch (err) { lsWarn('ls_revive:xp-save', err) }
})

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
      // ── 레벨 되돌려주기 ──
      // 부활 직후는 0 레벨이라 «더하기»가 아니라 «맞추기»다. 여기서 add 를 쓰면
      // 부활이 두 번 겹치는 드문 경우에 두 배가 된다.
      var rvLv = 0, rvPp = 0
      try {
        var rvSt = server.overworld().persistentData
        rvLv = rvSt.getInt(RV_XP_KEY + uname) | 0
        rvPp = rvSt.getInt(RV_XPP_KEY + uname) | 0
        rvSt.putInt(RV_XP_KEY + uname, 0)
        rvSt.putInt(RV_XPP_KEY + uname, 0)
      } catch (err) { lsWarn('ls_revive:xp-load', err) }
      if (rvLv > 0 || rvPp > 0) {
        server.runCommandSilent(`xp set ${uname} ${rvLv} levels`)
        if (rvPp > 0) {
          // 레벨 안 진행도는 «그 레벨의 총 포인트 × 비율» 로 되돌린다.
          // 레벨당 필요량은 구간마다 다르다(<16: 2L+7 · <31: 5L-38 · 그 이상: 9L-158).
          var need = rvLv < 16 ? (2 * rvLv + 7) : (rvLv < 31 ? (5 * rvLv - 38) : (9 * rvLv - 158))
          var pts = Math.floor(need * rvPp / 1000)
          if (pts > 0) server.runCommandSilent(`xp add ${uname} ${pts} points`)
        }
        player.tell(Text.of(`§8[ §7경험치 §f${rvLv}레벨§7은 그대로 남았습니다 §8]`))
      }
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
