// Last Stardust — 옛 persistentData → 모드 장부 한 번 옮기기 (이관 3·4단계)
//
// ── 두 단계, 두 플래그 ──
// 3단계(가호·유물·각성)와 4단계(공성)는 **플래그를 따로 쓴다.** 지금 도는 월드는 이미
// 3단계 플래그가 켜져 있어서, 같은 플래그를 재활용하면 4단계가 「이미 했음」으로 건너뛰어진다.
// 그러면 위협도·성벽·최종장 진행이 전부 0 에서 시작하고 **아무 오류도 나지 않는다.**
// 이관에서 제일 무서운 실패는 늘 이 모양이다 — 조용하고, 기본값이라서 그럴듯하다.
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
// `tools/scan_dead_kubejs.py` B절이 `fate_`·`relic_`·`star_`·`relic_altar_` 와
// 공성 키들을 「읽는데 쓰는 곳이 없다」로 잡는다. **맞는 말이고, 그게 이관이 끝났다는 증거다.**
// 쓰는 쪽은 전부 모드로 갔고 읽는 쪽은 여기 하나만 남았다.
// 그 경고를 없애려고 이 읽기를 지우면 옛 월드의 데이터를 옮길 방법이 사라진다.

const MG_FLAG = 'ls_hero_migrated'
const MG_SIEGE_FLAG = 'ls_siege_migrated'
const MG_BD_FLAG = 'ls_bossdiff_migrated'
const MG_TT_FLAG = 'ls_title_migrated'
const MG_BT_FLAG = 'ls_bounty_migrated'
const MG_FATE_KEYS = ['guardian', 'hunter', 'sage', 'pioneer', 'gunner', 'healer', 'assassin', 'lancer']

// ── 공성 키 (이관 4단계) ──
// 3단계와 **플래그를 따로 둔다.** 지금 도는 월드는 이미 `ls_hero_migrated` 가 켜져 있어서,
// 같은 플래그를 쓰면 공성 옮기기가 「이미 했음」으로 건너뛰어진다 — 그러면 위협도·성벽·
// 최종장 진행이 전부 0 으로 시작하고, **아무 오류 없이** 그렇게 된다.
//
// [옛 키, 종류, 세터 이름] — 세터는 LS 바인딩의 메서드다.
// 성벽 HP 는 여기 없다. 천장 인자가 필요하고 «미설정/부서짐» 구분이 걸려 있어 따로 다룬다.
const MG_SIEGE = [
  ['ls_threat',            'i', 'setThreat'],
  ['ls_nodes_names',       's', 'setNodeCsv'],
  ['wall_r',               'i', 'setWallRadius'],
  ['wall_warn',            'i', 'setWallWarn'],
  ['wall_last_stand',      'b', 'setWallLastStand'],
  ['wall_stand_until',     'i', 'setWallStandUntil'],
  ['ls_finale',            'i', 'setFinale'],
  ['ls_finale_armed',      'b', 'setFinaleArmed'],
  ['ls_fn_ok',             'b', 'setFinaleNightOk'],
  ['ls_true_spawned',      'b', 'setTrueSpawned'],
  ['ls_trueform_off',      'b', 'setTrueFormOff'],
  ['ls_dawnbreak',         'b', 'setDawnbreak'],
  ['ls_siege_active',      'b', 'setSiegeActive'],
  ['ls_siege_grand',       'b', 'setSiegeGrand'],
  ['ls_siege_waves',       'i', 'setSiegeWaves'],
  ['ls_siege_wave_no',     'i', 'setSiegeWaveNo'],
  ['ls_siege_remaining',   'i', 'setSiegeRemaining'],
  ['ls_siege_reward',      'i', 'setSiegeReward'],
  ['ls_siege_ang',         'i', 'setSiegeAngle'],
  ['ls_dread_step',        'i', 'setDreadStep'],
  ['ls_ann_tier',          'i', 'setAnnTier'],
  ['ls_day',               'i', 'setSiegeDay'],
  ['ls_night',             'b', 'setWasNight'],
  ['ls_first_siege_done',  'b', 'setFirstSiegeDone']
]

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

