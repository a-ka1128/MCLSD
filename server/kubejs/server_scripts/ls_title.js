// Last Stardust — 칭호 시스템 (Titles)  [명예/정체성]
// 히든·업적으로 칭호를 획득하고, 활성 칭호를 이름표 접두사로 표시(스코어보드 팀 prefix).
// 채팅/네임태그/탭 목록 모두에 [칭호] 접두가 붙는다. 다른 시스템(유물·피날레·원정)이 ttGrant로 부여.
// 팀 이름 = 유저명(≤16자, MC 규격). 함수는 tt 접두 = 타 파일과 충돌 없음.
//
// ── 저장: 이관 5단계로 모드가 소유한다 (2026-08-06) ──
// 옛 방식은 `titles_<이름>` 에 CSV 한 줄. 중복을 막는 곳이 `ttGrant` 하나뿐이라, 다른 경로로
// 한 줄만 써도 같은 칭호가 두 번 들어갔다. 이제는 모드 쪽이 Set 이라 그 자리가 없다.
// 그리고 **활성 칭호는 반드시 보유 목록 안**이라는 불변식도 그쪽이 건다.
//
// ※ 카탈로그(아래 TITLES)는 여기 남는다 — 문구·색은 `/reload` 로 고치는 값이다.
//   모드가 갖는 건 «누가 뭘 가졌나» 뿐이다.
//
// ⚠️ `ttGrant` 는 네 파일이 부른다 (ls_relic · ls_rescue · ls_rift · ls_siege).
//   전역 공유 스코프라 이름 그대로 두고 속만 바꿨다 — 호출부는 손댈 필요가 없다.

function ttSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }

// 칭호 카탈로그: slug → { display, color }
const TITLES = {
  guardian_relic: { display: '하늘을 떠받친 자', color: 'aqua' },
  hunter_relic: { display: '별을 쏘는 자', color: 'green' },
  sage_relic: { display: '별지기', color: 'light_purple' },
  pioneer_relic: { display: '거인을 부순 자', color: 'gold' },
  gunner_relic: { display: '태양을 쏘는 자', color: 'yellow' },
  healer_relic: { display: '별을 되살리는 자', color: 'white' },
  assassin_relic: { display: '어둠에서 온 자', color: 'dark_purple' },
  lancer_relic: { display: '운명을 꿰뚫는 자', color: 'red' },
  hecate_relic: { display: '횃불을 든 자', color: 'dark_aqua' },
  harmonia_relic: { display: '흩어진 이들을 모으는 자', color: 'light_purple' },
  night_lord: { display: '밤의 지배자', color: 'dark_purple' },
  rift_conqueror: { display: '균열 정복자', color: 'red' },
  savior: { display: '구원자', color: 'yellow' },
  first_blood: { display: '첫 피', color: 'gray' }
}

// 다리 호출은 감싼다 — 여기서 터지면 접속 이벤트가 죽어 **가호 재적용까지 같이 멈춘다.**
// 빈 목록으로 내려가면 칭호만 안 보이고 나머지는 돈다.
function ttOwned(server, uname) {
  try {
    // `''.split(',')` 은 길이 1 짜리 배열을 준다 — 빈 문자열을 먼저 거른다(4단계 노드와 같은 함정).
    var s = String(LS.titlesOwnedCsv(server, uname) || '')
    return s ? s.split(',') : []
  } catch (e) { lsWarn('ls_title:owned', e); return [] }
}
function ttActive(server, uname) {
  try { return String(LS.activeTitle(server, uname) || '') }
  catch (e) { lsWarn('ls_title:active', e); return '' }
}

// 이름표 접두사 적용 (팀 prefix)
function ttApply(server, uname, key) {
  const t = TITLES[key]
  server.runCommandSilent(`team add ${uname}`)
  if (t) {
    server.runCommandSilent(`team modify ${uname} prefix {"text":"[${t.display}] ","color":"${t.color}"}`)
    server.runCommandSilent(`team join ${uname} ${uname}`)
  }
}
function ttClearPrefix(server, uname) {
  server.runCommandSilent(`team add ${uname}`)
  server.runCommandSilent(`team modify ${uname} prefix ""`)
}

// 칭호 부여 (타 시스템에서 호출) — 이미 있으면 무시, 첫 칭호면 자동 활성화
// 「새로 받았나」 판정은 모드가 한다(grantTitle 이 false 를 주면 이미 있던 것). 예전엔 여기서
// 목록을 읽어 비교하고 다시 썼는데, 읽기와 쓰기 사이가 벌어져 있어 두 경로가 겹치면 한쪽이 덮였다.
function ttGrant(server, uname, key) {
  if (!TITLES[key]) return
  var fresh = false
  try { fresh = !!LS.grantTitle(server, uname, key) }
  catch (e) { lsWarn('ls_title:grant', e); return }
  if (!fresh) return
  const t = TITLES[key]
  ttSay(server, `§6✦ 칭호 획득: §f${uname} §7— §e[${t.display}]`)
  server.runCommandSilent(`execute as ${uname} at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 0.7 1.3`)
  // 첫 칭호 자동 활성은 모드가 이미 잡아뒀다 — 여기서는 이름표에 반영만 한다.
  if (ttActive(server, uname) === key) ttApply(server, uname, key)
}

// ── 서버 기동 시 착용자 전원의 이름표 복구 ──
// 예전엔 접속 이벤트에서 한 명씩만 되살렸다. 팀 정의는 scoreboard 에 남지만 재시작으로
// 사라질 수 있고, 그러면 **접속하지 않은 사람의 이름표가 남들 눈에 빈 채로** 남는다.
// 착용자 목록은 이제 장부가 갖고 있으니 한 번에 돈다.
ServerEvents.loaded(event => {
  const server = event.server
  if (!server) return
  try {
    String(LS.titleWearersCsv(server) || '').split(',').forEach(n => {
      var who = String(n).trim()
      if (!who) return
      var key = ttActive(server, who)
      if (key && TITLES[key]) ttApply(server, who, key)
    })
  } catch (e) { lsWarn('ls_title:restore', e) }
})

