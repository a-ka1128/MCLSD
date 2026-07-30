// Last Stardust — 희망 게이지 (위협도의 짝)
//
// 위협도는 «세상이 얼마나 나쁜가»를 센다. 그런데 그 반대편이 비어 있었다:
// 봉화를 세우고, 관문을 깨고, 마을을 올려도 그게 - 한 자리에 모이지 않았다 - .
// 각각은 자기 화면에만 있고, «우리가 얼마나 되찾았나»를 한눈에 볼 데가 없었다.
//
// ── 왜 따로 세지 않고 유도값인가 ──
// 희망을 별도 카운터로 두면 «희망을 올리는 행동»이 따로 생기고, 그건 할 일이 하나 느는 것이다.
// 여기서는 - 이미 한 일을 다시 읽을 뿐 - 이다. 저장하는 건 패배 누적 하나뿐이고
// 나머지는 매번 실제 상태에서 계산한다. 그래서 어긋날 수가 없다 —
// 봉화를 부수면 희망도 즉시 내려간다. 장부를 따로 들면 그 동기화가 언젠가 깨진다.
//
//   희망 = 관문 45% + 봉화 25% + 마을 30% − 패배×8   (0~100)
//
// 관문에 가장 큰 몫을 준 이유: 그게 서사의 진행이고, 나머지 둘은 그 사이를 메우는 일이다.
// 봉화·마을만으로는 70 이 상한이라 «관문을 안 깨면 하늘은 안 밝는다»가 수치로 남는다.
//
// ── 패배는 쌓이고, 승리는 하나씩 갚는다 ──
// 한 번 밀리면 −8. 다음 공성을 막으면 −1칸씩 되돌아온다. 밀린 밤이 바로 잊히지 않되
// 영원히 남지도 않는다. 되돌릴 수 없는 벌은 캐주얼 서버에서 그냥 이탈이 된다.
//
// 저장: ls_hope_loss (패배 누적) 하나뿐

const HO_MAX = 100
const HO_LOSS_PENALTY = 8      // 공성 패배 1회당
const HO_GATE_W = 45           // 관문 4개를 다 깨면 이만큼
const HO_BEACON_W = 25
const HO_TOWN_W = 30
const HO_BEACON_FULL = 6       // 봉화 이만큼이면 그 축 만점
const HO_TOWN_FULL = 16        // 4트랙 × 4레벨 (TownCatalog.ALL)
const HO_TRACKS = ['ramparts', 'workshop', 'sanctum', 'districts']

// 성역 반경 — ls_siege.js 의 SANCTUARY_RADIUS 와 같은 값이다.
// 전역을 빌려오지 않고 따로 둔 이유: 로드 순서(h < s)상 ls_siege 가 아직 안 읽혔을 수 있다.
const HO_SANC_R = 64

function hoStore(server) { return server.overworld().persistentData }

function hoBeacons(server) {
  const csv = String(hoStore(server).getString('pb_names') || '')
  return csv ? csv.split(',').length : 0
}

function hoTownSum(server) {
  var sum = 0
  for (var hoI = 0; hoI < HO_TRACKS.length; hoI++) {
    try { sum += LS.townLevel(server, HO_TRACKS[hoI]) } catch (e) { lsWarn('ls_hope:town', e) }
  }
  return sum
}

function hoValue(server) {
  var gate = 0, bea = 0, town = 0
  try {
    gate = (Math.max(0, Math.min(4, LS.progress(server))) / 4) * HO_GATE_W
    bea = (Math.min(hoBeacons(server), HO_BEACON_FULL) / HO_BEACON_FULL) * HO_BEACON_W
    town = (Math.min(hoTownSum(server), HO_TOWN_FULL) / HO_TOWN_FULL) * HO_TOWN_W
  } catch (e) { lsWarn('ls_hope:value', e) }
  const loss = hoStore(server).getInt('ls_hope_loss') * HO_LOSS_PENALTY
  return Math.max(0, Math.min(HO_MAX, Math.round(gate + bea + town - loss)))
}

