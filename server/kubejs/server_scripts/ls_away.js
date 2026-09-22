// Last Stardust — 부재중 보관함 (Away Box)  [「없는 동안 이런 일이 있었다」]
//
// ── 왜 생겼나 ──
// 2026-08-13 점검: 보상이 거의 다 **그때 접속해 있던 사람만** 받는 구조였다.
//   지갑·주간금고·칭호 → `server.players.forEach`
//   별의 파편          → 성역 «바닥»에 떨어지고 5분 뒤 사라진다
// 친구끼리 각자 일정이 다른 서버에서 이건 「바쁜 사람이 영영 밀린다」로 굳는다.
// 그리고 밀린 사람은 그걸 «불공평»으로 못 읽는다 — 그냥 자기가 약하다고 느낀다.
//
// ── 결정 (2026-08-13) ──
// **부재자도 전액 똑같이 받는다.** 공성은 보상 40 Ducat 때문에 모이는 게 아니라
// 재미있어서 모인다. 뒤처지는 고통이 무임승차의 불쾌함보다 훨씬 크다.
//
// ── 이미 되어 있던 것 ──
// 지갑·칭호·주간금고는 **이름 문자열을 받는 API 가 이미 있었다**
// (`LS.walletAdd(server, name, n)` · `LS.grantTitle` · `vtAdd`). 오프라인 지급이
// 처음부터 가능했는데 **호출부가 접속자만 돌고 있었다.** 그래서 이 파일이 새로 만드는 건
// 셋뿐이다 — **명단 · 아이템 보관함 · 부재중 기록.**
//
// ⚠️ 상태는 KubeJS persistentData 다 (`ls_rift.js`·`ls_ruin.js` 와 같은 자리).
//    모드를 안 건드리므로 jar 재빌드가 필요 없다.

function awStore(server) { return server.overworld().persistentData }
function awGetS(server, k) { return String(awStore(server).getString(k) || '') }
function awSetS(server, k, v) { awStore(server).putString(k, v) }
function awGetI(server, k) { return awStore(server).getInt(k) }
function awSetI(server, k, v) { awStore(server).putInt(k, v) }

// ⚠️ 상수는 전부 핸들러 «위»에 (Rhino top-level const 함정)
const AW_DELAY = 40          // 로그인 후 지연 틱. 그 전엔 클라가 아직 월드에 들어오는 중이라
                             // 채팅도 아이템도 조용히 묻힌다 (`ls_ascend.js` 로그인 훅과 같은 값).
const AW_LOG_MAX = 10        // 보관할 «사건» 줄 수. 넘으면 오래된 것부터 버린다 —
                             // 한 달 비운 사람에게 200줄을 쏟으면 아무도 안 읽는다.
const AW_SEP = ''      // 줄 구분자. 한글·색코드와 안 겹치는 제어문자를 쓴다.

// 반복되는 일은 «줄»이 아니라 «횟수»로 센다. 「공성 격퇴」가 12줄 흐르면 요약이 아니다.
const AW_COUNTERS = [
  { k: 'siege', label: '공성 격퇴', color: '§a' },
  { k: 'grand', label: '대공세 격퇴', color: '§6' },
  { k: 'boss', label: '관문 보스 처치', color: '§d' },
  { k: 'node', label: '균열 노드 파괴', color: '§5' }
]

function awSay(server, name, text) {
  var p = lsPlayerByName(server, name)
  if (p) p.tell(Text.of(text))
}

// ── 명단 ──
// 「접속한 적 있는 사람」이 곧 구성원이다. 화이트리스트를 읽지 않는 이유:
// 넣어만 두고 한 번도 안 들어온 사람에게까지 보상이 쌓이면, 그 사람이 처음 들어온 날
// 한 달치가 쏟아진다. 그건 선물이 아니라 «건너뛴 진행»이다.
function awMembers(server) {
  var s = awGetS(server, 'aw_roster')
  return s ? s.split(',') : []
}

function awJoin(server, name) {
  var list = awMembers(server)
  if (list.indexOf(name) >= 0) return false
  list.push(name)
  awSetS(server, 'aw_roster', list.join(','))
  console.log(`[LS-AWAY] 명단 추가: ${name} (총 ${list.length}명)`)
  return true
}

// 명단 전원에게 fn(name, online) 을 돌린다. **보상 분배의 유일한 입구다** —
// 호출부가 각자 `server.players.forEach` 를 쓰면 그 순간 부재자가 다시 빠진다.
function awAll(server, fn) {
  var list = awMembers(server)
  for (var awAi = 0; awAi < list.length; awAi++) {
    try { fn(list[awAi], !!lsPlayerByName(server, list[awAi])) }
    catch (e) { lsWarn('ls_away:all', e) }
  }
}

// ── 아이템 ──
// 접속 중이면 바로 인벤토리로, 아니면 보관함에 쌓는다.
// 형식: `id|개수;id|개수` — 아이템 id 에 `:` 가 들어가므로 구분자로 `|` 를 쓴다.
function awItem(server, name, id, n) {
  if (!n || n <= 0) return
  var p = lsPlayerByName(server, name)
  if (p) {
    // `/give` 는 인벤이 꽉 차면 발밑에 떨어뜨린다 — 잃지는 않는다.
    server.runCommandSilent(`give ${name} ${id} ${n}`)
    return
  }
  var key = 'aw_i_' + name
  var cur = awGetS(server, key)
  var parts = cur ? cur.split(';') : []
  var merged = false
  for (var awIi = 0; awIi < parts.length && !merged; awIi++) {
    var kv = parts[awIi].split('|')
    if (kv[0] === id) { parts[awIi] = id + '|' + (Number(kv[1]) + n); merged = true }
  }
  if (!merged) parts.push(id + '|' + n)
  awSetS(server, key, parts.join(';'))
}

