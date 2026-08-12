// Last Stardust — 별의 유물 (Stellar Relics)  [히든 무기 / RPG 정체성]
// 유물 아이템·실제 동작(활 발사/방패 막기/도끼 채굴/지팡이)·스탯은 커스텀 모드(lsrelics)가 담당한다.
// 이 스크립트는 획득 흐름만 관리: 가호별 유물 지급 · 제단 클레임 · 칭호(ls_title.js) · /relic 대시보드.
// 저장: 전부 모드가 소유한다 (이관 3단계) — LS.hasRelic / LS.setAltar / LS.fate.
// 옛 persistentData 키(relic_<user> · relic_altar_<fate>_* · fate_<user>)는 더 이상 안 쓴다.

function rlSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function rlCmd(server, s) { server.runCommandSilent(s) }

// ── 유물 정의 (가호 키 기준) — id는 커스텀 모드 아이템 ──
//
// ── `lore` 와 `echo` 는 다른 사람이 쓴 글이다 ──
// `lore` 는 유물에 새겨진 명문(銘文)이다 — 별이 사람에게 하는 말이고, 그래서 웅장하다.
// `echo` 는 **이 무기를 마지막으로 쥐었던 사람**이 남긴 한 줄이다 (STORY 부록B 떡밥3,
// 「옛 별지기들」). 사람의 말이라 짧고, 문어체가 아니고, 자랑이 없다.
//
// ── 2026-08-10 전면 교체: «회상» 에서 «그 순간» 으로 ──
// 옛 열한 줄은 전부 돌아보는 말이었다 (『막는 법은 배웠다. 물러서는 법은 끝내 못 배웠다』).
// 지금 열두 줄은 **그때 하고 있던 말**이다 — 현재형이고, 옆 사람에게 건네는 말이고,
// 아직 질 거라고 생각하지 않는다.
//
// **읽는 사람만 그 다음에 무슨 일이 일어났는지 안다.** 그게 이 글의 전부다.
// 『괜찮아. 내가 앞에 있잖아』 를 읽고 나서 아틀라스가 마지막에 혼자였다는 걸 떠올리는 건
// 플레이어의 몫이지, 글이 알려줄 것이 아니다.
//
// 열두 줄 중 어느 하나도 «우리는 실패했다»고 말하지 않는다. 한 줄씩 보면 그냥 다정한 말이고,
// 모아 보면 아무도 돌아오지 못한 세대의 마지막 대화가 된다.
//
// ⚠️ 새 줄을 쓸 때 지킬 것: **현재형 · 반말 · 옆 사람에게** · 열 자~스무 자.
//    과거형으로 쓰면 혼자 남아 회상하는 목소리가 되어 열두 줄의 결이 깨진다.
const RELICS = {
  guardian: { id: 'lsrelics:guardian', name: '§b에테르 이지스', title: 'guardian_relic', kind: '방패+무기 (좌클릭 공격 · 우클릭 쥐고 방어 — 앞에서 오는 피해 −25% · R 수호의 파동 · V 이지스 돌진 · C 수호 반격 · X 불멸의 맹세)',
    lore: '천공의 마지막 빛으로 벼려낸 방패 — 성벽이 무너지는 날, 그대가 곧 성벽이 되리라.',
    echo: '괜찮아. 내가 앞에 있잖아.' },
  hunter: { id: 'lsrelics:hunter', name: '§a시리우스', title: 'hunter_relic', kind: '활 (우클릭 발사 · 웅크림+우클릭 유성 화살)',
    lore: '가장 밝은 별이 그 손에 내렸으니, 어둠이 삼킨 세상에서도 표적을 놓치지 마라.',
    echo: '저 정도 거리라면 아직 맞힐 수 있어.' },
  sage: { id: 'lsrelics:sage', name: '§1셀레스티아', title: 'sage_relic', kind: '마법 무기 (우클릭 소멸)',
    lore: '별이 지기 전의 모든 지혜가 이 지팡이에 잠들었노라 — 꺼져가는 하늘을 대신해 길을 밝혀라.',
    echo: '별이 너무 조용하다. 이상할 만큼.' },
  pioneer: { id: 'lsrelics:pioneer', name: '§6타이탄 브레이커', title: 'pioneer_relic', kind: '도끼 (좌클릭 강타 · 우클릭 균열 붕괴 · 쉬프트+우클릭 대지 쪼개기 · 쉬프트+좌클릭 타이탄 강림)',
    lore: '폐허를 갈라 길을 연 손이여 — 종말이 세운 그 무엇도 이 도끼 앞에 무너지리라.',
    echo: '조금만 더 세게. 이번엔 열릴 거야.' },
  gunner: { id: 'lsrelics:gunner', name: '§e솔라리스', title: 'gunner_relic', kind: '화기 (좌클릭 사격 · 우클릭 스코프 줌 · 더블쉬프트 산탄 · 쉬프트+우클릭 작열탄 · 쉬프트+좌클릭 일식)',
    lore: '꺼지지 않는 태양의 불씨를 총구에 봉인했으니 — 밤이 세상을 삼켜도, 네 방아쇠 끝에서 새벽이 터진다.',
    echo: '탄환은 남았다. 빛도 아직 있다.' },
  healer: { id: 'lsrelics:healer', name: '§f파나케이아', title: 'healer_relic', kind: '치유 지팡이 (좌클릭 평타 · 우클릭 심판의 빛 · 더블쉬프트 천사의 발걸음 · 쉬프트+우클릭 성역 · 쉬프트+좌클릭 소생)',
    lore: '별이 스러지기 전 마지막 온기를 담은 지팡이 — 쓰러진 자의 이름을 부르면, 별이 그를 다시 일으키리라.',
    echo: '잠깐만. 아직 포기하지 마.' },
  assassin: { id: 'lsrelics:assassin', name: '§5스틱스', title: 'assassin_relic', kind: '쌍단검 (좌클릭 좌우 연격 · 우클릭 급소 가르기 · 더블쉬프트 그림자 도약 · 쉬프트+우클릭 망각의 안개 · 쉬프트+좌클릭 무저갱)',
    lore: '태초의 어둠에서 벼려낸 한 쌍의 칼 — 빛이 닿지 못하는 곳에서, 너는 이미 그 뒤에 서 있다.',
    echo: '쉿. 들키면 안 돼.' },
  lancer: { id: 'lsrelics:lancer', name: '§c게볼그', title: 'lancer_relic', kind: '창 (좌클릭 찌르기·베기 · 우클릭 투창(차징) · 더블쉬프트 질풍 돌진 · 쉬프트+우클릭 꿰뚫기 · 쉬프트+좌클릭 백 개의 창)',
    lore: '운명을 꿰뚫는 단 하나의 창 — 던져도 네 손으로 돌아오나니, 겨눈 표적은 결코 달아나지 못한다.',
    echo: '돌아올 거야. 원래 그랬으니까.' },
  hecate: { id: 'lsrelics:hecate', name: '§3헤스페로스', title: 'hecate_relic', kind: '낫 (좌클릭 베기 — 맞을 때마다 저주 · R 재의 채찍 · V 재의 결계 · C 연좌 · X 헤카테의 밤 — 저주 최대 + 지대)',
    lore: '어둠이 세상을 삼킨 밤, 홀로 횃불을 들어 길을 밝힌 자의 낫 — 네가 새긴 저주는 동료 모두의 칼끝에서 타오른다.',
    echo: '손을 놓아. 이제 내가 할게.' },
  harmonia: { id: 'lsrelics:harmonia', name: '§d바르비톤', title: 'harmonia_relic', kind: '저음 리라 (좌클릭 음률 — 원거리 연사 · R 고양의 선율 — 아군 버프/적 피해 · V 엮인 걸음 · C 결속의 매듭 · X 만상의 화음)',
    lore: '흩어진 이들을 하나의 선율로 묶는 리라 — 상처를 덮는 대신 사이를 잇는다. 이 선율이 닿는 곳에서 누구도 흐트러지지 않는다.',
    echo: '조금만 더 같이 가자.' },
  nemesis: { id: 'lsrelics:nemesis', name: '§7아드라스테이아', title: 'nemesis_relic', kind: '대검 (좌클릭 3타 콤보 · 우클릭 흘리기 — 쥐고 방어 −15%, 쥔 직후 0.4초는 완벽 패링 · R 강철 발 · V 참격 인계 · C 불굴 · X 일도양단)',
    lore: '피할 수 없는 것의 이름을 새긴 대검 — 받은 것은 무엇이든 그대로 돌아간다. 이 날이 흘려낸 일격은 벤 자에게 되돌아가리라.',
    echo: '여기는 내가 막을게.' },
  chiron: { id: 'lsrelics:chiron', name: '§2펠리온', title: 'chiron_relic', kind: '봉 (좌클릭 6타 콤보 — 때릴 때마다 아군 회복 · R 축성 · V 바람 걸음 · C 가르침 · X 펠리온의 밤)',
    lore: '가르치며 싸운 자의 봉 — 이 봉이 오가는 동안 곁의 상처가 아문다. 다만 그 힘은 끝내 제 몸에만 닿지 않는다.',
    echo: '아픈 건 괜찮아. 다른 사람부터 봐줘.' }
}

