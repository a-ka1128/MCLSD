# Last Stardust — 모드 리스트 (NeoForge 1.21.1)

> **설치 방법:** **Prism Launcher**의 내장 모드 브라우저를 쓰세요 (Modrinth·CurseForge **둘 다에서** 설치되고
> **의존성 자동 해결**됨). 아래 모드들을 NeoForge 1.21.1 인스턴스에 추가하면 됩니다 — 라이브러리 의존 모드는
> 대부분 손으로 안 넣어도 Prism이 알아서 당겨옵니다. 진행 순서는 `SETUP.md` 참고.
>
> 표기: 🖥️ = 클라 전용(서버엔 넣지 말 것) · ⚙️ = 의존 모드 필요 · ⚠️ = 비고 꼭 확인

## Layer 0 — 토대 (성능 + 편의)
| 모드 | 링크 | 비고 |
|---|---|---|
| Sodium 🖥️ | https://modrinth.com/mod/sodium | 클라 FPS (공식 NeoForge). Embeddium 대체 |
| Iris Shaders 🖥️ | https://modrinth.com/mod/iris | ⚙️ **Sodium** 필요. 셰이더 로더(Oculus 대체) — Rethinking Voxels 실행에 필수 |
| FerriteCore | https://modrinth.com/mod/ferrite-core | 메모리 절감 |
| ModernFix | https://modrinth.com/mod/modernfix | 로딩·성능 |
| EMI 🖥️ | https://modrinth.com/mod/emi | 레시피·아이템 뷰어 |
| Jade 🖥️ | https://modrinth.com/mod/jade | 블록·몹 툴팁 |
| Xaero's Minimap 🖥️ | https://modrinth.com/mod/xaeros-minimap | 미니맵 |
| Xaero's World Map 🖥️ | https://modrinth.com/mod/xaeros-world-map | 전체 지도 |
| Corpse | https://modrinth.com/mod/corpse | 죽으면 시체로 회수(죽음 완화, P6) |
| FancyMenu 🖥️ | https://modrinth.com/mod/fancymenu | ⚙️ **Konkrete** 필요. 타이틀 화면 커스텀(배경·로고·버튼 배치) — 인게임 에디터 제공 |
| Konkrete 🖥️ | https://modrinth.com/mod/konkrete | FancyMenu 의존 라이브러리 |

## 셰이더 (🖥️ 클라 전용 — "어둠이 삼킨 세계" 무드)
> ⚙️ **Iris + Sodium** 위에서 돈다. 서버 mods 폴더엔 절대 넣지 말 것.
| 항목 | 링크 | 비고 |
|---|---|---|
| **Rethinking Voxels** 🖥️ | https://modrinth.com/shader/rethinking-voxels | **서버 표준 셰이더.** 컬러 복셀 조명 — 어둠 속 광원·유물 스킬 이펙트가 실제로 주변을 물들임. 무거움(호스트·고사양용) |
| Shrimple Shaders 🖥️ | https://modrinth.com/shader/shrimple | 저사양 친구용 대체. 초경량 + 컬러 조명 유지 |
| ~~Distant Horizons~~ 🖥️ | https://modrinth.com/mod/distant-horizons | ❌ **2026-08-06 제거.** 셰이더 궁합이 안 맞고 오류가 잦았다 — 아래 |

> **Distant Horizons 를 뺀 이유 (2026-08-06)**
>
> 원경 LOD 를 그리는 모드인데, **Iris 셰이더와 궁합이 안 맞아 화면이 깨지고 오류가 잦았다.**
> DH 는 자체 렌더 경로를 쓰기 때문에 셰이더팩이 그걸 따로 지원해야 하고, Rethinking Voxels 처럼
> 무거운 복셀 조명 셰이더와는 특히 부딪힌다. 「점박이 나면 `Transparency=Complete`」 라고
> 적어 뒀던 게 그 증상인데, 그걸로도 안 잡혔다.
>
> **원경은 이 서버의 핵심이 아니다.** 「어둠이 삼킨 세계」 무드는 가까운 곳의 빛과 그림자로
> 만들고, 그건 Rethinking Voxels 가 한다. 원경을 얻으려고 그쪽을 흔드는 건 순서가 뒤바뀐 것이다.
>
> 지운 것: jar(30MB) · `config/DistantHorizons.toml` · `Distant_Horizons_server_data/`(LOD 캐시 83MB).
> 다시 넣고 싶으면 셰이더를 **먼저** 정하고, 그 셰이더가 DH 를 지원하는지 확인한 뒤에 넣을 것.

