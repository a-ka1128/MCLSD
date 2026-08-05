// priority: 900
// Last Stardust — 목소리 (3채널 연출 라이브러리 + 안내자 호데고스 대사 테이블)
//
// ── 왜 이 파일이 있나 ──
// STORY.md 의 화자(호데고스)가 인게임에 존재하지 않았다. 서사는 문서에만 있고,
// 플레이어가 실제로 듣는 건 각 스크립트가 제각각 찍는 say()/title 뿐이었다.
// 이 파일은 두 가지를 한다:
//   ① 3채널 연출 함수 표준화 — 앞으로 모든 스크립트가 같은 목소리로 말하게
//   ② 안내자 대사 테이블 — {조건, 우선순위, 1회성, 쿨다운} 구조 + 트리거
//
// ── 3채널 (STORY 문체 규칙) ──
//   vSys()   시스템   : [ ~합니다. ]  무감정 존댓말·대괄호. 감정 표현 금지(최종장의 단 한 번을 위해)
//   vGuide() 호데고스 : 노(老)안내자의 구어체 반말. "~게야 / ~걸세 / ~하게". 반 발짝만 알려줌
//   vWorld() 지문     : 3인칭 문어체. 호데고스와 대비를 만드는 유일한 문어체 구역
//   vStar()  성좌     : 꺼진 별의 중계. 각성 단계 따라 동사 승급
//
// ── 설계 원칙: 이 파일은 다른 스크립트를 수정하지 않는다 ──
// 트리거를 ls_rift/ls_siege 안에 심으면 그 파일들이 이 파일에 의존하게 되고,
// 로드 순서가 꼬이면 공성·관문이 통째로 죽는다. 대신 여기서 persistentData 를
// 주기적으로 훑어 "값이 바뀐 순간"을 잡는다. 이 파일이 통째로 사라져도 게임은 그대로 돌아간다.
//
// ── 침묵이 기본, 발화가 이벤트 ──
// 안내자가 매번 떠들면 아무도 안 읽는다. 자발 발화(앰비언트)는 토큰 버킷으로 제한하고,
// 서사 분기점(전멸·관문 해방·첫 공세)은 예산과 무관하게 항상 말한다.
//
// 저장: persistentData — vc_* (1회성 플래그·회전 인덱스·감시 스냅샷·플레이어별 마지막 접속일)

// ── 저장소 ──
function vcStore(server) { return server.overworld().persistentData }
function vcGetI(server, k) { return vcStore(server).getInt(k) }
function vcSetI(server, k, v) { vcStore(server).putInt(k, v) }
function vcGetB(server, k) { return vcStore(server).getBoolean(k) }
function vcSetB(server, k, v) { vcStore(server).putBoolean(k, v) }

function vcTicks(server) { return Number(server.overworld().getDayTime()) }
function vcDay(server) { return Math.floor(vcTicks(server) / 24000) }
function vcDayTime(server) { return vcTicks(server) % 24000 }

// ── 한국어 조사 ──
// 플레이어 이름 뒤에 붙는 조사를 받침 유무로 고른다. 한글이 아니면(영문 닉) 받침 없는 쪽.
// ※ ls_fate 의 '와/과' 버그와 같은 계열 — 하드코딩하면 절반이 어색해진다.
function vcJosa(word, withJong, noJong) {
  const s = String(word || '')
  if (!s.length) return noJong
  const c = s.charCodeAt(s.length - 1)
  if (c >= 0xAC00 && c <= 0xD7A3) return ((c - 0xAC00) % 28) !== 0 ? withJong : noJong
  return noJong
}

// ════════════════════════════════════════════════════════════
//  3채널 연출 함수 (다른 스크립트에서 그대로 호출해 쓰라고 전역으로 둔다)
// ════════════════════════════════════════════════════════════

