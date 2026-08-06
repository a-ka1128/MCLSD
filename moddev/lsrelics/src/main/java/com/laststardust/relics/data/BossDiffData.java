package com.laststardust.relics.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

// 보스 난이도 라이브 오버라이드 (이관 5단계, docs/ARCHITECTURE.md)
//
// ── 여기 있는 건 2층뿐이다 ──
// 값의 출처는 원래 2층 구조였고 그건 안 바꾼다:
//   1층 `ls_config.js`  — 영구 기본값. kubejs 폴더라 **월드 리셋에도 남는다.**
//   2층 여기(LSData)    — `/bossdiff` 로 건 라이브 오버라이드. **월드와 함께 사라진다(정상).**
// 1층을 모드로 끌고 오면 튜닝 값을 고칠 때마다 재빌드가 필요해진다. `/reload` 로 고치는 값은
// 스크립트가 맞다 — 4단계에서 웨이브 구성을 안 옮긴 것과 같은 판단이다.
//
// ── 그래서 왜 옮기나 ──
// 이 섹션은 «키가 새는 것»을 막으려고 옮기는 게 아니다(`bd_*` 는 `ls_bossdiff.js` 밖으로 안 나간다).
// 옮기는 이유는 **키 이름을 문자열로 조립하고 있었다**는 것이다:
//     p.putInt('bd_' + id + '_hp', ...)
// 보스 id 에 콜론이 들어가는데 그게 NBT 키가 된다. 오타 하나면 조용히 다른 키에 쓰고,
// 읽는 쪽은 «오버라이드 없음(0)» 으로 읽는다 — 예외도 로그도 없다. 명예 보드 사고와 같은 모양이다.
// 여기서는 보스별 값이 **한 덩어리(Entry)** 라 그런 어긋남이 생길 자리가 없다.
//
// ── 0 의 뜻 ──
// **0 = 오버라이드 없음.** 스크립트가 그렇게 읽고 있었고 그대로 지킨다. 그래서 «체력 0인 보스» 는
// 표현할 수 없는데, 그건 어차피 즉사라 의미가 없다. 하한 1 을 여기서 건다 — 호출부에 맡기면
// 한 곳이 빠뜨렸을 때 그 보스만 조용히 다르게 동작한다.
public class BossDiffData {

    // 전역 배율(%) — 0 이면 ls_config.js 의 globalHp/globalDmg 를 쓴다.
    private int globalHp;
    private int globalDmg;

    // 보스별. 셋 다 0 이면 항목 자체를 지운다 — 안 그러면 «전부 해제했는데 목록엔 남아 있는»
    // 상태가 되고, /bossdiff list 가 아무것도 아닌 줄을 계속 뿌린다.
    public static final class Entry {
        public int hp;    // % (0 = 없음)
        public int dmg;   // % (0 = 없음)
        public int abs;   // 절대 체력 (0 = 없음, %보다 우선)

        boolean empty() { return hp <= 0 && dmg <= 0 && abs <= 0; }
    }

    // 순서를 지키는 이유: /bossdiff list 와 export 의 출력이 매번 같은 순서로 나와야
    // «뭐가 바뀌었나»를 눈으로 비교할 수 있다.
    private final Map<String, Entry> perBoss = new LinkedHashMap<>();

    // ── 전역 ──
    public int globalHp() { return globalHp; }
    public int globalDmg() { return globalDmg; }

    // 0 을 넣으면 해제(= 파일 값으로 복귀). 그 외에는 1 이 하한.
    public void setGlobal(int hp, int dmg) {
        globalHp = clampPct(hp);
        globalDmg = clampPct(dmg);
    }

    private static int clampPct(int v) {
        return v <= 0 ? 0 : Math.max(1, v);
    }

    // ── 보스별 ──
    public int hp(String id)  { Entry e = perBoss.get(id); return e == null ? 0 : e.hp; }
    public int dmg(String id) { Entry e = perBoss.get(id); return e == null ? 0 : e.dmg; }
    public int abs(String id) { Entry e = perBoss.get(id); return e == null ? 0 : e.abs; }

    public void setHp(String id, int v)  { edit(id, e -> e.hp = clampPct(v)); }
    public void setDmg(String id, int v) { edit(id, e -> e.dmg = clampPct(v)); }
    public void setAbs(String id, int v) { edit(id, e -> e.abs = Math.max(0, v)); }

    private void edit(String id, java.util.function.Consumer<Entry> fn) {
        if (id == null || id.isEmpty()) return;
        Entry e = perBoss.computeIfAbsent(id, k -> new Entry());
        fn.accept(e);
        if (e.empty()) perBoss.remove(id);
    }

    // 오버라이드가 걸린 보스만. 목록 순회를 스크립트의 BOSS_LIST(33종) 전체로 돌 필요가 없다.
    public List<String> overridden() {
        return new ArrayList<>(perBoss.keySet());
    }

    // 라이브 오버라이드만 지운다 → ls_config.js 의 파일 값으로 되돌아간다.
    public void reset() {
        globalHp = 0;
        globalDmg = 0;
        perBoss.clear();
    }

    public boolean hasGlobalOverride() { return globalHp > 0 || globalDmg > 0; }

    // ── 저장 ──
    // 보스 id 를 NBT 키로 쓰지 않는다. 콜론이 든 문자열을 키로 삼으면 나중에 경로 문법(`a.b`)을
    // 쓰는 도구에서 걸린다. 대신 항목마다 id 를 값으로 들고 리스트로 담는다.
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("globalHp", globalHp);
        tag.putInt("globalDmg", globalDmg);

        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (Map.Entry<String, Entry> en : perBoss.entrySet()) {
            CompoundTag b = new CompoundTag();
            b.putString("id", en.getKey());
            b.putInt("hp", en.getValue().hp);
            b.putInt("dmg", en.getValue().dmg);
            b.putInt("abs", en.getValue().abs);
            list.add(b);
        }
        tag.put("perBoss", list);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        globalHp = clampPct(tag.getInt("globalHp"));
        globalDmg = clampPct(tag.getInt("globalDmg"));
        perBoss.clear();
        net.minecraft.nbt.ListTag list = tag.getList("perBoss", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            String id = b.getString("id");
            if (id.isEmpty()) continue;
            setHp(id, b.getInt("hp"));
            setDmg(id, b.getInt("dmg"));
            setAbs(id, b.getInt("abs"));
        }
    }

    // 이관 확인용 (/lsdata).
    public String summary() {
        if (!hasGlobalOverride() && perBoss.isEmpty()) return "없음 (ls_config.js 값 사용)";
        return "전역 " + (globalHp > 0 ? globalHp + "%" : "-") + "/" + (globalDmg > 0 ? globalDmg + "%" : "-")
             + " · 개별 " + perBoss.size() + "종";
    }
}
