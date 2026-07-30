# Last Stardust — 커스텀 시스템 (원본 설계도 · 보존용)

> ## ⚠️ 이 문서는 만들기 전의 초안이다. 지금 코드와 다르다.
>
> 아래는 2026-07 초 Phase 7 을 시작하기 전에 그린 **설계도**다. 그 뒤로 전부 구현됐고,
> 구조도 크게 달라졌다. **여기 있는 코드를 근거로 삼지 말 것** — `let TREASURY = 0` 같은
> 초안 스니펫이 그대로 남아 있다.
>
> | 이 문서 | 실제 |
> |---|---|
> | §1 공동 금고 = KubeJS 변수 + `/treasury` | 모드 소유(`data/TownData`) · 4트랙×4레벨 업그레이드 창(`M` 키) · 창고형 보관함 |
> | §2 밤 공성 = `ACTIVE_GATEWAYS` 초안 | `ls_siege.js` 1,082줄 — 위협도·웨이브 티어·성벽 내구도·균열 노드·대공세·최종장 |
> | §3 FTB Quests 챕터로 관문 해금 | **균열 원정** — 성역에서 정수를 바쳐 봉인 해제 → 링 거리 랜덤 제단 → 보스 4관문 (`ls_rift.js`) |
> | §4 이중 지갑 | 개인 Ducat + 공동 금고. 성장축은 유물 각성 5성(`ls_ascend.js`)이 추가됨 |
>
> **지금 무엇이 어디에 있는지는 `docs/ARCHITECTURE.md` 를 본다.**
> 설계 의도와 원칙은 `DESIGN.md`, 진행 상황은 `docs/TODO.md`, 모드 쪽은 `moddev/lsrelics/README.md`.
>
> 그래도 지우지 않는 이유: **왜 이 모양이 됐는지**가 여기 남아 있다. 이중 지갑, 공동체 단위 성장,
> "개인 파워는 낮게" 같은 판단은 지금 코드에도 그대로 살아 있다.

---

## 1) 공동 금고 (Communal Treasury = 팀 공유 계좌)
**아이디어:** 다같이 입금하는 하나의 공유 잔액, 마을 업그레이드는 여기서 지불. 개인 잔액과 분리해서
"강해짐"이 공동체 단위가 되게(P4).

**방식(가장 단순·모드 무관):** 금고를 KubeJS 영구 데이터로 추적하고 명령어 제공.
```js
// server_scripts/treasury.js  — 초안(DRAFT)
let TREASURY = 0 // TODO: KubeJS 영구 데이터 / 월드 저장소로 지속화

ServerEvents.commandRegistry(event => {
  const { commands: Commands, arguments: Arguments } = event
  event.register(
    Commands.literal('treasury')
      .then(Commands.literal('balance').executes(ctx => {
        ctx.source.sendSystemMessage(Text.of(`🏛️ 공동 금고: ${TREASURY}`)); return 1
      }))
      .then(Commands.literal('deposit').then(Commands.argument('amount', Arguments.INTEGER.create(event))
        .executes(ctx => {
          const amt = Arguments.INTEGER.getResult(ctx, 'amount')
          // TODO: 먼저 경제 모드로 플레이어 '개인' 잔액에서 amt 차감
          TREASURY += amt
          ctx.source.sendSystemMessage(Text.of(`+${amt} → 공동 금고 (${TREASURY})`)); return 1
        })))
  )
})
```
**지출 = 마을 업그레이드:** 각 업그레이드를 금고 비용 뒤에 두기 (해당 FTB Quest 보상 안에서 처리하거나,
`TREASURY >= cost`를 확인하는 `/treasury buy <업그레이드>` 명령어로).

## 2) 미처리 관문 밤 공성  (T4의 긴장감)
**아이디어:** 그룹이 아직 안 깬 *활성* 관문이 밤에 성역을 공격. 깨면 → 보상 + 멈춤. 두면 → 반복 방어전.

