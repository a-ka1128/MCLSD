# Last Stardust — 시스템 구조 (KubeJS → 모드 이관)

> 2026-07-25 시작. **데이터의 단일 소유자는 모드(`lsrelics`)** 로 옮기는 중이다.
> 이 문서는 "지금 무엇이 어디에 있는가"와 "왜 옮기는가"를 적는다. 이관이 끝나면 마지막 절만 남는다.

---

## 왜 옮기는가

KubeJS 는 붙이기 빨랐지만, 하루 동안 **문법 검사로는 안 잡히고 런타임에만 터지는 함정 네 가지**에
반복해서 걸렸다. 넷 다 컴파일러가 있으면 빌드에서 막혔을 것들이다.

| 함정 | 증상 | 실제 피해 |
|---|---|---|
| `runCommandSilent()` 가 `void` | `/clear <item> 0` 로 개수 세기가 항상 `undefined` | **5개 시스템**이 조용히 죽어 있었다 |
| Rhino 가 `try` 안의 `const` 를 상위 스코프로 흘림 | `redeclaration of var` | 호데고스 접속 처리·마을 창이 통째로 실패 |
| 서버 스크립트가 전역 스코프 공유 | 파일이 달라도 이름 충돌 | `day` 하나로 접속 인사가 죽음 |
| `NaN` 은 모든 비교가 `false` | 부족 검사를 공짜로 통과 | 정수 없이 유물 수령·관문 개봉 가능했음 |

추가로, **스크립트로는 진짜 아이템 슬롯(컨테이너)을 만들 수 없다.** 마을 보관함이 필요해진 시점에
이관이 불가피해졌다.

---

## 현재 위치

### 모드가 소유 (`moddev/lsrelics`)

| 영역 | 파일 |
|---|---|
| 저장 뿌리 | `data/LSData.java` — `SavedData`. 새 기능은 여기에 섹션을 건다 |
| 마을 정의 | `data/TownCatalog.java` — 4트랙 × 4레벨 (밸런스 다이얼) |
| 마을 상태 | `data/TownData.java` — 단계·**보관함**·기여도·플래그·금고 |
| 마을 판정 | `town/TownService.java` — 완성 가능 여부·집행. **판정은 여기 한 곳** |
| 마을 창 | `town/TownMenu.java` + `client/TownHubScreen`·`TownTrackScreen` |
| 유물 8종·스킬·각성 새김 | `item/`, `RelicSkills`, `LSCommands` |
| 가호 선택 화면 | `client/FateSelectScreen`, `FateCatalog` |

### 아직 스크립트 (`server/kubejs/server_scripts`)

`ls_siege`(923) · `ls_voice`(533) · `ls_rift`(323) · `ls_rescue`(280) · `ls_fate`(253) ·
`ls_casino`(244) · `ls_ascend`(242) · `ls_bossdiff`(190) · `ls_bounty`(187) · `ls_stats`(154) ·
`ls_relic`(143) · `ls_title`(119) · `ls_mobscale`(111) · `ls_beacon`(105) · `ls_daynight`(93) ·
`ls_util`(59) · `ls_keys`(53) · `ls_config`(49) · `ls_revive`(46) · `last_stardust_rules`(23)

### 은퇴

`ls_town.js.disabled` · `ls_income.js.disabled` · `ls_treasury.js.disabled`
— 모드로 이관 완료. 비교·복구용으로만 남겨둠.

---

## 다리: `LS` 바인딩 (이관 기간 한정)

`compat/LSKubeBridge.java` 가 전역 `LS` 를 등록한다. 아직 스크립트인 시스템이
**모드가 가진 하나의 금고**를 보게 하는 장치다.

```js
LS.treasury(server)                  // 잔액
LS.addTreasury(server, 50)           // 적립
LS.spendTreasury(server, 200)        // 지출 — 부족하면 false, 아무것도 차감 안 함
LS.townLevel(server, 'ramparts')     // 마을 단계
LS.addContribution(server, name, n)  // 기여도
LS.townFlag(server, 'market')        // 구역 해금 플래그
LS.syncTown(player)                  // 열려 있는 마을 화면 갱신
```

**이게 없으면 금고가 둘로 갈린다** — 모드의 금고와 스크립트의 `persistentData['ls_treasury']` 가
서로 다른 값이 되어 공성 보상이 영영 도착하지 않는다.