// ── 공성 (이관 4단계) ──
// 0/false 는 옮기지 않는다. 새 월드의 기본값과 구분이 안 되는 값이라, 옮겨도 결과가 같고
// 「N개 옮김」 숫자만 부풀어 «실제로 뭔가 있었나»를 못 읽게 된다.
function mgRunSiege(server, force) {
  const st = mgStore(server)
  if (!force && st.getBoolean(MG_SIEGE_FLAG)) return null

  var n = 0
  MG_SIEGE.forEach(row => {
    try {
      var mgK = row[0], mgT = row[1], mgFn = row[2]
      if (mgT === 'i') {
        var iv = st.getInt(mgK)
        if (iv === 0) return
        LS[mgFn](server, iv)
      } else if (mgT === 'b') {
        if (!st.getBoolean(mgK)) return
        LS[mgFn](server, true)
      } else {
        var sv = String(st.getString(mgK) || '')
        if (!sv) return
        LS[mgFn](server, sv)
      }
      n++
    } catch (e) { lsWarn('ls_migrate:siege:' + row[0], e) }
  })

  // 성벽 HP — `wall_init` 이 꺼져 있으면 «아직 한 번도 안 정해짐»이라 그대로 둔다.
  // 옮기면 새 월드의 성벽이 처음부터 부서진 상태가 된다.
  // 천장은 값 자신을 준다 — 여기서 자를 이유가 없고(이미 저장될 때 잘렸다),
  // 방벽 레벨을 읽으려면 `ls_siege.js` 의 함수에 기대야 해서 파일 간 결합이 생긴다.
  try {
    if (st.getBoolean('wall_init')) {
      var wv = st.getInt('wall_hp')
      LS.setWallHp(server, wv, Math.max(wv, 1))
      n++
    }
  } catch (e) { lsWarn('ls_migrate:siege:wall_hp', e) }

  st.putBoolean(MG_SIEGE_FLAG, true)
  return n
}

// ── 보스 난이도 라이브 오버라이드 (이관 5단계) ──
// 「월드 리셋 시 사라지는 게 정상」인 값이라 안 옮겨도 된다고 볼 수도 있다. 그런데 **모드 이관은
// 월드 리셋이 아니다.** 튜닝 중이던 값이 재시작 한 번에 조용히 사라지면, 그게 이 저장소가
// 계속 잡아온 바로 그 실패 모양이다(명예 보드·희망 게이지).
//
// 키 목록을 갖지 않고 `bd_` 접두사를 훑는다. `ls_bossdiff.js` 의 BOSS_LIST 를 여기서 참조하면
// 파일 간 결합이 생기고, 목록에 없는 보스에 걸어둔 값은 어차피 못 옮긴다.
function mgRunBossDiff(server, force) {
  const st = mgStore(server)
  if (!force && st.getBoolean(MG_BD_FLAG)) return null

  var n = 0
  // 전역 — 옛 키는 `bd_g_hp` / `bd_g_dmg`. 둘을 한 번에 넣는다(모드가 둘을 같이 받는다).
  try {
    var gh = st.getInt('bd_g_hp'), gd = st.getInt('bd_g_dmg')
    if (gh > 0 || gd > 0) { LS.setBossGlobal(server, gh, gd); n++ }
  } catch (e) { lsWarn('ls_migrate:bd:global', e) }

  // 보스별 — `bd_<id>_hp` / `_dmg` / `_abs`. id 에 콜론이 들어 있어 접미사로 갈라야 한다.
  // hp·dmg 는 짝으로 넣어야 해서 먼저 id 별로 모은다.
  var pend = {}
  mgNamesWith(server, 'bd_').forEach(rest => {
    var r = String(rest)
    var cut = r.lastIndexOf('_')
    if (cut <= 0) return
    var id = r.substring(0, cut), kind = r.substring(cut + 1)
    if (id === 'g') return                         // bd_g_hp / bd_g_dmg — 위에서 처리한 전역
    if (kind !== 'hp' && kind !== 'dmg' && kind !== 'abs') return
    var v = 0
    try { v = st.getInt('bd_' + r) } catch (e) { lsWarn('ls_migrate:bd:read', e); return }
    if (v <= 0) return                             // 0 = 오버라이드 없음
    if (!pend[id]) pend[id] = { hp: 0, dmg: 0, abs: 0 }
    pend[id][kind] = v
  })

  Object.keys(pend).forEach(id => {
    try {
      var e2 = pend[id]
      if (e2.hp > 0 || e2.dmg > 0) LS.setBossDiff(server, id, e2.hp, e2.dmg)
      if (e2.abs > 0) LS.setBossAbs(server, id, e2.abs)
      n++
    } catch (e) { lsWarn('ls_migrate:bd:' + id, e) }
  })

  st.putBoolean(MG_BD_FLAG, true)
  return n
}

