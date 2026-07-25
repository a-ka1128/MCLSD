// Last Stardust — 칭호 시스템 (Titles)  [명예/정체성]
// 히든·업적으로 칭호를 획득하고, 활성 칭호를 이름표 접두사로 표시(스코어보드 팀 prefix).
// 채팅/네임태그/탭 목록 모두에 [칭호] 접두가 붙는다. 다른 시스템(유물·피날레·원정)이 ttGrant로 부여.
// 저장(공유 persistentData): titles_<user>(CSV) · title_active_<user>
// 팀 이름 = 유저명(≤16자, MC 규격). 함수는 tt 접두 = 타 파일과 충돌 없음.

function ttStore(server) { return server.overworld().persistentData }
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
  night_lord: { display: '밤의 지배자', color: 'dark_purple' },
  rift_conqueror: { display: '균열 정복자', color: 'red' },
  savior: { display: '구원자', color: 'yellow' },
  first_blood: { display: '첫 피', color: 'gray' }
}

function ttOwned(server, uname) {
  const s = String(ttStore(server).getString('titles_' + uname) || '')
  return s ? s.split(',') : []
}
function ttActive(server, uname) { return String(ttStore(server).getString('title_active_' + uname) || '') }

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
function ttGrant(server, uname, key) {
  if (!TITLES[key]) return
  const owned = ttOwned(server, uname)
  if (owned.indexOf(key) >= 0) return
  owned.push(key)
  ttStore(server).putString('titles_' + uname, owned.join(','))
  const t = TITLES[key]
  ttSay(server, `§6✦ 칭호 획득: §f${uname} §7— §e[${t.display}]`)
  server.runCommandSilent(`execute as ${uname} at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 0.7 1.3`)
  if (!ttActive(server, uname)) {
    ttStore(server).putString('title_active_' + uname, key)
    ttApply(server, uname, key)
  }
}

// 접속 시 활성 칭호 재적용 (팀은 재시작 시 사라질 수 있음)
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
      if (ttOwned(s, p.username).indexOf(key) < 0) { ctx.source.sendSystemMessage(Text.of('§c보유하지 않은 칭호')); return 0 }
      ttStore(s).putString('title_active_' + p.username, key)
      ttApply(s, p.username, key)
      ctx.source.sendSystemMessage(Text.of(`§a활성 칭호: §e[${TITLES[key].display}]`)); return 1
    })))
    .then(Commands.literal('clear').executes(ctx => {
      const s = ctx.source.server; const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      ttStore(s).putString('title_active_' + p.username, '')
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
        })))))
})

console.log('[Last Stardust] 칭호 시스템 로드됨 — ' + Object.keys(TITLES).length + '종')
