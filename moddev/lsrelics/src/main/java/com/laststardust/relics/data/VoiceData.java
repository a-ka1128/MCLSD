package com.laststardust.relics.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

// 안내자의 목소리 — 발화 예산 · 대사별 상태 · 감시 스냅샷 · 사람별 마지막 접속
// (이관 6단계 = 마지막, docs/ARCHITECTURE.md)
//
// ── 왜 마지막인가 ──
// `ls_voice.js` 는 **다른 시스템의 상태 전이를 감시해서** 대사를 만든다. 관문 진행도가 오르면,
// 성벽이 부서지면, 위협도 밴드가 올라가면 말한다. 그래서 감시 대상이 먼저 자리를 잡아야 했다 —
// 감시 대상을 옮기는 도중에 감시자를 같이 옮기면 「대사가 안 나오는」 원인이 둘로 갈린다.
// 지금은 진행도·공성·위협도가 전부 LSData 에 있으니 옮길 수 있다.
//
// ── 이 파일의 값은 밖으로 안 샌다 ──
// 앞선 다섯 단계와 다른 점이다. `vc_*` 키를 읽는 스크립트가 `ls_voice.js` 하나뿐이라
// 「읽는 쪽을 같이 옮겨야 하는」 함정이 여기엔 없다(스캐너로 확인).
// 그래서 이번엔 옮기는 이유가 순수하게 **키 조립**이다:
//     `vc_once_<대사id>` · `vc_cd_<대사id>` · `vc_rot_<대사id>` · `vc_pend_<대사id>`
//     `vc_p_known_<이름>` · `vc_p_day_<이름>` · `vc_p_rf_<이름>`
// 대사 하나에 키 넷, 사람 하나에 키 셋이 흩어져 있었다. 대사 id 오타 하나면 조용히
// 「한 번도 말한 적 없는」 상태가 되고, 그건 대사가 **또 나오는** 것으로만 드러난다.
//
// ── 대사 테이블은 여기 없다 ──
// 대사 10종의 문장·채널·쿨다운·예산 여부는 `ls_voice.js` 에 남는다. 문장은 `/reload` 로 고치는
// 것이고, 서사를 다듬을 때마다 재빌드가 필요해지면 아무도 안 다듬는다.
// 여기가 아는 건 **「무엇을 언제 말했나」** 뿐이다.
public class VoiceData {

    // ── 발화 예산 ──
    // 상한(VC_BUDGET_MAX)은 스크립트가 갖는다 — 튜닝 값이라 그쪽이 맞는 자리다.
    // 그래서 여기 초깃값은 «아직 안 정해짐»을 뜻하는 **-1** 이고, 스크립트가 처음 읽을 때 채운다.
    //
    // `SiegeData.wallInit` 처럼 별도 플래그를 두지 않은 이유: 성벽 HP 는 0 이 «미설정»과
    // «부서짐» 두 뜻이라 구분이 필요했지만, 예산은 **음수가 될 수 없어** -1 이 그 자체로 유일하다.
    private int budget = -1;
    private int budgetDay;
    private boolean mute;

    // ── 대사별 상태 ──
    // 넷을 한 덩어리로 묶는다. 흩어져 있으면 `/voice reset` 이 셋만 지우고 하나를 빠뜨릴 수 있고,
    // 실제로 옛 코드는 `vc_rot_` 를 일부러 안 지웠는데 그게 의도인지 실수인지 코드만 봐선 몰랐다.
    // (의도가 맞다 — 회전 인덱스는 「어디까지 읽었나」라 초기화 대상이 아니다. 아래 resetLines 참고)
    public static final class Line {
        public boolean once;   // 서버 생애 1회 대사를 이미 썼나
        public int cd;         // 마지막 발화 시각(틱). 0 = 아직 없음
        public int rot;        // 회전 인덱스 — 여러 줄짜리 대사가 어디까지 갔나
        public boolean pend;   // 예약됨(발화가 몇 틱 뒤라, 그 사이 중복 예약을 막는다)

