# Last Stardust — 빌드 & 서버 세팅 가이드

대상: **NeoForge 1.21.1**, 이 PC에서 자체 호스팅 (Ryzen 9 9950X3D, 61 GB RAM).
페이즈 순서대로 진행하세요. Phase 7(KubeJS/퀘스트 커스텀)은 서버가 부팅된 **이후**에 합니다 — `CUSTOM.md` 참고.

---

## Phase 0 — 사전 준비
1. **Java 21** (NeoForge 1.21.1 필수). Microsoft/Adoptium Temurin JDK 21 설치.
   확인: `java -version` → 21이라고 떠야 함.
2. **Prism Launcher** — https://prismlauncher.org/ (무료·오픈소스). 설치.
3. (선택, 권장) Prism에 **CurseForge API 키** 등록 → CurseForge 모드도 설치 가능:
   Prism → Settings → APIs → CurseForge 키 붙여넣기 (키 받는 링크는 Prism이 안내). Modrinth는 키 불필요.

## Phase 1 — 클라이언트 인스턴스 만들기
1. Prism → **Add Instance** → 이름 `Last Stardust`.
2. **Minecraft 1.21.1** 선택 → 모드로더 **NeoForge** → 최신 1.21.1 NeoForge 버전 선택.
3. 인스턴스 → **Settings → Java**: **6–8 GB** 할당 (`-Xmx8G`). (61 GB 있으니 여유롭게.)

## Phase 2 — 모드 담기
1. 인스턴스 → **Mods → Download mods** (Prism 안에서 Modrinth+CurseForge 브라우저 열림).
2. `MODS.md`의 모드를 전부 추가. **의존성 프롬프트는 수락** — Balm·GeckoLib·Placebo 등은 Prism이 자동 추가.
3. ⚠️ 항목: 경제 모드는 **하나만**; **FTB XMod Compat** 추가; Puffish는 **Puffish Attributes** + 스킬트리 팩도 같이.
4. **한 번 실행**해서(싱글플레이 테스트 월드) 정상 부팅 + EMI(레시피 뷰어)·모드 콘텐츠 확인. 빨간 에러는 다음
   단계 전에 해결.

## Phase 3 — 친구 배포용 패키징 (.mrpack)
1. Prism → 인스턴스 → **Export instance** → **Modrinth (.mrpack)** 포맷 선택.
2. `.mrpack`을 친구에게 전송 → 친구는 자기 Prism에서 **Import** → 동일한 모드셋, 오차 없음.
   (모드 불일치가 "접속 안 됨"의 1순위 원인이라 이게 안전.)

## Phase 4 — 전용 서버
1. 서버 폴더 생성, 예: `D:\Study\MC\CustomServer1\server`.
2. https://neoforged.net/ 에서 **NeoForge 1.21.1 서버 인스톨러** 다운로드 → 서버 폴더에서 실행:
   `java -jar neoforge-<버전>-installer.jar --installServer`
3. **클라 모드**를 `server\mods`에 복사한 뒤, **클라 전용 모드는 삭제** (Sodium, Iris, Rethinking Voxels, EMI,
   Jade, Xaero's ×2 — MODS.md 참고). 나머지는 유지.
4. 첫 실행 시 `eula.txt` 생성 → `eula=true` 로 변경.
5. **`server.properties`** 편집:
   - `pvp=false`  (P4 — 협동, PvP 없음)
   - `difficulty=hard`  (PvE 긴장감; 나중에 조정)
   - `max-players=8`
   - `allow-nether=true`, `spawn-protection=0` (허브 보호는 청구/op로 대체)
   - `motd=Last Stardust`
6. **RAM:** `user_jvm_args.txt` 편집 → `-Xmx10G` 와 `-Xms10G` 설정 (콘텐츠 무거운 팩이라 3–6인엔 10 GB로
   시작, 필요하면 12 G까지).
7. 생성된 **`run.bat`** 으로 실행. 콘솔에 "Done" 뜰 때까지 확인.

## Phase 5 — 친구 접속
하나 선택:
- **Playit.gg (가장 쉬움, 라우터 접근 불필요):** Playit 에이전트 설치 → 공개 주소(예: `xxxxx.playit.gg`)를
  줌 → `localhost:25565`로 터널링. 친구는 그 주소로 접속. 무료.
- **포트포워딩 (다이렉트):** 라우터에서 TCP **25565**를 이 PC 로컬 IP로 포워딩 → 친구는 내 공인 IP로 접속.
  더 빠르지만 IP 노출 + 라우터 접근 필요.
- 참고: 서버는 이 PC가 켜져있고 `run.bat`이 돌아가는 동안만 작동.

## Phase 6 — 월드 첫 세팅
1. 자기 op 부여: 서버 콘솔에 `op <내닉네임>`.
2. 스폰 지점에 **성역 허브** 건설(또는 `/fill`/스키매틱) → `/setworldspawn` 으로 지정.
3. 허브에 **Waystone** 설치해서 다같이 귀환 가능하게.
4. 공동 창고 구역 + 허브 상점 위치 정하기.
5. 유용한 게임룰: `keepInventory`는 불필요(Corpse가 처리); 팬텀 스팸 거슬리면 `/gamerule doInsomnia false`.

## Phase 7 — 서버 정체성(커스텀), 안정화 후
이제 Last Stardust 고유 시스템을 얹습니다 → **`CUSTOM.md`** 참고: 공동 금고, 미처리 관문 밤 공성,
FTB Quests 정화 진행.

---

### 빠른 점검 체크리스트
- [ ] Java 21 설치
- [ ] Prism 인스턴스(NeoForge 1.21.1) 모드 다 넣고 부팅됨
- [ ] `.mrpack` 내보내기 + 친구 1명 임포트 성공
- [ ] 서버 "Done"까지 부팅, pvp=false, 10 GB
- [ ] 친구 접속됨 (Playit or 포트포워딩)
- [ ] 허브 건설 + 월드 스폰 + Waystone 설치