function vcTellAll(server, text) { server.players.forEach(p => p.tell(Text.of(text))) }
function vcPlayAll(server, sound, vol, pitch) {
  try { server.runCommandSilent(`execute as @a at @s run playsound ${sound} master @s ~ ~ ~ ${vol} ${pitch}`) } catch (e) { lsWarn('ls_voice:56', e) }
}
function vcPlayTo(player, sound, vol, pitch) {
  try { player.server.runCommandSilent(`execute as ${player.username} at @s run playsound ${sound} master @s ~ ~ ~ ${vol} ${pitch}`) } catch (e) { lsWarn('ls_voice:59', e) }
}

// ① 시스템 — 무감정 존댓말. 소리도 건조하게(노트블록 하이햇).
function vSys(server, text) {
  vcTellAll(server, `§8[ §7${text} §8]`)
  vcPlayAll(server, 'minecraft:block.note_block.hat', 0.5, 1.6)
}
function vSysTo(player, text) {
  player.tell(Text.of(`§8[ §7${text} §8]`))
  vcPlayTo(player, 'minecraft:block.note_block.hat', 0.5, 1.6)
}

// ② 호데고스 — 이름표를 항상 달아준다. "누가 말하는지" 가 서사의 절반이라.
function vGuide(server, text) {
  vcTellAll(server, `§6✧ §e호데고스 §8│ §f${text}`)
  vcPlayAll(server, 'minecraft:entity.allay.ambient_without_item', 0.6, 0.7)
}
function vGuideTo(player, text) {
  player.tell(Text.of(`§6✧ §e호데고스 §8│ §f${text}`))
  vcPlayTo(player, 'minecraft:entity.allay.ambient_without_item', 0.6, 0.7)
}
// ※ 「이어지는 말」 헬퍼(vGuideCont)는 2026-07-31 삭제했다. 대사 10종 중 두 줄짜리가
//   하나도 없어 한 번도 불린 적이 없다. 필요해지면 한 줄이라 그때 다시 쓰는 게 낫다 —
//   «언젠가 쓸 것»으로 남겨두면 스캐너가 매번 지적하고, 그 소음이 진짜 죽은 코드를 가린다.

// ③ 지문 — 3인칭 문어체. 기울임 + 어두운 회색으로 "목소리가 아님"을 표시.
function vWorld(server, text) {
  vcTellAll(server, `§8§o  ${text}`)
}

// ④ 성좌(꺼진 별) — 각성 단계 따라 동사가 승급한다.
// ※ 조사를 '을/를' 로 고정하면 안 된다. 동사마다 요구하는 조사가 다르다:
//   바라보다·주목하다 → 을/를 · 눈길을 거두다 → 에게서 · 이름을 부르다 → 의
// 그래서 동사만이 아니라 {조사, 꼬리} 를 한 쌍으로 들고 다닌다.
const VC_STAR_LINE = {
  0: { josa: 'eul', tail: '바라봅니다' },
  1: { josa: 'eul', tail: '바라봅니다' },
  2: { josa: 'egeseo', tail: '눈길을 거두지 않습니다' },
  3: { josa: 'eul', tail: '주목합니다' },
  4: { josa: 'ui', tail: '이름을 부릅니다' },
  5: { josa: 'eul', tail: '통해 빛나려 합니다' }
}
function vcStarJosa(name, kind) {
  if (kind === 'egeseo') return '에게서'
  if (kind === 'ui') return '의'
  return vcJosa(name, '을', '를')
}
function vStar(server, name, star) {
  const e = VC_STAR_LINE[Math.max(0, Math.min(5, star | 0))] || VC_STAR_LINE[1]
  vcTellAll(server, `§d[ §f꺼진 별 하나가 '${name}'${vcStarJosa(name, e.josa)} ${e.tail}. §d]`)
  vcPlayAll(server, 'minecraft:block.amethyst_block.chime', 0.7, 1.5)
}

// ⑤ 타이틀 — 색상 기본값을 하나로 모아둔다.
function vTitle(server, title, subtitle, color) {
  const c = color || 'gold'
  try {
    server.runCommandSilent(`title @a title {"text":"${title}","color":"${c}","bold":true}`)
    if (subtitle) server.runCommandSilent(`title @a subtitle {"text":"${subtitle}","color":"gray"}`)
  } catch (e) { lsWarn('ls_voice:118', e) }
}