현재 경유 중: `ls_siege` · `ls_rift` · `ls_beacon` · `ls_bounty` · `ls_rescue` · `ls_stats`

> **수명:** 남은 시스템을 전부 옮기면 이 파일과 `build.gradle` 의 KubeJS `compileOnly` 를 함께 지운다.

---

## 다음 이관 순서 (공유 키 기준)

키 소유권을 훑어 정한 순서다. **공유가 많은 것부터** 옮겨야 이음새가 최소로 남는다.

| 순서 | 대상 | 이유 |
|---|---|---|
| 1 | **성역 좌표** (`ls_sanc_*`) | `siege`·`rift`·`rescue`·`stats` **4개가 공유**. 가장 넓게 퍼진 상태 |
| 2 | **진행도** (`rf_progress`) | `rift`·`ascend`·`mobscale`·`voice` 4개가 읽음 |
| 3 | **가호·유물·각성** (`fate_*`·`relic_*`·`star_*`) | RPG 성장의 뿌리. 모드가 이미 아이템 NBT 를 다루므로 자연스럽다 |
| 4 | **공성** (`ls_siege` 923줄) | 최대 덩어리이자 간판 시스템. 위 셋이 끝나야 깔끔하게 옮겨진다 |
| 5 | 나머지 (`bounty`·`casino`·`rescue`·`beacon`·`bossdiff`·`title`) | 서로 거의 독립 |

`ls_voice`(533)는 **가장 마지막**이 낫다 — 다른 시스템의 상태 전이를 감시하는 구조라,
감시 대상이 먼저 자리를 잡아야 한다.

---

## 원칙 (앞으로 기능을 추가할 때)

1. **새 기능은 처음부터 모드로.** KubeJS 는 이관 대상이지 확장 대상이 아니다.
2. **데이터는 `LSData` 의 섹션 하나로.** 저장·로드가 자동으로 따라온다.
3. **판정은 한 곳에서.** 화면·명령·패킷이 모두 같은 서비스 클래스를 부른다.
   판정이 두 곳에 생기면 "버튼은 켜졌는데 눌러도 안 되는" 상태가 반드시 나온다.
4. **서버→클라는 코덱으로.** JSON 문자열은 파싱 실패가 조용한 빈 화면이 된다.
5. **클라가 보낸 값은 카탈로그와 대조.** 형태 검사만으로는 부족하다.
6. **`catch` 는 반드시 뭔가를 남긴다.** `catch (e) { }` 는 "이 실패는 없던 일로 한다"는 선언이다.
   `lsWarn('<위치>', e)` 를 쓴다 (`ls_util.js`) — 자리마다 3번까지만 찍고 접어서, 틱 루프 안의
   예외가 로그를 뒤덮는 일은 막는다.

---

## 함정 검출 (실제로 쓰는 명령)

위의 함정 표는 2026-07-25 오전에 이미 적혀 있었다. **그런데 같은 날 오후에 다섯 개가 더 나왔다** —
표를 읽는 것으로는 안 걸러지고, 놓친 *호출 지점*이 남아 있었기 때문이다. 그래서 설명이 아니라
검출 방법을 적는다. `server/kubejs/server_scripts` 에서 실행한다.

```bash
# ① 명령 반환값으로 개수를 세려는 곳 — runCommandSilent 는 void 다
grep -anE "= *[a-zA-Z]*Cmd\([^)]*clear|(const|let|var) +\w+ *= *(server\.)?runCommandSilent\(" *.js

# ② 기록 없이 예외를 삼키는 곳 (주석 줄은 걸러낸다)
grep -an "catch" *.js | grep -v "lsWarn" | grep -v "console.log" | grep -v ": *//"

# ③ 모드가 소유한 상태를 스크립트가 직접 쓰는 곳 — 반드시 LS 브릿지를 타야 한다
grep -anE "put(Int|Boolean|String|Double)\('ls_(treasury|town|sanc|lvl|level)" *.js

```

**④ `try` 안의 `const`/`let`** — grep 으로는 블록 안팎이 안 갈려서 중괄호 깊이를 세야 한다.
저장소에 도구가 있다 (프로젝트 루트에서 실행):

```bash
python tools/scan_try_decls.py            # 목록만 (정상 = total: 0)
python tools/scan_try_decls.py --write    # var 로 일괄 변환
```

