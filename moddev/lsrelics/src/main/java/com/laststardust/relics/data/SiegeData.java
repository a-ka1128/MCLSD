package com.laststardust.relics.data;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

// 공성 — 위협도 · 성벽 · 균열 노드 · 최종장 · 진행 중인 공세 (이관 4단계, docs/ARCHITECTURE.md)
//
// ── 왜 이게 가장 큰 덩어리인가 ──
// `ls_siege.js` 는 1200줄이 넘고 키를 26개 들고 있다. 그런데 옮기는 이유는 «크다» 가 아니라
// **이 26개 중 다섯이 파일 경계를 넘어간다**는 것이다:
//     ls_threat            → ls_hope.js (희망 게이지의 짝) · ls_voice.js (밴드 대사)
//     wall_hp              → ls_voice.js (성벽 붕괴 대사)
//     ls_siege_active      → ls_voice.js (밤 앰비언트 억제)
//     ls_first_siege_done  → ls_voice.js (첫 격퇴 대사)
//     ls_finale            → ls_stats.js (자동 폐막식)
// 그리고 그 다섯을 읽는 쪽은 **`ls_siege.js` 의 접근 함수를 부르지 않는다.** 각자 자기 파일의
// `hoStore(...).getInt('ls_threat')` · `vcGetI(server,'ls_threat')` 로 직접 읽는다.
//
// 여기서 쓰는 쪽만 옮기면 저 다섯 줄이 전부 **조용히 0 을 읽는다.** 예외도 로그도 없다:
//   · 희망 게이지가 위협 0 을 보고 늘 최대로 뜬다
//   · 호데고스가 위협 밴드를 못 넘어 영원히 침묵한다
//   · 성벽이 부서져도 «붕괴» 대사가 안 나온다
//   · 최종 보스를 잡아도 폐막식이 안 열린다
// 명예 보드가 몇 주 동안 «CSV 첫 사람, 0점»을 1위로 내보내던 사고와 **완전히 같은 구조**다.
// 그래서 이번 이관의 핵심 작업은 자바를 쓰는 게 아니라 **읽는 쪽 다섯을 같이 옮기는 것**이다.
//
// ── 옮기는 것과 두는 것 ──
// **데이터만 옮긴다. 판정·연출·명령은 스크립트에 남는다.** 1~3단계와 같은 모양이다.
// 웨이브 구성·스폰 각도·보상 계산은 `/reload` 로 고치는 값이라 스크립트가 맞고, 그걸 같이
// 끌고 오면 «이관해서 깨졌나, 원래 그랬나»를 가릴 수 없게 된다. 그 판단은 3단계에서 이름 키를
// UUID 로 안 바꾼 것과 같은 이유다.
//
// ── 상한을 어디서 거는가 ──
// 위협도(0~15)는 순수한 공성 불변식이라 여기서 건다. 성벽 최대 HP 는 다르다 —
// `3000 + 방벽Lv×1000` 인데 3000 이 스크립트의 튜닝 상수다. 그래서 `setWallHp(hp, max)` 로
// **천장을 인자로 받아** 자르는 건 여기서 한다. 자르는 곳이 한 곳이면 호출부가 빠뜨릴 수 없다.
public class SiegeData {

    public static final int MAX_THREAT = 15;

    // ── 위협도 · 균열 노드 ──
    // 노드는 이름 목록이다. 스크립트가 CSV 한 줄로 들고 있었고 그대로 받는다 —
    // 리스트로 바꾸는 건 옳지만, 이관과 형식 변경을 같이 하면 원인 분리가 안 된다.
    private int threat;
    private final List<String> nodes = new ArrayList<>();

    // ── 성벽 ──
    // `init` 이 따로 있는 이유: hp 0 에는 «아직 한 번도 안 정해짐»과 «부서짐» 두 뜻이 있다.
    // 그 둘을 못 가리면 새 월드의 성벽이 처음부터 부서진 상태로 시작한다.
    private int wallHp;
    private boolean wallInit;
    private int wallRadius;
    private int wallWarn;
    private boolean wallLastStand;
    private int wallStandUntil;

    // ── 최종장 (가장 긴 밤) ──
    private int finale;
    private boolean finaleArmed;
    private boolean finaleNightOk;   // 이 밤 방어 성공
    private boolean trueSpawned;     // 진 형태가 이미 나왔나
    private boolean trueFormOff;     // 운영자가 진 형태를 껐나
    private boolean dawnbreak;       // 여명 가속 중

