// Last Stardust — 별의 유물 (Stellar Relics)  [히든 무기 / RPG 정체성]
// 유물 아이템·실제 동작(활 발사/방패 막기/도끼 채굴/지팡이)·스탯은 커스텀 모드(lsrelics)가 담당한다.
// 이 스크립트는 획득 흐름만 관리: 가호별 유물 지급 · 제단 클레임 · 칭호(ls_title.js) · /relic 대시보드.
// 저장(공유 persistentData): relic_<user>(획득bool) · relic_altar_<fate>_set/x/y/z · fate_<user>(ls_fate.js가 씀)

function rlStore(server) { return server.overworld().persistentData }
function rlSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function rlCmd(server, s) { server.runCommandSilent(s) }

// ── 유물 정의 (가호 키 기준) — id는 커스텀 모드 아이템 ──
const RELICS = {
  guardian: { id: 'lsrelics:guardian', name: '§b에테르 이지스', title: 'guardian_relic', kind: '방패+무기 (좌클릭 공격 · 우클릭 막기 · 웅크림+우클릭 수호의 파동)',
    lore: '천공의 마지막 빛으로 벼려낸 방패 — 성벽이 무너지는 날, 그대가 곧 성벽이 되리라.' },
  hunter: { id: 'lsrelics:hunter', name: '§a시리우스', title: 'hunter_relic', kind: '활 (우클릭 발사 · 웅크림+우클릭 유성 화살)',
    lore: '가장 밝은 별이 그 손에 내렸으니, 어둠이 삼킨 세상에서도 표적을 놓치지 마라.' },
  sage: { id: 'lsrelics:sage', name: '§d셀레스티아', title: 'sage_relic', kind: '마법 무기 (우클릭 소멸)',
    lore: '별이 지기 전의 모든 지혜가 이 지팡이에 잠들었노라 — 꺼져가는 하늘을 대신해 길을 밝혀라.' },
  pioneer: { id: 'lsrelics:pioneer', name: '§6타이탄 브레이커', title: 'pioneer_relic', kind: '도끼 (좌클릭 강타 · 우클릭 균열 붕괴 · 쉬프트+우클릭 대지 쪼개기 · 쉬프트+좌클릭 타이탄 강림)',
    lore: '폐허를 갈라 길을 연 손이여 — 종말이 세운 그 무엇도 이 도끼 앞에 무너지리라.' },
  gunner: { id: 'lsrelics:gunner', name: '§e솔라리스', title: 'gunner_relic', kind: '화기 (좌클릭 사격 · 우클릭 스코프 줌 · 더블쉬프트 산탄 · 쉬프트+우클릭 작열탄 · 쉬프트+좌클릭 일식)',
    lore: '꺼지지 않는 태양의 불씨를 총구에 봉인했으니 — 밤이 세상을 삼켜도, 네 방아쇠 끝에서 새벽이 터진다.' },
  healer: { id: 'lsrelics:healer', name: '§f파나케이아', title: 'healer_relic', kind: '치유 지팡이 (좌클릭 평타 · 우클릭 심판의 빛 · 더블쉬프트 천사의 발걸음 · 쉬프트+우클릭 성역 · 쉬프트+좌클릭 소생)',
    lore: '별이 스러지기 전 마지막 온기를 담은 지팡이 — 쓰러진 자의 이름을 부르면, 별이 그를 다시 일으키리라.' },
  assassin: { id: 'lsrelics:assassin', name: '§5스틱스', title: 'assassin_relic', kind: '쌍단검 (좌클릭 좌우 연격 · 우클릭 급소 가르기 · 더블쉬프트 그림자 도약 · 쉬프트+우클릭 망각의 안개 · 쉬프트+좌클릭 무저갱)',
    lore: '태초의 어둠에서 벼려낸 한 쌍의 칼 — 빛이 닿지 못하는 곳에서, 너는 이미 그 뒤에 서 있다.' },
  lancer: { id: 'lsrelics:lancer', name: '§c게볼그', title: 'lancer_relic', kind: '창 (좌클릭 찌르기·베기 · 우클릭 투창(차징) · 더블쉬프트 질풍 돌진 · 쉬프트+우클릭 꿰뚫기 · 쉬프트+좌클릭 백 개의 창)',
    lore: '운명을 꿰뚫는 단 하나의 창 — 던져도 네 손으로 돌아오나니, 겨눈 표적은 결코 달아나지 못한다.' }
}

