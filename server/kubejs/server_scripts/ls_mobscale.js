// Last Stardust — 일반 몹 스케일링 (세계가 같이 험해진다)
//
// 각성 5성이면 플레이어 화력이 각성 ×3 · 스킬트리 ×1.75 로 대략 ×5 가 된다.
// 그 상태에서 바닐라 좀비를 만나면 아무 긴장도 없어서 탐험이 심심해진다.
// 그래서 관문을 깰수록(rf_progress) 세상의 잡몹도 같이 세진다.
//
// ── 설계 원칙 ──
// 체력보다 공격력을 더 크게 올린다. 체력만 올리면 싸움이 "길어질 뿐" 지루해지고(탄약 스펀지),
// 실제 긴장감은 한 대가 아픈 데서 나온다. 잡몹은 여전히 빨리 죽되 방심하면 위험해야 한다.
//
// ── 다른 다이얼과의 관계 ──
//   관문 진행도 (LS.progress) → "세계가 얼마나 험해졌나" (영구·진행도 연동)
//   위협도 0~15 (ls_siege) → "오늘 밤 공성이 얼마나 센가" (일일·주기적)
// 축이 달라서 서로 겹치지 않는다.
//
// 배율 표는 ls_config.js(월드 밖)에 있다 — 월드를 리셋해도 튜닝이 남는다.

function msCfg() {
  const d = { hp: [1, 1, 1, 1, 1], dmg: [1, 1, 1, 1, 1], dmgCapAdd: 20 }
  return (typeof LS_CONFIG !== 'undefined' && LS_CONFIG.mob) ? LS_CONFIG.mob : d
}
function msStore(server) { return server.overworld().persistentData }

// 유효 티어 — 테스트용 강제값(ms_force, 1~5로 저장해 0과 구분)이 있으면 그걸 쓴다
function msTier(server) {
  const forced = msStore(server).getInt('ms_force')
  // 관문 진행도는 모드가 소유 (ls_rift.js 주석 참고). ms_force 는 이 파일만 쓰는 테스트 노브라 그대로 둔다.
  const t = forced > 0 ? forced - 1 : LS.progress(server)
  return Math.max(0, Math.min(4, t))
}
function msForced(server) { return msStore(server).getInt('ms_force') > 0 }

// 적대 몹인지 — 소·양·주민까지 두꺼워지면 곤란하다.
// **공격력 속성이 있는가**로 판단한다 (소·양·닭은 attack_damage 속성 자체가 없다).
//
// ── 여기 있던 1순위 경로는 죽은 코드였다 (2026-07-25 발견) ──
// 원래 `e.getType().getCategory()` 로 MobCategory 가 monster 인지 먼저 봤는데,
// KubeJS 에서 `e.getType()` 은 EntityType 객체가 아니라 **id 문자열**을 돌려준다
// (아래 45번째 줄 근처가 이미 `String(e.type)` 으로 같은 값을 쓴다).
// 그래서 `.getCategory()` 는 존재하지 않고, **모든 엔티티 스폰마다 예외를 던지고 있었다** —
// 판별은 언제나 아래 예비 경로로 떨어졌다. `catch (err) { }` 가 그걸 통째로 가리고 있었다.
//
// ※ 남은 판별은 원래 의도보다 **넓다.** MobCategory=monster 가 아니어도 공격력이 있으면
//   적대로 본다 — 철골렘·늑대·벌·눈사람도 스케일링을 받는다. 여태 이렇게 돌아왔으니
//   동작을 바꾸지 않고 그대로 두되, 좁힐지는 밸런스 판단이라 TODO 로 올린다.
function msIsHostile(e) {
  // 살아 있는 것만 속성을 가진다. 화살·아이템(ItemEntity)·경험치 구슬까지 들어오는데
  // 그쪽엔 getAttribute 자체가 없어 매번 예외가 났다 — 결과는 어차피 false 라 동작은
  // 맞았지만, 스폰마다 예외를 만들고 경고 로그를 채웠다. 부르기 전에 거른다.
  if (!e || typeof e.getAttribute !== 'function') return false
  // ※ ID 는 반드시 `minecraft:generic.attack_damage` 다. `generic.` 을 빼면 null 이 돌아와
  //   **모든 몹이 "비적대"로 판정되어 스케일링이 통째로 사라진다** — 예외도 안 나서 몇 달을 몰랐다.
  //   확인법: /attribute <대상> minecraft:generic.attack_damage base get
  try { return e.getAttribute('minecraft:generic.attack_damage') != null } catch (err) { lsWarn('ls_mobscale:attack-attr', err); return false }
}