// 0~3. 경계를 25 씩 나눈 이유는 «네 칸»이 눈으로 읽히는 최대치라서다.
function hoTier(v) { return v >= 75 ? 3 : v >= 50 ? 2 : v >= 25 ? 1 : 0 }

const HO_TIER_NAME = ['꺼진 하늘', '희미한 빛', '별이 돌아온다', '타오르는 하늘']
const HO_TIER_FX = [
  '§8효과 없음',
  '§7성역 안 §a재생 I',
  '§7성역 안 §a재생 I §7· 공성 보상 §e+20%',
  '§7성역 안 §a재생 II §7· 공성 보상 §e+35%'
]

// ls_siege.js 의 finishSiege 가 부른다. 없으면 1 이라 그쪽은 그대로 돈다.
function hoRewardMult(server) {
  const t = hoTier(hoValue(server))
  return t >= 3 ? 1.35 : t >= 2 ? 1.20 : 1.0
}

// 공성 결과를 희망에 반영한다. 승리는 빚을 하나 갚고, 패배는 하나 더 쌓는다.
function hoOnSiege(server, outcome) {
  const cur = hoStore(server).getInt('ls_hope_loss')
  if (outcome === 'win' || outcome === 'dawn') {
    if (cur > 0) hoStore(server).putInt('ls_hope_loss', cur - 1)
  } else {
    hoStore(server).putInt('ls_hope_loss', cur + 1)
  }
}

// ── 상시 사이드바 ──
// 두 수를 나란히 놓는 게 핵심이다. 희망만 보면 «높으면 좋은 것» 이상이 안 되는데,
// 위협 옆에 두면 - 저울 - 이 된다. 오늘 밤이 어느 쪽으로 기울었는지가 한 줄로 읽힌다.
const HO_OBJ = 'ls_hope'
const HO_ROW_HOPE = '§b희망'
const HO_ROW_THREAT = '§5위협'
let HO_LAST = ''
let HO_TICK = 0
let HO_READY = false

function hoSidebar(server, hope, threat, tier) {
  if (!HO_READY) {
    HO_READY = true
    // 이미 있으면 add 가 실패하는데, 그건 정상이다 — 재시작마다 다시 만들 필요가 없다.
    server.runCommandSilent(`scoreboard objectives add ${HO_OBJ} dummy {"text":"별의 저울","color":"aqua"}`)
    server.runCommandSilent(`scoreboard objectives setdisplay sidebar ${HO_OBJ}`)
  }
  server.runCommandSilent(`scoreboard players set ${HO_ROW_HOPE} ${HO_OBJ} ${hope}`)
  server.runCommandSilent(`scoreboard players set ${HO_ROW_THREAT} ${HO_OBJ} ${threat}`)
  server.runCommandSilent(
    `scoreboard objectives modify ${HO_OBJ} displayname {"text":"별의 저울 — ${HO_TIER_NAME[tier]}","color":"aqua"}`)
}

