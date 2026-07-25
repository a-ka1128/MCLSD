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
| Distant Horizons 🖥️ | https://modrinth.com/mod/distant-horizons | (선택) LOD 원경. Iris 1.8+와 함께면 셰이더로 그려짐. 점박이 나면 셰이더 궁합 문제 → DH `Transparency=Complete` |

## Layer 1 — 거점
| 모드 | 링크 | 비고 |
|---|---|---|
| Waystones | https://modrinth.com/mod/waystones | ⚙️ **Balm** 필요 |
| FTB Teams | https://www.curseforge.com/minecraft/mc-mods/ftb-teams-forge | ⚙️ **FTB Library**; 팀 금고의 기반 |
| FTB Chunks | https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-forge | 청크 점유 보호 (친한 그룹이면 선택) |

## Layer 2 — 경제  ⚠️ 경제 모드는 하나만 선택
| 모드 | 링크 | 비고 |
|---|---|---|
| Simple Economy | https://modrinth.com/mod/simple-economies | 서버사이드, 가상 잔액, **클라 모드 불필요** |
| — 또는 EconomyMod | https://modrinth.com/mod/economymod | 인벤과 분리된 가상 잔액 |
| KubeJS | https://modrinth.com/mod/kubejs | 공동 금고 + 업그레이드 게이팅 커스텀 (CUSTOM.md 참고) |

## Layer 3 — 목표 / 진행
| 모드 | 링크 | 비고 |
|---|---|---|
| FTB Quests | https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge | ⚙️ **FTB Library** |
| FTB XMod Compat | https://www.curseforge.com/minecraft/mc-mods/ftb-xmod-compat | ⚠️ KubeJS ↔ FTB Quests 연결에 필요 |

## Layer 4 — 모험 / 월드
| 모드 | 링크 | 비고 |
|---|---|---|
| Terralith | https://modrinth.com/mod/terralith | ⚙️ **Lithostitched** 필요 (또는 데이터팩 형태 사용) |
| When Dungeons Arise | https://modrinth.com/mod/when-dungeons-arise | 로그라이크 던전·구조물 |
| YUNG's Better Dungeons | https://www.curseforge.com/minecraft/mc-mods/yungs-better-dungeons-neoforge | ⚙️ **YUNG's API** |

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
Balm · FTB Library · Lithostitched · YUNG's API · GeckoLib · Placebo · Apothic Attributes · Curios API ·
playerAnimator · Cloth Config API · Moonlight Lib · Architectury API (요구 시) · Kotlin for Forge (요구 시)

## 클라 전용 모드 (🖥️ — 서버 mods 폴더에 넣지 말 것)
Sodium · Iris · Rethinking Voxels(셰이더) · FancyMenu · Konkrete · EMI · Jade · Xaero's Minimap · Xaero's World Map
(부팅 시 다른 모드가 "client-only"라고 로그에 뜨면 그 jar만 빼고 재시작하면 됩니다.)

> **FancyMenu 배포 주의:** 타이틀 화면 설정은 `config/fancymenu/` 에 저장된다.
> `.mrpack` Export 때 **config 폴더를 포함**해야 친구들 화면도 똑같이 나온다.
> 배경 이미지는 `config/fancymenu/assets/` 안에 두면 같이 묶여 나간다.