// ── 칭호 (이관 5단계) ──
// 옛 키: `titles_<이름>` (CSV) · `title_active_<이름>`.
// 접두사가 겹친다 — `title_active_철수` 는 `titles_` 로 시작하지 **않으니** 안 겹치지만,
// 반대로 `titles_` 훑기가 `title_active_` 를 잡을 일도 없다. 그래도 명시적으로 걸러 둔다:
// 접두사 훑기에서 한쪽이 다른 쪽을 삼키는 사고는 3단계에서 `relic_` / `relic_altar_` 로 이미 겪었다.
function mgRunTitle(server, force) {
  const st = mgStore(server)
  if (!force && st.getBoolean(MG_TT_FLAG)) return null

  var n = 0
  mgNamesWith(server, 'titles_').forEach(who => {
    try {
      var csv = String(st.getString('titles_' + who) || '')
      if (!csv) return
      csv.split(',').forEach(k => {
        var key = String(k).trim()
        if (key) LS.grantTitle(server, who, key)
      })
      // 착용 칭호. grantTitle 이 첫 칭호를 자동 활성으로 잡아두므로 저장값으로 덮는다.
      // 보유 목록에 없으면 모드가 거절한다 — 그게 맞다(유령 칭호를 옮기지 않는다).
      var act = String(st.getString('title_active_' + who) || '')
      if (act) LS.setActiveTitle(server, who, act)
      n++
    } catch (e) { lsWarn('ls_migrate:title:' + who, e) }
  })

  st.putBoolean(MG_TT_FLAG, true)
  return n
}

// ── 현상금 (이관 5단계) ──
// 옛 저장: `bt_day`(주기 번호) · `bt<n>`(= `kind|target|name|need|reward`) · `bt<n>_have` · `bt<n>_done`.
// 굳이 옮기는 이유: 안 옮기면 **게시판이 빈 채로 최대 3일**이다(주기가 바뀌어야 다시 굴린다).
// 그 사이 「오늘 할 것」이 사라지는데, 그건 이 시스템이 존재하는 이유 자체다.
//
// ※ 조각이 다섯이 아니면 버린다. 옛 코드가 그렇게 읽고 있었으니 여기서 되살릴 방법도 없다 —
//   이름에 `|` 가 든 현상금은 이미 그때 사라졌다. 옮기면서 새로 만들어 낼 수는 없다.
function mgRunBounty(server, force) {
  const st = mgStore(server)
  if (!force && st.getBoolean(MG_BT_FLAG)) return null

  var n = 0
  try {
    var cyc = st.getInt('bt_day')
    if (cyc > 0) LS.setBountyCycle(server, cyc)
  } catch (e) { lsWarn('ls_migrate:bounty:cycle', e) }

  for (var i = 1; i <= 3; i++) {
    try {
      var raw = String(st.getString('bt' + i) || '')
      if (!raw) continue
      var p = raw.split('|')
      if (p.length !== 5) { lsWarn('ls_migrate:bounty:형식', new Error('bt' + i + ' = ' + raw)); continue }
      LS.postBounty(server, i, p[0], p[1], p[2], parseInt(p[3]) || 1, parseInt(p[4]) || 0)
      // postBounty 가 진행도를 0 으로 되돌리므로 순서가 중요하다 — 게시 → 진행도 → 완료.
      var hv = st.getInt('bt' + i + '_have')
      if (hv > 0) LS.addBountyProgress(server, i, hv)
      if (st.getInt('bt' + i + '_done') > 0) LS.completeBounty(server, i)
      n++
    } catch (e) { lsWarn('ls_migrate:bounty:' + i, e) }
  }

  st.putBoolean(MG_BT_FLAG, true)
  return n
}