ServerEvents.tick(event => {
  HO_TICK++
  if (HO_TICK % 100 !== 0) return   // 5초마다
  const server = event.server
  if (!server) return

  var hoV = 0, hoT = 0, hoTr = 0
  try {
    hoV = hoValue(server)
    // 위협도는 2026-07-31 부로 모드(LSData.siege)가 소유한다. 여기서 옛 키를 계속 읽으면
    // **늘 0 이 나오고 사이드바의 «위협»이 영원히 0 으로 뜬다** — 오류도 로그도 없이.
    hoT = LS.threat(server)
    hoTr = hoTier(hoV)
  } catch (e) { lsWarn('ls_hope:tick', e); return }

  // 값이 안 바뀌었으면 패킷을 안 보낸다. 5초마다 전원에게 스코어보드를 갱신하면
  // 아무것도 안 달라져도 계속 깜빡인다.
  const sig = hoV + '/' + hoT + '/' + hoTr
  if (sig !== HO_LAST) {
    HO_LAST = sig
    try { hoSidebar(server, hoV, hoT, hoTr) } catch (e) { lsWarn('ls_hope:sidebar', e) }
  }

  // ── 단계 효과: 성역 안 재생 ──
  // 지속을 갱신 주기보다 길게(6초 > 5초) 줘서 경계에서 끊기지 않게 한다.
  // 파티클은 숨긴다 — 마을에 서 있는 내내 초록 입자가 튀면 그건 보상이 아니라 소음이다.
  // 왜 회복되는지는 사이드바가 말해준다.
  if (hoTr < 1) return
  try {
    if (!LS.hasSanctuary(server)) return
    var hoX = LS.sanctuaryX(server), hoY = LS.sanctuaryY(server), hoZ = LS.sanctuaryZ(server)
    var hoAmp = hoTr >= 3 ? 1 : 0
    server.runCommandSilent(
      `execute positioned ${hoX} ${hoY} ${hoZ} run effect give @a[distance=..${HO_SANC_R}] minecraft:regeneration 6 ${hoAmp} true`)
  } catch (e) { lsWarn('ls_hope:regen', e) }
})

ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  event.register(Commands.literal('hope')
    .executes(ctx => {
      const s = ctx.source.server
      const v = hoValue(s)
      const t = hoTier(v)
      const loss = hoStore(s).getInt('ls_hope_loss')
      ctx.source.sendSystemMessage(Text.of(`§b═══ ✦ 희망 §f${v}§7/100 §8— ${HO_TIER_NAME[t]} §b═══`))
      ctx.source.sendSystemMessage(Text.of(HO_TIER_FX[t]))
      ctx.source.sendSystemMessage(Text.of(
        `§8관문 §7${LS.progress(s)}/4§8 · 봉화 §7${hoBeacons(s)}/${HO_BEACON_FULL}§8 · 마을 §7${hoTownSum(s)}/${HO_TOWN_FULL}`
        + (loss > 0 ? `§8 · 패배 §c-${loss * HO_LOSS_PENALTY}` : '')))
      // 다음 칸까지 얼마인지 — «올리면 뭐가 되나»가 안 보이면 그냥 장식이다.
      if (t < 3) {
        var need = (t + 1) * 25 - v
        ctx.source.sendSystemMessage(Text.of(`§8다음 「${HO_TIER_NAME[t + 1]}」까지 §7${need}§8 — ${HO_TIER_FX[t + 1]}`))
      }
      ctx.source.sendSystemMessage(Text.of('§8위협도는 /threat get · 사이드바에 둘이 같이 보인다'))
      return 1
    })
    // 패배 누적만 손댈 수 있게 한다. 나머지는 유도값이라 «설정»이라는 개념이 없다 —
    // 봉화를 세우거나 관문을 깨면 저절로 오른다.
    .then(Commands.literal('loss').requires(s => s.hasPermission(2))
      .then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
        const s = ctx.source.server
        const n = Math.max(0, Arguments.INTEGER.getResult(ctx, 'n'))
        hoStore(s).putInt('ls_hope_loss', n)
        HO_LAST = ''   // 다음 틱에 사이드바를 강제로 갱신시킨다
        ctx.source.sendSystemMessage(Text.of(`§a패배 누적 §e${n}§a 로 설정 §7— 희망 ${hoValue(s)}`))
        return 1
      })))
    .then(Commands.literal('hide').requires(s => s.hasPermission(2)).executes(ctx => {
      ctx.source.server.runCommandSilent('scoreboard objectives setdisplay sidebar')
      HO_READY = false
      ctx.source.sendSystemMessage(Text.of('§7사이드바를 내렸다. §8(다음 값 변화에 다시 뜬다)'))
      return 1
    })))
})

console.log('[Last Stardust] 희망 게이지 로드됨 — 관문 ' + HO_GATE_W + '% / 봉화 ' + HO_BEACON_W
  + '% / 마을 ' + HO_TOWN_W + '% · 패배 -' + HO_LOSS_PENALTY)