// ── 횟수 ──
function awBump(server, name, key, n) {
  var p = lsPlayerByName(server, name)
  if (p) return // 접속 중이었으면 직접 봤다. 요약에 넣을 이유가 없다.
  var k = 'aw_c_' + name + '_' + key
  awSetI(server, k, awGetI(server, k) + (n || 1))
}

// ── 사건 한 줄 ──
// 반복되지 않는 일(보스 이름, 노드 이름)만. 반복되는 건 awBump 로 센다.
function awLog(server, name, line) {
  var p = lsPlayerByName(server, name)
  if (p) return
  var key = 'aw_l_' + name
  var cur = awGetS(server, key)
  var lines = cur ? cur.split(AW_SEP) : []
  lines.push(line)
  while (lines.length > AW_LOG_MAX) lines.shift()
  awSetS(server, key, lines.join(AW_SEP))
}

// ── 전달 ──
function awDeliver(server, name) {
  var p = lsPlayerByName(server, name)
  if (!p) return

  // 1. 무슨 일이 있었나
  var head = []
  for (var awDi = 0; awDi < AW_COUNTERS.length; awDi++) {
    var c = AW_COUNTERS[awDi]
    var v = awGetI(server, 'aw_c_' + name + '_' + c.k)
    if (v > 0) head.push(`${c.color}${c.label} §f${v}§7회`)
    awSetI(server, 'aw_c_' + name + '_' + c.k, 0)
  }
  var logRaw = awGetS(server, 'aw_l_' + name)
  var lines = logRaw ? logRaw.split(AW_SEP) : []
  awSetS(server, 'aw_l_' + name, '')

  // 2. 보관함
  var itemRaw = awGetS(server, 'aw_i_' + name)
  var items = itemRaw ? itemRaw.split(';') : []
  awSetS(server, 'aw_i_' + name, '')

  if (!head.length && !lines.length && !items.length) return

  p.tell(Text.of('§8§m                                        '))
  p.tell(Text.of('§b✦ §f없는 동안 이런 일이 있었습니다'))
  if (head.length) p.tell(Text.of('§7   ' + head.join(' §8· ')))
  for (var awDj = 0; awDj < lines.length; awDj++) p.tell(Text.of('§7   · ' + lines[awDj]))

  if (items.length) {
    p.tell(Text.of('§e✦ 맡아둔 보상'))
    for (var awDk = 0; awDk < items.length; awDk++) {
      var kv = items[awDk].split('|')
      if (!kv[0] || !Number(kv[1])) continue
      server.runCommandSilent(`give ${name} ${kv[0]} ${kv[1]}`)
      p.tell(Text.of(`§7   · §f${kv[0]} §7× §e${kv[1]}`))
    }
    p.tell(Text.of('§8   인벤이 가득 찼으면 발밑에 떨어집니다.'))
  }
  p.tell(Text.of('§8§m                                        '))
  server.runCommandSilent(`execute as ${name} at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 0.8 1.1`)
  console.log(`[LS-AWAY] deliver ${name}: ${head.length}종 요약 · ${lines.length}줄 · ${items.length}묶음`)
}

// ── 로그인 ──
PlayerEvents.loggedIn(event => {
  const p = event.player
  if (!p) return
  const server = p.server
  if (!server) return
  const uname = String(p.username)
  server.scheduleInTicks(AW_DELAY, () => {
    try {
      awJoin(server, uname)
      awDeliver(server, uname)
    } catch (e) { lsWarn('ls_away:login', e) }
  })
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event

  event.register(Commands.literal('away')
    .executes(ctx => {
      const s = ctx.source.server
      const list = awMembers(s)
      ctx.source.sendSystemMessage(Text.of(`§6═══ ✦ 부재중 보관함 §7(명단 ${list.length}명) §6═══`))
      if (!list.length) { ctx.source.sendSystemMessage(Text.of('§8아직 아무도 접속한 적이 없습니다.')); return 1 }
      for (var awCi = 0; awCi < list.length; awCi++) {
        var n = list[awCi]
        var on = !!lsPlayerByName(s, n)
        var box = awGetS(s, 'aw_i_' + n)
        var cnt = 0
        for (var awCj = 0; awCj < AW_COUNTERS.length; awCj++) cnt += awGetI(s, 'aw_c_' + n + '_' + AW_COUNTERS[awCj].k)
        ctx.source.sendSystemMessage(Text.of(
          `${on ? '§a●' : '§8○'} §f${n} §7— 보관 §e${box ? box.split(';').length : 0}§7묶음 · 사건 §e${cnt}§7건`))
      }
      return 1
    })
    // 명단에서 뺀다. 안 올 사람에게 계속 쌓는 걸 멈추는 용도다.
    .then(Commands.literal('forget').requires(s => s.hasPermission(2))
      .then(Commands.argument('name', Arguments.GREEDY_STRING.create(event)).executes(ctx => {
        const s = ctx.source.server
        const name = Arguments.STRING.getResult(ctx, 'name')
        var list = awMembers(s)
        var i = list.indexOf(name)
        if (i < 0) { ctx.source.sendSystemMessage(Text.of('§c명단에 없습니다: ' + name)); return 0 }
        list.splice(i, 1)
        awSetS(s, 'aw_roster', list.join(','))
        awSetS(s, 'aw_i_' + name, '')
        awSetS(s, 'aw_l_' + name, '')
        ctx.source.sendSystemMessage(Text.of(`§a${name} 을(를) 명단에서 뺐습니다. §8(보관함도 비움)`))
        return 1
      }))))
})

console.log('[Last Stardust] 부재중 보관함 로드됨 — 접속자·부재자 전액 동일 지급')
