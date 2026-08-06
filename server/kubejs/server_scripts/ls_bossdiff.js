// Last Stardust — 보스 난이도 다이얼 (보스별 + 전역)
// 보스 스폰 시 체력·공격력에 배율(%) 적용. 기본 100 = 모드 원본.
// /bossdiff 로 실시간 조절 (월드 리셋 시 초기화됨 = 정상).
//
// ── 저장: 이관 5단계로 모드가 소유한다 (2026-08-06) ──
// 옛 방식은 `p.putInt('bd_' + id + '_hp', ...)` — **NBT 키를 문자열로 조립**하고 있었다.
// 보스 id 에 콜론이 들어가는데 그게 그대로 키가 되고, 오타 하나면 조용히 다른 키에 쓰고
// 읽는 쪽은 「오버라이드 없음(0)」으로 읽는다. 예외도 로그도 안 난다.
// 지금은 `LS.bossHpOverride` / `LS.setBossDiff` 로 모드(LSData.bossDiff)에 묻는다.
//
// ※ **2층 구조는 그대로다.** 영구 기본값(ls_config.js)은 여전히 이 스크립트가 읽는다 —
//   `/reload` 로 고치는 튜닝 값이라 스크립트가 맞는 자리다. 모드가 가진 건 라이브 오버라이드뿐.

const BOSS_LIST = [
  // Cataclysm (8)
  'cataclysm:ignis', 'cataclysm:netherite_monstrosity', 'cataclysm:ender_guardian',
  'cataclysm:the_harbinger', 'cataclysm:the_leviathan', 'cataclysm:ancient_remnant',
  'cataclysm:maledictus', 'cataclysm:scylla',
  // Twilight Forest (9)
  'twilightforest:naga', 'twilightforest:lich', 'twilightforest:minoshroom',
  'twilightforest:hydra', 'twilightforest:knight_phantom', 'twilightforest:ur_ghast',
  'twilightforest:alpha_yeti', 'twilightforest:snow_queen', 'twilightforest:plateau_boss',
  // Bosses of Mass Destruction (4)
  'bosses_of_mass_destruction:lich', 'bosses_of_mass_destruction:obsidilith',
  'bosses_of_mass_destruction:gauntlet', 'bosses_of_mass_destruction:void_blossom',
  // Aether (3)
  'aether:slider', 'aether:sun_spirit', 'aether:valkyrie_queen',
  // Mowzie's (3)
  'mowziesmobs:ferrous_wroughtnaut', 'mowziesmobs:frostmaw', 'mowziesmobs:umvuthi',
  // 기타 (4)
  'alexsmobs:void_worm', 'undergarden:forgotten_guardian', 'deeperdarker:stalker',
  // 바닐라 (3)
  'minecraft:ender_dragon', 'minecraft:wither', 'minecraft:warden'
]
const BOSS_SET = {}
BOSS_LIST.forEach(id => { BOSS_SET[id] = true })

// ── 값의 출처는 2층이다 ──
//   1층: ls_config.js (kubejs 폴더 = 월드 밖) — 영구 기본값. 월드를 리셋해도 남는다.
//   2층: LSData.bossDiff (모드 · 월드 세이브) — /bossdiff 로 건 라이브 오버라이드. 리셋 시 사라진다.
// 읽을 때는 2층이 있으면 2층, 없으면 1층을 쓴다. 0 = "오버라이드 없음".
//
// 다리 호출은 전부 감싼다. 모드가 없거나 메서드 이름이 어긋나면 여기서 터지는데, 그러면
// **보스 스폰 핸들러 전체가 죽어서** 난이도가 아니라 보스 자체가 이상해진다.
// 실패했을 때 「오버라이드 없음(0)」으로 내려가면 파일 값이 그대로 쓰인다 — 안전한 쪽이다.
function bdCfg() { return (typeof LS_CONFIG !== 'undefined' && LS_CONFIG.boss) ? LS_CONFIG.boss : { globalHp: 100, globalDmg: 100, perBoss: {} } }
function bdCfgBoss(id) { const c = bdCfg().perBoss || {}; return c[id] || null }