ServerEvents.loaded(event => {
  const server = event.server
  if (!server) return
  var mgR = null
  try { mgR = mgRun(server, false) } catch (e) { lsWarn('ls_migrate:run', e) }
  if (mgR) {
    // 아무것도 없으면(새 월드) 조용히 넘어간다 — 옮길 게 없는 건 사고가 아니다.
    if (mgR.fate + mgR.relic + mgR.star + mgR.altar === 0) console.log('[LS-MIGRATE] 성장 — 옛 키 없음 (새 월드로 본다)')
    else console.log(`[LS-MIGRATE] 성장 → 모드 장부 — 가호 ${mgR.fate} · 유물 ${mgR.relic} · 각성 ${mgR.star} · 제단 ${mgR.altar}`)
  }

  var mgS = null
  try { mgS = mgRunSiege(server, false) } catch (e) { lsWarn('ls_migrate:siege', e) }
  if (mgS !== null) {
    if (mgS === 0) console.log('[LS-MIGRATE] 공성 — 옛 키 없음 (새 월드로 본다)')
    else console.log(`[LS-MIGRATE] 공성 → 모드 장부 — 값 ${mgS}개 · ${LS.siegeSummary(server)}`)
  }

  var mgB = null
  try { mgB = mgRunBossDiff(server, false) } catch (e) { lsWarn('ls_migrate:bossdiff', e) }
  if (mgB !== null) {
    if (mgB === 0) console.log('[LS-MIGRATE] 보스 난이도 — 옛 키 없음 (파일 값만 쓰고 있었다)')
    else console.log(`[LS-MIGRATE] 보스 난이도 → 모드 장부 — ${mgB}건 · ${LS.bossDiffSummary(server)}`)
  }

  // ⚠️ 이 옮기기는 `ls_title.js` 의 이름표 복구보다 **먼저** 끝나야 한다.
  // 지금은 파일 이름 순서(ls_migrate < ls_title)로 핸들러 등록 순서가 그렇게 잡혀서 맞는다.
  // 어긋나도 치명적이진 않다 — 접속할 때 한 번 더 붙이므로 한 판 늦게 보일 뿐이다.
  var mgT = null
  try { mgT = mgRunTitle(server, false) } catch (e) { lsWarn('ls_migrate:title', e) }
  if (mgT !== null) {
    if (mgT === 0) console.log('[LS-MIGRATE] 칭호 — 옛 키 없음 (새 월드로 본다)')
    else console.log(`[LS-MIGRATE] 칭호 → 모드 장부 — ${mgT}명 · ${LS.titleSummary(server)}`)
  }

  var mgBt = null
  try { mgBt = mgRunBounty(server, false) } catch (e) { lsWarn('ls_migrate:bounty', e) }
  if (mgBt !== null) {
    if (mgBt === 0) console.log('[LS-MIGRATE] 현상금 — 옛 키 없음 (새 월드로 본다)')
    else console.log(`[LS-MIGRATE] 현상금 → 모드 장부 — ${mgBt}건 · ${LS.bountySummary(server)}`)
  }
})