const RL_ESS = 'kubejs:rift_essence'
const RL_COST = 1   // 유물 해금에 바치는 별의 파편

// ── 유물 지급 (모드 아이템이 스탯·무적 내장 → 순수 give) ──
// 유물은 그냥 주지 않는다: 첫 공세를 맨몸으로 버텨 얻은 정수를 제단에 바쳐야 깨어난다.
// "받는 무기"가 아니라 "버텨서 얻은 무기"가 되어야 강한 성능이 정당해진다.
function rlGrant(server, player, force) {
  const uname = player.username
  const fate = String(LS.fate(server, uname) || '')
  if (!fate || !RELICS[fate]) { player.tell(Text.of('§c먼저 별의 가호를 선택하세요. §e/fate')); return 0 }
  if (!force && LS.hasRelic(server, uname)) { player.tell(Text.of('§7이미 당신의 유물을 손에 넣었습니다.')); return 0 }
  if (!force) {
    // 정수 확인 후 회수 — 인벤토리를 직접 읽는다 (ls_util.js).
    // /clear 반환값으로 세는 건 애초에 불가능했고(runCommandSilent 는 void),
    // 그래서 정수가 0개일 때 검사를 공짜로 통과해 유물이 그냥 나갔다.
    var have = lsCountItem(player, RL_ESS)
    if (have < RL_COST) {
      player.tell(Text.of(`§c별의 파편이 부족합니다: §e${have}/${RL_COST}`))
      player.tell(Text.of('§7   첫 공세를 막아내면 정수가 주어집니다 — 그것을 제단에 바치세요.'))
      return 0
    }
    if (lsTakeItem(player, RL_ESS, RL_COST) < RL_COST) { player.tell(Text.of('§c정수 회수에 실패했습니다.')); return 0 }
  }
  const r = RELICS[fate]
  rlCmd(server, `give ${uname} ${r.id}`)
  // 스틱스는 쌍단검 — 보조손에도 한 자루 쥐여준다. 각성 별은 asStamp 가 양손 모두 새긴다.
  //
  // 보조손에 한 자루 더 — **스틱스는 쌍단검이라 두 자루가 «정상 상태»다.**
  //
  // ⚠️ **2026-08-10 정정: 아래 옛 주석은 더 이상 사실이 아니다.**
  //   《BC 폴백 규칙이 아이템 id 정규식이라 `lsrelics:assassin` 은 어디에도 안 걸린다.
  //     그래서 두 번째 칼은 딜에 영향이 없다 — 외형과 은신 연출뿐이다.
  //     등록할지 검토했고 안 하기로 했다(`docs/DECISIONS.md` 1-C).》
  //
  //   그 뒤 `data/lsrelics/weapon_attributes/assassin.json` 이 실제로 만들어졌다.
  //   **지금 스틱스는 BC 에 등록돼 있고, 두 자루를 들면 `DUAL_WIELDING_SAME_CATEGORY` 가 통한다** —
  //   즉 쌍수 찌르기(×1.4)가 콤보에 실제로 들어가고, BC 의 쌍수 공속 보너스도 걸린다.
  //   1-C 가 그때 추정한 영향은 «총합 +4%» 였다. **아직 실측은 없다.**
  //
  //   `tools/make_weapon_combos.py` 는 그래서 평균 배율을 **두 자루 기준**으로 맞춰 둔다.
  if (fate === 'assassin') { rlCmd(server, `item replace entity ${uname} weapon.offhand with ${r.id}`) }
  LS.setHasRelic(server, uname, true)
  // 저장된 각성 단계를 새 유물에 다시 새긴다 (ls_ascend.js — 공유 스코프).
  // 유물을 잃고 재지급받아도 각성이 날아가지 않게.
  try { asStamp(server, uname) } catch (e) { lsWarn('ls_relic:60', e) }
  try { ttGrant(server, uname, r.title) } catch (e) { lsWarn('ls_relic:61', e) } // 칭호 (ls_title.js — 공유 스코프)
  lsAdv(server, uname, 'relic')   // 도전과제 (ls_util.js)
  rlCmd(server, `title ${uname} title {"text":"${r.name.replace(/§./g, '')}","color":"aqua","bold":true}`)
  rlCmd(server, `title ${uname} subtitle {"text":"유물이 당신을 택했다","color":"gray"}`)
  rlCmd(server, `execute as ${uname} at @s run playsound minecraft:block.beacon.power_select master @s ~ ~ ~ 1 1.2`)
  rlCmd(server, `execute as ${uname} at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1`)
  rlCmd(server, `execute as ${uname} at @s run particle minecraft:end_rod ~ ~1 ~ 0.4 0.6 0.4 0.05 60`)
  rlSay(server, `§b✦ ${uname}§7이(가) 유물 §r${r.name}§7을(를) 손에 넣었습니다!`)
  rlSay(server, `§8   "${r.lore}"`)
  // 3초 뒤 한 박자 늦게. 명문과 붙여 내보내면 두 줄이 같은 사람의 글로 읽히고,
  // 그러면 «누가 썼나»라는 질문 자체가 안 생긴다. 사이를 두어야 다른 목소리가 된다.
  server.scheduleInTicks(60, () => {
    try {
      // 아직 안 쓴 유물이 있다(케이론). 빈 문자열이면 «"" — 마지막으로 쥐었던 자» 라는
      // 빈 인용이 나가므로 통째로 건너뛴다.
      if (!r.echo) return
      rlSay(server, `§8§o   "${r.echo}"`)
      rlSay(server, '§8      — 이 무기를 마지막으로 쥐었던 자')
    } catch (e) { lsWarn('ls_relic:echo', e) }
  })
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
  const keys = Object.keys(RELICS)
  for (let i = 0; i < keys.length; i++) {
    var fate = keys[i]
    if (!LS.hasAltar(server, fate)) continue
    if (b.x === LS.altarX(server, fate) && b.y === LS.altarY(server, fate) && b.z === LS.altarZ(server, fate)) {
      event.cancel()
      var pf = String(LS.fate(server, player.username) || '')
      if (pf !== fate) { player.tell(Text.of(`§7이 제단은 §r${RELICS[fate].name}§7의 것 — 당신의 길이 아니다.`)); return }
      rlGrant(server, player, false)
      return
    }
  }
})