        boolean empty() { return !once && cd == 0 && rot == 0 && !pend; }
    }

    private final Map<String, Line> lines = new LinkedHashMap<>();

    // ── 감시 스냅샷 ──
    // **사본이 아니라 진도표다.** 진행도·위협도·성벽의 주인은 각자 따로 있고, 여기 있는 건
    // 「어디까지 대사를 읽어줬나」다. 이름을 seen* 으로 둔 이유이기도 하다.
    private int seenRf;
    private int seenBand;
    private int seenWall;      // 0 = 아직 본 적 없음 · 1 = 멀쩡 · 2 = 붕괴
    private boolean seenNight;

    // ── 사람별 마지막 접속 ──
    public static final class Seen {
        public boolean known;  // 첫 접속 인사를 이미 했나
        public int day;
        public int rf;
    }

    private final Map<String, Seen> players = new TreeMap<>();

    // ── 예산 ──
    public int budget() { return budget; }
    public int budgetDay() { return budgetDay; }

    // 상한은 호출부가 준다. 자르는 건 여기서 한 번 — 호출부가 빠뜨릴 수 없게.
    public void setBudget(int n, int max) {
        budget = Math.max(0, Math.min(Math.max(0, max), n));
    }

    public void setBudgetDay(int d) { budgetDay = d; }

    // 남았으면 하나 쓰고 true. 없으면 false — 읽고·빼고·쓰는 세 걸음을 한 번으로 접었다.
    public boolean takeBudget() {
        if (budget <= 0) return false;
        budget--;
        return true;
    }

    public boolean mute() { return mute; }
    public void setMute(boolean v) { mute = v; }

    // ── 대사 ──
    private Line line(String id) {
        return lines.computeIfAbsent(id, k -> new Line());
    }

    public boolean once(String id) { Line l = lines.get(id); return l != null && l.once; }
    public int cd(String id)       { Line l = lines.get(id); return l == null ? 0 : l.cd; }
    public boolean pend(String id) { Line l = lines.get(id); return l != null && l.pend; }

    public void setOnce(String id, boolean v) { line(id).once = v; prune(id); }
    public void setCd(String id, int ticks)   { line(id).cd = ticks; prune(id); }
    public void setPend(String id, boolean v) { line(id).pend = v; prune(id); }

    private void prune(String id) {
        Line l = lines.get(id);
        if (l != null && l.empty()) lines.remove(id);
    }

    // 지금 인덱스를 돌려주고 **동시에** 다음으로 넘긴다. 옛 코드는 읽기와 쓰기가 두 줄이었고,
    // 그 사이에 다른 발화가 끼면 같은 대사가 두 번 연달아 나온다.
    // size 는 스크립트가 준다 — 대사 줄 수는 그쪽 테이블에만 있다.
    public int nextRot(String id, int size) {
        if (size <= 1) return 0;
        Line l = line(id);
        int i = ((l.rot % size) + size) % size;   // 음수·범위 밖을 방어한다
        l.rot = (i + 1) % size;
        return i;
    }

    // `/voice reset` — 1회성·쿨다운·예약만 되돌리고 **회전 인덱스는 남긴다.**
    // 회전은 「어디까지 읽었나」라 초기화 대상이 아니다. 지우면 리셋할 때마다 같은 첫 줄이 나온다.
    // 되돌린 1회성 대사 수를 돌려준다 — 호출부가 「n개」를 그대로 찍을 수 있게.
    public int resetLines() {
        int n = 0;
        for (Map.Entry<String, Line> e : lines.entrySet()) {
            Line l = e.getValue();
            if (l.once) n++;
            l.once = false;
            l.cd = 0;
            l.pend = false;
        }
        lines.entrySet().removeIf(e -> e.getValue().empty());
        return n;
    }

    // ── 감시 스냅샷 ──
    public int seenRf() { return seenRf; }
    public void setSeenRf(int n) { seenRf = n; }

    public int seenBand() { return seenBand; }
    public void setSeenBand(int n) { seenBand = n; }