const RL_ESS = 'kubejs:rift_essence'
const RL_COST = 1   // 유물 해금에 바치는 균열 정수

// ── 유물 지급 (모드 아이템이 스탯·무적 내장 → 순수 give) ──
// 유물은 그냥 주지 않는다: 첫 공세를 맨몸으로 버텨 얻은 정수를 제단에 바쳐야 깨어난다.
// "받는 무기"가 아니라 "버텨서 얻은 무기"가 되어야 강한 성능이 정당해진다.
function rlGrant(server, player, force) {
  const uname = player.username
  const fate = String(rlStore(server).getString('fate_' + uname) || '')
  if (!fate || !RELICS[fate]) { player.tell(Text.of('§c먼저 별의 가호를 선택하세요. §e/fate')); return 0 }
  if (!force && rlStore(server).getBoolean('relic_' + uname)) { player.tell(Text.of('§7이미 당신의 유물을 손에 넣었습니다.')); return 0 }
  if (!force) {
    // 정수 확인 후 회수 — 인벤토리를 직접 읽는다 (ls_util.js).
    // /clear 반환값으로 세는 건 애초에 불가능했고(runCommandSilent 는 void),
    // 그래서 정수가 0개일 때 검사를 공짜로 통과해 유물이 그냥 나갔다.
    const have = lsCountItem(player, RL_ESS)
    if (have < RL_COST) {
      player.tell(Text.of(`§c균열 정수가 부족합니다: §e${have}/${RL_COST}`))
      player.tell(Text.of('§7   첫 공세를 막아내면 정수가 주어집니다 — 그것을 제단에 바치세요.'))
      return 0
    }
    if (lsTakeItem(player, RL_ESS, RL_COST) < RL_COST) { player.tell(Text.of('§c정수 회수에 실패했습니다.')); return 0 }
  }
  const r = RELICS[fate]
  rlCmd(server, `give ${uname} ${r.id}`)
  // 스틱스는 쌍단검 — 보조손에도 한 자루 쥐여준다 (Better Combat 쌍수). 각성 별은 asStamp가 양손 모두 새긴다.
  if (fate === 'assassin') { rlCmd(server, `item replace entity ${uname} weapon.offhand with ${r.id}`) }
  rlStore(server).putBoolean('relic_' + uname, true)
  // 저장된 각성 단계를 새 유물에 다시 새긴다 (ls_ascend.js — 공유 스코프).
  // 유물을 잃고 재지급받아도 각성이 날아가지 않게.
  try { asStamp(server, uname) } catch (e) { lsWarn('ls_relic:60', e) }
  try { ttGrant(server, uname, r.title) } catch (e) { lsWarn('ls_relic:61', e) } // 칭호 (ls_title.js — 공유 스코프)
  rlCmd(server, `title ${uname} title {"text":"${r.name.replace(/§./g, '')}","color":"aqua","bold":true}`)
  rlCmd(server, `title ${uname} subtitle {"text":"유물이 당신을 택했다","color":"gray"}`)
  rlCmd(server, `execute as ${uname} at @s run playsound minecraft:block.beacon.power_select master @s ~ ~ ~ 1 1.2`)
  rlCmd(server, `execute as ${uname} at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1`)
  rlCmd(server, `execute as ${uname} at @s run particle minecraft:end_rod ~ ~1 ~ 0.4 0.6 0.4 0.05 60`)
  rlSay(server, `§b✦ ${uname}§7이(가) 유물 §r${r.name}§7을(를) 손에 넣었습니다!`)
  rlSay(server, `§8   "${r.lore}"`)
  console.log(`[LS-RELIC] ${uname} claimed ${fate} (${r.id})`)
  return 1
}