## Layer 1 — 거점
| 모드 | 링크 | 비고 |
|---|---|---|
| Waystones | https://modrinth.com/mod/waystones | ⚙️ **Balm** 필요 |
| ~~FTB Teams~~ | — | ❌ **2026-08-06 제거** — 아래 |
| ~~FTB Chunks~~ | — | ❌ **2026-08-06 제거** — 아래 |

## Layer 2 — 경제  ⚠️ 경제 모드는 하나만 선택
| 모드 | 링크 | 비고 |
|---|---|---|
| **KubeJS + lsrelics** | — | ✅ **채택.** 공동 금고(Ducat)는 `data/TownData` 가 소유. 도박장 칩은 에메랄드 실물 |
| ~~SDM Economy / Shop / Core / UI~~ | — | ❌ **2026-08-06 제거** (4개) — 아래 |
| ~~Simple Economy · EconomyMod~~ | — | 검토만 하고 안 깔았다 |

> **SDM 넷을 뺀 이유 (2026-08-06)**
>
> FTB 를 지우자 **SDMShop 이 부팅을 깨뜨렸다** — `mods.toml` 에 선언하지 않고
> `dev.ftb.mods.ftblibrary.snbt.config.SNBTConfig` 를 직접 쓰고 있었다.
> **선언된 의존만 보고 「FTB 는 서로만 의존한다」고 판단한 것이 틀렸다.** 선언 안 한 의존은
> jar 를 열어봐도 안 보이고, 부팅을 시켜봐야 나온다.
>
> 그래서 둘 중 하나였다 — FTB Library 만 되살리거나, SDM 을 같이 빼거나.
> 열어보니 **상점이 완전히 비어 있었다**(상품 0개·탭 0개, 파일 21바이트). FTB 퀘스트가
> 0개였던 것과 같은 상태다. 게다가 이 표의 원칙이 「경제 모드는 하나만」이고,
> 실제로 쓰이는 건 우리 Ducat 금고다 — SDM 은 **두 번째 화폐가 될 뻔한 자리**였다.
>
> 백업은 남겼다(jar 4개). 상점을 정말 쓸 일이 생기면 그때는 FTB Library 도 같이 필요하다.

**지금 이 서버의 돈은 둘뿐이다:** 공동 금고 **Ducat**(공성·현상금·구출·봉화가 쓰는 것)과
도박장의 **에메랄드 실물**. 둘 다 우리 코드가 소유하므로 모드를 지워도 안 사라진다.

### 재화 아이템 둘 (2026-08-08)

| | 어디서 | 무엇에 |
|---|---|---|
| **별의 파편** `kubejs:rift_essence` | 공성 격퇴 · 보스 · 정예 현상금 | 각성 · 관문 개봉 · 마을 발전 · 균열 열쇠 · 축복 **종류** 리롤 |
| **별먼지** `kubejs:stardust` | **구조물 상자** | 일반 균열 열쇠 · 축복 **수치** 리롤 |

두 축이 서로 다른 플레이를 요구하게 갈랐다 — 파편은 **전투**, 별먼지는 **탐험**.
수치 리롤을 광물로 하면 Create 자동화로 후반에 무한이 되어 다들 최댓값으로 수렴하는데,
구조물은 자동화가 안 되므로 비용이 «자원»이 아니라 **«이동 시간»**이 된다.

