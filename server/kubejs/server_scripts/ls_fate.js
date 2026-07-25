// Last Stardust — 별의 가호 (Starlight Fate)  [캐릭터 정체성]
// 첫 접속 후 1회 선택하는 가호(소프트 클래스). 고유 패시브 + 시작 키트.
// "나는 누구인가"를 첫 순간에 정한다 — Cisco's/Prominence의 Fate 패턴.
// 패시브는 /attribute modifier(고정 ID)로 적용 — 재접속에도 유지, 중복 적용은 명령이 거부.
// 저장: fate_<uuid> (선택한 가호 키)

function ftStore(server) { return server.overworld().persistentData }
function ftSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }

// ── 공용 시작 키트 ──
// 가호는 "정체성"만 정한다 — 출발 장비는 8종 모두 동일하게 준다.
// 직업별로 시작템이 다르면 고르기 전에 장비를 보고 고르게 돼서, 가호 선택이
// 컨셉이 아니라 아이템 비교가 되어버린다. 실제 무기는 유물(제단)에서 갈린다.
const STARTER_KIT = [
  ['minecraft:stone_pickaxe', 1],
  ['minecraft:stone_axe', 1],
  ['minecraft:stone_sword', 1],
  ['minecraft:cooked_beef', 16]
]

// 가죽 갑옷은 가호 색으로 염색해서 준다 — 성능은 8종 모두 같고 겉모습만 소속을 드러낸다.
// 1.21.1 은 NBT 대신 아이템 컴포넌트를 쓴다: dyed_color={rgb:<10진수>}
// (JS 숫자를 문자열에 넣으면 자동으로 10진수가 되므로 0xRRGGBB 그대로 둬도 된다)
const ARMOR_PIECES = ['leather_helmet', 'leather_chestplate', 'leather_leggings', 'leather_boots']
function dyedArmor(color) {
  return ARMOR_PIECES.map(part =>
    [`minecraft:${part}[dyed_color={rgb:${color},show_in_tooltip:false}]`, 1])
}

