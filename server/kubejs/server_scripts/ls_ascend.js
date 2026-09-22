// Last Stardust — 유물 각성 (Ascension)
// 유물의 성장 계단. 관문(ls_rift.js) 클리어가 자격을 열고, 별의 파편이 값을 치른다.
//
//   1성  유물 획득 시 기본        패시브 + 기본 스킬 (우클릭)   ※ 체력은 가호를 고를 때 이미 정해진다
//   2성  T1 개척 클리어 후        피해 ×1.5 · 체력↑ · 이동기 해금 (웅크림 두 번)
//   3성  T2 심층 클리어 후        피해 ×2.0 · 체력↑ · 추가 스킬 해금 (웅크림+우클릭)
//   4성  T3 정점 클리어 후        피해 ×2.5 · 체력↑ · 궁극기 해금 (웅크림+좌클릭)
//   5성  T4 균열핵 클리어 후      피해 ×3.0 · 체력↑ · 패시브 2단 (Passive2.java)
// ※ 체력 증가폭은 직업(가호)마다 다르다 — AS_HEALTH_BY_FATE 참고.
//
// 실제 별을 아이템에 새기는 건 lsrelics 모드(/lsrelic star <n>)가 한다.
// 자격 판정(정수·관문 진행도·성급 상한)은 이쪽이 맡는다 — 조건이 여러 시스템에 걸쳐 있어서다.
//
// 저장: 모드가 소유한다 (이관 3단계) — LS.star / LS.setStar / LS.hasRelic.
// ※ 관문 진행도와 금고는 모드(LSData)가 소유한다 — `LS.progress()` / `LS.treasury()` 로 읽는다.
//   ※ 별은 아이템 NBT에도 새겨지지만, 유물을 잃고 다시 받는 경우를 위해 여기가 원본이다.

function asCmd(server, s) { return server.runCommandSilent(s) }
function asSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }

const AS_ESS = 'kubejs:rift_essence'
const AS_MAX = 5

// [필요 관문 클리어 수, 정수 비용, 설명]
// ── 각성 단계별 해금 ──
// 1성(가호 수령 시점) = 패시브 + 기본 스킬(우클릭). 그 뒤로 한 계단마다 손패가 하나씩 는다.
// 매 단계에 새 버튼이 생기므로 "각성했는데 숫자만 올랐다"는 구간이 없다.
//
// ── 5성은 버튼이 아니라 «패시브 2단»이다 (2026-08-11) ──
// 원래 5성은 배율·체력만 최대가 되는 '완성' 단계였다. 그런데 **제일 오래 걸려 도착하는
// 계단이 제일 심심했다** — 위 주석이 스스로 "4성까지는"이라고 자백하고 있었다.
// 그래서 1성 패시브에 2단을 붙인다(`moddev/lsrelics/.../Passive2.java`).
// 열둘 전부 **딜이 아니라 생존·유틸·난전** 축이다. 5성은 이미 피해 ×3.0 으로 숫자를 다
// 주고 있어서, 여기서 딜을 또 올리면 12종 목표선(90~114)이 통째로 깨진다.
const ASCEND = {
  2: { gate: 1, cost: 2, desc: '피해 ×1.5 · §e이동기 해금' },
  3: { gate: 2, cost: 3, desc: '피해 ×2.0 · §e추가 스킬 해금' },
  4: { gate: 3, cost: 4, desc: '피해 ×2.5 · §e궁극기 해금' },
  5: { gate: 4, cost: 5, desc: '피해 ×3.0 · §e패시브 2단 해금' }
}
const GATE_NAME = ['', '개척(T1)', '심층(T2)', '정점(T3)', '균열핵(T4)']