// 다리를 이름으로 골라 부르지 않는다(`LS[fn](...)`). Rhino 에서 되긴 하지만, 이름이 틀려도
// 「메서드가 없다」가 런타임에야 나오고 오타는 문법 검사에 안 걸린다. 여덟 줄 늘어나는 대신
// 스캐너(`scan_dead_kubejs.py` A절)가 없는 메서드를 빌드 전에 잡아준다.
function bdOvGHp(server) { try { return LS.bossGlobalHpOverride(server) | 0 } catch (e) { lsWarn('ls_bossdiff:ov-ghp', e); return 0 } }
function bdOvGDmg(server) { try { return LS.bossGlobalDmgOverride(server) | 0 } catch (e) { lsWarn('ls_bossdiff:ov-gdmg', e); return 0 } }
function bdOvHp(server, id) { try { return LS.bossHpOverride(server, id) | 0 } catch (e) { lsWarn('ls_bossdiff:ov-hp', e); return 0 } }
function bdOvDmg(server, id) { try { return LS.bossDmgOverride(server, id) | 0 } catch (e) { lsWarn('ls_bossdiff:ov-dmg', e); return 0 } }
function bdOvAbs(server, id) { try { return LS.bossAbsOverride(server, id) | 0 } catch (e) { lsWarn('ls_bossdiff:ov-abs', e); return 0 } }

function bdGlobalHp(server) { const v = bdOvGHp(server); return v > 0 ? v : bdCfg().globalHp }
function bdGlobalDmg(server) { const v = bdOvGDmg(server); return v > 0 ? v : bdCfg().globalDmg }
function bdEffHp(server, id) {
  const p = bdOvHp(server, id); if (p > 0) return p
  const c = bdCfgBoss(id); if (c && c.hp > 0) return c.hp
  return bdGlobalHp(server)
}
// ── 절대 체력 (%보다 우선) ──
// %는 모드 원본 체력을 알아야 쓰는데 그 값이 코드 안에 있어 안 보인다(Mowzie's·Cataclysm).
// 반면 튜닝의 입력은 «4명이 1분» 같은 목표 시간이고, 거기서 나오는 건 절대값이다.
// 모드가 업데이트로 원본을 바꿔도 우리 목표는 안 흔들린다. 0 = 지정 없음(= % 경로).
function bdEffAbs(server, id) {
  const p = bdOvAbs(server, id); if (p > 0) return p
  const c = bdCfgBoss(id); if (c && c.absHp > 0) return c.absHp
  return 0
}
function bdEffDmg(server, id) {
  const p = bdOvDmg(server, id); if (p > 0) return p
  const c = bdCfgBoss(id); if (c && c.dmg > 0) return c.dmg
  return bdGlobalDmg(server)
}
// 목록에 보여야 할 보스 전부 = BOSS_LIST ∪ 「오버라이드가 걸린 것」.
// 합집합을 쓰는 이유: BOSS_LIST 는 손으로 관리하는 33종이라 모드팩이 바뀌면 낡는다. 목록에서
// 빠진 보스에 걸어둔 값이 있으면 **/bossdiff list 에 안 보이고 /bossdiff reset 으로도 안 지워지는**
// 유령이 된다. reset 은 이제 모드가 표를 통째로 비우고, 목록은 여기서 합친다.
function bdAllIds(server) {
  var out = BOSS_LIST.slice()
  var seen = {}
  BOSS_LIST.forEach(x => { seen[x] = true })
  try {
    String(LS.bossOverriddenCsv(server) || '').split(',').forEach(x => {
      var t = String(x).trim()
      if (t && !seen[t]) { seen[t] = true; out.push(t) }
    })
  } catch (e) { lsWarn('ls_bossdiff:all-ids', e) }
  return out
}