> **구조물 상자 보정은 Global Loot Modifier 다** (`moddev/lsrelics` · `data/lsrelics/loot_modifiers/chest_loot.json`)
>
> 셋을 한 수정자가 한다 — **별먼지 지급 · 잡템을 자원으로 치환 · 장비 추가.** 등급 판정을
> 공유하므로 따로 두면 키워드 표가 두 벌이 된다.
>
> 이 서버에는 상자 전리품표가 **566개**다(WDA 141 · Nova 119 · Repurposed 146 …).
> **KubeJS 2101 에는 전리품 «수정» 이벤트가 없다** — 구버전에 있던 게 1.21 에서 빠졌다.
> 스크립트로 하려면 표를 통째로 덮어써야 하는데 그러면 원래 내용이 다 날아간다.
> GLM 만이 기존 표를 안 건드리고 결과에 얹는다.
>
> 등급은 **표 이름 규칙**으로 자동 판정한다(`StardustLootModifier`). 손으로 566개를 나눌 필요가 없다:
>
> | 등급 | 이름에 들어가는 말 | 별먼지 | 잡템 치환 | 장비 추가 | 표 개수 |
> |---|---|---|---|---|---|
> | 대형 | `treasure` `tresure` `vault` `hoard` `boss` | 100% · 2~4 | 60% | 40% | 87 |
> | 중형 | 그 외 | 50% · 1~2 | 40% | 15% | 364 |
> | 소형 | `barrel` `supply` `storage` `house` `grave` `kitchen` `garden` `wool` | 25% · 1 | 20% | — | 115 |
>
> **잡템은 지우지 않고 «치환»한다.** 지우면 상자가 휑해져서 「보상이 줄었다」로 느껴진다.
> 자리에서 바꾸므로 채움은 그대로고 질만 오른다.
> 잡템 = 썩은 고기·실·막대기·씨앗·종이·독감자·점토구슬·부싯돌·뼈.
> 치환 결과 = 철·구리·석탄·금·레드스톤·청금석·자수정·가죽·석영·(드물게) 다이아·네더라이트 조각.
>
> ⚠️ **치환 결과에 에메랄드는 절대 넣지 않는다.** 도박장 칩이 에메랄드 실물이라
> (`ls_casino.js`) 상자에서 쏟아지면 판돈이 무의미해진다 — 균열 보상에서 뺀 것과 같은 이유다.
>
> `tresure` 는 오타가 아니라 **Nova Structures 의 실제 오타 테이블 4개**다. 빼면 그 넷이 조용히 중형이 된다.
>
> **상자만 붙는다**(`chests/` 로 시작하는 표). 몹·블록·낚시까지 열면 별먼지가 자동화 가능해져서
> 「탐험해야 나온다」가 무너진다.
>
> 확률·개수는 JSON 이라 **재빌드 없이 조절**된다. 등급 «기준»만 Java 다 — 그건 규칙이라.
>
> **실측 (상자에 `/loot insert`)**
>
> 별먼지 30회: 대형 30/30 · 중형 16/30 · 소형 6/30 — 기대값과 일치.
>
> 잡템 치환은 **보정을 끄고 한 번 더 재서** 전후를 비교했다(끄는 건 `server/kubejs/data/` 에
> 같은 경로로 확률 0 짜리를 잠깐 얹으면 된다 — KubeJS 데이터가 모드 데이터를 덮는다):
>
> | `simple_dungeon` 25회 | 총 슬롯 | 잡템 | 자원 | 장비 | 별먼지 |
> |---|---|---|---|---|---|
> | 보정 끔 | 230 | **43** | 25 | 0 | 0 |
> | 보정 켬 | 242 | **32** | 35 | 4 | 12 |
>
> 잡템 **-26%** · 자원 **+40%** · 총 슬롯은 거의 그대로(치환이라 자리가 안 준다).
> 장비 4/25 = 16% ≈ 설정 15%.

## Layer 3 — 목표 / 진행
| 모드 | 링크 | 비고 |
|---|---|---|
| ~~FTB Quests~~ | — | ❌ **2026-08-06 제거** — 아래 |
| ~~FTB XMod Compat~~ | — | ❌ **2026-08-06 제거** (FTB 전용 연결 계층) |
| **바닐라 도전과제 27종** | `data/lsrelics/advancement/` | ✅ **이게 목표·진행을 맡는다.** 표는 `tools/gen_advancements.py` |

