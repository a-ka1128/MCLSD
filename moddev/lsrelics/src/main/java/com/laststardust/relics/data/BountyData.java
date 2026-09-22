package com.laststardust.relics.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

// 현상금 게시판 — 게시 중인 3건과 진행도 (이관 5단계, docs/ARCHITECTURE.md)
//
// ── 왜 옮기는가: `|` 로 이어붙인 문자열 ──
// 옛 저장은 한 칸이 이랬다:
//     'hunt|minecraft:zombie|좀비|25|60'
// 그리고 읽는 쪽은 `split('|')` 로 다섯 조각이 나오는지만 봤다. 이름에 `|` 가 들어가면 조각이
// 여섯이 되고, 그러면 **그 현상금은 조용히 사라진다** — `btDec` 가 null 을 주고 호출부는 `continue`
// 한다. 예외도 로그도 없다. 지금 카탈로그에 `|` 가 없어서 안 터졌을 뿐이지, 한글 이름은 우리가
// 계속 늘리는 값이다.
// 여기서는 필드가 각자 자리를 갖는다. 이어붙이는 곳이 없으니 쪼개지는 곳도 없다.
//
// ── 카탈로그는 여기 없다 ──
// 사냥 8종·납품 8종·정예 6종의 «후보 목록»과 보상 배율은 `ls_bounty.js` 에 남는다. 굴린 결과만
// 여기 온다. 후보를 늘리는 건 `/reload` 로 하는 일이고, 이 파일이 아는 건 «지금 뭐가 걸려 있나» 뿐이다.
//
// ── 슬롯은 3칸 고정 ──
// 옛 코드는 `bt1`·`bt2`·`bt3` 를 문자열로 조립해 읽었다. 여기서는 인덱스 범위를 한 곳에서 걸고,
// 벗어나면 **빈 현상금**을 돌려준다(예외를 안 던진다) — 호출부가 틱 루프 안이라, 던지면 그 틱의
// 나머지 현상금까지 같이 죽는다.
public class BountyData {

    public static final int SLOTS = 3;

    public static final class Slot {
        public String kind = "";     // hunt · supply · elite
        public String target = "";   // 엔티티/아이템 id
        public String name = "";     // 표시 이름 (한글)
        public int need;
        public int reward;
        public int have;
        public boolean done;

        boolean empty() { return kind.isEmpty() || target.isEmpty(); }
    }

    private final Slot[] slots = new Slot[SLOTS];
    private final Slot blank = new Slot();   // 범위를 벗어난 접근이 받는 값

    // 갱신 주기 번호. 「며칠째」가 아니라 「몇 번째 주기」다 — BT_EVERY 로 나눈 뒤의 값이라
    // 스크립트가 주기 길이를 바꿔도 여기 뜻은 안 변한다.
    private int cycle;

    public BountyData() {
        for (int i = 0; i < SLOTS; i++) slots[i] = new Slot();
    }

    // 1-기반 인덱스를 받는다. 스크립트가 `bt1`~`bt3` 로 세고 있었고, 그 감각을 유지한다 —
    // 이관하면서 번호 체계까지 바꾸면 「이관해서 어긋났나, 원래 그랬나」를 못 가린다.
    private Slot at(int i) {
        if (i < 1 || i > SLOTS) return blank;
        return slots[i - 1];
    }

    public int cycle() { return cycle; }
    public void setCycle(int n) { cycle = n; }

    // 새로 건다 — 진행도(have·done)는 반드시 같이 초기화된다.
    // 옛 코드는 굴리기와 초기화가 다른 루프였다. 한쪽만 돌면 **새 현상금이 「이미 완료」로 시작**한다.
    public void post(int i, String kind, String target, String name, int need, int reward) {
        if (i < 1 || i > SLOTS) return;
        Slot s = slots[i - 1];
        s.kind = kind == null ? "" : kind;
        s.target = target == null ? "" : target;
        s.name = name == null ? "" : name;
        s.need = Math.max(1, need);
        s.reward = Math.max(0, reward);
        s.have = 0;
        s.done = false;
    }

    public boolean posted(int i) { return !at(i).empty(); }
    public String kind(int i)    { return at(i).kind; }
    public String target(int i)  { return at(i).target; }
    public String name(int i)    { return at(i).name; }
    public int need(int i)       { return at(i).need; }
    public int reward(int i)     { return at(i).reward; }
    public int have(int i)       { return at(i).have; }
    public boolean done(int i)   { return at(i).done; }

    // 진행분을 더하고 **더한 뒤의 값**을 돌려준다. 옛 코드는 읽고·더하고·쓰는 세 줄이라
    // 그 사이에 다른 경로가 끼면 한쪽이 덮였다(킬 추적과 납품이 같은 칸을 만진다).
    // 완료된 칸이나 빈 칸은 안 올린다 — 걸러내는 자리가 호출부마다 흩어져 있었다.
    public int addProgress(int i, int n) {
        Slot s = at(i);
        if (s.empty() || s.done || n <= 0) return s.have;
        s.have = Math.min(s.need, s.have + n);
        return s.have;
    }

    // 완료 표시. 이미 완료였으면 false — 호출부가 보상 지급을 두 번 하지 않게 한다.
    public boolean complete(int i) {
        Slot s = at(i);
        if (s.empty() || s.done) return false;
        s.done = true;
        return true;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cycle", cycle);
        ListTag list = new ListTag();
        for (Slot s : slots) {
            CompoundTag t = new CompoundTag();
            t.putString("kind", s.kind);
            t.putString("target", s.target);
            t.putString("name", s.name);
            t.putInt("need", s.need);
            t.putInt("reward", s.reward);
            t.putInt("have", s.have);
            t.putBoolean("done", s.done);
            list.add(t);
        }
        tag.put("slots", list);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        cycle = tag.getInt("cycle");
        ListTag list = tag.getList("slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < SLOTS; i++) {
            Slot s = slots[i];
            if (i >= list.size()) { slots[i] = new Slot(); continue; }
            CompoundTag t = list.getCompound(i);
            s.kind = t.getString("kind");
            s.target = t.getString("target");
            s.name = t.getString("name");
            s.need = t.getInt("need");
            s.reward = t.getInt("reward");
            s.have = t.getInt("have");
            s.done = t.getBoolean("done");
        }
    }

    // 이관 확인용 (/lsdata).
    public String summary() {
        StringBuilder sb = new StringBuilder("주기 ").append(cycle);
        int live = 0;
        for (int i = 1; i <= SLOTS; i++) if (posted(i)) live++;
        if (live == 0) return sb.append(" · 게시 없음").toString();
        sb.append(" · ");
        for (int i = 1; i <= SLOTS; i++) {
            if (!posted(i)) continue;
            sb.append(name(i)).append(' ')
              .append(done(i) ? "완료" : have(i) + "/" + need(i)).append(' ');
        }
        return sb.toString().trim();
    }
}