// 값이 어디서 왔는지 (표시용)
function bdSrc(server, id, kind) {
  const live = kind === 'hp' ? bdOvHp(server, id) : bdOvDmg(server, id)
  if (live > 0) return '라이브'
  const c = bdCfgBoss(id); if (c && c[kind] > 0) return '파일'
  return '전역'
}

// ── 보스 스폰 시 배율 적용 ──
EntityEvents.spawned(event => {
  const e = event.entity
  if (!e) return
  const id = String(e.type)
  if (!BOSS_SET[id]) return
  const server = e.server
  if (!server) return
  const hp = bdEffHp(server, id)
  const dmg = bdEffDmg(server, id)
  const abs = bdEffAbs(server, id)

  // 체력: setMaxHealth 는 내부적으로 Attributes.MAX_HEALTH Holder 를 직접 쓰므로 안전하다.
  //
  // ※ 2026-07-26 정정: 예전 주석은 "KubeJS 의 Holder 변환 실패"를 원인으로 적어놨는데 **틀렸다.**
  //   그냥 **ID 가 틀렸던 것**이다 — 1.21.1 의 실제 이름은 `minecraft:generic.max_health` 이고
  //   `generic.` 을 빼면 존재하지 않는 속성이라 null 이 온다. 라이브러리 탓으로 적어두면
  //   다음 사람이 우회만 늘리고 원인은 못 고친다(실제로 아래 공격력이 그렇게 방치돼 있었다).
  // ※ 자바 쪽 실제 이름은 kjs$setMaxHealth 지만, KubeJS 는 kjs$ 접두사를 떼고 노출한다 —
  //   스크립트에서 접두사를 붙여 부르면 'Cannot find function' 으로 핸들러 전체가 죽는다.
  // 원본 최대 체력은 - 항상 - 찍는다. 예전엔 배율이 100%면 아무것도 안 찍혀서,
  // "이 보스 원본이 얼마인지"를 알아낼 방법이 서버 어디에도 없었다. 튜닝의 출발점이 그 숫자다.
  var oldMax = e.getMaxHealth()
  let hpMsg = 'base=' + oldMax + ' '
  if (abs > 0) {
    // 절대 지정이 있으면 %는 무시한다. 둘을 곱하면 어느 쪽을 만졌는지 추적이 안 된다.
    e.setMaxHealth(abs)
    e.setHealth(e.getMaxHealth())
    hpMsg += `hp=절대 ${e.getMaxHealth()}`
  } else {
    hpMsg += 'hp=' + hp + '%'
    if (hp !== 100) {
      var newMax = oldMax * hp / 100.0
      e.setMaxHealth(newMax)
      e.setHealth(e.getMaxHealth())   // 새 최대치로 채운다
      hpMsg += ` (${oldMax}→${e.getMaxHealth()})`
    }
  }

  // 공격력: 여전히 속성으로 접근한다. old→new 를 찍어서, 안 바뀌면
  // (1) 속성 자체가 없거나(고정 데미지 보스) (2) 문자열 변환 실패인지 로그로 구분한다.
  let dmgMsg = 'dmg=' + dmg + '%'
  if (dmg !== 100) {
    var d = e.getAttribute('minecraft:generic.attack_damage')
    if (d) {
      var oldD = d.getBaseValue()
      d.setBaseValue(oldD * dmg / 100.0)
      dmgMsg += ` (${oldD}→${d.getBaseValue()})`
    } else {
      dmgMsg += ' (attack_damage 속성 없음 — 고정 데미지 보스거나 문자열 변환 실패)'
    }
  }
  console.log(`[LS-BOSSDIFF] ${id} ${hpMsg} ${dmgMsg}`)
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  // ── 보스 인자 ──
  // 예전엔 Arguments.STRING 이었다. 그건 Brigadier 의 «따옴표 가능 문자열»이고, 따옴표 없이
  // 읽을 때는 [0-9A-Za-z_.+-] 만 허용한다 — 콜론에서 토큰이 끊긴다. 그래서 탭 완성이 제안한
  // mowziesmobs:ferrous_wroughtnaut 를 그대로 치면
  //     Expected whitespace to end one argument, but found trailing data
  // 로 거부됐다. - 제안한 값을 그대로 입력할 수 없는 - 상태였고, 따옴표를 아는 사람만 쓸 수 있었다.
  //
  // RESOURCE_LOCATION 은 ResourceLocationArgument.id() 라 ResourceLocation.read() 로 읽는다.
  // 콜론·슬래시가 정식 문자이므로 따옴표가 필요 없고, 대문자·공백 같은 진짜 오타는
  // 우리 코드에 닿기 전에 파서가 걸러준다.
  const bossArg = () => Commands.argument('boss', Arguments.RESOURCE_LOCATION.create(event))
    .suggests((ctx, b) => { BOSS_LIST.forEach(x => b.suggest(x)); return b.buildFuture() })

  // 결과는 ResourceLocation 객체다. 문자열로 눌러 BOSS_SET 키와 맞춘다.
  // 네임스페이스를 생략하면 파서가 minecraft: 를 붙이므로(`frostmaw` → `minecraft:frostmaw`)
  // 짧은 이름도 받아준다 — 목록에서 뒷부분이 유일하게 맞으면 그걸로 친다.
  const bossId = (ctx) => {
    var raw = String(Arguments.RESOURCE_LOCATION.getResult(ctx, 'boss'))
    if (BOSS_SET[raw]) return raw
    var hits = BOSS_LIST.filter(x => x.substring(x.indexOf(':') + 1) === raw.substring(raw.indexOf(':') + 1))
    return hits.length === 1 ? hits[0] : raw
  }

  // 못 찾았을 때. 이름이 겹쳐서 못 고른 건지(리치가 둘 있다) 아예 없는 건지 구분해 준다 —
  // 둘 다 «알 수 없는 보스»로 뭉뚱그리면 네임스페이스를 붙이면 된다는 걸 알 방법이 없다.
  const bossFail = (ctx, raw) => {
    var hits = BOSS_LIST.filter(x => x.substring(x.indexOf(':') + 1) === raw.substring(raw.indexOf(':') + 1))
    ctx.source.sendSystemMessage(Text.of(hits.length > 1
      ? `§c이름이 겹칩니다: §7${hits.join(' · ')}§c — 네임스페이스까지 적어주세요.`
      : `§c알 수 없는 보스: ${raw}`))
    return 0
  }

  event.register(Commands.literal('bossdiff')
    .executes(ctx => {
      const s = ctx.source.server
      ctx.source.sendSystemMessage(Text.of(`§6보스 난이도 §7— 전역 체력 §e${bdGlobalHp(s)}%§7 · 공격 §e${bdGlobalDmg(s)}%§7 (보스 ${BOSS_LIST.length}종)`))
      const liveG = bdOvGHp(s) > 0 || bdOvGDmg(s) > 0
      ctx.source.sendSystemMessage(Text.of(liveG
        ? '§c● 라이브 오버라이드 적용 중 §7— 월드 리셋 시 사라진다. 남기려면 §e/bossdiff export'
        : '§a● ls_config.js 파일 값 사용 중 §7— 월드 리셋에도 유지된다'))
      ctx.source.sendSystemMessage(Text.of('§7/bossdiff global <hp> <dmg> · set <boss> <hp> <dmg> · get <boss> · list · export · reset'))
      ctx.source.sendSystemMessage(Text.of('§7/bossdiff §eabs <boss> <체력>§7 — 절대 체력 (%보다 우선 · 0 이면 해제). 목표 시간으로 잡을 땐 이쪽.'))
      return 1
    })
    .then(Commands.literal('global').requires(s => s.hasPermission(2))
      .then(Commands.argument('hp', Arguments.INTEGER.create(event))
        .then(Commands.argument('dmg', Arguments.INTEGER.create(event)).executes(ctx => {
          // 하한(1)은 모드가 건다 — 자르는 곳이 하나면 호출부가 빠뜨릴 수 없다.
          LS.setBossGlobal(ctx.source.server,
            Arguments.INTEGER.getResult(ctx, 'hp'), Arguments.INTEGER.getResult(ctx, 'dmg'))
          ctx.source.sendSystemMessage(Text.of(`§a전역 보스 난이도: 체력 ${bdGlobalHp(ctx.source.server)}% · 공격 ${bdGlobalDmg(ctx.source.server)}% §7(새로 스폰되는 보스부터 적용)`))
          return 1
        }))))
    .then(Commands.literal('set').requires(s => s.hasPermission(2))
      .then(bossArg()
        .then(Commands.argument('hp', Arguments.INTEGER.create(event))
          .then(Commands.argument('dmg', Arguments.INTEGER.create(event)).executes(ctx => {
            const id = bossId(ctx)
            if (!BOSS_SET[id]) return bossFail(ctx, id)
            LS.setBossDiff(ctx.source.server, id,
              Arguments.INTEGER.getResult(ctx, 'hp'), Arguments.INTEGER.getResult(ctx, 'dmg'))
            ctx.source.sendSystemMessage(Text.of(`§a${id} → 체력 ${bdEffHp(ctx.source.server, id)}% · 공격 ${bdEffDmg(ctx.source.server, id)}%`))
            return 1
          })))))
    // 절대 체력 지정. 0 을 넣으면 해제되어 % 로 돌아간다.
    .then(Commands.literal('abs').requires(s => s.hasPermission(2))
      .then(bossArg()
        .then(Commands.argument('hp', Arguments.INTEGER.create(event)).executes(ctx => {
          const id = bossId(ctx)
          if (!BOSS_SET[id]) return bossFail(ctx, id)
          const v = Math.max(0, Arguments.INTEGER.getResult(ctx, 'hp'))
          LS.setBossAbs(ctx.source.server, id, v)
          ctx.source.sendSystemMessage(Text.of(v > 0
            ? `§a${id} → 체력 §e절대 ${v} §7(%는 무시된다 · 새로 스폰되는 보스부터)`
            : `§a${id} → 절대 체력 해제 §7(체력 ${bdEffHp(ctx.source.server, id)}% 로 복귀)`))
          return 1
        }))))
    .then(Commands.literal('get')
      .then(bossArg().executes(ctx => {
        const id = bossId(ctx); const s = ctx.source.server
        if (!BOSS_SET[id]) return bossFail(ctx, id)
        const ab = bdEffAbs(s, id)
        ctx.source.sendSystemMessage(Text.of(ab > 0
          ? `§6${id}§7 — 체력 §e절대 ${ab}§7 · 공격 §e${bdEffDmg(s, id)}%§8(${bdSrc(s, id, 'dmg')})`
          : `§6${id}§7 — 체력 §e${bdEffHp(s, id)}%§8(${bdSrc(s, id, 'hp')})§7 · 공격 §e${bdEffDmg(s, id)}%§8(${bdSrc(s, id, 'dmg')})`))
        return 1
      })))
    .then(Commands.literal('list').executes(ctx => {
      const s = ctx.source.server
      ctx.source.sendSystemMessage(Text.of(`§6전역: 체력 ${bdGlobalHp(s)}% · 공격 ${bdGlobalDmg(s)}% §7| 개별 오버라이드:`))
      let any = false
      bdAllIds(s).forEach(id => {
        const ab = bdEffAbs(s, id)
        const ph = bdOvHp(s, id); const pd = bdOvDmg(s, id)
        // 절대 지정은 파일 값이어도 보여준다 — 이제 이쪽이 주 튜닝 수단이라 안 보이면 놓친다.
        if (ab > 0) { any = true; ctx.source.sendSystemMessage(Text.of(`§7 ${id}: 체력 §e절대 ${ab}§7 · 공격 ${bdEffDmg(s, id)}%`)) }
        else if (ph > 0 || pd > 0) { any = true; ctx.source.sendSystemMessage(Text.of(`§7 ${id}: 체력 ${bdEffHp(s, id)}% · 공격 ${bdEffDmg(s, id)}%`)) }
      })
      if (!any) ctx.source.sendSystemMessage(Text.of('§7 (개별 설정 없음 — 전부 전역값 사용)'))
      return 1
    }))
    // 라이브 오버라이드만 지운다 → ls_config.js 의 파일 값으로 되돌아간다
    .then(Commands.literal('reset').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      // 예전엔 BOSS_LIST 를 훑으며 키마다 0 을 넣었다. 그러면 **목록에 없는 보스에 걸어둔
      // 오버라이드는 안 지워진다** — 모드팩이 보스를 늘리면 목록이 낡으니 실제로 생길 수 있다.
      // 이제는 모드가 자기 표를 통째로 비운다.
      LS.resetBossDiff(s)
      ctx.source.sendSystemMessage(Text.of(`§a라이브 오버라이드 초기화 — ls_config.js 값으로 복귀 §7(전역 ${bdGlobalHp(s)}% / ${bdGlobalDmg(s)}%)`))
      return 1
    }))
    // 지금 적용 중인 값을 ls_config.js 에 붙여넣을 형태로 뽑는다 (월드 리셋에도 남기려면 이걸 파일에 박는다)
    .then(Commands.literal('export').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      ctx.source.sendSystemMessage(Text.of('§6═══ ls_config.js 에 붙여넣기 ═══'))
      ctx.source.sendSystemMessage(Text.of(`§f    globalHp: ${bdGlobalHp(s)},`))
      ctx.source.sendSystemMessage(Text.of(`§f    globalDmg: ${bdGlobalDmg(s)},`))
      ctx.source.sendSystemMessage(Text.of('§f    perBoss: {'))
      // 줄을 **한 번만** 만든다. 예전엔 채팅용과 로그용으로 같은 루프를 두 벌 돌렸는데,
      // 한쪽만 고치면 «채팅에는 있는데 로그에는 없는» 항목이 생기고 그 로그를 그대로 붙여넣게 된다.
      const lines = []
      bdAllIds(s).forEach(id => {
        const hp = bdEffHp(s, id); const dmg = bdEffDmg(s, id); const ab = bdEffAbs(s, id)
        if (ab > 0) { lines.push(`      '${id}': { absHp: ${ab}, dmg: ${dmg} },`); return }
        // 전역값과 같으면 굳이 적지 않는다
        if (hp === bdGlobalHp(s) && dmg === bdGlobalDmg(s)) return
        lines.push(`      '${id}': { hp: ${hp}, dmg: ${dmg} },`)
      })
      lines.forEach(l => ctx.source.sendSystemMessage(Text.of('§f' + l)))
      ctx.source.sendSystemMessage(Text.of('§f    }'))
      if (!lines.length) ctx.source.sendSystemMessage(Text.of('§8(개별 설정 없음 — 전역값만 옮기면 된다)'))
      ctx.source.sendSystemMessage(Text.of('§7※ 서버 로그에도 같은 내용이 남는다 — 복사하기 편함'))
      console.log('[LS-BOSSDIFF] export → globalHp:' + bdGlobalHp(s) + ' globalDmg:' + bdGlobalDmg(s))
      lines.forEach(l => console.log(l))
      return 1
    })))
})

console.log(`[Last Stardust] 보스 난이도 다이얼 로드됨 (보스 ${BOSS_LIST.length}종)`)