> **FTB 다섯을 뺀 이유 (2026-08-06)**
>
> `ftb-quests` · `ftb-teams` · `ftb-chunks` · `ftb-library` · `ftb-xmod-compat` — 다섯이 서로만
> 의존해서 통째로 빠졌다(**바깥에서 이걸 필요로 하는 모드는 하나도 없었다**).
>
> ⚠️ **처음에 「퀘스트 0개」라고 적었는데 틀렸다 (같은 날 정정).**
> `world/ftbquests/`(진행 상황, 1KB, 비어 있음)만 보고 판단했는데, **퀘스트 정의는
> `config/ftbquests/quests/` 에 있었다.** 거기엔 실제로 **5챕터 25퀘스트 + 한국어 설명 70줄**이
> 들어 있었다:
>
> | 챕터 | |
> |---|---|
> | 성역의 불씨 | 퀘스트 3 |
> | 변경 개척 | 6 |
> | 지역 정화 | 6 |
> | 관문 원정 | 6 |
> | 최후의 관문 | 4 |
>
> **그 파일들은 지우지 않았다** — `server/config/ftbquests/` 에 그대로 있고 git 에도 있다.
> 모드만 빠졌으므로 나중에 되돌리면 그대로 살아난다.
>
> 그래도 뺀 이유는 남는다: **아무도 그 책을 연 적이 없다**(`world/ftbquests` 진행 0). 그리고
> 관문·성역·개척은 그 사이에 전부 `ls_rift`·`TownData`·`ls_beacon` 으로 **실제로 구현됐다** —
> 퀘스트 책은 그 구현을 안내하는 두 번째 설명서가 됐지 진행을 잡는 장치가 아니게 됐다.
>
> 대신 **바닐라 도전과제 27종**이 그 자리를 맡는다(2026-08-06). 차이가 큰 쪽이 오히려 낫다:
> · FTB Quests 데이터는 `world/ftbquests/` 라 **월드 리셋에 날아간다.** 도전과제는 jar 안이라 남는다
> · 별도 책 UI 를 안 열어도 `L` 키로 보인다
> · 보상·선행조건이 없다 — 그건 이 서버에서 이미 유물·각성·관문이 하는 일이라 겹쳤다
>
> 서사(수기 12편·오프닝)는 **호데고스**(`ls_voice.js`, 대사 10종 + 3채널 연출)가 이미 맡고 있다.
> 그쪽이 이 서버의 화자다 — 퀘스트 책보다 그편이 「어둠이 삼킨 세계」에 맞는다.
>
> **되돌리려면 jar 다섯만 다시 받으면 된다** — `config/ftbquests/`(챕터 5·퀘스트 25)가
> 그대로 있어서 책이 그대로 살아난다. 그 폴더는 **지우지 말 것.**

## Layer 4 — 모험 / 월드
| 모드 | 링크 | 비고 |
|---|---|---|
| Terralith | https://modrinth.com/mod/terralith | ⚙️ **Lithostitched** 필요 (또는 데이터팩 형태 사용) |
| When Dungeons Arise | https://modrinth.com/mod/when-dungeons-arise | 로그라이크 던전·구조물 |
| YUNG's Better Dungeons | https://www.curseforge.com/minecraft/mc-mods/yungs-better-dungeons-neoforge | ⚙️ **YUNG's API** |
| Structory | https://modrinth.com/mod/structory | 전리품 없는 **분위기용 폐허.** 세상이 한때 사람이 살던 곳처럼 보이게 |
| Structory: Towers **v1.0.16** | https://modrinth.com/mod/structory-towers | 탑·전초 22종. ⚠️ **1.0.17 은 쓰지 말 것** — 아래 |

> **⚠️ Structory: Towers 는 `1.0.16` 을 쓴다 — `1.0.17` 은 부팅을 깨뜨린다 (2026-08-06)**
>
> 1.0.17 을 켜면:
>
> ```
> Missing ModLoader in file (Structory_Towers_26.2_v1.0.17.jar) → 부팅 실패
> ```
>
> **파일이 손상된 게 아니다.** 우리 사본의 sha1 이 Modrinth 공식값과 정확히 같았다
> (`fa40253068…`) — 다시 받아도 같은 파일이 온다. **배포처의 패키징 실수다.**
>
> 원인은 **한 jar 안에 설정 파일이 둘**이라는 것:
>
> | | `META-INF/mods.toml` (옛 형식) | `META-INF/neoforge.mods.toml` (새 형식) |
> |---|---|---|
> | 1.0.17 | `modLoader="lowcodefml"` ✅ | **빠짐** ❌ |
> | 1.0.16 | ✅ | ✅ |
>
> NeoForge 21.1 은 **새 형식을 우선 읽고 옛 것을 안 본다.** 그래서 옛 파일에 값이 멀쩡히
> 있어도 소용이 없다.
>
> `1.0.16` (sha1 `ed22fe659cb…`) 은 두 파일 모두 정상이고, 1.21.1 에서 잘 돈다 —
> `/place`·`/locate` 로 확인했다. 1.0.17 은 지웠다.
>
> **다음에 올릴 때는 `unzip -p <jar> META-INF/neoforge.mods.toml | head -2` 로 먼저 본다.**
> 이 한 줄이면 켜보기 전에 걸러진다.