// ── 성급별 최대 체력 보너스 (직업별 차등) ──
// 값은 "추가 HP" = (목표 칸수 × 2) − 20. 주석의 숫자가 실제 도달 칸수.
//
// ── 두 번 손봤다 (2026-08-11, 유저 결정) ──
// ① **1성 바닥**: 예전엔 전원 10칸에서 출발했다. 유물을 막 받은 시점이 이 팩에서 제일
//    얇은 구간인데 몹 스케일링과 공성은 그때도 돌고 있었다. 이제 역할별로 셋이다 —
//    탱커 20칸 · 근접 18칸 · 그 밖 16칸.
// ② **5성 천장**: 26~42칸 → **40~60칸.** 「보이는 맛」이 적었다. 다섯 계단을 다 오르고도
//    원거리가 26칸이면 각성이 체력으로는 거의 안 보인다.
//
// ⚠️ **보스·몹이 그만큼 쉬워진다.** 대략 원거리 ×1.54 · 이지스 ×1.43 만큼 두꺼워졌으니,
//    같은 위협을 유지하려면 `/bossdiff dmg` 를 **+45~55%** 쯤 올려야 계산이 맞는다.
//    유저 결정으로 **체력을 먼저 올리고 보스는 실측 뒤에 맞추기로 했다** — 숫자 두 개를
//    동시에 움직이면 어느 쪽이 원인인지 못 가린다(게볼그 앵커와 같은 이유, DECISIONS 1-F).
//
// ── 헤스페로스는 근접 딜러다 (2026-08-11 정정) ──
// 「중거리 지원」으로 적혀 있었는데, 저주를 쌓으려면 평타 사거리 안까지 들어가야 하므로
// 실제로 서는 자리는 근접이다. 1성 바닥도 근접(18칸), 5성도 펠리온과 같은 46칸.
const AS_HEALTH_BY_FATE = {
  // ── 탱커 셋 (1성 20칸) ──
  guardian: { 1: 20, 2: 40, 3: 60, 4: 80, 5: 100 }, // 20/30/40/50/60칸 — 절대 탱커
  // 네메시스는 두 번째 탱커다. 「막아서 안 맞는」 이지스보다는 얇아야
  // 패링을 맞추는 값어치가 생긴다.
  nemesis:  { 1: 20, 2: 38, 3: 56, 4: 74, 5:  92 }, // 20/29/38/47/56칸 — 패링 탱커
  pioneer:  { 1: 20, 2: 34, 3: 50, 4: 64, 5:  80 }, // 20/27/35/42/50칸 — 근접 탱커

  // ── 근접 셋 (1성 18칸) ──
  lancer:   { 1: 16, 2: 30, 3: 46, 4: 60, 5:  76 }, // 18/25/33/40/48칸 — 근접 브루저
  // 케이론은 «앞에 서는 힐러»다. 때려야 힐이 나오므로 뒤에 못 서고,
  // 그렇다고 전선을 대신 맡을 수는 없어야 한다.
  chiron:   { 1: 16, 2: 30, 3: 44, 4: 58, 5:  72 }, // 18/25/32/39/46칸 — 근접 하이브리드 힐러
  // 헤카테는 저주를 «평타로» 쌓는다 — 사거리 안까지 들어가야 하므로 자리는 근접이다.
  hecate:   { 1: 16, 2: 30, 3: 44, 4: 58, 5:  72 }, // 18/25/32/39/46칸 — 근접 딜러

  // ── 그 밖 여섯 (1성 16칸) ──
  assassin: { 1: 12, 2: 26, 3: 40, 4: 54, 5:  68 }, // 16/23/30/37/44칸 — 근접 딜러(회피형)
  // ※ 힐러는 가호 패시브로 최대 체력 +4(2칸)를 따로 받으므로 최종 44칸이 된다.
  healer:   { 1: 12, 2: 24, 3: 38, 4: 50, 5:  64 }, // 16/22/29/35/42칸 — 서포터(+가호 2칸)
  // 하르모니아는 원거리로 서지만 결속의 매듭을 깔고 그 안을 지켜야 해서
  // 딜러 셋처럼 완전히 빠질 수가 없다 — 그 차이가 2칸이다.
  harmonia: { 1: 12, 2: 24, 3: 38, 4: 50, 5:  64 }, // 16/22/29/35/42칸 — 원거리 지원 딜러

  gunner:   { 1: 12, 2: 24, 3: 36, 4: 48, 5:  60 }, // 16/22/28/34/40칸 — 원거리 딜러
  hunter:   { 1: 12, 2: 24, 3: 36, 4: 48, 5:  60 }, // 16/22/28/34/40칸 — 원거리 딜러
  sage:     { 1: 12, 2: 24, 3: 36, 4: 48, 5:  60 }  // 16/22/28/34/40칸 — 마법 유리대포
}
// 알 수 없는 가호 키가 왔을 때의 기준선 (16/22/28/34/40칸 — 「그 밖」과 같다).
// ※ 가호가 **없는** 사람은 여기로 안 온다 — asHealth 가 0 을 주고 끝낸다.
const AS_HEALTH_DEFAULT = { 1: 12, 2: 24, 3: 36, 4: 48, 5: 60 }
const AS_HP_MOD = 'last_stardust:ascend_health'