    // ── 진행 중인 공세 ──
    // 서버가 공성 도중에 꺼질 수 있으므로 전부 저장된다(지금 동작 그대로).
    private boolean siegeActive;
    private boolean siegeGrand;
    private int siegeWaves;      // 남은 웨이브
    private int siegeWaveNo;     // 현재 물결 번호
    private int siegeRemaining;  // 남은 적
    private int siegeReward;     // 누적 보상
    private int siegeAngle;      // 스폰 방향(도)

    // ── 하루 · 예고 ──
    private int dreadStep;
    private int annTier;
    private int day;
    private boolean wasNight;

    // ── 이력 ──
    private boolean firstSiegeDone;

    // ── 위협도 ──
    public int threat() { return threat; }

    public void setThreat(int v) {
        threat = Math.max(0, Math.min(MAX_THREAT, v));
    }

    // ── 균열 노드 ──
    // 스크립트와 주고받는 형식은 CSV 다. 빈 목록은 빈 문자열 — `"".split(",")` 이
    // 길이 1 짜리 배열을 주는 함정이 있어서, 스크립트 쪽에서 빈 문자열을 먼저 거른다.
    public String nodeCsv() {
        return String.join(",", nodes);
    }

    public void setNodeCsv(String csv) {
        nodes.clear();
        if (csv == null || csv.isEmpty()) return;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) nodes.add(t);
        }
    }

    public int nodeCount() { return nodes.size(); }

    // ── 성벽 ──
    public boolean wallInit() { return wallInit; }
    public int wallHpRaw() { return wallHp; }

    // 천장은 호출부가 준다(방벽 레벨에 따라 달라진다). 자르는 건 여기서 한 번.
    public void setWallHp(int hp, int max) {
        wallInit = true;
        wallHp = Math.max(0, Math.min(Math.max(0, max), hp));
    }

    public int wallRadius() { return wallRadius; }
    public void setWallRadius(int r) { wallRadius = Math.max(0, r); }

    public int wallWarn() { return wallWarn; }
    public void setWallWarn(int n) { wallWarn = n; }

    public boolean wallLastStand() { return wallLastStand; }
    public void setWallLastStand(boolean v) { wallLastStand = v; }

    public int wallStandUntil() { return wallStandUntil; }
    public void setWallStandUntil(int t) { wallStandUntil = t; }

    // ── 최종장 ──
    public int finale() { return finale; }
    public void setFinale(int v) { finale = Math.max(0, v); }

    public boolean finaleArmed() { return finaleArmed; }
    public void setFinaleArmed(boolean v) { finaleArmed = v; }

    public boolean finaleNightOk() { return finaleNightOk; }
    public void setFinaleNightOk(boolean v) { finaleNightOk = v; }

    public boolean trueSpawned() { return trueSpawned; }
    public void setTrueSpawned(boolean v) { trueSpawned = v; }

    public boolean trueFormOff() { return trueFormOff; }
    public void setTrueFormOff(boolean v) { trueFormOff = v; }

    public boolean dawnbreak() { return dawnbreak; }
    public void setDawnbreak(boolean v) { dawnbreak = v; }

    // ── 진행 중인 공세 ──
    public boolean siegeActive() { return siegeActive; }
    public void setSiegeActive(boolean v) { siegeActive = v; }

    public boolean siegeGrand() { return siegeGrand; }
    public void setSiegeGrand(boolean v) { siegeGrand = v; }

    public int siegeWaves() { return siegeWaves; }
    public void setSiegeWaves(int n) { siegeWaves = Math.max(0, n); }

    public int siegeWaveNo() { return siegeWaveNo; }
    public void setSiegeWaveNo(int n) { siegeWaveNo = Math.max(0, n); }

    public int siegeRemaining() { return siegeRemaining; }
    public void setSiegeRemaining(int n) { siegeRemaining = Math.max(0, n); }

    public int siegeReward() { return siegeReward; }
    public void setSiegeReward(int n) { siegeReward = Math.max(0, n); }

    public int siegeAngle() { return siegeAngle; }
    public void setSiegeAngle(int deg) { siegeAngle = deg; }

    // 공세를 끝낼 때 되돌려야 하는 네 값을 한 번에. 스크립트에 이 네 줄이 **두 군데** 있었고
    // (정상 종료 · 강제 중단), 한쪽에만 값을 더하면 공성이 끝났는데 남은 적이 남아 있는
    // 상태가 된다. 끝내는 방법이 하나뿐이면 그 어긋남이 생길 자리가 없다.
    public void endSiege() {
        siegeActive = false;
        siegeGrand = false;
        siegeRemaining = 0;
        siegeWaves = 0;
    }

    // ── 하루 · 예고 ──
    public int dreadStep() { return dreadStep; }
    public void setDreadStep(int n) { dreadStep = n; }

    public int annTier() { return annTier; }
    public void setAnnTier(int n) { annTier = n; }

    public int day() { return day; }
    public void setDay(int n) { day = n; }

    public boolean wasNight() { return wasNight; }
    public void setWasNight(boolean v) { wasNight = v; }

    // ── 이력 ──
    public boolean firstSiegeDone() { return firstSiegeDone; }
    public void setFirstSiegeDone(boolean v) { firstSiegeDone = v; }

    // ── 저장 ──
    // 섹션을 나눠 담는다. 저장 파일을 열었을 때 «공성 중인가»와 «성벽이 몇인가»가
    // 한 뭉치로 섞여 있으면 사람이 못 읽는다.
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();

        tag.putInt("threat", threat);
        tag.putString("nodes", nodeCsv());
        tag.putBoolean("firstSiegeDone", firstSiegeDone);

        CompoundTag w = new CompoundTag();
        w.putInt("hp", wallHp);
        w.putBoolean("init", wallInit);
        w.putInt("radius", wallRadius);
        w.putInt("warn", wallWarn);
        w.putBoolean("lastStand", wallLastStand);
        w.putInt("standUntil", wallStandUntil);
        tag.put("wall", w);

        CompoundTag f = new CompoundTag();
        f.putInt("stage", finale);
        f.putBoolean("armed", finaleArmed);
        f.putBoolean("nightOk", finaleNightOk);
        f.putBoolean("trueSpawned", trueSpawned);
        f.putBoolean("trueFormOff", trueFormOff);
        f.putBoolean("dawnbreak", dawnbreak);
        tag.put("finale", f);

        CompoundTag s = new CompoundTag();
        s.putBoolean("active", siegeActive);
        s.putBoolean("grand", siegeGrand);
        s.putInt("waves", siegeWaves);
        s.putInt("waveNo", siegeWaveNo);
        s.putInt("remaining", siegeRemaining);
        s.putInt("reward", siegeReward);
        s.putInt("angle", siegeAngle);
        tag.put("run", s);

        CompoundTag d = new CompoundTag();
        d.putInt("dreadStep", dreadStep);
        d.putInt("annTier", annTier);
        d.putInt("day", day);
        d.putBoolean("wasNight", wasNight);
        tag.put("day", d);

        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        setThreat(tag.getInt("threat"));
        setNodeCsv(tag.getString("nodes"));
        firstSiegeDone = tag.getBoolean("firstSiegeDone");

        CompoundTag w = tag.getCompound("wall");
        wallHp = w.getInt("hp");
        wallInit = w.getBoolean("init");
        wallRadius = w.getInt("radius");
        wallWarn = w.getInt("warn");
        wallLastStand = w.getBoolean("lastStand");
        wallStandUntil = w.getInt("standUntil");

        CompoundTag f = tag.getCompound("finale");
        finale = f.getInt("stage");
        finaleArmed = f.getBoolean("armed");
        finaleNightOk = f.getBoolean("nightOk");
        trueSpawned = f.getBoolean("trueSpawned");
        trueFormOff = f.getBoolean("trueFormOff");
        dawnbreak = f.getBoolean("dawnbreak");

        CompoundTag s = tag.getCompound("run");
        siegeActive = s.getBoolean("active");
        siegeGrand = s.getBoolean("grand");
        siegeWaves = s.getInt("waves");
        siegeWaveNo = s.getInt("waveNo");
        siegeRemaining = s.getInt("remaining");
        siegeReward = s.getInt("reward");
        siegeAngle = s.getInt("angle");

        CompoundTag d = tag.getCompound("day");
        dreadStep = d.getInt("dreadStep");
        annTier = d.getInt("annTier");
        day = d.getInt("day");
        wasNight = d.getBoolean("wasNight");
    }

    // 이관 확인용 (/lsdata).
    public String summary() {
        return "위협 " + threat + " · 노드 " + nodes.size() + "곳 · 성벽 "
             + (wallInit ? String.valueOf(wallHp) : "미설정")
             + " · 최종장 " + finale + (siegeActive ? " · §c공성 중" : "");
    }
}
