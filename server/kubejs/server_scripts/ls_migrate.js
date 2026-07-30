// Last Stardust — 옛 persistentData → 모드 장부 한 번 옮기기 (이관 3단계)
//
// `ls_fate`·`ls_relic`·`ls_ascend` 가 오늘부로 모드 장부(HeroData)를 본다. 그런데 이미 돌던
// 월드에는 옛 키(`fate_<이름>` · `relic_<이름>` · `star_<이름>` · `relic_altar_<가호>_*`)에
// 값이 들어 있다. 그대로 두면 **모두의 가호·유물·각성이 한순간에 사라진 것처럼 보인다.**
//
// 이관 1·2단계(성역 좌표·진행도)에는 이런 옮기기가 없었다. 그 둘은 OP 명령 한 줄로 다시
// 세울 수 있어서 넘어갔지만, 여기는 여덟 명의 성장 기록이라 같은 판단을 할 수 없다.
//
// ── 왜 자바가 아니라 스크립트인가 ──
// 읽어야 할 곳이 KubeJS 의 `persistentData` 다. 자바에서 그 태그에 닿는 경로가 KubeJS 버전에
// 따라 다른데, 스크립트에서는 한 줄이다. **옮기는 코드가 옮기는 대상보다 복잡하면 안 된다.**
//
// ── 한 번만 돈다 ──
// `ls_hero_migrated` 플래그로 잠근다. 두 번 돌아도 값은 같지만(멱등), 로그가 매 기동마다
// 「N명 옮김」을 찍으면 그게 정상인지 사고인지 구분이 안 된다.
//
// **이 파일은 지울 수 있다.** 옮기기가 끝나고 한동안 문제가 없으면 통째로 삭제한다.
// (지우기 전에 `/lsdata` 로 장부에 값이 있는지 먼저 볼 것)
//
// ── 스캐너가 이 파일을 지적하는 건 정상이다 ──
// `tools/scan_dead_kubejs.py` B절이 `fate_`·`relic_`·`star_`·`relic_altar_` 를
// 「읽는데 쓰는 곳이 없다」로 잡는다. **맞는 말이고, 그게 이관이 끝났다는 증거다.**
// 쓰는 쪽은 전부 모드로 갔고 읽는 쪽은 여기 하나만 남았다.
// 그 경고를 없애려고 이 읽기를 지우면 옛 월드의 데이터를 옮길 방법이 사라진다.

const MG_FLAG = 'ls_hero_migrated'
const MG_FATE_KEYS = ['guardian', 'hunter', 'sage', 'pioneer', 'gunner', 'healer', 'assassin', 'lancer']

function mgStore(server) { return server.overworld().persistentData }

// 접두사로 시작하는 키에서 그 뒷부분(이름)을 모은다.
// ※ 블록 안에서는 var — const/let 은 Rhino 재선언 오류를 낸다 (docs/TODO.md 함정 #1)
function mgNamesWith(server, prefix) {
  var out = []
  try {
    mgStore(server).getAllKeys().forEach(k => {
      var ks = String(k)
      if (ks.indexOf(prefix) !== 0) return
      var who = ks.substring(prefix.length)
      if (who) out.push(who)
    })
  } catch (e) { lsWarn('ls_migrate:keys', e) }
  return out
}

