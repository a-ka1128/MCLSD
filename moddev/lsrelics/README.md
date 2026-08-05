# lsrelics — Last Stardust 전용 모드

NeoForge 1.21.1 / Java 21. 서버 정체성의 절반이 여기 있다. 나머지 절반은
`server/kubejs/server_scripts` 이고, 그쪽에서 이쪽으로 옮기는 중이다 — 순서와 이유는
`docs/ARCHITECTURE.md`.

```bash
./gradlew build
# → build/libs/lsrelics-1.0.0.jar
# 배포는 rebuild_install.bat (서버 mods/ + Prism 인스턴스 mods/ 양쪽에 복사)
```

> ⚠️ **클라이언트가 켜져 있을 때 jar 를 덮어쓰지 말 것.** 열려 있는 파일 핸들이 상해서
> 접속 시 `Failed to load registries` 가 난다. jar 도 JSON 도 멀쩡한데 원인이 안 보인다.
> Prism 을 껐다 켜면 낫는다.

---

## 무엇이 들어 있나

### 유물 8종 (`item/`)

| 클래스 | 이름 | 계열 |
|---|---|---|
| `BulwarkBlade` | 이지스 | 근접 · 탱커 (주손 한정 방어력 +6 / 방어강도 +4) |
| `RiftAxe` | 타이탄브레이커 | 근접 · 순수 딜러 |
| `GaeBolg` | 게볼그 | 근접 · 창 (사거리 +1.5, Better Combat) |
| `SoulDagger` | 스틱스 | 근접 · 암살 (백어택·기습·크리가 곱해진다) |
| `StarBow` | 시리우스 | 원거리 |
| `SolarMusket` | 솔라리스 | 원거리 (탄창 6발 · 스코프) |
| `StargazerStaff` | 셀레스티아 | 마법 |
| `PanaceaStaff` | 파나케이아 | 마법 · 힐러 |

스킬 본체는 전부 `item/RelicSkills.java` 에 모여 있고, 유물 클래스는 키 입력을 거기로 넘기기만 한다.
조작은 좌클릭 = 평타 · `R` 기본 · `V` 이동 · `C` 추가 · `X` 궁극 (`client/RelicKeybinds`).

### 피해의 단일 창구 — `LsDamage`

유물 피해는 **전부 여기를 지난다.** 바닐라 20틱 무적 프레임을 무시하기 위해서다.

솔로에선 티가 안 나지만 파티에서는 표적이 거의 항상 무적이라, 그 창에 들어간 공격이
`hurt()` 가 `false` 를 돌려주며 **로그도 없이 사라진다.** 이펙트는 그대로 나가서
"분명 맞았는데 체력이 안 준다"가 된다.

- 단발 스킬 → `hit()`
- 다단히트·장판 → `hitLimited(key, ...)` — **key 는 스킬마다 달라야 한다.** 공유하면 서로의
  도장을 덮어써 상대의 피해를 지운다
- **몹의 공격은 이걸 안 쓴다.** 전역으로 풀면 수성전에서 플레이어가 순식간에 녹는다

### 위력 배율

```java
RelicSkills.GLOBAL_POWER   // 8종 통째로 올리고 내리는 손잡이
RelicSkills.ASCENSION      // 각성 1~5성 (×1.0 ~ ×3.0)
RelicSkills.power(stack)   // = 둘의 곱. 피해를 만드는 곳은 전부 이걸 쓴다
```

적용 지점은 넷뿐이다 — `dmg()` · `dmgTick()` · 근접 공격력 속성(`RelicEventHandlers.onRelicAttributes`) ·
화살 기본피해(`StarBow`).

> **GLOBAL_POWER 는 DPS 배수가 아니다.** 플레이어 피해에는 유물을 안 타는 덧셈 항
> (성소 축복 Strength I = +3.0)이 섞여 있어 실효 배수가 갈린다 — 근접 ×1.55~1.73 / 원거리 ×1.80.
> 보스 체력을 다시 잡을 땐 `python tools/calc_power_ratio.py` 를 쓴다.

### 보스 기믹 — 어휘 5색

