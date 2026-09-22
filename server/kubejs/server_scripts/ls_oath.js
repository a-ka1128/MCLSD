// Last Stardust — 균열 서약 (Rift Oath)  [자발적 난이도 상향]
// 열쇠를 쓰기 «전에» 어픽스를 걸어 두면, 관문이 열리는 순간 그 설정이 그 판에 통째로
// 복사된다. 몹이 세지고 보상이 늘어난다. WoW M+ 의 열쇠 등급과 같은 자리다 —
// **어려워지는 쪽을 내가 고른다**는 게 이 장치의 전부다.
//
// ── 왜 열쇠 NBT 가 아닌가 ──
// 「입장권 어픽스」라는 이름대로면 아이템에 새기는 게 자연스럽다. 그런데 이 저장소에서
// **KubeJS 가 아이템 NBT 를 다룬 전례가 하나도 없다** — 1.21 에서 NBT 가 데이터
// 컴포넌트로 갈렸고 접근법이 버전마다 달라, 확인 없이 쓰면 조용히 안 붙는다.
// `ARCHITECTURE.md` 원칙 1(데이터는 Java 소유)도 같은 방향이고, 별의 축복이
// **아이템이 아니라 플레이어에 저장**되는 것과 같은 선택이다. 열쇠를 잃거나 남에게 줘도
// 서약이 어긋나지 않는 이득까지 같다.  → 장부는 `OathData.java`.
//
// ── 왜 ls_keys.js 가 아니라 별도 파일인가 ──
// 열쇠는 「제작 → 우클릭 → 개방」 셋뿐이라 62줄이다. 서약은 카탈로그·명령 4종·몹 훅·
// 틱 루프·정산까지라 그 세 배다. 한 파일에 넣으면 열쇠 고치러 들어와서 서약을 읽게 된다.
//
// ── 카탈로그는 여기 있다 ──
// 이름·배율·보상은 `/reload` 로 고치는 값이라 스크립트에 남는다. 자바는 «누가 무엇을
// 걸어 뒀나»만 안다 — 여기서 하나를 늘려도 자바를 안 고쳐도 된다.
//
// ⚠️ 로드 순서: ls_keys < ls_mobscale < ls_oath. 저 둘이 이 파일의 함수를 부르지만
//    전부 «런타임»이라(우클릭·스폰) 정의가 늦어도 괜찮다. 로드 시점에 참조하면 안 된다.

const RK_AFFIX = {
  // 몹 배율은 `ls_mobscale.js` 의 티어 배율에 **곱해진다**(대체가 아니다).
  frenzy: { name: '광포', color: '§c', dmg: 1.35, hp: 1.00, frail: 0,
            desc: '관문의 어둠이 사납다 — 적 공격력 +35%' },
  steel:  { name: '강철', color: '§7', dmg: 1.00, hp: 1.50, frail: 0,
            desc: '어둠이 굳었다 — 적 체력 +50%' },
  frail:  { name: '쇠약', color: '§5', dmg: 1.00, hp: 1.00, frail: 0.20,
            desc: '별빛이 나를 갉는다 — 내 최대 체력 -20%' }
}

// 어픽스 하나당 보상. 걸린 수만큼 곱해서 준다.
// 별먼지로 주는 이유: 축복 «수치» 리롤(3~8개)의 재료라 소모처가 확실하고,
// 파편(각성)으로 주면 관문을 도는 것이 각성을 앞당겨 난이도 곡선을 스스로 무너뜨린다.
const RK_PAY_DUST = 3
const RK_PAY_DUCAT = 40

const RK_RANGE = 24.0     // 관문 중심에서 이 거리 안이 「그 판」이다
const RK_TTL = 12000      // 10분. 관문이 그 안에 안 끝나면 서약도 풀린다
const RK_MOD = 'last_stardust:oath_frail'

// ── 진행 중인 판 ──
// **일부러 저장하지 않는다.** 관문 한 판은 길어야 몇 분이고, 서버가 그 사이에 꺼졌다면
// 그 판은 어차피 없어진 것이다. `ls_hope.js` 가 희망 게이지를 저장 안 하는 것과 같은 판단 —
// 장부를 두면 어긋나고, 어긋난 장부는 없는 것만 못하다.
// ⚠️ 그래서 `/reload` 하면 돌던 판의 서약이 풀린다. 시험 중엔 그 점을 기억할 것.
var RK_RUN = {}

function rkAff(id) { return RK_AFFIX[id] || null }

function rkOaths(server, name) {
  try {
    var arr = LS.oathList(server, String(name))
    var out = []
    for (var i = 0; i < arr.length; i++) if (rkAff(String(arr[i]))) out.push(String(arr[i]))
    return out
  } catch (e) { lsWarn('ls_oath:list', e); return [] }
}