// ※ icon 은 인게임 채팅·타이틀에 뜨므로 반드시 BMP(U+FFFF 이하) 기호만 쓴다.
//    마크 기본 폰트는 Unifont로 BMP까지만 커버해서, ▦➶✚ 같은 보조 평면 이모지는 □로 깨진다.
//    (디스코드 카드 discord/post_*.py 는 이모지가 정상 렌더되므로 거긴 그대로 둔다.)
// ── 가호 정의 ──
// attr: [속성, 수치, 방식] — 1.21.1은 generic. 접두사. mod ID는 last_stardust:fate_<key>
// story[0] = 한 줄 요약(선택 시 subtitle에 뜸) · 나머지는 /fate 에서 이어서 출력
const FATES = {
  guardian: {
    name: '아틀라스', icon: '▣', color: 0xC9D6E8, // 은백
    desc: '넉백 저항 +0.5 · 기본 스타터킷과 함께 시작',
    story: [
      '무너지는 하늘을 홀로 떠받친 자의 가호.',
      '아군을 지키는 가장 튼튼한 방패이자, 어떤 충격에도 한 걸음 물러서지 않는 벽이 된다.',
      '어둠의 시선을 모조리 자신에게 붙들어두고, 모두가 쓰러지려는 그 순간 불멸의 맹세로 밤을 되돌린다.'
    ],
    // 넉백 저항은 1.0이 상한(완전 면역) — 0.5는 절반만 밀린다. 그 이상 올려도 1.0을 넘으면 무의미.
    attrs: [['minecraft:generic.knockback_resistance', 0.5, 'add_value']]
  },
  hunter: {
    name: '오리온', icon: '➶', color: 0x2D6FD9, // 사파이어 블루
    desc: '이동속도 +8% · 기본 스타터킷과 함께 시작',
    story: [
      '가장 밝은 별을 길잡이 삼아 어둠을 사냥하던 자의 가호.',
      '적에게 꽂힐 가장 날카로운 화살이 되어, 결코 붙잡히지 않는 걸음으로 거리를 지배한다.',
      '숨 돌릴 틈 없이 쏟아지던 화살은 이윽고 하늘을 가르는 별빛 폭풍이 되어 전장을 뒤덮는다.'
    ],
    attrs: [['minecraft:generic.movement_speed', 0.08, 'add_multiplied_base']]
  },
  sage: {
    name: '우라니아', icon: '✧', color: 0xB57EDC, // 라벤더
    desc: '경험치 획득 +15% · 기본 스타터킷과 함께 시작',
    story: [
      '별의 궤도를 읽어 그 힘을 빌리는 자의 가호.',
      '어둠을 지우는 가장 찬란한 별빛으로 전방을 꿰뚫고, 흩어진 적을 한 점으로 끌어모아 묶어둔다.',
      '그리고 하늘에서 별 하나를 떨어뜨려, 그 자리에 있던 모든 것을 소멸시킨다.'
    ],
    // 별을 읽어 더 빨리 깨우친다 — 퍼피시 스킬트리가 XP로 크므로 실질 성장 가속이 된다.
    attrs: [['apothic_attributes:experience_gained', 0.15, 'add_value']]
  },
  pioneer: {
    name: '크라토스', icon: '⚔', color: 0xE8B24A, // 골드
    desc: '방어구 견고함 +2 · 기본 스타터킷과 함께 시작',
    story: [
      '힘 그 자체가 형상을 얻은 자의 가호.',
      '무엇도 막아설 수 없는 가장 무거운 일격으로, 거대한 적일수록 더 깊이 파고든다.',
      '대지를 갈라 길을 열고 — 끝내 스스로 거인이 되어 전장을 짓밟는다.'
    ],
    attrs: [['minecraft:generic.armor_toughness', 2.0, 'add_value']]
  },
  gunner: {
    name: '헬리오스', icon: '☀', color: 0xF7931E, // 태양 오렌지
    desc: '화염 저항 (영구) · 기본 스타터킷과 함께 시작',
    story: [
      '식지 않는 태양을 방아쇠에 담아 쏘는 자의 가호.',
      '작열하는 탄환으로 거리를 지지고, 흩어진 무리를 산탄 한 발로 뒤로 밀어낸다.',
      '이윽고 하늘의 해를 통째로 끌어내려, 그 자리를 일식의 어둠으로 뒤덮는다.'
    ],
    // 태양을 다루는 자는 불에 타지 않는다 — 속성으론 표현이 안 돼(1.21.1엔 화염 속성 없음) 효과로 건다.
    attrs: [],
    effects: ['minecraft:fire_resistance']
  },
  healer: {
    name: '히기에이아', icon: '✚', color: 0x2ECC71, // 에메랄드
    desc: '최대 체력 +4 · 기본 스타터킷과 함께 시작',
    story: [
      '꺼져가는 생명에 다시 별빛을 불어넣는 자의 가호.',
      '심판의 빛으로 어둠을 태우고, 성역을 세워 아군의 상처를 되돌린다.',
      '그리고 죽음의 문턱을 넘어선 동료마저, 별의 이름으로 다시 일으켜 세운다.'
    ],
    // 각성 체력과 의도적으로 겹친다 — 대신 힐러의 각성 체력 최종치를 30칸으로 낮춰 균형을 맞춘다.
    attrs: [['minecraft:generic.max_health', 4.0, 'add_value']]
  },
  assassin: {
    name: '에레보스', icon: '†', color: 0x9400D3, // 다크 바이올렛
    desc: '공격 속도 +12% · 기본 스타터킷과 함께 시작',
    story: [
      '빛이 닿지 못하는 태초의 어둠에서 온 자의 가호.',
      '두 자루의 그림자 칼로 배후를 갈라, 쓰러뜨릴수록 더 빠르게 다음 목을 노린다.',
      '끝내 발밑에 무저갱을 열어, 그 안으로 모든 것을 삼켜버린다.'
    ],
    // Better Combat 쌍단검은 바닐라 스윙 타이밍을 타므로 공격속도가 실제로 콤보를 가속한다.
    attrs: [['minecraft:generic.attack_speed', 0.12, 'add_multiplied_base']]
  },
  lancer: {
    name: '쿠훌린', icon: '⚑', color: 0xDC143C, // 크림슨 레드
    desc: '공격 넉백 +1.5 · 기본 스타터킷과 함께 시작',
    story: [
      '단 한 번의 창격으로 운명을 꿰뚫은 자의 가호.',
      '긴 창의 간격을 지배하며, 손을 떠난 창은 적을 꿰고 다시 손으로 돌아온다.',
      '이윽고 하늘을 백 개의 창으로 가득 메워, 내리꽂히는 창비로 전장을 끝낸다.'
    ],
    attrs: [['minecraft:generic.attack_knockback', 1.5, 'add_value']]
  }
}
const FATE_KEYS = ['guardian', 'hunter', 'sage', 'pioneer', 'gunner', 'healer', 'assassin', 'lancer']

