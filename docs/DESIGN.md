# Last Stardust — 서버 설계

> 협동 PvE 커스텀 마인크래프트 서버의 확정 설계 스펙. 이 문서가 기준(source of truth)이고, 아래 모드
> 리스트는 층별로 검증하며 조립. 자체 호스팅 (Ryzen 9 9950X3D, 61 GB RAM).

## 컨셉
- **이름:** Last Stardust
- **소개 한 문장:** 균열의 어둠이 삼킨 중세 아포칼립스, 마지막 성역에 모인 생존자들이 힘을 합쳐 잃어버린
  세상을 되찾는 협동 PvE RPG 서버.
- **결(Shape):** 협동형 공동 마을 + PvE. 경쟁 아님, 산업 과중 아님, 낮은 학습 곡선.
- **플랫폼:** **NeoForge 1.21.1** (앵커 모드 존재 확인됨).

## 설계 원칙(헌장)
- P1 공동 거점 — 다같이 쓰는 마지막 성역 마을 하나에 정착.
- P2 경제 — 화폐 + 상점 (아래 이중 지갑).
- P3 이중 성장 — 개인 장비 + 공동체/마을 시스템 둘 다 발전.
- P4 협동 우선, 경쟁 아님 — PvP 없음, 공동 목표, 발전 레이스 없음.
- P5 RPG = 가벼운 양념만.
- P6 반노가다 / 반낙오 — 매일 노가다 안 해도 뒤처지지 않게 (최선 노력).
- P7 PvE 필수 — 몹 + 협동 보스 레이드.
- 보정 A — 자원/창고는 공용; 개인 집 = 개성(치장)만, 개인 창고 없음.
- 보정 B — 낮은 인지 부담; 가벼운 자동화(Create) OK, 헤비 기술 티어는 제외.

## 경제 (이중 지갑, 가상 잔액, 강제형)
- **개인 지갑** = 개인 가상 잔액(인벤 안 먹음). 치장 + 편의 + 소소·수평 강화에 지출.
- **공동 금고** = 팀 공유 계좌. 마을 자동화 + 발전 + 방어에 지출 (진짜 파워 스케일링은 여기 →
  강해지는 게 경쟁이 아니라 협동).
- 코인 흐름: PvE 전리품 / 퀘스트 / 관문 보상 → 잔액 → (개인 상점 / 금고 예치 → 마을 발전).

## 진행 & PvE (테마 = 균열 / 최후의 성역)
전제: 균열이 어둠을 새어나오게 해 세상이 잠식됨; 그룹은 **마지막 성역**을 지키며 세상을 지역 단위로 되찾음.
3단 PvE:
1. **변경 개척** — 잠식된 변경으로 진출, 둥지 소탕, 안전지대 확장 (오버월드 PvE/탐험).
2. **지역 정화** — 지역의 오염 근원 보스 처치 → 지역 해방 + 봉인된 관문 노출 (= FTB Quests 공동 마일스톤).
3. **관문 이벤트 레이드** — 활성 관문은 살아있는 위협: 깨면(협동 웨이브/보스 레이드) → 보상 + 진정;
   두면 → 밤에 관문에서 성역으로 공성 진격 (거점 방어 PvE). 공격 vs 방어의 전략적 선택.
   - 가드레일(P6): 공성은 건설/자동 방어(금고로 투자)로 버틸 수 있게 튜닝 → 사람 적거나 결석해도 안 무너짐.
     튜닝: 동시 활성 관문 수; 미처리 많을수록 공성 강화?; 클리어 = 영구 봉인 vs 주기 재활성.

---

## 모드 리스트 (층별)  — ✅ NeoForge 1.21.1 검증됨 · 🔲 후보(잠그기 전 검증) · 🎨 취향 선택

### Layer 0 — 토대 (성능 + 라이브러리 + 편의)  — 검증 완료 2026-07-20
- ✅ 성능(클라): **Sodium**(공식 NeoForge) + **Iris**(셰이더 로더); 셰이더 **Rethinking Voxels**(어둠의 종말 무드, 저사양=Shrimple); 🔲 FerriteCore, ModernFix, Noisium
- 🔲 성능(서버): Canary/Radium (Lithium 포트), FerriteCore, ModernFix
- 🔲 라이브러리(의존, 필요 시 자동): Architectury API, Balm, GeckoLib, Placebo, Cloth Config, Kotlin for Forge
- ✅ 편의: **EMI** 1.1.24 (레시피 뷰어), **Corpse** 1.1.13 (협동 PvE 죽음 완화); 🔲 Jade(툴팁), Xaero's Minimap + World Map

### Layer 1 — 거점
- ✅ Waystones (거점 이동)
- 🔲 FTB Teams (+ 청크 보호용 FTB Chunks) — "팀 계좌" 금고의 기반