// ── 제단 우클릭 클레임 (lodestone, 리셋 후 /relic altar 로 배치) ──
BlockEvents.rightClicked(event => {
  const b = event.block
  if (!b || b.id !== 'minecraft:lodestone') return
  const player = event.player
  if (!player) return
  const server = player.server
  if (!server) return
  const st = rlStore(server)
  const keys = Object.keys(RELICS)
  for (let i = 0; i < keys.length; i++) {
    var fate = keys[i]
    if (!st.getBoolean('relic_altar_' + fate + '_set')) continue
    if (b.x === st.getInt('relic_altar_' + fate + '_x') && b.y === st.getInt('relic_altar_' + fate + '_y') && b.z === st.getInt('relic_altar_' + fate + '_z')) {
      event.cancel()
      var pf = String(st.getString('fate_' + player.username) || '')
      if (pf !== fate) { player.tell(Text.of(`§7이 제단은 §r${RELICS[fate].name}§7의 것 — 당신의 길이 아니다.`)); return }
      rlGrant(server, player, false)
      return
    }
  }
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  const fateArg = () => Commands.argument('fate', Arguments.STRING.create(event))
    .suggests((ctx, b) => { Object.keys(RELICS).forEach(k => b.suggest(k)); return b.buildFuture() })

  event.register(Commands.literal('relic')
    .executes(ctx => {
      const s = ctx.source.server; const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      const fate = String(rlStore(s).getString('fate_' + p.username) || '')
      ctx.source.sendSystemMessage(Text.of('§6═══ ✦ 별의 유물 ═══'))
      if (!fate || !RELICS[fate]) { ctx.source.sendSystemMessage(Text.of('§7먼저 별의 가호를 선택하세요 — §e/fate')); return 1 }
      const r = RELICS[fate]
      const got = rlStore(s).getBoolean('relic_' + p.username)
      ctx.source.sendSystemMessage(Text.of(`§7당신의 유물: §r${r.name} §8(${fate})`))
      ctx.source.sendSystemMessage(Text.of(`§8   ${r.kind}`))
      if (got) {
        ctx.source.sendSystemMessage(Text.of('§a✔ 획득 완료'))
      } else {
        const have = lsCountItem(p, RL_ESS)
        ctx.source.sendSystemMessage(Text.of(`§7미획득 — §d균열 정수 ${RL_COST}개§7를 제단에 바쳐야 깨어난다. §8(보유 ${have})`))
        ctx.source.sendSystemMessage(Text.of('§8   첫 공세를 막아내면 정수가 주어진다.'))
      }
      return 1
    })
    .then(Commands.literal('grant').requires(s => s.hasPermission(2)).executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만 (본인에게 지급)')); return 0 }
      return rlGrant(ctx.source.server, p, true)
    }))
    .then(Commands.literal('altar').requires(s => s.hasPermission(2))
      .then(fateArg().executes(ctx => {
        const s = ctx.source.server; const p = ctx.source.player
        if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
        const fate = Arguments.STRING.getResult(ctx, 'fate')
        if (!RELICS[fate]) { ctx.source.sendSystemMessage(Text.of('§c가호: ' + Object.keys(RELICS).join('/'))); return 0 }
        const st = rlStore(s)
        st.putInt('relic_altar_' + fate + '_x', Math.floor(p.x))
        st.putInt('relic_altar_' + fate + '_y', Math.floor(p.y))
        st.putInt('relic_altar_' + fate + '_z', Math.floor(p.z))
        st.putBoolean('relic_altar_' + fate + '_set', true)
        ctx.source.sendSystemMessage(Text.of(`§a${RELICS[fate].name}§a 제단 등록: ${Math.floor(p.x)}, ${Math.floor(p.y)}, ${Math.floor(p.z)} §7(발밑 lodestone에 배치)`))
        return 1
      }))))
})

console.log('[Last Stardust] 별의 유물(획득 관리) 로드됨 — 실제 동작은 lsrelics 모드')