## Layer 5 — 전투 / PvE / RPG
| 모드 | 링크 | 비고 |
|---|---|---|
| L_Ender's Cataclysm | https://modrinth.com/mod/l_enders-cataclysm | ⚙️ **GeckoLib**; 레이드 보스 |
| Gateways to Eternity | https://www.curseforge.com/minecraft/mc-mods/gateways-to-eternity | ⚙️ **Placebo**; 관문 웨이브·보스 레이드 |
| Alex's Mobs (1.21.1 포트) | https://modrinth.com/mod/alexs-mobs(1.21.1) | ⚠️ Alex's Mobs 비공식 커뮤니티 포트 |
| Mowzie's Mobs | https://www.curseforge.com/minecraft/mc-mods/mowzies-mobs | 🔲 선택(미니보스) — 빌드 시 1.21.1 확인 |
| Better Combat | https://modrinth.com/mod/better-combat | ⚙️ **playerAnimator + Cloth Config**; 타격감 |
| Apotheosis | https://www.curseforge.com/minecraft/mc-mods/apotheosis | ⚙️ **Placebo + Apothic Attributes**; 장비 접사·티어 |
| Puffish Skills | https://www.curseforge.com/minecraft/mc-mods/puffish-skills | ⚙️ + **Puffish Attributes** + 스킬트리 팩(Default Skill Trees / RPG Series) |
| Krip Turrets **v2.4.0** | https://www.curseforge.com/minecraft/mc-mods/krip-turret | 설치형 자동 포탑 8종. **클라에도 필요**(side=BOTH). ⚠️ 커스텀 시스템과 연동 안 됨 — 아래 |

> **⚠️ Krip Turrets 는 «깔려만 있다» (2026-08-08 등재)**
>
> **이 줄이 여태 빠져 있었다.** 서버에는 jar 가 있는데 이 문서에 없었고, `SETUP.md` 2번은
> 「`MODS.md` 의 모드를 전부 추가」를 클라 구성 절차로 못박고 있다 — 즉 **절차대로 설치한
> 새 참가자는 서버에 못 들어왔다.** 코드로는 못 고치는 종류의 구멍이라 여기 적는다.
>
> **커스텀 시스템과 연결돼 있지 않다.** 공성 웨이브(`buildWave`)에 포탑이 없고, 공성 몹도
> 포탑을 표적으로 «지정»받지 않는다(붙는 건 바닐라 골렘 상속 덕분이지 우리 코드가 아니다).
> 연동(B안)은 2026-08-08 에 **안 하기로 했다** — 아래 실측 때문이다.
>
> | 항목 | 실측값 (티어 0 · 무보정) |
> |---|---|
> | 산탄 `turret_4` | **292 DPS** · 발당 3,600~4,500 |
> | 저격 `turret_1` | **146 DPS** · 발당 3,600~4,500 |
> | 기본 `turret_3` | 3.3 DPS |
> | 체력 | 8종 전부 **10** (화살계 피해는 완전 무효) |
>
> 발당 4천은 관통이 없어서 전부 버려진다 — 실효 화력은 DPS 가 아니라 **「N초에 1킬 확정」**이다.
> 참고로 방벽 Lv3 자동 방어가 28 DPS 인데 그건 누적 3,350 Ducat + 별의 파편 6 을 태워야 나온다.
> 포탑 재료는 구리·철나깃·레드스톤·유리판·조약돌이다. **우라늄은 포탑과 무관**하다(다른 레시피 2개뿐).
>
> **다만 성역을 못 지킨다.** 8칸·18칸에서는 쏘는데 **24칸에서는 한 발도 안 쏜다**
> (`follow_range` 를 36 으로 올려도 그대로다). 공성 스폰링은 32, 성벽 밀어내기는 26 이라
> **성역 안에 세운 포탑은 공성 몹을 아예 볼 수 없다.** 쓰려면 성벽 선(24) 위에 세워야 하고,
> 그러면 HP 10 짜리가 몹 한가운데 서게 된다.
>
> **알아둘 부작용 둘**
> - 엔티티 8종이 `MobCategory.MONSTER` 로 등록돼 있다 → `ls_mobscale` 이 «몬스터»로 보고
>   플레이어 소유 포탑을 같이 강화하고 있었다(티어4: HP 10→22, 공격력 45→65).
>   `MS_NEVER_SCALE` 로 제외했다(`ls_mobscale.js`).
> - **바닐라 `#minecraft:illager` 태그를 오염시킨다** — `turret_1`~`turret_8` + `plane_airbox`
>   **9종**이 들어간다(`data/minecraft/tags/entity_type/illager.json`). 이 태그를 보는 다른
>   모드·데이터팩이 포탑을 약탈자로 취급할 수 있다. 아직 실제 문제는 안 봤지만, 이상한 상호작용이
>   나오면 **여기부터 의심할 것.** 지우려면 `kubejs/data/minecraft/tags/entity_type/illager.json`
>   에 NeoForge `"remove"` 로 9종.