// 접속 시에도 한 번 더 — 기동 후에 칭호를 받은 사람, 팀이 어떤 이유로 지워진 경우를 덮는다.
PlayerEvents.loggedIn(event => {
  const player = event.player; const server = event.server
  if (!player || !server) return
  const key = ttActive(server, player.username)
  if (key) ttApply(server, player.username, key)
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  const keyArg = () => Commands.argument('key', Arguments.STRING.create(event))
    .suggests((ctx, b) => { Object.keys(TITLES).forEach(k => b.suggest(k)); return b.buildFuture() })

  event.register(Commands.literal('title')
    .executes(ctx => {
      const s = ctx.source.server; const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      const owned = ttOwned(s, p.username); const active = ttActive(s, p.username)
      ctx.source.sendSystemMessage(Text.of(`§6═══ ✦ 칭호 §7(보유 ${owned.length}) §6═══`))
      if (!owned.length) { ctx.source.sendSystemMessage(Text.of('§8아직 없음 — 히든·업적으로 획득')); return 1 }
      owned.forEach(k => {
        const t = TITLES[k]; if (!t) return
        const mark = k === active ? '§a▶' : '§8·'
        ctx.source.sendSystemMessage(Text.of(`${mark} §f[${t.display}] §8(${k})`))
      })
      ctx.source.sendSystemMessage(Text.of('§8/title set <key> · /title clear'))
      return 1
    })
    .then(Commands.literal('set').then(keyArg().executes(ctx => {
      const s = ctx.source.server; const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      const key = Arguments.STRING.getResult(ctx, 'key')
      // 보유 여부 판정은 모드가 한다 — 여기서 또 검사하면 규칙이 두 곳이 된다.
      var ok = false
      try { ok = !!LS.setActiveTitle(s, p.username, key) } catch (e) { lsWarn('ls_title:set', e) }
      if (!ok) { ctx.source.sendSystemMessage(Text.of('§c보유하지 않은 칭호')); return 0 }
      ttApply(s, p.username, key)
      ctx.source.sendSystemMessage(Text.of(`§a활성 칭호: §e[${TITLES[key].display}]`)); return 1
    })))
    .then(Commands.literal('clear').executes(ctx => {
      const s = ctx.source.server; const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      try { LS.setActiveTitle(s, p.username, '') } catch (e) { lsWarn('ls_title:clear', e) }
      ttClearPrefix(s, p.username)
      ctx.source.sendSystemMessage(Text.of('§7칭호 표시 해제')); return 1
    }))
    .then(Commands.literal('grant').requires(s => s.hasPermission(2))
      .then(Commands.argument('target', Arguments.STRING.create(event))
        .then(keyArg().executes(ctx => {
          const s = ctx.source.server
          const target = Arguments.STRING.getResult(ctx, 'target')
          const key = Arguments.STRING.getResult(ctx, 'key')
          if (!TITLES[key]) { ctx.source.sendSystemMessage(Text.of('§c없는 칭호 key')); return 0 }
          ttGrant(s, target, key)
          ctx.source.sendSystemMessage(Text.of(`§a${target} → [${TITLES[key].display}]`)); return 1
        }))))
    // ── 되돌리기 (2026-08-06) ──
    // 주는 것만 있고 뺏는 것이 없었다. `/title grant` 를 잘못 쓰면 되돌릴 방법이 세이브를
    // 손으로 고치는 것뿐이었고, 그건 방법이 아니다.
    .then(Commands.literal('revoke').requires(s => s.hasPermission(2))
      .then(Commands.argument('target', Arguments.STRING.create(event))
        .then(keyArg().executes(ctx => {
          const s = ctx.source.server
          const target = Arguments.STRING.getResult(ctx, 'target')
          const key = Arguments.STRING.getResult(ctx, 'key')
          var ok = false
          try { ok = !!LS.revokeTitle(s, target, key) } catch (e) { lsWarn('ls_title:revoke', e) }
          if (!ok) { ctx.source.sendSystemMessage(Text.of('§c그 사람이 안 가진 칭호다')); return 0 }
          // 착용 중이던 것이면 모드가 착용을 같이 풀었다 — 이름표도 여기서 지운다.
          if (!ttActive(s, target)) ttClearPrefix(s, target)
          ctx.source.sendSystemMessage(Text.of(`§7${target} 에게서 [${TITLES[key] ? TITLES[key].display : key}] 회수`)); return 1
        }))))
    // 사람 하나를 장부에서 통째로 지운다 — 시험용 가짜 이름을 치울 때.
    .then(Commands.literal('forget').requires(s => s.hasPermission(2))
      .then(Commands.argument('target', Arguments.STRING.create(event)).executes(ctx => {
        const s = ctx.source.server
        const target = Arguments.STRING.getResult(ctx, 'target')
        var n = 0
        try { n = LS.forgetTitles(s, target) | 0 } catch (e) { lsWarn('ls_title:forget', e) }
        if (!n) { ctx.source.sendSystemMessage(Text.of('§7그 이름은 장부에 없다')); return 0 }
        ttClearPrefix(s, target)
        ctx.source.sendSystemMessage(Text.of(`§7${target} 을(를) 칭호 장부에서 지웠다 §8(칭호 ${n}개)`)); return 1
      }))))
})

console.log('[Last Stardust] 칭호 시스템 로드됨 — ' + Object.keys(TITLES).length + '종')