**기준선 (2026-07-25 기준).** 새로 생긴 것만 눈에 띄게 하려면 지금 무엇이 나오는 게 정상인지
적어둬야 한다. 안 그러면 매번 같은 줄이 나와서 결국 출력을 안 보게 된다.

| | 정상 출력 |
|---|---|
| ① | 없음 |
| ② | 없음. **단** 함정을 설명하는 주석 안의 `catch (e) { }` 두 줄(`ls_mobscale`·`ls_util`)이 잡힐 수 있다 — 위 파이프의 `grep -v ": *//"` 가 그걸 걸러낸다 |
| ③ | 없음 (성역 좌표 이관 완료 후) |
| ④ | `total: 0` |

넷 중 하나라도 뭐가 잡히면 그게 새 함정이다.

**Rhino 블록 스코프는 정적으로 못 찾는다.** 틱 핸들러 안의 `const`/`let` 이 전부 문제인 것도
아니고(같은 함수의 다른 블록은 멀쩡히 돈다), 조건이 맞아야 실행되는 코드는 조건이 오기 전까지
조용하다 — `ls_voice.js` 는 접속자가, `ls_towneffect.js` 는 성역 지정이 있어야 돌았다.

> **원인은 선언 키워드다 — 이름이 아니다.** (2026-07-25 확정)
>
> `try` 블록 안의 `const`/`let` 이 런타임에 `redeclaration of var X` 로 터진다.
> **이름을 유일하게 바꿔도 낫지 않는다** — 전역에 하나뿐인 `lsSy` 로도 똑같이 터졌다.
> `var` 는 재선언이 합법이라 안전하다. (이건 `docs/TODO.md` 함정 1번에 처음부터 적혀 있었다)
>
> ```js
> try { const x = f() ; ... } catch (e) { }   // ✗ 매번 터진다
> try { var x = f() ; ... } catch (e) { }     // ○
> ```
>
> **그래서 정적 예측기가 있다** — `try` 안의 `const`/`let` 을 찾으면 된다. 아래 ④ 가 그것이다.
>
> ── 여기까지 오는 데 헛길을 두 번 걸었다. 같은 함정에 다시 빠지지 않도록 남긴다:
> · *가설 1: 파일 간 이름 충돌이다.* → 틀렸다. 유일한 이름으로도 터진다.
> · *가설 2: 로그에 없으니 그 자리는 정상이다.* → **이게 더 나쁜 실수였다.** `src`·`y` 를
>   스캔이 정확히 지목했는데 "로그에 없으니 멀쩡하다"고 기각했다. 실제로는 **아직 실행된 적이
>   없었을 뿐**이고(`src` 는 몹이 죽어야, `y` 는 공성이 시작돼야 돈다), 공성을 한 번 돌리자
>   즉시 셋 다 터졌다. **조용한 코드를 정상으로 읽지 마라 — 실행된 적 없는 코드일 수 있다.**

**판별기는 로그뿐이다:**

```bash
grep -aE "LS-WARN|InternalError|redeclaration" logs/latest.log
```

기능을 하나 건드렸으면, 그 기능이 실제로 도는 조건을 만들고 이 줄이 비어 있는지 본다.

### 2026-07-25 오후에 이 방법으로 찾은 것

| 위치 | 증상 |
|---|---|
| `ls_towneffect.js` 성역 동기화 | 2초마다 예외 — 성공 0건. 모드가 성역 좌표를 **한 번도 못 받았다** (귀환석 먹통) |
| `ls_ascend.js` `asEssence()` | `clear 0` 관용구가 남아 있었다 — `undefined < cost` 가 false 라 **각성이 정수 없이 공짜** |
| `ls_casino.js` `csCount()` | `server.getPlayer(name)` 은 UUID 전용 — 홀짝이 첫 줄에서 죽어 **한 번도 작동 안 함** |
| `TownEffects.onSmelt` | 스택 전체에 추첨 1회 — 한 개씩 꺼내면 20배 이득 (모드 쪽) |
| `LSCommands.setLevel` | 지급 훅 누락 — 관리자로 올린 레벨이 실제 업그레이드와 다른 상태 (모드 쪽) |

앞의 셋은 스크립트, 뒤의 둘은 모드다. **이관이 끝나도 "조용한 실패" 자체는 사라지지 않는다** —
컴파일러가 잡아주는 종류가 줄어들 뿐이다.