ServerEvents.commandRegistry(event => {
  const { commands: Commands } = event
  // 장부에 실제로 값이 있는지 본다. 이관에서 제일 무서운 실패는 «옮겼다고 찍혔는데 비어 있는» 것이다.
  event.register(Commands.literal('lsdata').requires(s => s.hasPermission(2))
    .executes(ctx => {
      const s = ctx.source.server
      ctx.source.sendSystemMessage(Text.of('§6═══ 모드 장부 §6═══'))
      ctx.source.sendSystemMessage(Text.of(`§7성장 §8— ${LS.heroSummary(s)}`))
      ctx.source.sendSystemMessage(Text.of(`§7공성 §8— ${LS.siegeSummary(s)}`))
      ctx.source.sendSystemMessage(Text.of(
        `§7성역 §8— ${LS.hasSanctuary(s) ? LS.sanctuaryX(s) + ', ' + LS.sanctuaryY(s) + ', ' + LS.sanctuaryZ(s) : '미지정'}`
        + ` §8· 관문 §7${LS.progress(s)}/4§8 · 금고 §7${LS.treasury(s)}`))
      ctx.source.sendSystemMessage(Text.of(`§7보스 난이도 §8— ${LS.bossDiffSummary(s)}`))
      ctx.source.sendSystemMessage(Text.of(`§7칭호 §8— ${LS.titleSummary(s)}`))
      ctx.source.sendSystemMessage(Text.of(`§7현상금 §8— ${LS.bountySummary(s)}`))
      // 옮기기를 따로 표시한다 — 하나만 됐을 때 그걸 알아볼 수 있어야 한다.
      ctx.source.sendSystemMessage(Text.of(
        `§8옛 키 옮기기 — 성장 ${mgStore(s).getBoolean(MG_FLAG) ? '§7완료' : '§c아직'}`
        + `§8 · 공성 ${mgStore(s).getBoolean(MG_SIEGE_FLAG) ? '§7완료' : '§c아직'}`
        + `§8 · 보스난이도 ${mgStore(s).getBoolean(MG_BD_FLAG) ? '§7완료' : '§c아직'}`
        + `§8 · 칭호 ${mgStore(s).getBoolean(MG_TT_FLAG) ? '§7완료' : '§c아직'}`
        + `§8 · 현상금 ${mgStore(s).getBoolean(MG_BT_FLAG) ? '§7완료' : '§c아직'}`))
      return 1
    })
    // 다시 옮긴다. 멱등이라 여러 번 돌려도 결과가 같다 — 옛 키가 아직 남아 있는 한.
    .then(Commands.literal('remigrate').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      var r = null, rs = null, rb = null, rt = null, rbt = null
      try { r = mgRun(s, true) } catch (e) { lsWarn('ls_migrate:cmd', e) }
      try { rs = mgRunSiege(s, true) } catch (e) { lsWarn('ls_migrate:cmd:siege', e) }
      try { rb = mgRunBossDiff(s, true) } catch (e) { lsWarn('ls_migrate:cmd:bossdiff', e) }
      try { rt = mgRunTitle(s, true) } catch (e) { lsWarn('ls_migrate:cmd:title', e) }
      try { rbt = mgRunBounty(s, true) } catch (e) { lsWarn('ls_migrate:cmd:bounty', e) }
      if (r === null && rs === null && rb === null && rt === null && rbt === null) { ctx.source.sendSystemMessage(Text.of('§c실패 — 로그 확인')); return 0 }
      if (r) ctx.source.sendSystemMessage(Text.of(
        `§a성장 다시 옮김 §7— 가호 ${r.fate} · 유물 ${r.relic} · 각성 ${r.star} · 제단 ${r.altar}`))
      if (rs !== null) ctx.source.sendSystemMessage(Text.of(`§a공성 다시 옮김 §7— 값 ${rs}개`))
      if (rb !== null) ctx.source.sendSystemMessage(Text.of(`§a보스 난이도 다시 옮김 §7— ${rb}건`))
      if (rt !== null) ctx.source.sendSystemMessage(Text.of(`§a칭호 다시 옮김 §7— ${rt}명`))
      if (rbt !== null) ctx.source.sendSystemMessage(Text.of(`§a현상금 다시 옮김 §7— ${rbt}건`))
      ctx.source.sendSystemMessage(Text.of(`§8지금 장부: ${LS.heroSummary(s)}`))
      ctx.source.sendSystemMessage(Text.of(`§8            ${LS.siegeSummary(s)}`))
      return 1
    })))
})

console.log('[Last Stardust] 이관 도우미 로드됨 — /lsdata · /lsdata remigrate')