## Layer 6 — 마을 자동화 / 내실 (라이트 Create)
| 모드 | 링크 | 비고 |
|---|---|---|
| Create | https://modrinth.com/mod/create | 가볍게만 (공장 말고 눈에 보이는 장치 수준) |
| Farmer's Delight | https://modrinth.com/mod/farmers-delight | ⚠️ *Create: Integrated Farming* 애드온 조합은 피할 것 |

## Layer 7 — 관문 차원
| 모드 | 링크 | 비고 |
|---|---|---|
| Deeper and Darker | https://modrinth.com/mod/deeperdarker | **디아더사이드(Otherside)** — 어둠의 차원 |
| The Twilight Forest | https://www.curseforge.com/minecraft/mc-mods/the-twilight-forest | 보스 진행형 모험 차원 |
| The Undergarden | https://modrinth.com/mod/the-undergarden | 버섯 지하 차원 |
| The Aether | https://modrinth.com/mod/aether | 밝은 천공 차원 (대비) |

## Layer 9 — 개성 / 건축 (개인 집)
| 모드 | 링크 | 비고 |
|---|---|---|
| Macaw's Furniture | https://www.curseforge.com/minecraft/mc-mods/macaws-furniture | (+ Macaw's Roofs/Doors/Paths/Bridges 시리즈) |
| Supplementaries | https://modrinth.com/mod/supplementaries | ⚙️ **Moonlight Lib**; 장식·유틸 |
| Chipped | https://modrinth.com/mod/chipped | 방대한 블록 변형 팔레트 |

## 의존 모드 (보통 Prism이 자동 추가)
Balm · Lithostitched · YUNG's API · GeckoLib · Placebo · Apothic Attributes · Curios API ·
playerAnimator · Cloth Config API · Moonlight Lib · Architectury API (요구 시) · Kotlin for Forge (요구 시)

## 리소스팩 (🖥️ 클라 전용 — `resourcepacks/`)

> **폴더에 넣는 것과 켜는 것은 다르다.** zip 을 `resourcepacks/` 에 두면 *목록에 뜨기만* 하고,
> 옵션 화면에서 오른쪽으로 옮겨야 실제로 켜진다. 실제 활성 목록은 `options.txt` 의
> `resourcePacks:` 한 줄이다 — **뒤에 올수록 앞의 것을 덮는다.**

| 팩 | 상태 | 비고 |
|---|---|---|
| **LS-Korean** (자체 제작) | ✅ 켬 (2026-08-08) | **무기 이름 한글화 319종.** `client/resourcepacks/LS-Korean.zip` — 아래 |
| **Fresh Animations** 1.10.4 | ✅ 켬 (2026-08-06) | 몹 동작을 크게 살린다. ⚙️ **Entity Model Features + Entity Texture Features** 필요 — 둘 다 깔려 있다 |
| **Symmetrical Swords** 1.0.1 | ✅ 켬 (2026-08-06) | 검을 들었을 때 좌우 대칭으로 그린다 |
| ~~Red-eyed 1.1~~ | ❌ **삭제 (2026-08-06)** | 적대 몹 눈을 붉게 발광. 아래 |

