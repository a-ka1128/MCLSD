# Last Stardust — 유물 모델·텍스처 출처

> 조사 2026-08-09. **원래 저장소엔 출처 기록이 거의 없었다** — 낫(헤카테) 하나에만 `credit` 이
> 있었고 나머지는 비어 있거나 `"Made with Blockbench"`(블록벤치 기본 문구, 저작자 정보 아님)였다.
> 파일 내부 단서로 역추적한 결과를 여기 남긴다.
>
> **결과는 각 모델 JSON 의 `credit` 필드에도 박아뒀다.** 이 문서와 그 필드가 같은 내용이고,
> 파일만 열어도 보이게 하려고 양쪽에 뒀다. 새 모델을 넣을 때도 `credit` 을 채우고 여기 한 줄 더한다.

---

## 0. 왜 이 조사를 다시 하면 안 되나

무료 배포라도 **조건이 붙는다.** 아래 nongko 팩은 *비상업 무료 · 상업 이용은 작성자 문의*다.
출처를 잃으면 그 조건도 같이 잃는다 — 서버에 후원·유료 요소가 붙는 순간 확인할 방법이 없어진다.
그리고 이 조사는 **텍스트 단서가 이미 바닥났다.** 지금 못 찾은 3종은 다시 검색해도 안 나온다(§3).

## 1. 결정적 단서는 파일 안에 있었다

블록벤치는 **원작자가 지은 큐브·그룹 이름을 그대로 보존한다.** 남의 제품이면 제품명이 박혀 있고,
자작이면 작업용 약어(`s`, `p`, `fabric`)가 남는다. 텍스처 키(`staff_divine_staff`)도 같은 역할을 한다.

```powershell
# 이름 뽑는 법 (다음에 또 필요하면)
py -c "import json,glob; [print(f, [e.get('name') for e in json.load(open(f,encoding='utf-8')).get('elements',[])][:5]) for f in glob.glob('*.json')]"
```

## 2. ✅ 확인된 출처

| 유물 | 모델 | 원본 이름 | 출처 | 조건 |
|---|---|---|---|---|
| 에레보스 (단검) | `assassin.json` | **Gloomsteel Knife** | nongko's 3D Weapons | 비상업 무료 |
| 크라토스 (도끼) | `pioneer.json` | **Gilded Phoenix Greataxe** | nongko's 3D Weapons | 비상업 무료 |
| 헤카테 (낫) | `hecate.json` | Lukah scythe | Scythes Vanilla Pack | **MIT** |
| 하르모니아 (띠) | `harmonia.json` | 〃 (**재채색 자리맡기**) | 〃 | **MIT** |

- **nongko's 3D Weapons (Fantasy 3D Weapons CIT)** · 66종 · 128x
  - https://www.curseforge.com/minecraft/texture-packs/nongko-3d-weapons
  - 무기 목록: https://nongkos-3d-weapons-guide.webflow.io/ · https://mc3dweapons.com/
  - ⚠️ **조건: "비상업 프로젝트면 자유롭게 사용, 상업 이용은 디스코드로 문의."**
    사설 서버는 해당 없음. **후원·유료화가 붙으면 그때 문의가 필요하다.**
  - 근거: 큐브 이름이 팩의 무기명과 글자 그대로 일치 + 텍스처가 팩과 같은 128×128
- **Scythes Vanilla Pack** · MIT · https://modrinth.com/project/iHUIn5hZ
  - 우리 쪽에서 사슬(`chain_0`~`chain_weight`)을 덧붙였다
  - 색은 채도 있는 픽셀만 색상환을 돌렸다 — 헤카테 녹청 `#17A2A2`, 하르모니아 로즈 `#E86A9A`.
    검은 금속부는 채도가 낮아 그대로 남는다
  - `display` 에 1인칭 변환이 없어 게볼그 값을 참고해 채웠다. 원본대로 두면 손에서 각도가 엉킨다
  - ⚠️ **하르모니아는 자리 맡기다.** 「케스토스(엮는 띠)」인데 낫을 재채색해 쓰고 있다.
    제대로 된 모델이 생기면 `models/item/harmonia.json` 과 `textures/item/harmonia.png`
    **두 파일만** 덮으면 된다 — 아이템 ID·스킬·등록·밸런스는 하나도 안 바뀐다

## 3. ❓ 끝내 못 찾은 것 (검색 소진)

| 유물 | 모델 | 내부 단서 | 해상도 |
|---|---|---|---|
| 아틀라스 (방패) | `guardian.json` | `plank_shield4` · `Layer 4 Copy` … | 128x |
| 오리온 (활 4종) | `hunter.json` `hunter_pulling_0~2` | `corrupted (6)`, `corrupted (5)` … | 128x |
| 우라니아 (지팡이) | `sage.json` | 텍스처 키 `staff_divine_staff` · 큐브 `Box1~Box18` | 64x |

- 128x 두 종은 nongko 팩과 해상도·반입 시각이 같지만 **그 팩엔 방패·활이 없다.** 다른 출처다.
- `corrupted (6)` 의 괄호 숫자는 **다운로드 파일명 패턴**(`corrupted (6).bbmodel`)이다.
  같은 이름을 여러 번 받았다는 뜻이라 **다운로드 폴더·브라우저 기록에 흔적이 남아 있을 수 있다.**
- `Box1~Box18` 은 외부 3D 툴에서 변환된 흔적. `Layer N Copy` 는 이미지 레이어 → 3D 변환 흔적.
- **남은 방법은 이미지 역검색뿐이다**(텍스트 검색으로는 안 나온다).

## 4. 🔧 자작으로 보이는 것

부품 이름이 이 프로젝트 규칙(`_glow` 접미사)을 따르고, 구조를 직접 기술한다.

| 유물 | 모델 | 단서 |
|---|---|---|
| 헬리오스 (총) | `gunner.json` | `s` `p` `f` `shroud` `vent0~4` `mag_body` `s_glow` `el_glow` |
| 〃 (라이플 모드) | `gunner_rifle.json` | `gunner` 에서 파생 (2026-07-27, 「마총 C 과열 전환」 커밋) |
| 히기에이아 (지팡이) | `healer.json` | `staff` `fabric` `el_glow` `fabric_glow` |
| 화롯돌 | `hearthstone.json` | 16x · 106바이트 |

※ **확정은 아니다.** 「제품명이 없다」는 자작의 증거가 아니라 출처 기록이 없다는 뜻일 뿐이다.

## 5. 반입 시각 (정황)

```
2026-07-22 23:32  guardian.geo.json                        ← GeckoLib 실험
2026-07-23 17:44  guardian / hunter / pioneer 텍스처
2026-07-23 23:21  sage · assassin · healer · lancer · gunner 텍스처   ┐ 2분 안에
2026-07-23 23:22  glow 8장 + 모델 8개                                  ┘ 16개
2026-07-25 15:52  hearthstone
2026-07-27 13:38  gunner → gunner_rifle 파생
2026-08-08 23:43  hecate (낫)
```

07-23 밤 **2분 안에 16개가 나타난다** — 하나씩 만든 흐름이 아니라 한 번에 반입된 흐름이다.
§3 의 출처를 찾는다면 그날 밤의 다운로드 기록이 가장 유력한 실마리다.

---

*텍스처는 `assets/lsrelics/textures/item/`, 모델은 `models/item/`, GeckoLib 모델은 `geo/item/`.
PNG 에는 메타데이터가 없고(tEXt 청크 전무), `.bbmodel` 원본 프로젝트 파일도 저장소에 없다.*
