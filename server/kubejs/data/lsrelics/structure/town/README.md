# 마을 구조물 — 레벨이 오르면 여기 있는 것이 세계에 선다

파일 이름은 **`<트랙>_<단계>.nbt`** 다. 트랙 키는 `ramparts` · `workshop` · `sanctum` · `district`.

```
workshop_1.nbt   공용 작업대
workshop_2.nbt   대장간 지원      ← 「대장간」이 여기다
workshop_3.nbt   자동 생산 라인
workshop_4.nbt   귀환의 요람
```

## 만드는 법

1. 크리에이티브에서 **구조물 블록**(`/give @s structure_block`)을 놓는다
2. 저장 모드 → 이름 `lsrelics:town/workshop_2` → **저장**
3. 파일이 `server/world/generated/lsrelics/structures/town/workshop_2.nbt` 에 떨어진다
4. 그걸 **이 폴더로** 옮긴다
5. `/reload` → `/town build workshop`

## ⚠️ 단계마다 «같은 원점»으로 떠야 한다

다음 단계는 이전 단계 자리에 **그대로 얹힌다**. 원점이 다르면 건물이 어긋나고,
뒤 단계가 앞 단계보다 작으면 **이전 단계의 잔해가 삐져나온다.**

그래서 **완성형(4단계)을 먼저 짓고 거기서 덜어내는** 순서를 권한다.
반대로 하면 1단계부터 짓다가 4단계에서 자리가 안 맞는다.

## 앵커

구조물이 설 자리는 **성역 기준 상대 좌표**다. 절대 좌표로 두면 성역을 옮기는 날
건물만 제자리에 남는다.

```
/town anchor workshop      ← 지금 서 있는 자리를 앵커로 잡는다
```

구조물 블록의 **원점(코너)** 에 서서 잡는 게 맞다 — 구조물은 그 점에서 +x/+y/+z 로 자란다.

## 다시 세우기

`reconcile` 은 **레벨이 바뀔 때만** 돈다. 파일만 고쳐서는 아무 일도 안 일어난다.

```
/town build workshop       ← 지금 레벨의 구조물을 다시 세운다
```

안 서면 이유는 `logs/latest.log` 의 `[마을건축]` 줄에 있다.