// ════════════════════════════════════════════════════════════
//  발화 예산 (토큰 버킷)
// ════════════════════════════════════════════════════════════
// "세션당 자발 발화 3~5회" 를 코드로 강제한다. 서사 분기점은 이 예산을 쓰지 않는다.
// 버킷이라 아껴두면 몰아서 쓸 수 있고, 계속 떠들면 자연히 조용해진다.
const VC_BUDGET_MAX = 4
const VC_REFILL_DAYS = 3   // 인게임 3일(≈ 실시간 1시간)마다 1회분 회복

function vcBudget(server) {
  if (!vcGetB(server, 'vc_budget_init')) {
    vcSetB(server, 'vc_budget_init', true)
    vcSetI(server, 'vc_budget', VC_BUDGET_MAX)
    vcSetI(server, 'vc_budget_day', vcDay(server))
  }
  return vcGetI(server, 'vc_budget')
}
function vcRefill(server) {
  vcBudget(server)
  var refillDay = vcDay(server)
  var vcLastDay = vcGetI(server, 'vc_budget_day')
  if (refillDay - vcLastDay < VC_REFILL_DAYS) return
  var gain = Math.floor((refillDay - vcLastDay) / VC_REFILL_DAYS)
  vcSetI(server, 'vc_budget', Math.min(VC_BUDGET_MAX, vcGetI(server, 'vc_budget') + gain))
  vcSetI(server, 'vc_budget_day', vcLastDay + gain * VC_REFILL_DAYS)
}
function vcTakeBudget(server) {
  if (vcBudget(server) <= 0) return false
  vcSetI(server, 'vc_budget', vcGetI(server, 'vc_budget') - 1)
  return true
}