function mgRun(server, force) {
  const st = mgStore(server)
  if (!force && st.getBoolean(MG_FLAG)) return null

  var moved = { fate: 0, relic: 0, star: 0, altar: 0 }

  // 가호 — 빈 문자열은 «해제됨»이라 옮기지 않는다(옮기면 «가호 없음»이 «가호 ''»가 된다).
  mgNamesWith(server, 'fate_').forEach(who => {
    try {
      var v = String(st.getString('fate_' + who) || '')
      if (!v) return
      LS.setFate(server, who, v)
      moved.fate++
    } catch (e) { lsWarn('ls_migrate:fate', e) }
  })

  // 유물 수령 — `relic_altar_` 로 시작하는 키가 같은 접두사에 걸리므로 걸러낸다.
  // 안 거르면 `altar_guardian` 같은 이름의 «사람»이 유물을 받은 것으로 기록된다.
  mgNamesWith(server, 'relic_').forEach(who => {
    try {
      if (who.indexOf('altar_') === 0) return
      if (!st.getBoolean('relic_' + who)) return
      LS.setHasRelic(server, who, true)
      moved.relic++
    } catch (e) { lsWarn('ls_migrate:relic', e) }
  })

  // 각성 성급
  mgNamesWith(server, 'star_').forEach(who => {
    try {
      var n = st.getInt('star_' + who)
      if (n <= 0) return
      LS.setStar(server, who, n)
      moved.star++
    } catch (e) { lsWarn('ls_migrate:star', e) }
  })

  // 제단 — 사람이 아니라 가호에 붙는다. 키가 여덟 개로 정해져 있어 훑을 필요가 없다.
  MG_FATE_KEYS.forEach(f => {
    try {
      if (!st.getBoolean('relic_altar_' + f + '_set')) return
      LS.setAltar(server, f,
        st.getInt('relic_altar_' + f + '_x'),
        st.getInt('relic_altar_' + f + '_y'),
        st.getInt('relic_altar_' + f + '_z'))
      moved.altar++
    } catch (e) { lsWarn('ls_migrate:altar', e) }
  })

  st.putBoolean(MG_FLAG, true)
  return moved
}

ServerEvents.loaded(event => {
  const server = event.server
  if (!server) return
  var mgR = null
  try { mgR = mgRun(server, false) } catch (e) { lsWarn('ls_migrate:run', e); return }
  if (!mgR) return
  // 아무것도 없으면(새 월드) 조용히 넘어간다 — 옮길 게 없는 건 사고가 아니다.
  if (mgR.fate + mgR.relic + mgR.star + mgR.altar === 0) {
    console.log('[LS-MIGRATE] 옛 키 없음 — 새 월드로 본다')
    return
  }
  console.log(`[LS-MIGRATE] 모드 장부로 옮김 — 가호 ${mgR.fate} · 유물 ${mgR.relic} · 각성 ${mgR.star} · 제단 ${mgR.altar}`)
})

ServerEvents.commandRegistry(event => {
  const { commands: Commands } = event
  // 장부에 실제로 값이 있는지 본다. 이관에서 제일 무서운 실패는 «옮겼다고 찍혔는데 비어 있는» 것이다.
  event.register(Commands.literal('lsdata').requires(s => s.hasPermission(2))
    .executes(ctx => {
      const s = ctx.source.server
      ctx.source.sendSystemMessage(Text.of('§6═══ 모드 장부 §6═══'))
      ctx.source.sendSystemMessage(Text.of(`§7성장 §8— ${LS.heroSummary(s)}`))
      ctx.source.sendSystemMessage(Text.of(
        `§7성역 §8— ${LS.hasSanctuary(s) ? LS.sanctuaryX(s) + ', ' + LS.sanctuaryY(s) + ', ' + LS.sanctuaryZ(s) : '미지정'}`
        + ` §8· 관문 §7${LS.progress(s)}/4§8 · 금고 §7${LS.treasury(s)}`))
      ctx.source.sendSystemMessage(Text.of(
        `§8옛 키 옮기기: ${mgStore(s).getBoolean(MG_FLAG) ? '§7완료' : '§c아직'}`))
      return 1
    })
    // 다시 옮긴다. 멱등이라 여러 번 돌려도 결과가 같다 — 옛 키가 아직 남아 있는 한.
    .then(Commands.literal('remigrate').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      var r = null
      try { r = mgRun(s, true) } catch (e) { lsWarn('ls_migrate:cmd', e) }
      if (!r) { ctx.source.sendSystemMessage(Text.of('§c실패 — 로그 확인')); return 0 }
      ctx.source.sendSystemMessage(Text.of(
        `§a다시 옮김 §7— 가호 ${r.fate} · 유물 ${r.relic} · 각성 ${r.star} · 제단 ${r.altar}`))
      ctx.source.sendSystemMessage(Text.of(`§8지금 장부: ${LS.heroSummary(s)}`))
      return 1
    })))
})

console.log('[Last Stardust] 이관 도우미 로드됨 — /lsdata · /lsdata remigrate')