EntityEvents.spawned(event => {
  const e = event.entity
  if (!e) return
  const server = e.server
  if (!server) return

  const id = String(e.type)
  if (id === 'minecraft:player') return
  // 보스는 ls_bossdiff.js 가 따로 스케일링한다 — 여기서 또 곱하면 이중 적용이 된다
  if (typeof BOSS_SET !== 'undefined' && BOSS_SET[id]) return

  const tier = msTier(server)
  if (tier <= 0) return
  if (!msIsHostile(e)) return

  const cfg = msCfg()
  const hpMul = cfg.hp[tier] || 1.0
  const dmgMul = cfg.dmg[tier] || 1.0

  if (hpMul !== 1.0) {
    var a = e.getAttribute('minecraft:generic.max_health')
    if (a) { var nb = a.getBaseValue() * hpMul; a.setBaseValue(nb); e.setHealth(nb) }
  }
  if (dmgMul !== 1.0) {
    var d = e.getAttribute('minecraft:generic.attack_damage')
    if (d) {
      var base = d.getBaseValue()
      // 원래 센 몹이 배율만으로 즉사기를 갖지 않게 절대 상한을 씌운다
      d.setBaseValue(Math.min(base * dmgMul, base + cfg.dmgCapAdd))
    }
  }
})

// ── 명령어 ──
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event

  event.register(Commands.literal('mobscale')
    .executes(ctx => {
      const s = ctx.source.server
      const cfg = msCfg(); const t = msTier(s)
      ctx.source.sendSystemMessage(Text.of(
        `§6일반 몹 스케일링 §7— 티어 §e${t}§7 (관문 ${LS.progress(s)}/4)${msForced(s) ? ' §c[강제]' : ''}`))
      ctx.source.sendSystemMessage(Text.of(
        `§7체력 §e×${cfg.hp[t]}§7 · 공격력 §e×${cfg.dmg[t]}§7 §8(공격력 상한: 원본+${cfg.dmgCapAdd})`))
      ctx.source.sendSystemMessage(Text.of('§8보스 제외(ls_bossdiff가 담당) · 비적대 몹 제외 · 새로 스폰되는 몹부터 적용'))
      ctx.source.sendSystemMessage(Text.of('§8/mobscale set <0-4> · auto'))
      return 1
    })
    // 테스트용: 관문 진행과 무관하게 티어를 강제한다
    .then(Commands.literal('set').requires(s => s.hasPermission(2))
      .then(Commands.argument('tier', Arguments.INTEGER.create(event)).executes(ctx => {
        const s = ctx.source.server
        const n = Math.max(0, Math.min(4, Arguments.INTEGER.getResult(ctx, 'tier')))
        msStore(s).putInt('ms_force', n + 1)   // 0과 구분하려고 +1 해서 저장
        const cfg = msCfg()
        ctx.source.sendSystemMessage(Text.of(
          `§a몹 스케일 티어 강제 §e${n}§a — 체력 ×${cfg.hp[n]} · 공격력 ×${cfg.dmg[n]} §7(새 스폰부터)`))
        return 1
      })))
    .then(Commands.literal('auto').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      msStore(s).putInt('ms_force', 0)
      ctx.source.sendSystemMessage(Text.of(`§a관문 진행도 연동으로 복귀 §7(현재 티어 ${msTier(s)})`))
      return 1
    })))
})

console.log('[Last Stardust] 일반 몹 스케일링 로드됨 — 관문 진행도 연동')