function ftGet(server, player) { return String(ftStore(server).getString('fate_' + player.username) || '') }

function ftModId(key, i) { return 'last_stardust:fate_' + key + (i ? '_' + i : '') }

// 가호 패시브를 다시 붙인다. 패시브 수치는 전부 고정값(각성과 무관).
// ※ "이미 있으면 무시"가 아니라 제거 후 재적용한다 — 여기 수치를 나중에 조정해도 옛 값이 남지 않게.
//   (접속/부활 시점마다 호출 — 모디파이어는 리스폰에서 살아남지 못한다)
function ftApply(server, player, key) {
  const f = FATES[key]
  if (!f) return
  const name = player.username
  f.attrs.forEach((a, i) => {
    const id = ftModId(key, i)
    try { server.runCommandSilent(`attribute ${name} ${a[0]} modifier remove ${id}`) } catch (err) { lsWarn('ls_fate:139', err) }
    try {
      server.runCommandSilent(`attribute ${name} ${a[0]} modifier add ${id} ${a[1]} ${a[2]}`)
    } catch (err) { console.log(`[LS-FATE] attr fail ${a[0]}: ${err}`) }
  })
  // 속성으로 표현 못 하는 패시브(예: 헬리오스의 화염 저항)는 영구 효과로 건다.
  const effs = f.effects || []
  effs.forEach(eff => {
    try { server.runCommandSilent(`effect give ${name} ${eff} infinite 0 true`) } catch (err) { lsWarn('ls_fate:147', err) }
  })
}

function ftChoose(server, player, key) {
  if (!FATES[key]) { player.tell(Text.of('§c가호: ' + FATE_KEYS.join(' / '))); return 0 }
  const cur = ftGet(server, player)
  if (cur) { player.tell(Text.of(`§c이미 §e${FATES[cur].name}§c의 가호를 받았습니다. §7(가호는 바꿀 수 없다 — OP만 /fate reset)`)); return 0 }
  const f = FATES[key]
  ftStore(server).putString('fate_' + player.username, key)
  ftApply(server, player, key)
  // 시작 키트
  STARTER_KIT.concat(dyedArmor(f.color)).forEach(it => { server.runCommandSilent(`give ${player.username} ${it[0]} ${it[1]}`) })
  server.runCommandSilent(`title ${player.username} title {"text":"${f.icon} ${f.name}의 가호","color":"gold","bold":true}`)
  server.runCommandSilent(`title ${player.username} subtitle {"text":"${f.story[0]}","color":"gray"}`)
  server.runCommandSilent(`execute as ${player.username} at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1`)
  ftSay(server, `§6✦ ${player.username}§7이(가) §e${f.icon} ${f.name}§7의 가호를 받았다 — ${f.desc}`)
  // 성좌 중계 (전체) — STORY 부록A. 가호를 받은 순간 꺼진 별 하나가 그를 알아본다.
  // ls_voice.js 가 없어도 가호 지급은 끝나야 하므로 감싼다(ttGrant 호출과 같은 방식).
  try { vStar(server, player.username, 1) } catch (e) { lsWarn('ls_fate:166', e) }
  console.log(`[LS-FATE] ${player.username} -> ${key}`)
  return 1
}

// ── 접속 시: 패시브 재보증 + 미선택자 안내 ──
PlayerEvents.loggedIn(event => {
  const player = event.player
  const server = event.server
  if (!player || !server) return
  const cur = ftGet(server, player)
  if (cur) {
    ftApply(server, player, cur) // 제거 후 재적용 — 현재 성급 수치로 갱신된다
  } else {
    player.tell(Text.of('§6✦ 별의 가호를 아직 받지 않았습니다. §7— §e/fate §7로 선택 (1회, 변경 불가)'))
  }
})