// 가호(직업) 조회 — 장부는 모드가 갖는다(ls_fate.js 도 같은 곳을 본다).
function asFate(server, uname) { return String(LS.fate(server, uname) || '') }
function asHealthTable(server, uname) { return AS_HEALTH_BY_FATE[asFate(server, uname)] || AS_HEALTH_DEFAULT }
// 해당 성급의 총 하트 칸수 (표시용) — 기본 10칸 + 보너스÷2. 가호 패시브 체력은 미포함.
function asHearts(server, uname, star) { return 10 + (asHealthTable(server, uname)[star] || 0) / 2 }

// 최대 체력 보너스를 현재 성급에 맞게 다시 붙인다.
// ※ 속성 모디파이어는 리스폰 시 복사되지 않는다(ServerPlayer.restoreFrom 은 base 값만 옮긴다).
//   그래서 각성/접속/부활 시점마다 이 함수를 다시 불러야 한다.
function asHealth(server, uname) {
  // ── 기준은 «가호»다. 유물이 아니다 (2026-08-11, 유저 결정) ──
  // 체력은 직업의 값이지 무기의 값이 아니다. 가호를 고르는 순간 정해지고,
  // **유물이 없는 0성과 1성의 체력이 같다** — 그 사이는 「같은 직업인데 아직 무기가
  // 없는」 구간이지 「더 약한 사람」인 구간이 아니다.
  // (`asStar` 가 0 을 1 로 바닥 까는 게 여기서 그대로 뜻이 된다.)
  //
  // 가호가 아예 없으면 0 — 서버에 막 들어온 사람은 바닐라 10칸에서 시작한다.
  if (!asFate(server, uname)) {
    try { asCmd(server, `attribute ${uname} minecraft:generic.max_health modifier remove ${AS_HP_MOD}`) } catch (e) { lsWarn('ls_ascend:nofate', e) }
    return 0
  }
  const bonus = asHealthTable(server, uname)[asStar(server, uname)] || 0
  try { asCmd(server, `attribute ${uname} minecraft:generic.max_health modifier remove ${AS_HP_MOD}`) } catch (e) { lsWarn('ls_ascend:69', e) }
  if (bonus > 0) {
    try { asCmd(server, `attribute ${uname} minecraft:generic.max_health modifier add ${AS_HP_MOD} ${bonus} add_value`) } catch (e) { lsWarn('ls_ascend:71', e) }
  }
  return bonus
}

function asStar(server, uname) {
  const v = LS.star(server, uname)
  return v < 1 ? 1 : Math.min(v, AS_MAX)
}

// 저장된 성급을 실제 손에 든 유물에 새긴다 (재지급·재접속 대비)
function asStamp(server, uname) {
  const s = asStar(server, uname)
  try { asCmd(server, `execute as ${uname} run lsrelic star ${s}`) } catch (e) { lsWarn('ls_ascend:84', e) }
  asHealth(server, uname)
  return s
}

