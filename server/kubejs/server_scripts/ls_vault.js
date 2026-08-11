// Last Stardust — 주간 별빛 금고 (Weekly Vault)  [주간 콘텐츠 루프]
// 세 목표를 채우면 칸이 하나씩 열리고, 열린 칸 중 **딱 하나**에서만 받는다.
// 많이 할수록 «양»이 아니라 «고를 수 있는 폭»이 는다 — WoW 의 Great Vault 패턴.
//
// ── 왜 이게 필요한가 ──
// 공성·현상금·열쇠 던전은 전부 반복 가능한데, **반복할 이유가 그날치 보상뿐**이었다.
// 그래서 「오늘 할 일」은 있어도 「이번 주에 뭘 할지」가 없었다. 금고는 그 자리를 메운다 —
// 한 주를 가로질러 쌓이고, 무엇을 포기할지 고르게 만든다.
//
// ── 저장은 모드가 소유한다 (VaultData) ──
// 진행도는 **사람마다 따로**다. 공동으로 세면 한 명이 다 채운 주에 나머지가 아무것도
// 안 해도 받게 되고, 그러면 재클리어의 이유가 정확히 사라진다.
// 주 경계는 **실시간 월요일 00시**다 — 게임 내 하루가 18분이라 게임 시간으로 재면
// 한 세션 안에 두세 번 리셋되어 「주간」이 뜻을 잃는다(VaultData 머리말).
//
// ── 진행도가 오르는 곳 (여기가 아니라 저쪽이 부른다) ──
//   1칸 공성 방어 : ls_siege.js  finishSiege 승리 분기
//   2칸 관문 원정 : gateways JSON 의 `gateways:command` 보상 → /vault gate <summoner>
//   3칸 현상금    : ls_bounty.js btComplete

function vtSay(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }

const VT_SLOTS = 3

// 칸 이름과 보상. **보상은 칸마다 고정**이다 — 굴리지 않는다.
// 무엇이 걸려 있는지 주 초에 알아야 「이번 주엔 열쇠를 노린다」는 계획이 선다.
// 굴리면 채워 놓고 열었더니 안 쓰는 게 나오는 주가 생기고, 그 주는 통째로 헛수고가 된다.
const VT_ROWS = [
  { n: 1, key: 'siege',  label: '성역 방어',  desc: '공성 격퇴',
    give: 'kubejs:rift_essence', count: 3, prize: '별의 파편 ×3' },
  { n: 2, key: 'gate',   label: '균열 원정',  desc: '열쇠 던전 클리어',
    give: 'kubejs:stardust',     count: 8, prize: '별먼지 ×8' },
  { n: 3, key: 'bounty', label: '현상금',     desc: '게시판 완료',
    give: 'kubejs:rift_key_gold', count: 1, prize: '황금 균열 열쇠 ×1' }
]

function vtRow(n) { return VT_ROWS[n - 1] }

// ── 진행도 적립 ──
// 이름을 문자열로 받는다. 오프라인이어도 쌓여야 하고(공성은 여럿이 같이 겪는다),
// 플레이어 객체를 요구하면 호출부마다 null 검사를 다시 쓰게 된다.
function vtAdd(server, name, slot, n) {
  if (!server || !name) return
  try {
    LS.vaultRollover(server)
    LS.addVault(server, String(name), slot, n | 0)
    var r = vtRow(slot)
    var have = LS.vaultHave(server, String(name), slot) | 0
    var need = LS.vaultNeed(slot) | 0
    // ⚠️ `server.getPlayer(name)` 은 **UUID 전용**이다. 이름을 넣으면
    //     UUID string must be 32 or 36 characters long, got 'a_ka1128'
    // 로 터지고 명령이 통째로 죽는다. `ls_util.js` 가 이미 그 함정을 문서로 남겨 뒀는데
    // 초판에서 그대로 밟았다 — 이름으로 찾을 땐 언제나 lsPlayerByName 이다.
    // 딱 채운 순간에만 크게 알린다. 매번 띄우면 공성 끝날 때마다 채팅이 세 줄씩 는다.
    if (have === need) {
      var p = lsPlayerByName(server, name)
      if (p) {
        p.tell(Text.of(`§b✦ 별빛 금고 §7— §f${r.label}§7 칸이 열렸다! §8(${r.prize}) §7· /vault`))
        server.runCommandSilent(`execute as ${name} at @s run playsound minecraft:block.amethyst_block.chime master @s ~ ~ ~ 1.0 1.2`)
      }
    } else if (have < need) {
      var p2 = lsPlayerByName(server, name)
      if (p2) p2.tell(Text.of(`§8✦ 금고 ${r.label} ${have}/${need}`))
    }
  } catch (e) { lsWarn('ls_vault:add', e) }
}

// 다른 스크립트가 부르는 진입점. 전역에 세 개를 노출하는 이유는 KubeJS 스크립트끼리
// import 가 없어서다 — 로드 순서상 `ls_vault` < `ls_siege` 라 정의가 먼저 끝난다.
// (알파벳 순: ls_bounty < ls_siege < ls_vault — ⚠️ ls_vault 가 **뒤**다.
//  그래서 저쪽은 호출 시점(런타임)에만 쓰고, 로드 시점에 참조하면 안 된다.)
function vtSiegeWin(server, name)  { vtAdd(server, name, 1, 1) }
function vtGateClear(server, name) { vtAdd(server, name, 2, 1) }
function vtBounty(server, name)    { vtAdd(server, name, 3, 1) }