    public int seenWall() { return seenWall; }
    public void setSeenWall(int n) { seenWall = n; }

    public boolean seenNight() { return seenNight; }
    public void setSeenNight(boolean v) { seenNight = v; }

    // ── 사람 ──
    // **처음 보는 사람이면 true 를 돌려주면서 동시에 표시한다.** 옛 코드는 확인과 표시가
    // 두 줄이었고, 그 사이에 예외가 나면 첫 접속 인사가 접속할 때마다 반복된다.
    public boolean markKnown(String name) {
        if (name == null || name.isEmpty()) return false;
        Seen s = players.computeIfAbsent(name, k -> new Seen());
        if (s.known) return false;
        s.known = true;
        return true;
    }

    public int lastDay(String name) { Seen s = players.get(name); return s == null ? 0 : s.day; }
    public int lastRf(String name)  { Seen s = players.get(name); return s == null ? 0 : s.rf; }

    public void stamp(String name, int day, int rf) {
        if (name == null || name.isEmpty()) return;
        Seen s = players.computeIfAbsent(name, k -> new Seen());
        s.day = day;
        s.rf = rf;
    }

    // ── 저장 ──
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();

        tag.putInt("budget", budget);
        tag.putInt("budgetDay", budgetDay);
        tag.putBoolean("mute", mute);

        ListTag ls = new ListTag();
        for (Map.Entry<String, Line> e : lines.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", e.getKey());
            t.putBoolean("once", e.getValue().once);
            t.putInt("cd", e.getValue().cd);
            t.putInt("rot", e.getValue().rot);
            t.putBoolean("pend", e.getValue().pend);
            ls.add(t);
        }
        tag.put("lines", ls);

        CompoundTag w = new CompoundTag();
        w.putInt("rf", seenRf);
        w.putInt("band", seenBand);
        w.putInt("wall", seenWall);
        w.putBoolean("night", seenNight);
        tag.put("watch", w);

        ListTag ps = new ListTag();
        for (Map.Entry<String, Seen> e : players.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("name", e.getKey());
            t.putBoolean("known", e.getValue().known);
            t.putInt("day", e.getValue().day);
            t.putInt("rf", e.getValue().rf);
            ps.add(t);
        }
        tag.put("players", ps);

        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        // 없으면 -1 을 유지한다 — 「아직 안 정해짐」이고, 스크립트가 상한으로 채운다.
        budget = tag.contains("budget") ? tag.getInt("budget") : -1;
        budgetDay = tag.getInt("budgetDay");
        mute = tag.getBoolean("mute");

        lines.clear();
        ListTag ls = tag.getList("lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < ls.size(); i++) {
            CompoundTag t = ls.getCompound(i);
            String id = t.getString("id");
            if (id.isEmpty()) continue;
            Line l = line(id);
            l.once = t.getBoolean("once");
            l.cd = t.getInt("cd");
            l.rot = t.getInt("rot");
            l.pend = t.getBoolean("pend");
            prune(id);
        }

        CompoundTag w = tag.getCompound("watch");
        seenRf = w.getInt("rf");
        seenBand = w.getInt("band");
        seenWall = w.getInt("wall");
        seenNight = w.getBoolean("night");

        players.clear();
        ListTag ps = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < ps.size(); i++) {
            CompoundTag t = ps.getCompound(i);
            String name = t.getString("name");
            if (name.isEmpty()) continue;
            Seen s = players.computeIfAbsent(name, k -> new Seen());
            s.known = t.getBoolean("known");
            s.day = t.getInt("day");
            s.rf = t.getInt("rf");
        }
    }

    // 이관 확인용 (/lsdata).
    public String summary() {
        int spent = 0;
        for (Line l : lines.values()) if (l.once) spent++;
        return "예산 " + (budget < 0 ? "미설정" : String.valueOf(budget))
             + " · 1회성 소진 " + spent + " · 아는 사람 " + players.size() + "명"
             + (mute ? " · §c침묵" : "");
    }
}
