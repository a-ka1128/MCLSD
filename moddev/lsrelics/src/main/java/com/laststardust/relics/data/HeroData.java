package com.laststardust.relics.data;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

// 사람에 붙는 성장 상태 — 가호(직업) · 유물 수령 · 각성 성급 · 직업별 제단 좌표.
//
// ── 왜 여기로 옮기는가 (이관 3단계, docs/ARCHITECTURE.md) ──
// 이 셋은 RPG 성장의 뿌리인데 여태 `level.persistentData` 에 `fate_<이름>` · `relic_<이름>` ·
// `star_<이름>` 이라는 - 문자열로 조립한 키 - 로 흩어져 있었다. 그래서 이런 게 가능했다:
//   · 오타 하나로 다른 키를 읽어도 예외 없이 «없음/0» 이 나온다
//   · 쓰는 쪽만 옮기고 읽는 쪽을 두고 오면 조용히 기본값을 읽는다
//     (명예 보드가 몇 주 동안 «CSV 첫 사람, 0점»을 1위로 내보내던 사고가 정확히 이것이다)
// 여기로 오면 오타는 컴파일 오류가 되고, 상한(1~5)도 한 곳에서만 걸린다.
//
// ── 왜 UUID 가 아니라 이름을 키로 쓰나 ──
// 이름이 바뀌면 데이터가 고아가 된다 — UUID 가 옳다. 그런데 지금 옮기는 건 - 이미 있는 -
// `fate_<이름>` 체계이고, 스크립트 쪽도 이름만 쥐고 있다. 키 체계까지 같이 바꾸면
// «이관해서 깨졌나, 원래 그랬나»를 가릴 수 없게 된다.
// 이름 그대로 옮기고, UUID 전환은 이관이 다 끝난 뒤 따로 한다.
public class HeroData {

    public static final int MAX_STAR = 5;

    private final Map<String, String> fate = new HashMap<>();     // 이름 -> 가호 키
    private final Map<String, Boolean> gotRelic = new HashMap<>(); // 이름 -> 유물 수령 여부
    private final Map<String, Integer> star = new HashMap<>();     // 이름 -> 성급 (0 = 아직 없음)
    private final Map<String, BlockPos> altar = new HashMap<>();   // 가호 키 -> 제단 좌표

    // ── 가호 ──
    // 없으면 빈 문자열. null 을 돌려주면 KubeJS 쪽에서 `String(null)` 이 "null" 이 되어
    // 그 문자열이 그대로 가호 키처럼 돌아다닌다 — 실제로 겪을 수 있는 종류의 사고라 막는다.
    public String fate(String name) {
        return fate.getOrDefault(name, "");
    }

    public void setFate(String name, String key) {
        if (key == null || key.isEmpty()) fate.remove(name);
        else fate.put(name, key);
    }

    // 이 가호를 이미 가진 사람 — 없으면 "".
    // 한 서버에 같은 직업이 둘일 수 없다(8종·8인이라 겹치면 누군가는 못 고른다).
    // 스크립트가 키 목록을 직접 훑던 걸 여기로 옮겼다 — 그쪽은 `fate_` 접두사를 문자열로
    // 잘라내고 있어서, 키 형식이 바뀌면 조용히 아무도 못 찾는 상태가 된다.
    public String ownerOf(String fateKey, String exceptName) {
        if (fateKey == null || fateKey.isEmpty()) return "";
        for (Map.Entry<String, String> e : fate.entrySet()) {
            if (exceptName != null && exceptName.equals(e.getKey())) continue;
            if (fateKey.equals(e.getValue())) return e.getKey();
        }
        return "";
    }

    // ── 유물 수령 ──
    public boolean hasRelic(String name) {
        return gotRelic.getOrDefault(name, Boolean.FALSE);
    }

    public void setHasRelic(String name, boolean v) {
        if (v) gotRelic.put(name, Boolean.TRUE);
        else gotRelic.remove(name);
    }

    // ── 각성 성급 ──
    // 0 = 아직 유물이 없다. 1~5 = 성급. 그 구분이 중요해서 «없으면 1» 로 뭉개지 않는다 —
    // 스크립트의 asStar() 가 표시용으로 1 을 깔지만, 장부는 «없음»을 그대로 들고 있어야
    // «유물은 받았는데 각성이 0» 같은 어긋난 상태를 나중에 알아볼 수 있다.
    public int star(String name) {
        return star.getOrDefault(name, 0);
    }

    // 상한을 여기서 건다. 호출부에 맡기면 한 곳이 빠뜨렸을 때 조용히 어긋난다
    // (LSData.setProgress 와 같은 이유).
    public void setStar(String name, int n) {
        int v = Math.max(0, Math.min(MAX_STAR, n));
        if (v == 0) star.remove(name);
        else star.put(name, v);
    }

    // ── 직업별 제단 ──
    // 사람이 아니라 - 가호 - 에 붙는다. 여덟 직업이 각자 자기 제단에서 유물을 받는다.
    public boolean hasAltar(String fateKey) {
        return altar.containsKey(fateKey);
    }

    public BlockPos altar(String fateKey) {
        return altar.getOrDefault(fateKey, BlockPos.ZERO);
    }

    public void setAltar(String fateKey, int x, int y, int z) {
        altar.put(fateKey, new BlockPos(x, y, z));
    }

    // ※ clearAltar 는 2026-07-31 삭제했다 — 부르는 곳이 없었다. 제단은 옮기지 않고 «다시 놓는»
    //   물건이라 setAltar 하나로 끝난다. 지우는 경로가 필요해지면 그때 만든다.

    // ── 저장 ──
    // TreeMap 으로 한 번 감싸 이름 순으로 쓴다. 저장 파일을 사람이 열어볼 때
    // 순서가 매번 달라지면 «뭐가 바뀌었나»를 눈으로 못 본다.
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();

        CompoundTag f = new CompoundTag();
        new TreeMap<>(fate).forEach(f::putString);
        tag.put("fate", f);

        CompoundTag r = new CompoundTag();
        new TreeMap<>(gotRelic).forEach(r::putBoolean);
        tag.put("relic", r);

        CompoundTag s = new CompoundTag();
        new TreeMap<>(star).forEach(s::putInt);
        tag.put("star", s);

        CompoundTag a = new CompoundTag();
        new TreeMap<>(altar).forEach((k, p) -> {
            CompoundTag one = new CompoundTag();
            one.putInt("x", p.getX());
            one.putInt("y", p.getY());
            one.putInt("z", p.getZ());
            a.put(k, one);
        });
        tag.put("altar", a);

        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        fate.clear();
        gotRelic.clear();
        star.clear();
        altar.clear();

        CompoundTag f = tag.getCompound("fate");
        for (String k : f.getAllKeys()) fate.put(k, f.getString(k));

        CompoundTag r = tag.getCompound("relic");
        for (String k : r.getAllKeys()) if (r.getBoolean(k)) gotRelic.put(k, Boolean.TRUE);

        CompoundTag s = tag.getCompound("star");
        for (String k : s.getAllKeys()) setStar(k, s.getInt(k));

        CompoundTag a = tag.getCompound("altar");
        for (String k : a.getAllKeys()) {
            CompoundTag one = a.getCompound(k);
            altar.put(k, new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z")));
        }
    }

    // 이관 확인용 — 「옮겨는 왔나」를 한 줄로 본다.
    public String summary() {
        return "가호 " + fate.size() + "명 · 유물 " + gotRelic.size() + "명 · 각성 " + star.size()
             + "명 · 제단 " + altar.size() + "곳";
    }
}