// ── 현황 ──
function vtStatus(src, server, name) {
  try { if (LS.vaultRollover(server)) vtSay(server, '§b✦ 새 주가 열렸다 — 별빛 금고가 초기화됐다. §8(/vault)') }
  catch (e) { lsWarn('ls_vault:rollover', e) }

  var claimed = !!LS.vaultClaimed(server, name)
  var open = LS.vaultOpenCount(server, name) | 0

  src.sendSystemMessage(Text.of('§b═══ ✦ 주간 별빛 금고 ═══'))
  for (var i = 1; i <= VT_SLOTS; i++) {
    var r = vtRow(i)
    var have = LS.vaultHave(server, name, i) | 0
    var need = LS.vaultNeed(i) | 0
    var isOpen = have >= need
    var mark = isOpen ? '§a✔' : '§8✖'
    var prog = isOpen ? '§a열림' : `§e${have}/${need}`
    src.sendSystemMessage(Text.of(
      `${mark} §f${r.label} §7${r.desc} — ${prog} §8· ${r.prize}`))
  }
  if (claimed) {
    src.sendSystemMessage(Text.of('§7이번 주 보상은 §f이미 수령§7했다. §8(다음 월요일에 초기화)'))
  } else if (open <= 0) {
    src.sendSystemMessage(Text.of('§8열린 칸이 없다 — 하나라도 채우면 받을 수 있다.'))
  } else {
    src.sendSystemMessage(Text.of(`§7열린 칸 §f${open}§7개 — §e하나만§7 고른다. §8/vault claim <1~3>`))
  }
  return 1
}

// ── 수령 ──
// 판정은 모드에 하나뿐이다(VaultData.claim). 여기서 또 검사하면 두 곳이 어긋난다.
function vtClaim(src, server, player, slot) {
  var name = String(player.username)
  try { LS.vaultRollover(server) } catch (e) { lsWarn('ls_vault:claim-roll', e) }

  if (slot < 1 || slot > VT_SLOTS) {
    src.sendSystemMessage(Text.of('§c칸은 1~3 이다.')); return 0
  }
  if (LS.vaultClaimed(server, name)) {
    src.sendSystemMessage(Text.of('§c이번 주 보상은 이미 받았다. §8(다음 월요일에 초기화)')); return 0
  }
  var r = vtRow(slot)
  if (!LS.vaultOpen(server, name, slot)) {
    var have = LS.vaultHave(server, name, slot) | 0
    src.sendSystemMessage(Text.of(`§c${r.label} 칸이 아직 안 열렸다 §7(${have}/${LS.vaultNeed(slot)})`)); return 0
  }

  // ⚠️ 지급보다 **플래그를 먼저** 세운다. give 가 실패해도(인벤 꽉 참 등) 재시도로
  // 두 번 받는 일이 없어야 한다. 반대 순서면 아이템이 바닥에 떨어진 뒤 또 받을 수 있다.
  var ok = false
  try { ok = !!LS.claimVault(server, name, slot) } catch (e) { lsWarn('ls_vault:claim', e); return 0 }
  if (!ok) { src.sendSystemMessage(Text.of('§c수령할 수 없다.')); return 0 }

  server.runCommandSilent(`give ${name} ${r.give} ${r.count}`)
  vtSay(server, `§b✦ ${name} §7이(가) 주간 금고를 열었다 — §e${r.prize} §8(${r.label})`)
  server.runCommandSilent(`execute as ${name} at @s run playsound minecraft:entity.player.levelup master @s ~ ~ ~ 1.0 1.1`)
  console.log(`[LS-VAULT] ${name} claimed slot ${slot} (${r.give}×${r.count})`)
  return 1
}

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  event.register(Commands.literal('vault')
    .executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      return vtStatus(ctx.source, ctx.source.server, String(p.username))
    })
    .then(Commands.literal('claim')
      .then(Commands.argument('slot', Arguments.INTEGER.create(event)).executes(ctx => {
        const p = ctx.source.player
        if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
        return vtClaim(ctx.source, ctx.source.server, p, ctx.getArgument('slot', 'java.lang.Integer') | 0)
      })))
    // 관문 클리어 훅 — gateways JSON 의 `gateways:command` 보상이 부른다.
    // 관문 엔티티의 커맨드 소스로 실행되므로 «누가 열었나»를 인자로 받아야 한다(<summoner>).
    .then(Commands.literal('gate').requires(s => s.hasPermission(2))
      .then(Commands.argument('who', Arguments.STRING.create(event)).executes(ctx => {
        var who = String(ctx.getArgument('who', 'java.lang.String'))
        vtGateClear(ctx.source.server, who)
        return 1
      })))
    .then(Commands.literal('reset').requires(s => s.hasPermission(2))
      .executes(ctx => { LS.resetVault(ctx.source.server, ''); ctx.source.sendSystemMessage(Text.of('§7금고 전원 초기화')); return 1 })
      .then(Commands.argument('who', Arguments.STRING.create(event)).executes(ctx => {
        var who = String(ctx.getArgument('who', 'java.lang.String'))
        LS.resetVault(ctx.source.server, who)
        ctx.source.sendSystemMessage(Text.of(`§7${who} 금고 초기화`)); return 1
      })))
    // 시험용 — 진행도를 직접 밀어 넣는다. 공성 3회를 기다리지 않고 칸을 열어 볼 수 있다.
    .then(Commands.literal('add').requires(s => s.hasPermission(2))
      .then(Commands.argument('slot', Arguments.INTEGER.create(event))
        .then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
          const p = ctx.source.player
          if (!p) return 0
          vtAdd(ctx.source.server, String(p.username),
            ctx.getArgument('slot', 'java.lang.Integer') | 0,
            ctx.getArgument('n', 'java.lang.Integer') | 0)
          return 1
        })))))
})

console.log('[Last Stardust] 주간 별빛 금고 로드됨 — 3칸 목표, 주 1회 수령')