`Telegraph.java` 가 기호의 뜻을 소유한다. **같은 색은 언제나 같은 뜻이어야 한다** —
보스마다 자기 연출을 쓰면 "왜 죽었는지 모르겠다"가 되고, 공략 생태계가 없는 자체 팩에서 그건 이탈 요인이다.

| 색 | 뜻 | 그리는 법 | 쓰는 곳 |
|---|---|---|---|
| 🔴 DANGER | 피해라 | 바닥 테두리 · 예고 1.5초 | T1 · T3 2P · T4 2P |
| 🟡 STACK | 뭉쳐라 | 〃 | T2 · T4 · 최종 |
| 🟣 SPREAD | 흩어져라 | 〃 | T3 · T4 |
| 🟢 OPENING | 지금 쳐라 | 바닥 부채꼴(`band`) · 지속 | 강철거인 취약 창 |
| 🔵 HOLD | 멈춰라 | 몸을 감싸는 기둥(`aura`) · 지속 | 이그니스 반격 · 리치 삼킴 |

앞의 셋은 **어디에 설 것인가**, 뒤의 둘은 **언제 때릴 것인가**다. 그래서 뒤의 둘만 바닥이 아닌 곳에 그린다.

`BossFightTracker` 가 추적·교전 판정·페이즈·시험 소환을 맡고, 각 `*Gimmick` 은 "무엇이 일어나는가"만 쓴다.
**이벤트 구독은 각 기믹이 한다** — 뼈대가 다 받는 구조면 그 기믹 클래스를 아무도 로드하지 않아 등록 자체가 안 일어난다.

> 🟢🔵 는 대부분 **모드가 이미 가진 규칙을 보이게 만든 것**이다(강철거인 `vulnerable`, 이그니스 반격 구간).
> Cataclysm·Mowzie's 는 컴파일 의존이 아니라 리플렉션으로 읽고, 실패하면 표시만 끄고 기믹은 계속 돈다.
> 예외는 최종 리치 — 그쪽은 `hurt()` 를 안 건드려서 무적이 없기에 우리가 만들었다.

### 마을 (`data/`, `town/`)

`LSData`(SavedData)가 저장 뿌리다. 새 기능은 여기에 섹션을 건다.
`TownCatalog`(정의) / `TownData`(상태·보관함·기여도·금고) / `TownService`(판정) / `TownMenu`(창).

**판정은 `TownService` 한 곳.** 두 곳에 생기면 "버튼은 켜졌는데 눌러도 안 되는" 상태가 반드시 나온다.

### 그 밖

`ReviveRules`(부활 무적 창 + 별빛 쇠약) · `SanctuaryManager` · `ThreatManager` · `DummyManager`(DPS 실측) ·
`compat/LSKubeBridge`(전역 `LS` 바인딩 — 이관 기간 한정).

---

## 관리자 명령

```
/dummy      spawn · start [초] · stop · clear · armor <값> [견고함] · calc <피해>
/lsgimmick  summon · now · window · test <danger|stack|spread> · clear
            ignis · gauntlet · monstrosity · lich  <summon|now>
/lsrelic    star <1-5>
/town       info · treasury · level
/fateui     [가호]
```

`/dummy` 는 밸런스의 근거다. **비교하려면 반드시 시간을 고정**하고(`start <초>`), 성역 축복
안팎을 통일한다 — 축복은 근접에 +3.0, 원거리에 +25% 라 섞이면 같은 유물도 ×1.25 넘게 벌어진다.

---

## 손대기 전에 알아둘 것

1. **총 DPS 하나만 보고 배분을 추측하지 마라.** 마총에서 세 번 연속 빗나갔다 — 총량은 목표에
   붙었는데 평타·연사·궁의 몫이 계산과 달랐고, 합계로는 어느 쪽이 틀렸는지 알 방법이 없었다.
   그래서 `LsDamage` 에 이름표가 있고 `/dummy` 가 스킬별로 쪼개 준다.
2. **배수는 계산하지 말고 실측한다.** 발사 속도를 두 번 계산해 두 번 틀렸다.
3. **주석의 숫자가 코드와 어긋나면 그게 다음 실수의 원인이다.** 밸런스를 만질 때 주석을
   근거로 삼게 되기 때문이다.
4. `ItemStack` 의 custom_data 는 읽을 때 복사본이다 — `tag(stack)` 을 고쳐도 안 박힌다.