// ════════════════════════════════════════════════════════════
//  대사 테이블
// ════════════════════════════════════════════════════════════
// ch      : 'guide' | 'sys' | 'world'
// once    : 서버 생애 1회만 (persistentData 에 플래그)
// cd      : 쿨다운(틱). 같은 id 를 이 안에 두 번 말하지 않는다
// ambient : true 면 발화 예산을 소모한다 (= 조용할 수 있음). 서사 분기점은 false
// lines   : 여러 개면 순환(random 아님 — 랜덤은 같은 대사가 연달아 나와서 티가 난다)
// after   : 대사 뒤에 붙일 추가 연출 함수(선택)
const VC_LINES = {

  // ── 전멸 (Hades 식: 실패가 대사를 부른다) ──
  wipe: {
    ch: 'guide', cd: 400, ambient: false,
    lines: [
      '"일어나게. 어둠은 자네들이 쓰러진 걸 기억하지만, 다시 일어난 것도 기억하지."',
      '"옛 별지기들도 처음엔 그랬어. ……끝은 달랐지만. 자네들은 다르게 만들어보게."',
      '"죽음이 아니야. 별빛이 잠시 흐려진 것뿐이지."',
      '"오늘 밤은 놈들의 것이군. 내일 밤은 아니야."',
      '"……익숙해지지는 말게. 그것만 부탁하지."'
    ]
  },

  // ── 첫 공세 격퇴 = 유물 해금 ──
  first_siege: {
    ch: 'guide', once: true, ambient: false,
    lines: ['"버텼군. ……맨몸으로 첫 밤을 넘긴 건 자네들이 처음일세."'],
    after: (server) => {
      vSys(server, '제1야를 버텨냈습니다.')
      server.scheduleInTicks(50, () => {
        vSys(server, '증명이 인정되었습니다 — 제단이 당신을 부릅니다.')
      })
    }
  },

  // ── 관문(뿌리) 해방 — 별이 하나씩 돌아온다 ──
  // ls_rift 가 자기 타이틀("봉인 해방")을 먼저 띄우므로, 이건 100틱 뒤 두 번째 박자로 얹힌다.
  gate_1: {
    ch: 'guide', once: true, ambient: false,
    lines: ['"……하나 돌아왔군. 남은 뿌리는 셋이야. 오늘 밤부터 놈들이 달라질 걸세."']
  },
  gate_2: {
    ch: 'guide', once: true, ambient: false,
    lines: ['"둘. 하늘이 조금 밝아졌지? 그만큼 그늘도 깊어졌다는 뜻이야. 방심하지 말게."']
  },
  gate_3: {
    ch: 'guide', once: true, ambient: false,
    lines: ['"셋이야. ……이쯤 오면 놈들도 자네들 이름을 알게 되지. 여기서부턴 놈들이 먼저 찾아올 걸세."']
  },
  gate_4: {
    ch: 'guide', once: true, ambient: false,
    lines: ['"네 뿌리가 모두 재가 됐어. 이제 아래로 내려가는 길이 열렸네. ……그 아래는 나도 말해줄 수가 없어."']
  },

  // ── 위협도 임계 ──
  threat_high: {
    ch: 'guide', cd: 24000 * 6, ambient: false,
    lines: [
      '"밖이 무거워졌군. 오늘 밤은 벽 뒤에 붙어 있게."',
      '"어둠이 쌓이고 있어. 쌓인 만큼은 반드시 한 번에 쏟아지는 법이지."'
    ]
  },
  threat_crit: {
    ch: 'guide', cd: 24000 * 6, ambient: false,
    lines: [
      '"……이건 안 좋아. 다음 공세는 지금까지 것과 다를 걸세. 준비하게."',
      '"내가 본 것 중 가장 짙어. 오늘은 욕심내지 말고, 살아남는 것만 생각하게."'
    ]
  },

  // ── 성벽 붕괴 ──
  wall_break: {
    ch: 'guide', cd: 24000 * 2, ambient: false,
    lines: [
      '"벽이 무너졌어! 뒤로 물러서지 말고 — 서로 등을 붙이게!"',
      '"돌은 다시 쌓으면 그만이야. 자네들은 아니지. 살아서 물러나게."'
    ]
  },

  // ── 밤 앰비언트 (예산 소모 — 조용할 수 있고, 그게 정상) ──
  night: {
    ch: 'guide', cd: 24000 * 2, ambient: true,
    lines: [
      '"해가 졌군. ……서로의 이름을 불러주게. 어둠 속에서는 그게 생각보다 큰 힘이 되니까."',
      '"밤이야. 나는 지켜보고 있겠네. 언제나 그래왔듯이."',
      '"이런 밤이면 옛 별지기들 생각이 나. ……별 얘기는 아니야. 그냥, 그렇다는 게지."',
      '"조심하게. 오늘 밤 그늘이 평소보다 길어."',
      '"불을 끄지 말게. 빛이 있는 곳까지는 놈들도 함부로 못 오니까."'
    ]
  }
}

// ── 회전 인덱스 (persistentData — 재시작해도 이어진다) ──
function vcPick(server, id, lines) {
  if (lines.length === 1) return lines[0]
  const k = 'vc_rot_' + id
  const i = vcGetI(server, k) % lines.length
  vcSetI(server, k, (i + 1) % lines.length)
  return lines[i]
}