// 별의 파편 보유량.
//
// ── 여기가 오래 고장나 있었다 ──
// 예전엔 `clear <이름> <아이템> 0` 의 반환값으로 개수를 셌는데, runCommandSilent 는
// 반환값이 void 다 (ls_util.js 서두 참고). 그래서 이 함수는 항상 undefined 를 돌려줬고,
// 호출부의 `have < req.cost` 가 `undefined < 5` → **false** 가 되어 비용 검사를 그냥
// 통과했다 — 각성이 정수 없이 5성까지 공짜였다.
// 인벤토리를 직접 읽는다. 반환값에 기대지 않는다.
function asEssence(server, uname) {
  const p = lsPlayerByName(server, uname)
  return p ? lsCountItem(p, AS_ESS) : 0
}

// ── 각성 실행 ──
function asAscend(server, player) {
  const uname = player.username

  if (!LS.hasRelic(server, uname)) {
    player.tell(Text.of('§c아직 유물을 손에 넣지 못했다. §7제단에서 §e/relic'))
    return 0
  }
  const cur = asStar(server, uname)
  if (cur >= AS_MAX) {
    player.tell(Text.of('§6✦✦✦✦✦ §7유물은 이미 모든 힘을 되찾았다.'))
    return 0
  }
  const next = cur + 1
  const req = ASCEND[next]

  // ① 관문 자격
  const done = LS.progress(server)   // 관문 진행도는 모드가 소유 (ls_rift.js 주석 참고)
  if (done < req.gate) {
    player.tell(Text.of(`§c${next}성 각성에는 §e${GATE_NAME[req.gate]} 봉인 해방§c이 필요하다.`))
    player.tell(Text.of(`§7   현재 진행: §e${done}/4§7 관문 — §e/expedition`))
    return 0
  }

  // ② 정수 비용
  const have = asEssence(server, uname)
  if (have < req.cost) {
    player.tell(Text.of(`§c별의 파편이 부족하다: §e${have}/${req.cost}`))
    player.tell(Text.of('§7   공세 격퇴 · 현상금 · 구출 · 균열 시련에서 얻는다.'))
    return 0
  }
  // 세는 것과 빼는 것을 같은 방식으로 한다 — 한쪽만 인벤토리를 직접 보면
  // "검사는 통과했는데 실제로는 안 빠지는" 어긋남이 다시 생긴다.
  const ap = lsPlayerByName(server, uname)
  const took = ap ? lsTakeItem(ap, AS_ESS, req.cost) : 0
  if (took < req.cost) {
    player.tell(Text.of(`§c별의 파편 회수에 실패했다: §e${took}/${req.cost}`))
    lsWarn('ls_ascend:essence-take', `took ${took} of ${req.cost} from ${uname}`)
    return 0
  }

  // ③ 각성
  LS.setStar(server, uname, next)
  asStamp(server, uname)
  // 도전과제 — 2~5성만 있다(1성은 유물 수령과 같은 순간이라 `relic` 이 대신한다).
  if (next >= 2 && next <= 5) lsAdv(server, uname, 'star' + next)

  // 최대 체력이 늘면 새 하트가 빈 칸으로 남는다 — 각성 보상이 손해처럼 보이지 않게 가득 채운다.
  const hpTable = asHealthTable(server, uname)
  const gained = (hpTable[next] || 0) - (hpTable[cur] || 0)
  if (gained > 0) { try { asCmd(server, `effect give ${uname} minecraft:instant_health 1 4 true`) } catch (e) { lsWarn('ls_ascend:134', e) } }

  const stars = '✦'.repeat(next) + '✧'.repeat(AS_MAX - next)
  asCmd(server, `title ${uname} title {"text":"각성 ${next}성","color":"gold","bold":true}`)
  asCmd(server, `title ${uname} subtitle {"text":"${stars}","color":"yellow"}`)
  asCmd(server, `execute as ${uname} at @s run playsound minecraft:block.beacon.power_select master @s ~ ~ ~ 1 0.8`)
  asCmd(server, `execute as ${uname} at @s run playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1`)
  asCmd(server, `execute as ${uname} at @s run playsound minecraft:block.amethyst_block.resonate master @s ~ ~ ~ 1 0.6`)
  asCmd(server, `execute as ${uname} at @s run particle minecraft:end_rod ~ ~1 ~ 0.5 0.8 0.5 0.08 120`)
  asCmd(server, `execute as ${uname} at @s run particle minecraft:totem_of_undying ~ ~1 ~ 0.4 0.6 0.4 0.3 60`)

  // ── 각성 충격파 — 주변 몹을 밀어내는 파동 1회 (피해 0, 넉백 전용) ──
  // damage 0 + by <player> = 피해 없이 넉백만. 각성 순간의 '노바'(디아블로 레벨업).
  asCmd(server, `execute at ${uname} run damage @e[distance=..5,type=!minecraft:player,type=!minecraft:item] 0 minecraft:player_attack by ${uname}`)
  asCmd(server, `execute as ${uname} at @s run particle minecraft:explosion ~ ~0.5 ~ 2 0.3 2 0 8`)
  asCmd(server, `execute as ${uname} at @s run playsound minecraft:entity.generic.explode master @a ~ ~ ~ 0.5 1.3`)

  // ── 성좌 중계 (전체) — 각성 단계 따라 동사 승급 (STORY 부록A: "전체 중계 — 성좌식") ──
  // 성좌 연출의 값어치는 내가 보는 게 아니라 '남이 본다'는 데 있다. 개인 메시지로 두면
  // 그냥 플레이버 텍스트지만, 전체에 뿌리면 "쟤 5성 찍었네"가 사건이 된다.
  // 문구·조사·동사 승급은 ls_voice.js 의 vStar() 가 관리한다(파일이 없으면 조용히 건너뛴다).
  try { vStar(server, uname, next) } catch (e) { lsWarn('ls_ascend:155', e) }
  // 개인에게는 서정적 지문만 — 전체 중계와 역할을 나눈다.
  player.tell(Text.of(`§8   ${next >= 5 ? '마지막 봉인이 풀리고, 유물이 별이었던 시절의 힘을 전부 기억해낸다.' : '잊혀졌던 힘의 한 조각이 유물 속에서 다시 깨어난다.'}`))

  asSay(server, `§6✦ ${uname}§7의 유물이 §e${next}성§7으로 각성했다! §8${stars}`)
  asSay(server, `§8   ${req.desc} · 체력 ${asHearts(server, uname, next)}칸`)
  if (next === 2) asSay(server, `§7   §e이동기§7가 깨어났다 — §8웅크림 두 번`)
  if (next === 3) asSay(server, `§7   §e추가 스킬§7이 깨어났다 — §8웅크림 + 우클릭`)
  if (next === 4) asSay(server, `§7   §e궁극기§7가 깨어났다 — §8웅크림 + 좌클릭`)
  if (next === 5) asSay(server, `§7   §e모든 힘이 깨어났다§7 — 유물이 별이었던 시절의 위력에 이르렀다`)
  console.log(`[LS-ASCEND] ${uname} -> ${next} star (gate ${done}, cost ${req.cost})`)
  return 1
}

