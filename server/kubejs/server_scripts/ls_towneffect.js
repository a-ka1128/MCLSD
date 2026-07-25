// Last Stardust — 마을 발전 효과 중 성역 좌표·전송석이 얽힌 것들
//
// ── 왜 여기 있나 ──
// 마을 데이터·판정은 lsrelics 모드(LSData)로 옮겼다. 다만 **성역 좌표를 정하는 명령**
// (/sanctuary here)은 아직 ls_siege.js 에 있어서, 그 값을 모드로 밀어 넣는 역할이 필요하다.
// 성역까지 완전히 이관하면 이 파일 대부분이 사라진다 (docs/ARCHITECTURE.md 이관 1순위).
//
// ※ 2026-07-25: 마을을 모드로 옮기며 효과 구현을 빠뜨렸다가 복구한 것.

// 성역 좌표는 모드(LSData)가 유일 소유 — 사본을 두지 않는다 (ls_siege.js 주석 참고)
function teSancSet(server) { return LS.hasSanctuary(server) }
function teSanc(server) { return { x: LS.sanctuaryX(server), y: LS.sanctuaryY(server), z: LS.sanctuaryZ(server) } }

// 별빛 축복 — 원거리 보정에 쓰는 속성 (Apotheosis 제공)
const TE_PROJ_ATTR = 'apothic_attributes:projectile_damage'
const TE_PROJ_MOD = 'last_stardust:blessing_projectile'
const TE_PROJ_AMT = 0.25   // 투사체 피해 +25%

let TE_TICK = 0
let TE_BLESSED = []           // 지금 축복 범위 안에 있는 사람 (범위를 벗어나면 걷어내려고 기억한다)

ServerEvents.tick(event => {
  TE_TICK++
  if (TE_TICK % 40 !== 0) return   // 2초마다
  const server = event.server
  if (!teSancSet(server)) return
  const c = teSanc(server)

  // ※ 여기 있던 "성역 좌표를 모드로 동기화" 블록은 삭제했다. 좌표가 이제 모드에만 있어서
  //   동기화할 상대가 없다 — 그 코드가 조용히 죽어 귀환석을 먹통으로 만든 게 이 이관의 계기였다.

  // ── 성소 Lv4 「별빛 축복」 — 성역 64칸 상시 힘·신속 + 투사체 피해 ──
  // 힘(strength)은 근접에만 붙어서, 그대로 두면 원거리 가호는 축복을 절반만 받는다.
  // Apotheosis 의 projectile_damage 를 함께 걸어 활·총·지팡이도 같이 세지게 한다.
  try {
    if (LS.townLevel(server, 'sanctum') >= 4) {
      server.runCommandSilent(`execute positioned ${c.x} ${c.y} ${c.z} run effect give @a[distance=..64] minecraft:strength 4 0 true`)
      server.runCommandSilent(`execute positioned ${c.x} ${c.y} ${c.z} run effect give @a[distance=..64] minecraft:speed 4 0 true`)

      var inside = []
      server.players.forEach(p => {
        const teDx = p.x - c.x, teDy = p.y - c.y, teDz = p.z - c.z   // 고유 접두사 (Rhino 재선언 함정)
        if (teDx * teDx + teDy * teDy + teDz * teDz <= 64 * 64) inside.push(p.username)
      })
      // 새로 들어온 사람에게 걸고, 나간 사람에게서 걷어낸다
      inside.forEach(n => {
        if (TE_BLESSED.indexOf(n) < 0) {
          server.runCommandSilent(`attribute ${n} ${TE_PROJ_ATTR} modifier add ${TE_PROJ_MOD} ${TE_PROJ_AMT} add_multiplied_total`)
        }
      })
      TE_BLESSED.forEach(n => {
        if (inside.indexOf(n) < 0) {
          server.runCommandSilent(`attribute ${n} ${TE_PROJ_ATTR} modifier remove ${TE_PROJ_MOD}`)
        }
      })
      TE_BLESSED = inside
    } else if (TE_BLESSED.length) {
      TE_BLESSED.forEach(n => server.runCommandSilent(`attribute ${n} ${TE_PROJ_ATTR} modifier remove ${TE_PROJ_MOD}`))
      TE_BLESSED = []
    }
  } catch (e) { console.log('[LS-TOWNFX] blessing fail: ' + e) }
})

// ── 성소 Lv1 「웨이스톤 공명」 — 해금 전에는 전송석을 쓸 수 없다 ──
// 마을을 짓기 전엔 세상이 넓고 위험해야 한다. 전송망이 처음부터 열려 있으면
// 성역을 키울 이유도, 원정을 각오할 이유도 옅어진다.
//
// 블록 ID 를 하나씩 적지 않고 네임스페이스로 잡는다 — 전송석 모드는 색깔별 변종
// (portstone·sharestone 16색)까지 수십 종을 내서 목록으로 관리하면 반드시 빠뜨린다.
const TE_WAYSTONE_FREE = [
  'waystones:fleeting_memorial'   // 사망 지점 임시 표식 — 이건 막으면 너무 가혹하다
]

BlockEvents.rightClicked(event => {
  const server = event.server
  if (!server) return
  let id = ''
  try { id = String(event.block.id) } catch (e) { lsWarn('ls_towneffect:block-id', e); return }
  if (id.indexOf('waystones:') !== 0) return
  if (TE_WAYSTONE_FREE.indexOf(id) >= 0) return
  // 브릿지가 터지면 게이팅이 통째로 풀린다 — 성소를 안 지었는데도 전송석이 열린다.
  // 막지 않고 통과시키는 쪽이 안전한 방향이라 동작은 그대로 두되, 조용히 사라지게는 두지 않는다.
  try { if (LS.townLevel(server, 'sanctum') >= 1) return } catch (e) { lsWarn('ls_towneffect:gate-level', e); return }

  event.cancel()
  const p = event.player
  if (!p) return
  p.tell(Text.of('§5✧ 전송석이 침묵한다. §7— 성소 §e웨이스톤 공명§7을 지어야 공명이 시작된다.'))
  try {
    server.runCommandSilent(`execute as ${p.username} at @s run playsound minecraft:block.beacon.deactivate master @s ~ ~ ~ 0.6 0.8`)
  } catch (e2) { lsWarn('ls_towneffect:107', e2) }
})

console.log('[Last Stardust] 마을 효과 로드됨 — 별빛 축복(근접+원거리) · 전송석 게이팅 · 성역 동기화')