// ── 발화 엔트리 포인트 ──
// 반환: 실제로 말했으면 true. (1회성/쿨다운/예산/음소거에 걸리면 false)
function vSpeak(server, id) {
  if (vcGetB(server, 'vc_mute')) return false
  if (!server.players.length) return false          // 아무도 없는데 떠들면 대사만 낭비된다
  const e = VC_LINES[id]
  if (!e) { console.log('[LS-VOICE] unknown line id: ' + id); return false }

  if (e.once && vcGetB(server, 'vc_once_' + id)) return false
  const now = vcTicks(server)
  if (e.cd) {
    // ※ 여기서 이름이 겹치면 감시자가 통째로 죽는다. Rhino 는 블록 안의 const 를
    //    상위 스코프로 흘려서 `redeclaration of var last` 로 터진다 (실제로 겪음).
    //    블록 안 선언은 반드시 var + 고유 접두사.
    var vcLastCd = vcGetI(server, 'vc_cd_' + id)
    if (vcLastCd > 0 && now - vcLastCd < e.cd) return false
  }
  if (e.ambient && !vcTakeBudget(server)) return false

  const text = vcPick(server, id, e.lines)
  if (e.ch === 'sys') vSys(server, text)
  else if (e.ch === 'world') vWorld(server, text)
  else vGuide(server, text)

  if (e.once) vcSetB(server, 'vc_once_' + id, true)
  if (e.cd) vcSetI(server, 'vc_cd_' + id, now)
  if (e.after) { try { e.after(server) } catch (err) { console.log('[LS-VOICE] after() fail: ' + err) } }
  console.log(`[LS-VOICE] spoke ${id} (budget ${vcBudget(server)})`)
  return true
}

// ════════════════════════════════════════════════════════════
//  상태 감시 — persistentData 전이를 잡아 트리거로 바꾼다
// ════════════════════════════════════════════════════════════
// 다른 스크립트에 훅을 심지 않기 위한 장치. 1초에 한 번만 훑으므로 부하는 없다시피 하다.
const VC_WATCH_INTERVAL = 20

function vcThreatBand(t) { return t >= 13 ? 3 : t >= 10 ? 2 : t >= 7 ? 1 : 0 }

function vcWatch(server) {
  vcRefill(server)

  // ① 관문 진행도 — 오르는 순간을 잡는다.
  // 진행도 자체는 모드(LSData)가 소유하고(ls_rift.js 주석 참고), `vc_seen_rf` 는
  // "어디까지 대사를 읽어줬나" 하는 **이 파일의 기억**이라 persistentData 에 남는다.
  // 둘은 성격이 다르다 — 사본이 아니라 진도표다.
  const rf = LS.progress(server)
  const rfPrev = vcGetI(server, 'vc_seen_rf')
  if (rf > rfPrev) {
    vcSetI(server, 'vc_seen_rf', rf)
    var vcGateId = 'gate_' + rf
    if (VC_LINES[vcGateId]) {
      // ls_rift 의 "봉인 해방" 타이틀이 먼저 지나가도록 한 박자 늦춘다.
      server.scheduleInTicks(100, () => {
        try {
          vTitle(server, '별이 돌아왔다', '하늘을 보라', 'gold')
          vcPlayAll(server, 'minecraft:block.amethyst_block.chime', 0.8, 1.4)
        } catch (e) { lsWarn('ls_voice:307', e) }
      })
      server.scheduleInTicks(160, () => { try { vSpeak(server, vcGateId) } catch (e) { lsWarn('ls_voice:309', e) } })
    }
  } else if (rf < rfPrev) {
    vcSetI(server, 'vc_seen_rf', rf) // /rift reset 등으로 되돌아간 경우 스냅샷만 맞춘다
  }

  // ② 첫 공세 격퇴 — ls_siege 의 ls_first_siege_done
  // 예약 표식을 먼저 세운다. 감시는 1초마다 도는데 발화는 80틱 뒤라, 표식이 없으면
  // 그 사이 네 번 더 예약되어 같은 대사가 겹쳐 나간다.
  // ※ 공성 상태 넷(첫격퇴·위협·성벽·진행)은 모드가 소유한다 (이관 4단계, 2026-07-31).
  //   여기가 옛 키를 계속 읽으면 전부 «없음/0» 이 되어 **호데고스가 영원히 침묵한다.**
  //   이 파일은 다른 시스템의 상태 전이를 감시하는 구조라, 감시 대상이 옮겨가면 같이 따라가야 한다.
  if (LS.firstSiegeDone(server) && !vcGetB(server, 'vc_once_first_siege') && !vcGetB(server, 'vc_pend_first_siege')) {
    vcSetB(server, 'vc_pend_first_siege', true)
    server.scheduleInTicks(80, () => {
      try { if (!vSpeak(server, 'first_siege')) vcSetB(server, 'vc_pend_first_siege', false) } catch (e) { lsWarn('ls_voice:321', e) }
    })
  }

  // ③ 위협도 — 밴드가 올라갈 때만 (내려갈 땐 조용히)
  const band = vcThreatBand(LS.threat(server))
  const bandPrev = vcGetI(server, 'vc_seen_band')
  if (band !== bandPrev) {
    vcSetI(server, 'vc_seen_band', band)
    if (band > bandPrev) {
      if (band >= 3) vSpeak(server, 'threat_crit')
      else if (band >= 1) vSpeak(server, 'threat_high')
    }
  }

  // ④ 성벽 — 멀쩡(1) → 붕괴(2) 전이에서만. 0 = 아직 본 적 없음
  if (LS.wallInit(server)) {
    var vcWallSt = LS.wallHpRaw(server) <= 0 ? 2 : 1
    var vcWallPrev = vcGetI(server, 'vc_seen_wall')
    if (vcWallSt !== vcWallPrev) {
      vcSetI(server, 'vc_seen_wall', vcWallSt)
      if (vcWallSt === 2 && vcWallPrev === 1) vSpeak(server, 'wall_break')
    }
  }

  // ⑤ 밤의 시작 — 공성 중에는 말하지 않는다 (그때는 공성 스크립트가 화면을 다 쓴다)
  const t = vcDayTime(server)
  const isNight = t >= 13000 && t <= 23000
  const wasNight = vcGetB(server, 'vc_seen_night')
  if (isNight !== wasNight) {
    vcSetB(server, 'vc_seen_night', isNight)
    if (isNight && !LS.siegeActive(server) && !vcGetB(server, 'ls_time_locked')) {
      vSpeak(server, 'night')
    }
  }
}