// ── 판 시작 — 열쇠를 쓴 순간 서약을 «복사»한다 (ls_keys.js ksTryUse 가 부른다) ──
// 참조가 아니라 사본이다. 도는 중에 `/rkey add` 로 난이도를 올려 보상만 챙기는 길을 막는다.
function rkBegin(server, player) {
  try {
    var name = String(player.username)
    var ids = rkOaths(server, name)
    if (ids.length === 0) return
    RK_RUN[name] = {
      x: player.x, y: player.y, z: player.z,
      dim: String(player.level.dimension),
      ids: ids,
      until: Number(server.overworld().getDayTime()) + RK_TTL
    }
    var txt = []
    for (var i = 0; i < ids.length; i++) txt.push(RK_AFFIX[ids[i]].color + RK_AFFIX[ids[i]].name)
    server.players.forEach(p => p.tell(Text.of(
      `§5⚠ 서약 §7— ${txt.join('§7 · ')} §8(클리어 시 별먼지 +${ids.length * RK_PAY_DUST})`)))
    console.log(`[LS-OATH] ${name} began with [${ids.join(',')}]`)
  } catch (e) { lsWarn('ls_oath:begin', e) }
}

function rkRunAt(server, x, y, z, dim) {
  var now = Number(server.overworld().getDayTime())
  for (var k in RK_RUN) {
    var r = RK_RUN[k]
    if (!r) continue
    if (r.until <= now) { delete RK_RUN[k]; continue }
    if (String(r.dim) !== String(dim)) continue
    var dx = r.x - x, dy = r.y - y, dz = r.z - z
    if (dx * dx + dy * dy + dz * dz <= RK_RANGE * RK_RANGE) return r
  }
  return null
}

// ── ls_mobscale.js 가 부르는 훅 ──
// **곱셈으로 넘긴다.** 저쪽이 `setBaseValue(출고값 × 배율)` 로 «쓰는 사람이 하나»인
// 구조라(그 파일 머리말의 «세 번째 시도»), 여기서 따로 또 쓰면 복리가 되살아난다.
// 그 사고는 재시작 10번에 ×6.2 까지 갔던 적이 있다 — 반복하지 않는다.
function rkMobMul(server, e, which) {
  try {
    var r = rkRunAt(server, e.x, e.y, e.z, String(e.level.dimension))
    if (!r) return 1.0
    var m = 1.0
    for (var i = 0; i < r.ids.length; i++) {
      var a = RK_AFFIX[r.ids[i]]
      if (a) m *= (which === 'hp' ? a.hp : a.dmg)
    }
    return m
  } catch (err) { lsWarn('ls_oath:mobmul', err); return 1.0 }
}

// ── 쇠약 — 판 안에 있는 사람의 최대 체력을 깎는다 ──
// 매 2초 «있어야 할 사람»을 새로 세고, 먼저 뗀 뒤 필요한 사람에게만 다시 건다.
// 들어갈 때와 나올 때를 각각 잡으려 하면 로그아웃·사망·판 만료 중 하나는 반드시 새고,
// 그러면 **영구 디버프가 남는다.** 각성 체력이 리스폰 때 안 따라와서 매번 다시 붙이는
// `ls_ascend.js asHealth` 와 같은 이유의 같은 모양이다.
let RK_TICK = 0
ServerEvents.tick(event => {
  RK_TICK++
  if (RK_TICK % 40 !== 0) return
  const server = event.server
  try {
    server.players.forEach(p => {
      var r = rkRunAt(server, p.x, p.y, p.z, String(p.level.dimension))
      var frail = 0
      if (r) {
        for (var i = 0; i < r.ids.length; i++) {
          var a = RK_AFFIX[r.ids[i]]
          if (a) frail += a.frail
        }
      }
      var u = String(p.username)
      server.runCommandSilent(`attribute ${u} minecraft:generic.max_health modifier remove ${RK_MOD}`)
      if (frail > 0) {
        server.runCommandSilent(`attribute ${u} minecraft:generic.max_health modifier add ${RK_MOD} ${-frail} add_multiplied_base`)
      }
    })
  } catch (e) { lsWarn('ls_oath:frail', e) }
})

