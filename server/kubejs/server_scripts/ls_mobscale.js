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
  if (!e) return false
  // ── 2026-07-31: MobCategory == monster 로 좁혔다 (유저 결정, DECISIONS 2절 C안) ──
  // 스크립트에서는 이 판정을 할 수 없다 — KubeJS 의 `e.getType()` 이 EntityType 이 아니라
  // id 문자열이라 `.getCategory()` 가 없다. 그래서 모드에 다리를 놓고 그쪽에 물었다.
  //
  // **늑대·북극곰도 빠진다.** 「실제로 덤비는데 안 세지는」 경우가 생기는 걸 알고 고른 값이다.
  // 규칙이 한 문장으로 설명되는 쪽을 택했다 — «몬스터만 세진다».
  try { return LS.isMonster(e) } catch (err) { lsWarn('ls_mobscale:isMonster', err) }

  // 예비 경로 — 다리가 없을 때(모드 없이 스크립트만 돌릴 때)만 온다.
  // ※ ID 는 반드시 `minecraft:generic.attack_damage` 다. `generic.` 을 빼면 null 이 돌아와
  //   **모든 몹이 "비적대"로 판정되어 스케일링이 통째로 사라진다** — 예외도 안 나서 몇 달을 몰랐다.
  //   확인법: /attribute <대상> minecraft:generic.attack_damage base get
  if (typeof e.getAttribute !== 'function') return false
  try { return e.getAttribute('minecraft:generic.attack_damage') != null } catch (err) { return false }
}