let VC_TICK = 0
ServerEvents.tick(event => {
  VC_TICK++
  if (VC_TICK % VC_WATCH_INTERVAL !== 0) return
  try { vcWatch(event.server) } catch (e) { console.log('[LS-VOICE] watch fail: ' + e) }
})

// ════════════════════════════════════════════════════════════
//  전멸 감지
// ════════════════════════════════════════════════════════════
// 죽은 플레이어는 리스폰 전까지 server.players 에 체력 0 으로 남아 있다.
// 사망 시점엔 아직 체력이 남아 보일 수 있어 10틱 뒤에 센다.
EntityEvents.death(event => {
  const e = event.entity
  if (!e || String(e.type) !== 'minecraft:player') return
  const server = e.server
  if (!server) return
  server.scheduleInTicks(10, () => {
    var list = null, alive = 0
    try {
      list = server.players
      if (!list.length) return
      list.forEach(p => { if (Number(p.health) > 0) alive++ })
      if (alive === 0) {
        vWorld(server, '별빛이 모두 꺼졌다.')
        server.scheduleInTicks(40, () => { try { vSpeak(server, 'wipe') } catch (err) { lsWarn('ls_voice:383', err) } })
      }
    } catch (err) { console.log('[LS-VOICE] wipe check fail: ' + err) }
  })
})

// ════════════════════════════════════════════════════════════
//  접속 — 첫 접속 인사 / 재접속 요약
// ════════════════════════════════════════════════════════════
const VC_ABSENT_DAYS = 3   // 이 일수 이상 비웠을 때만 요약해준다 (매번 하면 잔소리가 된다)

function vcSeenKey(uname, k) { return 'vc_p_' + k + '_' + uname }