**현실적 구현(까다로운 길찾기 회피):** 활성 관문 좌표 집합을 두고, 밤이 되면 각 관문마다 허브 주변에
스케일된 웨이브 스폰.
```js
// server_scripts/siege.js  — 초안(DRAFT)
const ACTIVE_GATEWAYS = [] // {dim, x, y, z} — 지역 정화 시 추가, 클리어 시 제거
const HUB = { x: 0, y: 64, z: 0 } // 성역 스폰 좌표로 설정

let lastDay = -1
ServerEvents.tick(event => {
  const level = event.server.overworld()
  const t = level.dayTime() % 24000
  const day = Math.floor(level.dayTime() / 24000)
  if (t > 13000 && t < 13100 && day !== lastDay) { // 해질녘, 밤당 1회
    lastDay = day
    const n = ACTIVE_GATEWAYS.length
    if (n === 0) return
    const waveSize = 4 + n * 3 // 가드레일: 완만하게 스케일 — 마을 방어로 버텨야 함
    for (let i = 0; i < waveSize; i++) {
      // TODO: 허브 외곽(링 오프셋)에 '오염된 몹' 스폰, 플레이어 향해 어그로
      // level.createEntity('cataclysm:...' 또는 바닐라).setPos(...).spawn()
    }
    level.players.forEach(p => p.tell(Text.red(`🌑 균열이 성역을 노린다… (활성 관문 ${n})`)))
  }
})
```
**가드레일(P6):** `waveSize`를 튜닝해서 사람 적은 밤에도 건설/자동 방어로 기본선이 버티게 — 밤 놓쳤다고
아무도 손해 안 보게. `활성 관문 수`에 따라 천천히 상승.

**정할 튜닝 노브:** 동시 활성 관문 최대 수; 클리어 = 영구 봉인 vs 주기적 재활성; 공성 강도 곡선.

## 3) FTB Quests — 정화 진행 (공동 목표)
그룹이 항상 "다음 뭐 할지" 알게 챕터를 짜고, 지역을 정화하면 그 지역의 관문이 열리게.
추천 챕터 뼈대:
1. **표류 · 성역 (도착)** — 기초: 허브 점유, Waystone 설치, 공동 창고+상점 세팅.
2. **첫 변경 (First Frontier)** — 둥지 N개 소탕 / 구조물 탐험 → 첫 코인 보상.
3. **지역 I 정화** — Cataclysm/지역 보스 처치 → **보상: 관문 I 해금/노출** (KubeJS 이벤트로).
4. **관문 I 원정** — 관문 클리어(또는 차원 입장) → 금고 코인 + 장비.
5. **마을 발전 I** — **금고**에서 지불하는 공동 건설/자동화 마일스톤.
6. …지역 → 관문 → 마을 티어 반복, 각각 다음을 해금.

**연결 훅:** FTB Quest 보상 → KubeJS 이벤트로 (a) `ACTIVE_GATEWAYS`에 관문 추가, (b) 개인 지갑/금고에
코인 지급, (c) 다음 마을 업그레이드 구매 해금. **FTB XMod Compat** 필요.

## 4) 경제 튜닝 (이중 지갑 가드레일)
- **코인 원천:** 몹/보스 전리품 → 허브 상점서 판매; 퀘스트·관문 보상.
- **개인 지갑 소모처:** 치장(Macaw's/장식), 편의, 소소·수평 장비만 — 순수 파워는 X.
- **공동 금고 소모처:** 마을 자동화(Create), 방어, 시설 티어 — 진짜 파워 곡선은 여기.
- 개인 파워는 낮게 유지해 아무도 그룹을 앞지르지 않게(P4); 신나는 스케일링은 공동 쪽에.

---
### Phase 7 진행 순서
1. 경제 모드 설정 + 허브 상점 채우기 (원천 & 소모처).
2. 금고 명령어(§1) 작동.
3. FTB Quests 챕터 1–3 작성(§3).
4. 공성 스크립트(§2) — 테스트 관문 1개로 `waveSize` 튜닝.
5. 그룹 진행에 맞춰 챕터/관문/차원 확장.
