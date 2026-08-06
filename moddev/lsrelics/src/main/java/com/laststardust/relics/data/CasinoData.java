package com.laststardust.relics.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

// 별똥말 경마 — 진행 단계 · 말 위치 · 베팅 (이관 5단계, docs/ARCHITECTURE.md)
//
// ── 왜 옮기는가 ──
// 여기 문제는 «키가 안 지워진다» 였다. `cs_bet_<이름>` 은 경마가 끝날 때 **빈 문자열로 덮였을 뿐**
// 사라지지 않는다. 서버에 다녀간 사람 수만큼 빈 키가 세이브에 영원히 쌓이고, 목록(`cs_betters`)과
// 따로 살기 때문에 둘이 어긋나도 아무도 모른다. 봉화의 `pb_<이름>_x/y/z` 와 같은 모양이다.
// 여기서는 베팅이 Map 한 개다. 지우면 사라진다.
//
// 그리고 개장이 **한 번의 호출**이 됐다. 옛 코드는 단계·타이머·베팅목록·말 위치 다섯 줄이
// 나란히 있었고, 그중 하나만 빠뜨리면 «지난 경기의 말 위치에서 출발하는» 경마가 됐다.
//
// ── 주사위 결투는 여기 없다 ──
// 그건 60초짜리 메모리 상태다(`CS_DUEL`). 서버가 꺼지면 사라지는 게 맞고, 스크립트가 그렇게
// 적어 두고 그렇게 쓰고 있다. 저장할 이유가 없는 것을 저장하면 「재시작했더니 옛 결투 신청이
// 살아 있는」 쪽이 오히려 버그다.
public class CasinoData {

    public static final int HORSES = 5;

    public static final int PHASE_NONE = 0;
    public static final int PHASE_BET = 1;
    public static final int PHASE_RUN = 2;

    public static final class Bet {
        public int horse;
        public int amount;
    }

    private int phase;
    private int timer;
    private final int[] pos = new int[HORSES];
    // 베팅 순서를 지킨다 — 결과 방송이 매번 같은 순서로 나와야 「내 줄」을 찾을 수 있다.
    private final Map<String, Bet> bets = new LinkedHashMap<>();

    public int phase() { return phase; }
    public int timer() { return timer; }

    public void setPhase(int p) { phase = Math.max(0, Math.min(PHASE_RUN, p)); }
    public void setTimer(int t) { timer = t; }

    // 개장 — 단계·타이머·말 위치·베팅을 **한 번에** 세운다.
    // 다섯 줄로 흩어져 있으면 언젠가 하나가 빠지고, 그날 경마는 지난 경기 위치에서 출발한다.
    public void openRace(int seconds) {
        phase = PHASE_BET;
        timer = seconds;
        for (int i = 0; i < HORSES; i++) pos[i] = 0;
        bets.clear();
    }

    // 폐장 — 베팅을 비우고 단계를 0 으로. 정산은 호출부가 먼저 끝내고 부른다.
    public void closeRace() {
        phase = PHASE_NONE;
        timer = 0;
        bets.clear();
    }

    // 1-기반. 스크립트가 1~5 로 세고 있었고 그 감각을 유지한다.
    public int pos(int horse) {
        if (horse < 1 || horse > HORSES) return 0;
        return pos[horse - 1];
    }

    public int advance(int horse, int n) {
        if (horse < 1 || horse > HORSES) return 0;
        pos[horse - 1] = Math.max(0, pos[horse - 1] + n);
        return pos[horse - 1];
    }

    // 이미 건 사람이면 false — 호출부가 칩을 두 번 걷지 않게 한다.
    public boolean placeBet(String name, int horse, int amount) {
        if (name == null || name.isEmpty()) return false;
        if (horse < 1 || horse > HORSES || amount <= 0) return false;
        if (bets.containsKey(name)) return false;
        Bet b = new Bet();
        b.horse = horse;
        b.amount = amount;
        bets.put(name, b);
        return true;
    }

    public boolean hasBet(String name) { return bets.containsKey(name); }
    public List<String> betters() { return new ArrayList<>(bets.keySet()); }
    public int betHorse(String name)  { Bet b = bets.get(name); return b == null ? 0 : b.horse; }
    public int betAmount(String name) { Bet b = bets.get(name); return b == null ? 0 : b.amount; }
    public int betCount() { return bets.size(); }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("phase", phase);
        tag.putInt("timer", timer);
        tag.putIntArray("pos", pos.clone());
        ListTag list = new ListTag();
        for (Map.Entry<String, Bet> e : bets.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("name", e.getKey());
            t.putInt("horse", e.getValue().horse);
            t.putInt("amount", e.getValue().amount);
            list.add(t);
        }
        tag.put("bets", list);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        setPhase(tag.getInt("phase"));
        timer = tag.getInt("timer");
        int[] p = tag.getIntArray("pos");
        for (int i = 0; i < HORSES; i++) pos[i] = i < p.length ? p[i] : 0;
        bets.clear();
        ListTag list = tag.getList("bets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            placeBet(t.getString("name"), t.getInt("horse"), t.getInt("amount"));
        }
    }

    // 이관 확인용 (/lsdata).
    public String summary() {
        if (phase == PHASE_NONE) return "경마 없음";
        return (phase == PHASE_BET ? "베팅 중(" + timer + "초)" : "주행 중") + " · 베팅 " + bets.size() + "명";
    }
}