// ── 제단 등록 본체 ──
// 서 있는 자리에서 찾아 넣든 좌표로 찍어 넣든 **검사는 한 곳에서** 한다.
// 두 벌로 두면 한쪽에만 검사가 붙어 「좌표로 넣은 것만 조용히 어긋나는」 상태가 된다.
function rlAltarPut(src, server, level, fate, x, y, z) {
  var b = null
  try { b = level.getBlock(x, y, z) } catch (e) { lsWarn('ls_relic:altar-block', e) }
  if (!b || b.id !== 'minecraft:lodestone') {
    src.sendSystemMessage(Text.of(`§c${x}, ${y}, ${z} 는 lodestone(자석석)이 아닙니다 §8(${b ? b.id : '읽기 실패'})`))
    return 0
  }
  // 같은 블록에 두 가호를 등록하면 우클릭 판정이 «먼저 걸린 쪽»만 잡는다.
  // 열둘을 짓다 보면 한 번쯤 나오는 실수고, 증상은 「어떤 직업만 유물을 못 받는다」다.
  var dup = null
  Object.keys(RELICS).forEach(k => {
    if (k === fate || dup || !LS.hasAltar(server, k)) return
    if (LS.altarX(server, k) === x && LS.altarY(server, k) === y && LS.altarZ(server, k) === z) dup = k
  })
  if (dup) {
    src.sendSystemMessage(Text.of(`§c이 블록은 이미 §r${RELICS[dup].name}§c 의 제단입니다 — 다른 곳에 놓으세요.`))
    return 0
  }
  LS.setAltar(server, fate, x, y, z)
  src.sendSystemMessage(Text.of(`§a${RELICS[fate].name}§a 제단 등록: ${x}, ${y}, ${z}`))
  console.log(`[LS-RELIC] altar ${fate} @ ${x},${y},${z}`)
  return 1
}

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  const fateArg = () => Commands.argument('fate', Arguments.STRING.create(event))
    .suggests((ctx, b) => { Object.keys(RELICS).forEach(k => b.suggest(k)); return b.buildFuture() })

  event.register(Commands.literal('relic')
    .executes(ctx => {
      const s = ctx.source.server; const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      const fate = String(LS.fate(s, p.username) || '')
      ctx.source.sendSystemMessage(Text.of('§6═══ ✦ 별의 유물 ═══'))
      if (!fate || !RELICS[fate]) { ctx.source.sendSystemMessage(Text.of('§7먼저 별의 가호를 선택하세요 — §e/fate')); return 1 }
      const r = RELICS[fate]
      const got = LS.hasRelic(s, p.username)
      ctx.source.sendSystemMessage(Text.of(`§7당신의 유물: §r${r.name} §8(${fate})`))
      ctx.source.sendSystemMessage(Text.of(`§8   ${r.kind}`))
      if (got) {
        ctx.source.sendSystemMessage(Text.of('§a✔ 획득 완료'))
        // 가진 사람만 볼 수 있다. 아직 못 받은 사람에게는 그냥 멋진 문장이지만,
        // 매일 쥐는 무기에 붙어 있으면 언젠가 «이게 누구 말이지»가 된다.
        ctx.source.sendSystemMessage(Text.of(`§8§o   "${r.echo}"`))
        ctx.source.sendSystemMessage(Text.of('§8      — 이 무기를 마지막으로 쥐었던 자'))
      } else {
        var have = lsCountItem(p, RL_ESS)
        ctx.source.sendSystemMessage(Text.of(`§7미획득 — §d별의 파편 ${RL_COST}개§7를 제단에 바쳐야 깨어난다. §8(보유 ${have})`))
        ctx.source.sendSystemMessage(Text.of('§8   첫 공세를 막아내면 정수가 주어진다.'))
      }
      return 1
    })
    .then(Commands.literal('grant').requires(s => s.hasPermission(2)).executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만 (본인에게 지급)')); return 0 }
      return rlGrant(ctx.source.server, p, true)
    }))
    // ── 제단 등록 ──
    // ⚠️ 예전엔 `Math.floor(p.y)` 를 그대로 넣었다 — 그건 **발이 들어 있는 칸**이고,
    //    위 우클릭 판정은 **lodestone 블록 칸**과 비교한다. lodestone 위에 서면 한 칸 어긋나
    //    등록은 «✔» 로 뜨는데 우클릭이 영원히 안 먹었다. 조용히 실패하는 종류라
    //    제단 열둘을 다 짓고 나서야 알게 된다.
    //
    // 그래서 좌표를 짐작하지 않고 **lodestone 을 직접 찾는다** — 발밑(-1), 선 자리(0),
    // 반블록/카펫을 밟고 선 경우(-2)까지. 못 찾으면 등록하지 않고 이유를 말한다.
    //
    // ── 이름을 하나로 둔다 ──
    // 잠깐 `altar`(등록) 옆에 `altars`(현황)를 뒀는데, 한 글자 차이인 데다 한쪽만 인수를
    // 받아서 `/relic altars chiron` 이 그냥 «잘못된 인수» 로 튕겼다. 탭 완성으로도 안 갈린다.
    // 그래서 리터럴 하나로 합쳤다 — **인수가 있으면 등록, 없으면 현황.**
    .then(Commands.literal('altar').requires(s => s.hasPermission(2))
      .executes(ctx => {
        const s = ctx.source.server
        var rlLsKeys = Object.keys(RELICS), rlLsDone = 0
        ctx.source.sendSystemMessage(Text.of('§6═══ ✦ 유물 제단 ═══'))
        rlLsKeys.forEach(k => {
          if (LS.hasAltar(s, k)) {
            rlLsDone++
            ctx.source.sendSystemMessage(Text.of(`§a ✔ §r${RELICS[k].name} §8(${k}) §7${LS.altarX(s, k)}, ${LS.altarY(s, k)}, ${LS.altarZ(s, k)}`))
          } else {
            ctx.source.sendSystemMessage(Text.of(`§c ✘ §r${RELICS[k].name} §8(${k}) §c미등록`))
          }
        })
        ctx.source.sendSystemMessage(Text.of(`§7${rlLsDone} / ${rlLsKeys.length} 등록됨`))
        if (rlLsDone < rlLsKeys.length) ctx.source.sendSystemMessage(Text.of('§8   등록: 자석석 위에 서서 §7/relic altar <가호>'))
        // ── 복구용 목록을 로그에 남긴다 ──
        // 제단 좌표는 laststardust.dat 안에 있다. 플레이 데이터를 정리하려고 그 파일을 지우면
        // **자석석은 그대로인데 등록만 사라진다** — 열두 번 다시 서서 다시 찍어야 한다.
        // 여기서 좌표판 명령을 통째로 뽑아 두면 붙여넣기 열두 줄로 끝난다.
        // 채팅이 아니라 로그로 보내는 이유: 채팅은 스크롤로 사라지고 복사가 안 된다.
        if (rlLsDone > 0) {
          console.log('[LS-RELIC] ── 제단 복구용 (그대로 붙여넣으면 재등록된다) ──')
          rlLsKeys.forEach(k => {
            if (LS.hasAltar(s, k)) {
              console.log(`/relic altar ${k} ${LS.altarX(s, k)} ${LS.altarY(s, k)} ${LS.altarZ(s, k)}`)
            }
          })
          ctx.source.sendSystemMessage(Text.of('§8   복구용 좌표 목록을 §7서버 로그§8에 남겼습니다 (latest.log)'))
        }
        return 1
      })
      .then(fateArg().executes(ctx => {
        const s = ctx.source.server; const p = ctx.source.player
        if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
        const fate = Arguments.STRING.getResult(ctx, 'fate')
        if (!RELICS[fate]) { ctx.source.sendSystemMessage(Text.of('§c가호: ' + Object.keys(RELICS).join('/'))); return 0 }

        var rlAlX = Math.floor(p.x), rlAlZ = Math.floor(p.z), rlAlFeet = Math.floor(p.y)
        var rlAlY = null
        try {
          var rlAlOff = [-1, 0, -2]
          for (var rlAlI = 0; rlAlI < rlAlOff.length && rlAlY === null; rlAlI++) {
            var rlAlB = p.level.getBlock(rlAlX, rlAlFeet + rlAlOff[rlAlI], rlAlZ)
            if (rlAlB && rlAlB.id === 'minecraft:lodestone') rlAlY = rlAlFeet + rlAlOff[rlAlI]
          }
        } catch (e) { lsWarn('ls_relic:altar-scan', e) }

        if (rlAlY === null) {
          ctx.source.sendSystemMessage(Text.of('§c발밑에 lodestone(자석석)이 없습니다 — 제단 블록 위에 서서 실행하세요.'))
          ctx.source.sendSystemMessage(Text.of(`§8   찾아본 곳: ${rlAlX}, ${rlAlFeet - 2}~${rlAlFeet}, ${rlAlZ}`))
          return 0
        }

        var rlAlOk = rlAltarPut(ctx.source, s, p.level, fate, rlAlX, rlAlY, rlAlZ)
        if (rlAlOk) ctx.source.sendSystemMessage(Text.of('§8   그 자리에서 우클릭해 보면 바로 확인됩니다.'))
        return rlAlOk
      })
        // ── 좌표로 등록 ──
        // 서서 찍는 것만 있으면 **한 번 지운 뒤 열두 번 다시 걸어가야 한다.**
        // 제단 좌표는 성역·성벽과 함께 laststardust.dat 한 파일에 있어서, 플레이 데이터를
        // 정리하려고 그 파일을 지우면 자석석은 남고 등록만 사라진다 — 그때 이게 있으면
        // 인수 없는 `/relic altar` 가 로그에 남겨 둔 열두 줄을 붙여넣는 것으로 끝난다.
        //
        // 검사는 서서 찍는 쪽과 **같은 함수**를 쓴다. 자석석인지도 그대로 본다 —
        // 좌표를 손으로 넣다 한 칸 틀리는 건 서서 찍는 것보다 오히려 잦다.
        //
        // ⚠️ `fateArg()` 를 형제로 하나 더 두지 않는다. Brigadier 는 이름이 같은 인수 노드를
        //    «합쳐» 버려서 되기는 하는데, 읽는 사람에게는 같은 자리에 두 갈래가 있는 것처럼
        //    보인다. 같은 노드 아래에 매다는 게 실제 구조 그대로다.
        .then(Commands.argument('x', Arguments.INTEGER.create(event))
          .then(Commands.argument('y', Arguments.INTEGER.create(event))
            .then(Commands.argument('z', Arguments.INTEGER.create(event)).executes(ctx => {
              const s = ctx.source.server
              const fate = Arguments.STRING.getResult(ctx, 'fate')
              if (!RELICS[fate]) { ctx.source.sendSystemMessage(Text.of('§c가호: ' + Object.keys(RELICS).join('/'))); return 0 }
              // 콘솔에서도 부를 수 있어야 한다 — 붙여넣기 열두 줄이 이 명령의 존재 이유다.
              const p = ctx.source.player
              const lvl = (p && p.level) ? p.level : s.overworld()
              return rlAltarPut(ctx.source, s, lvl, fate,
                Arguments.INTEGER.getResult(ctx, 'x'),
                Arguments.INTEGER.getResult(ctx, 'y'),
                Arguments.INTEGER.getResult(ctx, 'z'))
            }))))))
    // ── 유물 회수 (OP) ──
    // /relic grant 는 본인 전용이라 남의 획득 상태를 되돌릴 방법이 없었다.
    // /fate set 으로 직업을 바꿔주면 유물 플래그만 옛 직업에 남아 어긋난다.
    //
    // 플래그만 지우면 옛 직업 유물을 손에 든 채 새 유물까지 받게 된다 — 아이템도 함께 걷는다.
    // 각성 성급은 건드리지 않는다. 그건 /ascend set 의 몫이다.
    .then(Commands.literal('revoke').requires(s => s.hasPermission(2))
      .then(lsTargetArg(event, Commands, Arguments).executes(ctx => {
        const s = ctx.source.server
        const target = Arguments.STRING.getResult(ctx, 'target')
        if (!LS.hasRelic(s, target)) {
          ctx.source.sendSystemMessage(Text.of(`§7${target} 은(는) 아직 유물을 받지 않았습니다.`))
          return 0
        }
        LS.setHasRelic(s, target, false)

        // 인벤에서 유물 8종을 전부 걷는다. 직업을 바꾼 뒤라면 저장된 가호와 손에 든 유물이
        // 다를 수 있으므로, 가호로 하나만 집어내지 않고 전부 훑는다.
        var taken = 0
        var p = lsPlayerByName(s, target)
        if (p) {
          Object.keys(RELICS).forEach(k => {
            try { taken += lsTakeItem(p, RELICS[k].id, 64) } catch (e) { lsWarn('ls_relic:revoke-take', e) }
          })
        }
        ctx.source.sendSystemMessage(Text.of(
          `§a${target} 의 유물 획득 해제 §7(아이템 ${taken}개 회수)${p ? '' : ' §c— 접속 중이 아니라 아이템은 못 걷었습니다'}`))
        if (!p) ctx.source.sendSystemMessage(Text.of('§8   접속하면 다시 실행해 주세요 — 안 그러면 유물을 둘 들게 됩니다.'))
        console.log(`[LS-RELIC] admin revoke ${target} (items ${taken})`)
        return 1
      }))))
})

console.log('[Last Stardust] 별의 유물(획득 관리) 로드됨 — 실제 동작은 lsrelics 모드')