PlayerEvents.loggedIn(event => {
  const p = event.player
  if (!p) return
  const server = p.server
  if (!server) return
  const uname = p.username

  // 다른 로그인 훅(칭호·각성)이 먼저 정리되도록 뒤에 선다.
  server.scheduleInTicks(60, () => {
    // ※ try 안에서는 const/let 금지 (Rhino 가 'redeclaration of var' 로 터진다).
    //   같은 이름을 다른 try 블록에서 또 쓰면 특히 확실하게 터진다 — 아래 tick 핸들러와 겹쳤었다.
    var nowDay = 0, rf = 0, st = null, first = false
    try {
      nowDay = vcDay(server)
      rf = LS.progress(server)
      st = vcStore(server)
      first = !st.getBoolean(vcSeenKey(uname, 'known'))

      if (first) {
        st.putBoolean(vcSeenKey(uname, 'known'), true)
        vSysTo(p, '별지기로 등록되었습니다.')
        server.scheduleInTicks(50, () => {
          try { vGuideTo(p, '"왔군. ……오래 기다렸네. 이름은 나중에 묻지."') } catch (e) { lsWarn('ls_voice:418', e) }
        })
      } else {
        var lastDay = st.getInt(vcSeenKey(uname, 'day'))
        var lastRf = st.getInt(vcSeenKey(uname, 'rf'))
        var gap = nowDay - lastDay
        if (gap >= VC_ABSENT_DAYS && !vcGetB(server, 'vc_mute')) {
          var bits = []
          if (rf > lastRf) bits.push(`뿌리가 ${rf - lastRf}개 더 탔고`)
          var threat = LS.threat(server)
          if (threat >= 10) bits.push('밖이 꽤 짙어졌어')
          else if (threat >= 7) bits.push('어둠이 조금 쌓였고')
          else bits.push('큰일은 없었네')
          vGuideTo(p, `"자네가 없는 사이 — ${bits.join(', ')}. 따라잡을 시간은 충분해."`)
        }
      }
      st.putInt(vcSeenKey(uname, 'day'), nowDay)
      st.putInt(vcSeenKey(uname, 'rf'), rf)
    } catch (e) { console.log('[LS-VOICE] login fail: ' + e) }
  })
})

// 접속 중에도 주기적으로 도장을 갱신한다.
// loggedOut 만 믿으면 서버가 크래시로 죽었을 때 마지막 접속일이 옛날로 남아
// 다음 접속에 엉뚱한 "오래 비웠군" 요약이 나간다.
ServerEvents.tick(event => {
  if (VC_TICK % 400 !== 0) return
  // 이름을 접속 핸들러와 일부러 다르게 둔다 (Rhino 의 try 블록 스코프 누출 회피)
  var srv = null, tickStore = null, tickDay = 0, tickRf = 0
  try {
    srv = event.server
    tickStore = vcStore(srv)
    tickDay = vcDay(srv)
    tickRf = LS.progress(srv)   // 진행도는 모드가 소유 (ls_rift.js 주석 참고)
    srv.players.forEach(p => {
      tickStore.putInt(vcSeenKey(p.username, 'day'), tickDay)
      tickStore.putInt(vcSeenKey(p.username, 'rf'), tickRf)
    })
  } catch (e) { lsWarn('ls_voice:456', e) }
})