> **LS-Korean — 무기 이름 한글화 (2026-08-08 제작)**
>
> `py tools/gen_lang.py --zip` 로 **통째로 다시 만든다.** 생성물은 손으로 고치지 말 것 —
> 다음 실행에 덮어써진다. 번역을 바꾸려면 `tools/gen_lang.py` 위쪽의 표만 고친다.
>
> | 모드 | 항목 |
> |---|---|
> | Simply Swords | **369** — 무기 이름 · 능력 툴팁 158 · 상태효과 24 · 도전과제 51 |
> | Epic Knights (`magistuarmory`) | 218 — 재료 11종 × 종류 21종 조합이라 규칙으로 찍는다 |
> | The Aether | 25 · Creatures and Beasts 6 · Deeper and Darker 4 · 그 외 4 |
> | **합계** | **626** |
>
> Simply Swords 표만 항목이 300 을 넘어 **`tools/lang_simplyswords.py`** 로 뺐다 —
> 저쪽은 «무엇으로 옮기나», `gen_lang.py` 는 «어떻게 찍나» 만 담는다.
>
> **왜 필요했나.** 「ko_kr 파일이 있으니 번역돼 있다」가 아니었다. Simply Swords 는 ko_kr 이
> 있는데 **1293개 중 459개만** 채워져 있고, 창작 탭 이름은 **키가 있는데 값이 `"Simply Swords"`**
> 그대로다. 그래서 이 도구는 「파일이 있나」가 아니라 **「그 키의 한국어 값이 영어와 다른가」**로 판정한다.
>
> **모드 이름·창작 탭은 영어로 둔다** (유저 결정 2026-08-08). 옮긴 것은 무기 이름과,
> 무기 툴팁에 같이 뜨는 Simply Swords 문구들뿐이다 — 이름만 한글이고 바로 밑줄이 영어면
> 툴팁이 반쪽이 되기 때문이다.
>
> **아직 영어인 것 (일부러):**
> - Simply Swords **설정 화면 약 470개** — fzzy_config 창에서만 보이고 플레이 중엔 안 뜬다.
>   항목당 설명이 길어 분량은 큰데 읽는 사람은 서버 여는 사람 하나다.
> - Simply Swords **미설치 호환 모드 아이템 63개**(MythicMetals·Gobber) — 그 모드가 없어서 안 뜬다.
> - Epic Knights **방어구 245종** (이 모드는 아이템 463개 중 무기가 218개다).
> - Chipped 7,265 · Rechiseled 3,656 — 블록 변형 스팸. 손으로 쓸 물건이 아니라 규칙 생성이 맞다.
>
> **도구가 스스로 신고한다.** 셋 다 조용히 빠지면 「다 됐다」로 읽히기 때문이다.
> - 못 옮긴 항목 → 종료 코드 **2** 로 목록
> - **서식 문자(`%d` `%s` `%d%%`)가 원문과 어긋나면** → 종료 코드 **3**. 빠뜨리면 숫자가 아예
>   안 나오고 순서가 어긋나면 엉뚱한 값이 박히는데, 화면에는 멀쩡한 한국어로 보여서 눈으로는 못 잡는다.
> - 모드 업데이트로 키가 바뀌어 **안 쓰이게 된 번역** → 별도 보고. 실제로 죽은 항목 하나를 이걸로 잡았다.

> **Red-eyed 를 뺀 이유.** 좀비·스켈레톤·크리퍼·피글린 등 76개 텍스처로 눈을 붉게 만드는 팩이고
> 무드에는 맞았는데, **`pack_format` 을 15(1.20 대)로 선언한다.** 1.21.1 은 34 라 게임이
> 「호환되지 않음」으로 분류한다. 강제로 켤 수는 있지만 그 상태를 문서 없이 남기면 다음 사람이
> 「왜 빨간 경고가 떠 있지」를 다시 조사하게 된다.
>
> 같은 효과가 필요하면 **1.21 대응 팩을 새로 받는 편이 낫다.**

## 클라 전용 모드 (🖥️ — 서버 mods 폴더에 넣지 말 것)
Sodium · Iris · Rethinking Voxels(셰이더) · FancyMenu · Konkrete · EMI · Jade · Xaero's Minimap · Xaero's World Map
(부팅 시 다른 모드가 "client-only"라고 로그에 뜨면 그 jar만 빼고 재시작하면 됩니다.)

> **FancyMenu 배포 주의:** 타이틀 화면 설정은 `config/fancymenu/` 에 저장된다.
> `.mrpack` Export 때 **config 폴더를 포함**해야 친구들 화면도 똑같이 나온다.
> 배경 이미지는 `config/fancymenu/assets/` 안에 두면 같이 묶여 나간다.