// ── 접속 시 저장된 성급을 다시 새긴다 ──
// 유물을 잃고 /relic 으로 재지급받으면 NBT 의 별이 초기화되므로 장부(모드)에서 복원한다.
PlayerEvents.loggedIn(event => {
  const p = event.player
  if (!p) return
  const server = p.server
  if (!server) return
  server.scheduleInTicks(40, () => { try { asStamp(server, p.username) } catch (e) { lsWarn('ls_ascend:176', e) } })
})

// ── 부활 시 재적용 ──
// 속성 모디파이어는 리스폰에서 살아남지 못한다. 이게 없으면 죽을 때마다 체력이 20으로 돌아간다.
PlayerEvents.respawned(event => {
  const p = event.player
  if (!p || !p.server) return
  p.server.scheduleInTicks(5, () => { try { asHealth(p.server, p.username) } catch (e) { lsWarn('ls_ascend:184', e) } })
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event

  event.register(Commands.literal('ascend')
    .executes(ctx => {
      const s = ctx.source.server; const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      const cur = asStar(s, p.username)
      const stars = '✦'.repeat(cur) + '✧'.repeat(AS_MAX - cur)
      const done = LS.progress(s)   // 관문 진행도는 모드가 소유 (ls_rift.js 주석 참고)

      ctx.source.sendSystemMessage(Text.of('§6═══ ✦ 유물 각성 ═══'))
      ctx.source.sendSystemMessage(Text.of(`§7현재: §6${stars} §e${cur}성 §8(체력 ${asHearts(s, p.username, cur)}칸)`))
      if (cur >= AS_MAX) {
        ctx.source.sendSystemMessage(Text.of('§a✔ 모든 힘이 깨어났다.'))
        return 1
      }
      const req = ASCEND[cur + 1]
      const have = asEssence(s, p.username)
      const gateOk = done >= req.gate
      ctx.source.sendSystemMessage(Text.of(`§7다음: §e${cur + 1}성 §8— ${req.desc} · 체력 ${asHearts(s, p.username, cur + 1)}칸`))
      ctx.source.sendSystemMessage(Text.of(
        `§7   관문 §${gateOk ? 'a✔' : 'c✘'} §7${GATE_NAME[req.gate]} §8(${done}/4)`))
      ctx.source.sendSystemMessage(Text.of(
        `§7   정수 §${have >= req.cost ? 'a✔' : 'c✘'} §7${have}/${req.cost}`))
      if (gateOk && have >= req.cost) {
        ctx.source.sendSystemMessage(Text.of('§a▶ 준비 완료 — §e/ascend up'))
      }
      return 1
    })
    .then(Commands.literal('up').executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      return asAscend(ctx.source.server, p)
    }))
    .then(Commands.literal('sync').executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      const s = asStamp(ctx.source.server, p.username)
      ctx.source.sendSystemMessage(Text.of(`§7손에 든 유물에 §e${s}성§7을 다시 새겼다.`))
      return 1
    }))
    .then(Commands.literal('set').requires(s => s.hasPermission(2))
      // /ascend set <n> — 본인 (기존)
      .then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
        const s = ctx.source.server; const p = ctx.source.player
        if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만 §7(대상 지정: /ascend set <이름> <n>)')); return 0 }
        const n = Math.max(1, Math.min(AS_MAX, Arguments.INTEGER.getResult(ctx, 'n')))
        LS.setStar(s, p.username, n)
        asStamp(s, p.username)
        ctx.source.sendSystemMessage(Text.of(`§a각성 §e${n}성§a으로 설정했다.`))
        return 1
      })))
    // /ascend set <이름> <n> — 대상 지정 (OP)
    // 여태 본인 전용이라 남의 각성을 되돌릴 방법이 없었다. /fate set 으로 직업을 바꿔주면
    // 성급만 그대로 남아 어긋난다.
    //
    // ※ 브리가디어는 형제 인자를 등록 순서대로 시도한다. 정수(n)를 먼저 등록해뒀으므로
    //   "/ascend set 3" 은 본인용으로, "/ascend set 이름 3" 은 이쪽으로 갈린다.
    .then(Commands.literal('set').requires(s => s.hasPermission(2))
      .then(lsTargetArg(event, Commands, Arguments)
        .then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
          const s = ctx.source.server
          const target = Arguments.STRING.getResult(ctx, 'target')
          const n = Math.max(1, Math.min(AS_MAX, Arguments.INTEGER.getResult(ctx, 'n')))
          LS.setStar(s, target, n)
          // 손에 든 유물 NBT 에 성급을 다시 새긴다 — 접속 중이어야 가능하다.
          var p = lsPlayerByName(s, target)
          if (p) asStamp(s, target)
          ctx.source.sendSystemMessage(Text.of(
            `§a${target} 의 각성 §e${n}성§a으로 설정${p ? '' : ' §7(접속 중이 아니라 유물에는 아직 안 새겨짐)'}`))
          if (!p) ctx.source.sendSystemMessage(Text.of('§8   본인이 접속해서 §7/ascend sync§8 를 치면 반영됩니다.'))
          console.log(`[LS-ASCEND] admin set ${target} -> ${n} (online=${!!p})`)
          return 1
        })))))
})

console.log('[Last Stardust] 유물 각성 로드됨 — /ascend')