### Layer 2 — 경제
- 🔲 Simple Economy 또는 EconomyMod (서버사이드, 가상 잔액, 클라 모드 불필요) — 존재 확인됨
- ✅ KubeJS (공동 금고 = 팀 공유 계좌 커스텀, 업그레이드 게이팅)

### Layer 3 — 목표 / 진행  — 검증 완료
- ✅ FTB Quests 2101.x (서버 공용 퀘스트라인)
- ✅ KubeJS (+ **FTB XMod Compat** 으로 KubeJS↔FTB Quests 연결; Waystones도 지도에 표시)

### Layer 4 — 모험 / 월드 (변경 개척)  — 검증 완료 2026-07-20
- ✅ 월드젠: **Terralith** 2.6.x (Lithostitched 필요, 또는 데이터팩 형태)
- ✅ 구조물: **When Dungeons Arise** 2.1.68, **YUNG's Better Dungeons** (NeoForge 1.21.1);
  🔲 선택: Dungeons and Taverns, Towns and Towers, Explorify, Structory

### Layer 5 — 전투 / PvE + RPG (몹 + 레이드)  — RPG 스택 검증 완료 2026-07-20
- ✅ L_Ender's Cataclysm (레이드 보스)
- ✅ Gateways to Eternity (관문 이벤트 레이드; 의존 Placebo)
- ✅ 몹 다양성: **Alex's Mobs** (1.21.1 NeoForge 포트); 🔲 Mowzie's Mobs (미니보스 — 검증/선택)
- **RPG = "일반 RPG에서 살짝 가볍게" (유저 선택):**
  - ✅ Better Combat 2.3.1 (방향성 근접; 의존 playerAnimator + Cloth Config)
  - ✅ Apotheosis 8.5.4 (접사 / 젬 / 월드 티어 = "게임을 잡아먹지 않는" 루팅 RPG 성장)
  - ✅ Puffish Skills + Puffish Attributes + 스킬트리 팩 (Default Skill Trees / RPG Series 클래스)
  - 가볍게 유지: 노가다 레벨링(PMMO) 없음 / 풀 ARPG 개조 없음. **조절 다이얼:** 배울 게 많으면 Puffish Skills만 빼기.

### Layer 6 — 마을 자동화 / 내실 (라이트 Create)  — 검증 완료
- ✅ Create 6.0.1 (+ 라이트 애드온만, 예: Steam 'n' Rails)
- ✅ Farmer's Delight 1.3.2 (코지 공동 음식/농사; Create: Integrated Farming 애드온 조합은 피할 것)

### Layer 7 — 관문 차원 (관문 원정)  — 다차원, 검증 완료 2026-07-20
- 🅰 일반 관문: **Gateways to Eternity** 제자리 아레나 (확실한 클리어/공성 루프).
- 🅱 깊은 관문 = 실제 테마 차원 (전부 ✅ NeoForge 1.21.1), 각각 다른 원정지:
  - ✅ **Deeper and Darker → Otherside** — 워든 어둠 차원 (가장 어두운 곳)
  - ✅ **Twilight Forest** 4.8.x — 보스 진행형 대형 모험 차원 (리치/히드라 등)
  - ✅ **The Undergarden** 0.9.6 — 버섯 지하 세계 (15 바이옴, 고유 장비)
  - ✅ **The Aether** 1.5.10 — 밝은 천공 차원 + 공중 던전 (대비)
  - (더 원하면: Dimensions & Dungeons, Dungeons Enhanced)
- **설계:** 관문/차원은 FTB Quests 지역 정화로 **점진 해금** → 콘텐츠가 서서히 열림(1일차부터 안 몰림).
  서사: 균열이 여러 세계로 찢어졌고 어둠이 각각에 스며듦 → 그룹이 각 차원의 오염 핵심을 레이드.
- ❌ Blue Skies — 1.21.1 미출시.

### Layer 8 — 접착 / 공성 기믹
- ✅ KubeJS (미처리 관문 → 밤 공성 스크립트; 금고; 업그레이드)
- (FTB Teams/Chunks는 Layer 1과 공유)

### Layer 9 — 개성 / 정체성 (개인 집)  — 검증 완료
- ✅ **Macaw's Furniture** 3.4.1 (+ Macaw's roofs/doors/paths 시리즈); 🔲 Supplementaries, Chipped,
  Handcrafted (1.21.1에서 인기·유지보수 중 — 빌드 시 정확 버전 확인)
- 🔲 코스메틱 아머 (외형 ≠ 파워) — 개인 지갑 치장 소모처

## 조립 중 설계할 것
- 관문 차원 구현 방식 (Layer 7) — 대부분 확정, 세부 튜닝만.
- 공동 금고 = 팀 공유 계좌 (KubeJS).
- 공성 튜닝 노브 (Layer 8).
- 🎨 층별 최종 후보 확정 (유저 취향).