// ── 정산 — gateways JSON 의 `gateways:command` 보상이 부른다 ──
function rkDone(server, name) {
  try {
    var key = String(name)
    var r = RK_RUN[key]
    if (!r) return                       // 서약 없이 돈 판 — 정상이다
    delete RK_RUN[key]
    var n = r.ids.length
    var dust = n * RK_PAY_DUST, ducat = n * RK_PAY_DUCAT
    server.runCommandSilent(`give ${key} kubejs:stardust ${dust}`)
    LS.addTreasury(server, ducat)
    server.players.forEach(p => p.tell(Text.of(
      `§5✦ 서약 완수 §7— §f${key} §8(${n}중첩) §7· §d별먼지 +${dust} §7· §e금고 +${ducat}`)))
    server.runCommandSilent(`execute as ${key} at @s run playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 1.0 0.7`)
    console.log(`[LS-OATH] ${key} cleared with ${n} affixes (+${dust} dust, +${ducat} ducat)`)
  } catch (e) { lsWarn('ls_oath:done', e) }
}

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event

  function rkStatus(src, server, name) {
    var ids = rkOaths(server, name)
    var running = !!RK_RUN[String(name)]
    src.sendSystemMessage(Text.of('§5═══ ⚠ 균열 서약 ═══'))
    for (var k in RK_AFFIX) {
      var a = RK_AFFIX[k]
      var on = ids.indexOf(k) >= 0
      src.sendSystemMessage(Text.of(
        `${on ? '§a✔' : '§8○'} ${a.color}${a.name} §8(${k}) §7— ${a.desc}`))
    }
    if (ids.length === 0) {
      src.sendSystemMessage(Text.of('§8아무것도 안 걸었다 — §7/rkey add <이름>'))
    } else {
      src.sendSystemMessage(Text.of(
        `§7${ids.length}§8/${LS.oathMax() | 0}§7 중첩 — 클리어 시 §d별먼지 +${ids.length * RK_PAY_DUST} §7· §e금고 +${ids.length * RK_PAY_DUCAT}`))
    }
    if (running) src.sendSystemMessage(Text.of('§e▶ 지금 도는 판이 있다 — §7그 판은 열 때의 서약으로 굳었다.'))
    return 1
  }

  // 어픽스 id 를 탭으로 띄운다. 셋뿐이라 직접 치게 두면 오타로 조용히 실패한다.
  function rkArg() {
    return Commands.argument('affix', Arguments.STRING.create(event))
      .suggests((ctx, b) => { for (var k in RK_AFFIX) b.suggest(k); return b.buildFuture() })
  }

  event.register(Commands.literal('rkey')
    .executes(ctx => {
      const p = ctx.source.player
      if (!p) { ctx.source.sendSystemMessage(Text.of('§c플레이어만')); return 0 }
      return rkStatus(ctx.source, ctx.source.server, String(p.username))
    })
    .then(Commands.literal('add').then(rkArg().executes(ctx => {
      const p = ctx.source.player
      if (!p) return 0
      var id = String(ctx.getArgument('affix', 'java.lang.String'))
      var a = rkAff(id)
      if (!a) { ctx.source.sendSystemMessage(Text.of(`§c그런 서약은 없다: ${id}`)); return 0 }
      if (!LS.addOath(ctx.source.server, String(p.username), id)) {
        ctx.source.sendSystemMessage(Text.of(`§c걸 수 없다 — 이미 걸었거나 ${LS.oathMax() | 0}중첩이 꽉 찼다.`))
        return 0
      }
      ctx.source.sendSystemMessage(Text.of(`§5⚠ ${a.color}${a.name}§7 서약. §8${a.desc}`))
      return 1
    })))
    .then(Commands.literal('remove').then(rkArg().executes(ctx => {
      const p = ctx.source.player
      if (!p) return 0
      var id = String(ctx.getArgument('affix', 'java.lang.String'))
      if (!LS.removeOath(ctx.source.server, String(p.username), id)) {
        ctx.source.sendSystemMessage(Text.of('§c안 걸려 있다.'))
        return 0
      }
      ctx.source.sendSystemMessage(Text.of('§7서약을 거뒀다.'))
      return 1
    })))
    .then(Commands.literal('clear').executes(ctx => {
      const p = ctx.source.player
      if (!p) return 0
      LS.clearOath(ctx.source.server, String(p.username))
      ctx.source.sendSystemMessage(Text.of('§7서약을 전부 거뒀다.'))
      return 1
    }))
    // 관문 클리어 훅 — gateways JSON 의 `gateways:command` 보상이 부른다.
    // 관문 엔티티의 커맨드 소스로 실행되므로 «누가 열었나»를 인자로 받는다(<summoner>).
    .then(Commands.literal('done').requires(s => s.hasPermission(2))
      .then(Commands.argument('who', Arguments.STRING.create(event)).executes(ctx => {
        rkDone(ctx.source.server, String(ctx.getArgument('who', 'java.lang.String')))
        return 1
      }))))
})

console.log('[Last Stardust] 균열 서약 로드됨 — 어픽스 3종 (광포/강철/쇠약)')