// ── 다시 적용돼도 결과가 같아야 한다 (2026-07-31) ──
// **`EntityEvents.spawned` 는 «새로 태어날 때»만 오지 않는다.** 디스크에서 다시 로드될 때도
// 온다. 그래서 여태 **서버를 껐다 켤 때마다 살아 있던 몹 전부가 다시 곱해졌다.**
//
// 실측: 태그를 붙인 좀비를 진행도 1에서 소환 → 24.0(20×1.2) → 재시작 → **28.8(20×1.2²)**.
// 재시작 10번이면 ×6.2 다. 백업·업데이트로 서버를 내렸다 올릴 때마다 조용히 쌓여 왔고,
// 아무 오류도 안 났다.
//
// ── 엔티티 태그로 막으려다 실패한 기록 ──
// 처음엔 `ls_scaled` 태그를 붙이고 «있으면 건너뛰기»로 막으려 했다. **안 된다.**
// 재로드 때 이 이벤트가 **NBT 태그가 붙기 전에** 온다 — 그 시점의 `e.tags` 는 비어 있다.
// (확인법: 재시작 후 그 개체를 보면 태그는 멀쩡히 있는데 체력은 또 곱해져 있다.)
// 로드 순서에 기대는 표식은 여기서 쓸 수 없다.
//
// ── 이름 붙은 모디파이어로도 실패했다 ──
// 두 번째 시도는 `ls_enrage.js` 방식이었다 — 같은 ID 로 add 하면 «중복»이 아니라 «교체»라
// 몇 번을 걸어도 하나만 남는다. 소환 직후에는 정확히 그렇게 됐다(base 20 + modifier 0.2 = 24).
// **그런데 재시작하면 그 모디파이어가 base 에 구워진다.** 이 모드팩 어딘가가 그렇게 한다.
// 구워진 34.56 이 다음 로드의 새 base 가 되고, 거기에 또 붙는다. 같은 복리다.
//
// ── 그래서 세 번째: 현재값을 아예 안 읽는다 ──
// `LS.defaultAttrBase(e, id)` 가 그 **몹 종류의 공장 출고값**을 준다. 개체가 지금 얼마든
// 상관없이 늘 같은 수라, `출고값 × 배율` 도 늘 같다. **탐지가 필요 없다 — 몇 번을 돌려도
// 결과가 하나다.** 덤으로 이미 망가진 개체도 다음 로드에서 제 값으로 돌아온다.

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

  // ── 현재값을 읽지 않는다 ──
  // `LS.defaultAttrBase` 는 그 «몹 종류»의 공장 출고값을 준다. 지금 이 개체가 몇 번
  // 스케일링됐든 상관없이 늘 같은 수가 나오므로, 여기서 나온 결과도 늘 같다.
  // 없으면 -1 (0 과 구분된다). 다리가 없거나 속성이 없는 몹은 **건너뛴다** —
  // 옛 방식으로 되돌아가면 조용히 복리가 다시 붙는다.
  if (hpMul !== 1.0) {
    var defHp = -1
    try { defHp = LS.defaultAttrBase(e, 'minecraft:generic.max_health') } catch (err) { lsWarn('ls_mobscale:defHp', err) }
    var a = defHp > 0 ? e.getAttribute('minecraft:generic.max_health') : null
    if (a) {
      var target = defHp * hpMul
      a.setBaseValue(target)
      // 다친 채로 재로드된 개체를 꽉 채우면 «재시작하면 몹이 회복된다»가 된다.
      // 넘칠 때(또는 아직 0일 때)만 맞춘다.
      if (Number(e.health) > target || Number(e.health) <= 0.0) e.setHealth(target)
    }
  }
  if (dmgMul !== 1.0) {
    var defDmg = -1
    try { defDmg = LS.defaultAttrBase(e, 'minecraft:generic.attack_damage') } catch (err) { lsWarn('ls_mobscale:defDmg', err) }
    var d = defDmg > 0 ? e.getAttribute('minecraft:generic.attack_damage') : null
    // 원래 센 몹이 배율만으로 즉사기를 갖지 않게 절대 상한을 씌운다
    if (d) d.setBaseValue(Math.min(defDmg * dmgMul, defDmg + cfg.dmgCapAdd))
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
      // 티어 0 = 배율이 통째로 꺼진 상태다. 예전엔 "×1.0"만 보여서 "왜 안 세지지"를
      // 알 수 없었다 — 꺼졌다는 사실을 명시한다.
      if (t <= 0) {
        ctx.source.sendSystemMessage(Text.of('§c⚠ 티어 0 — 스케일링이 꺼져 있습니다. §7관문을 하나도 안 깼기 때문입니다.'))
        ctx.source.sendSystemMessage(Text.of('§8   테스트하려면 §7/mobscale set 1~4'))
      }
      ctx.source.sendSystemMessage(Text.of(
        `§7체력 §e×${cfg.hp[t]}§7 · 공격력 §e×${cfg.dmg[t]}§7 §8(공격력 상한: 원본+${cfg.dmgCapAdd})`))
      ctx.source.sendSystemMessage(Text.of('§8보스 제외(ls_bossdiff가 담당) · 비적대 몹 제외'))
      // 이미 살아 있는 몹은 절대 안 바뀐다 — 공성 도중에 티어를 바꿔도 그 웨이브엔 반영되지 않는다.
      ctx.source.sendSystemMessage(Text.of('§c※ 새로 스폰되는 몹부터 적용됩니다 — 이미 나와 있는 몹은 바뀌지 않습니다.'))
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
    }))
    // ── 이미 부풀어 있는 몹을 제 값으로 되돌린다 ──
    // 스폰 훅의 자동 복구에는 구멍이 셋 있어서, 손으로 한 번 쓸어야 하는 경우가 남는다:
    //   ① 티어 0 이면 훅이 일찍 return 한다 — 관문을 하나도 안 깬 상태에서는 아무것도 안 고쳐진다
    //   ② 늑대·철골렘·벌·눈사람은 **옛 넓은 판정 시절에 부풀었는데** 이제 비적대라 훅이 건너뛴다
    //      → 그것들은 영원히 부푼 채로 남는다. 이 명령만이 되돌릴 수 있다
    //   ③ 청크가 로드돼야 훅이 온다 — 안 가본 곳의 몹은 그대로다
    // 그래서 이건 «가끔 돌리는 청소»다. 로드된 청크만 훑으므로 여러 번 돌려도 안전하다(멱등).
    .then(Commands.literal('fix').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      const cfg = msCfg(); const tier = msTier(s)
      var mfSeen = 0, mfFixed = 0
      try {
        s.overworld().getEntities().forEach(en => {
          try {
            if (!en || !en.getAttribute) return
            var mfId = String(en.type)
            if (mfId === 'minecraft:player') return
            if (typeof BOSS_SET !== 'undefined' && BOSS_SET[mfId]) return   // 보스는 ls_bossdiff 담당
            var mfDef = LS.defaultAttrBase(en, 'minecraft:generic.max_health')
            if (mfDef <= 0) return
            mfSeen++
            // 지금 이 몹이 «받아야 할» 배율. 비적대면 1.0 — 즉 출고값으로 되돌린다.
            var mfMul = msIsHostile(en) ? (cfg.hp[tier] || 1.0) : 1.0
            var mfWant = mfDef * mfMul
            var mfAttr = en.getAttribute('minecraft:generic.max_health')
            if (!mfAttr) return
            if (Math.abs(mfAttr.getBaseValue() - mfWant) < 0.01) return
            mfAttr.setBaseValue(mfWant)
            if (Number(en.health) > mfWant) en.setHealth(mfWant)
            // 공격력도 같이 — 여기도 출고값에서 다시 계산한다
            var mfDefD = LS.defaultAttrBase(en, 'minecraft:generic.attack_damage')
            var mfD = mfDefD > 0 ? en.getAttribute('minecraft:generic.attack_damage') : null
            if (mfD) {
              var mfDMul = msIsHostile(en) ? (cfg.dmg[tier] || 1.0) : 1.0
              mfD.setBaseValue(Math.min(mfDefD * mfDMul, mfDefD + cfg.dmgCapAdd))
            }
            mfFixed++
          } catch (err) { lsWarn('ls_mobscale:fix-one', err) }
        })
      } catch (err) { lsWarn('ls_mobscale:fix', err) }
      ctx.source.sendSystemMessage(Text.of(
        `§a몹 스케일 정리 §7— 살펴본 ${mfSeen}기 중 §e${mfFixed}기§7를 되돌렸다 §8(티어 ${tier})`))
      ctx.source.sendSystemMessage(Text.of('§8로드된 청크만 훑는다. 멀리 다녀온 뒤 한 번 더 돌릴 것.'))
      return 1
    })))
})

console.log('[Last Stardust] 일반 몹 스케일링 로드됨 — 관문 진행도 연동')