// ── 부활 시 가호 패시브 재적용 ──
// 속성 모디파이어는 리스폰에서 살아남지 못한다(ServerPlayer.restoreFrom 은 base 값만 옮긴다).
// 접속 시에만 재적용하면 죽을 때마다 가호 보너스가 사라진 채로 계속 플레이하게 된다.
PlayerEvents.respawned(event => {
  const player = event.player
  const server = player ? player.server : null
  if (!player || !server) return
  server.scheduleInTicks(5, () => {
    try {
      var cur = ftGet(server, player)
      if (cur) ftApply(server, player, cur)
    } catch (e) { lsWarn('ls_fate:195', e) }
  })
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  const keyArg = () => Commands.argument('key', Arguments.STRING.create(event))
    .suggests((ctx, b) => { FATE_KEYS.forEach(k => b.suggest(k)); return b.buildFuture() })

  event.register(Commands.literal('fate')
    // 선택 화면(lsrelics 모드의 /fateui)을 연다 — 유물 아이콘과 소개를 보고 고른다.
    // 현재 가호는 여기(persistentData)에만 있으므로 인자로 넘겨준다.
    .executes(ctx => {
      const s = ctx.source.server; const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      const cur = ftGet(s, p)
      s.runCommandSilent(`execute as ${p.username} run ${cur ? 'fateui ' + cur : 'fateui'}`)
      return 1
    })
    // 채팅 목록판 (화면을 못 쓰는 상황이나 빠르게 훑어볼 때)
    .then(Commands.literal('list').executes(ctx => {
      const s = ctx.source.server; const p = ctx.source.player
      ctx.source.sendSystemMessage(Text.of('§6═══ ✦ 별의 가호 §7(1회 선택 · 변경 불가) §6═══'))
      FATE_KEYS.forEach(k => {
        const f = FATES[k]
        ctx.source.sendSystemMessage(Text.of(`§e${f.icon} ${f.name} §8(${k}) §7— ${f.desc}`))
        f.story.forEach(line => ctx.source.sendSystemMessage(Text.of(`§8   ${line}`)))
      })
      if (p) {
        const cur = ftGet(s, p)
        ctx.source.sendSystemMessage(Text.of(cur ? `§7현재 가호: §e${FATES[cur].name}` : '§7선택: §e/fate §7(선택 화면)'))
      }
      return 1
    }))
    .then(Commands.literal('choose').then(keyArg().executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      return ftChoose(ctx.source.server, p, Arguments.STRING.getResult(ctx, 'key'))
    })))
    .then(Commands.literal('reset').requires(s => s.hasPermission(2)).then(Commands.argument('target', Arguments.STRING.create(event)).executes(ctx => {
      const s = ctx.source.server
      const target = Arguments.STRING.getResult(ctx, 'target')
      const cur = String(ftStore(s).getString('fate_' + target) || '')
      if (!cur) { ctx.source.sendSystemMessage(Text.of('§7해당 플레이어는 가호가 없습니다.')); return 0 }
      // 패시브 제거 (속성 + 효과형 둘 다)
      FATES[cur].attrs.forEach((a, i) => {
        try { s.runCommandSilent(`attribute ${target} ${a[0]} modifier remove ${ftModId(cur, i)}`) } catch (err) { lsWarn('ls_fate:242', err) }
      })
      ;(FATES[cur].effects || []).forEach(eff => {
        try { s.runCommandSilent(`effect clear ${target} ${eff}`) } catch (err) { lsWarn('ls_fate:245', err) }
      })
      ftStore(s).putString('fate_' + target, '')
      ctx.source.sendSystemMessage(Text.of(`§a${target}의 가호(${FATES[cur].name}) 해제 — 재선택 가능`))
      return 1
    }))))
})

console.log('[Last Stardust] 별의 가호 로드됨 — ' + FATE_KEYS.length + '가호 (아틀라스/오리온/우라니아/크라토스/헬리오스/히기에이아/에레보스/쿠훌린)')