// ════════════════════════════════════════════════════════════
//  명령어
// ════════════════════════════════════════════════════════════
ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event

  event.register(Commands.literal('voice')
    .executes(ctx => {
      const s = ctx.source.server
      const send = t => ctx.source.sendSystemMessage(Text.of(t))
      send('§6═══ ✧ 안내자의 목소리 ═══')
      send(`§7발화 예산: §e${vcBudget(s)}§7/${VC_BUDGET_MAX} §8(${VC_REFILL_DAYS}일마다 +1 · 앰비언트만 소모)`)
      send(`§7음소거: ${vcGetB(s, 'vc_mute') ? '§c켜짐' : '§a꺼짐'}`)
      const done = Object.keys(VC_LINES).filter(k => VC_LINES[k].once && vcGetB(s, 'vc_once_' + k))
      send(`§7소진된 1회성 대사: §f${done.length}§7/${Object.keys(VC_LINES).filter(k => VC_LINES[k].once).length}${done.length ? ' §8(' + done.join(', ') + ')' : ''}`)
      send('§8/voice test <id> · /voice list · /voice mute · /voice reset')
      return 1
    })
    .then(Commands.literal('list').executes(ctx => {
      const s = ctx.source.server
      ctx.source.sendSystemMessage(Text.of('§6═══ 대사 테이블 ═══'))
      Object.keys(VC_LINES).forEach(k => {
        const e = VC_LINES[k]
        const flags = [e.once ? '1회' : null, e.ambient ? '예산' : null, e.cd ? `cd${Math.round(e.cd / 24000 * 10) / 10}일` : null]
          .filter(x => x).join('·')
        const spent = e.once && vcGetB(s, 'vc_once_' + k)
        ctx.source.sendSystemMessage(Text.of(`${spent ? '§8✔' : '§a·'} §f${k} §8[${e.ch}] §7${e.lines.length}줄 §8${flags}`))
      })
      return 1
    }))
    // 테스트 발화 — 1회성/쿨다운/예산을 무시하고 그냥 읽는다 (연출 확인용)
    .then(Commands.literal('test').requires(s => s.hasPermission(2))
      .then(Commands.argument('id', Arguments.STRING.create(event))
        .suggests((ctx, b) => { Object.keys(VC_LINES).forEach(k => b.suggest(k)); return b.buildFuture() })
        .executes(ctx => {
          const s = ctx.source.server
          const id = Arguments.STRING.getResult(ctx, 'id')
          const e = VC_LINES[id]
          if (!e) { ctx.source.sendSystemMessage(Text.of('§c그런 대사 id 는 없다. §7/voice list')); return 0 }
          const text = vcPick(s, id, e.lines)
          if (e.ch === 'sys') vSys(s, text)
          else if (e.ch === 'world') vWorld(s, text)
          else vGuide(s, text)
          return 1
        })))
    .then(Commands.literal('mute').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      const v = !vcGetB(s, 'vc_mute')
      vcSetB(s, 'vc_mute', v)
      ctx.source.sendSystemMessage(Text.of(v ? '§c안내자가 침묵한다.' : '§a안내자가 다시 말한다.'))
      return 1
    }))
    .then(Commands.literal('budget').requires(s => s.hasPermission(2))
      .then(Commands.argument('n', Arguments.INTEGER.create(event)).executes(ctx => {
        const s = ctx.source.server
        const n = Math.max(0, Math.min(VC_BUDGET_MAX, Arguments.INTEGER.getResult(ctx, 'n')))
        vcBudget(s); vcSetI(s, 'vc_budget', n)
        ctx.source.sendSystemMessage(Text.of(`§a발화 예산 → §e${n}§a/${VC_BUDGET_MAX}`))
        return 1
      })))
    // 1회성 대사만 되돌린다. 감시 스냅샷은 건드리지 않는다 —
    // 스냅샷까지 지우면 다음 감시 때 rf_progress 가 통째로 "새로 오른 것"으로 잡혀 대사가 몰아친다.
    .then(Commands.literal('reset').requires(s => s.hasPermission(2)).executes(ctx => {
      const s = ctx.source.server
      let n = 0
      Object.keys(VC_LINES).forEach(k => {
        if (vcGetB(s, 'vc_once_' + k)) { vcSetB(s, 'vc_once_' + k, false); n++ }
        vcSetI(s, 'vc_cd_' + k, 0)
        vcSetB(s, 'vc_pend_' + k, false)
      })
      vcSetI(s, 'vc_budget', VC_BUDGET_MAX)
      ctx.source.sendSystemMessage(Text.of(`§a1회성 대사 ${n}개 · 쿨다운 · 예산 초기화 §7(감시 스냅샷은 유지)`))
      return 1
    })))
})

console.log('[Last Stardust] 안내자의 목소리 로드됨 — 대사 ' + Object.keys(VC_LINES).length + '종 · 예산 ' + VC_BUDGET_MAX + '회')
