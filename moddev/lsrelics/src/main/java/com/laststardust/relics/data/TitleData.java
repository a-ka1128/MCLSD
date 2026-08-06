package com.laststardust.relics.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

// 칭호 — 보유 목록 + 활성 칭호 (이관 5단계, docs/ARCHITECTURE.md)
//
// ── 왜 옮기는가 ──
// `titles_<이름>` 은 CSV 문자열 하나였다. 그래서 두 가지가 조용히 가능했다:
//   · 같은 칭호가 두 번 들어간다 — `ttGrant` 가 중복을 걸렀지만, 거르는 곳이 하나뿐이라
//     다른 경로로 한 줄만 쓰면 끝이었다
//   · 이름에 쉼표가 들어가면 목록이 쪼개진다 (마인크래프트 이름엔 못 들어가지만, 칭호 slug 는
//     우리가 만드는 값이라 언젠가 들어갈 수 있다)
// 여기서는 Set 이라 중복이 존재할 수 없고, 저장도 CSV 가 아니라 문자열 리스트다.
//
// ── 활성 칭호의 불변식 ──
// **활성 칭호는 반드시 보유 목록 안에 있어야 한다.** 옛 코드는 이걸 `/title set` 한 곳에서만
// 확인했다. 그래서 칭호를 뺏는 경로가 생기면 «가지고 있지 않은 칭호를 달고 있는» 상태가 되고,
// 그건 이름표에는 보이는데 `/title` 목록엔 없는 유령이 된다. 여기서 걸면 그 자리가 없어진다.
//
// ── 카탈로그는 여기 없다 ──
// 칭호 12종의 이름·색은 `ls_title.js` 에 남긴다. 문구는 `/reload` 로 고치는 값이고,
// 모드로 끌고 오면 오탈자 하나에 재빌드가 필요해진다. 여기가 갖는 건 **누가 뭘 가졌나** 뿐이다.
public class TitleData {

    // 이름 -> 보유 칭호. LinkedHashSet 이라 획득 순서가 유지된다 — `/title` 목록이 매번
    // 같은 순서로 나와야 「새로 뭐가 들어왔나」를 눈으로 찾을 수 있다.
    private final Map<String, Set<String>> owned = new TreeMap<>();
    private final Map<String, String> active = new TreeMap<>();

    public List<String> owned(String name) {
        Set<String> s = owned.get(name);
        return s == null ? List.of() : new ArrayList<>(s);
    }

    public boolean has(String name, String key) {
        Set<String> s = owned.get(name);
        return s != null && s.contains(key);
    }

    // 이미 있으면 false — 호출부가 「새로 받았나」로 연출(방송·소리)을 가른다.
    // 옛 코드는 `ttGrant` 안에서 직접 방송했는데, 그러면 조용히 주는 경로를 만들 수 없다.
    public boolean grant(String name, String key) {
        if (name == null || name.isEmpty() || key == null || key.isEmpty()) return false;
        Set<String> s = owned.computeIfAbsent(name, k -> new LinkedHashSet<>());
        if (!s.add(key)) return false;
        // 첫 칭호는 자동 활성 — 받아도 아무것도 안 보이면 받은 줄 모른다.
        active.putIfAbsent(name, key);
        return true;
    }

    public String active(String name) {
        return active.getOrDefault(name, "");
    }

    // 보유하지 않은 칭호는 활성이 될 수 없다. 빈 문자열은 「표시 안 함」이라 항상 통과.
    public boolean setActive(String name, String key) {
        if (name == null || name.isEmpty()) return false;
        if (key == null || key.isEmpty()) { active.remove(name); return true; }
        if (!has(name, key)) return false;
        active.put(name, key);
        return true;
    }

    // 활성 칭호를 단 사람들 — 재시작 후 팀 prefix 를 다시 붙이려면 목록이 필요하다.
    // 옛 코드는 접속 이벤트에서 한 명씩만 복구해서, **접속하지 않은 사람의 이름표는
    // 다른 사람 눈에 계속 빈 채로** 있었다.
    public List<String> namesWithActive() {
        return new ArrayList<>(active.keySet());
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();

        ListTag list = new ListTag();
        for (Map.Entry<String, Set<String>> e : owned.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putString("name", e.getKey());
            ListTag keys = new ListTag();
            for (String k : e.getValue()) keys.add(net.minecraft.nbt.StringTag.valueOf(k));
            t.put("keys", keys);
            String a = active.getOrDefault(e.getKey(), "");
            if (!a.isEmpty()) t.putString("active", a);
            list.add(t);
        }
        tag.put("players", list);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        owned.clear();
        active.clear();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            String name = t.getString("name");
            if (name.isEmpty()) continue;
            ListTag keys = t.getList("keys", Tag.TAG_STRING);
            for (int j = 0; j < keys.size(); j++) grant(name, keys.getString(j));
            // grant() 가 첫 칭호를 자동 활성으로 잡아두므로, 저장된 값으로 덮어쓴다.
            // setActive 를 쓰는 이유는 불변식을 로드에서도 통과시키기 위해서다 —
            // 저장 파일을 손으로 고쳐 없는 칭호를 넣어도 여기서 걸린다.
            setActive(name, t.getString("active"));
        }
    }

    // 이관 확인용 (/lsdata).
    public String summary() {
        if (owned.isEmpty()) return "없음";
        int total = 0;
        for (Set<String> s : owned.values()) total += s.size();
        return owned.size() + "명 · 칭호 " + total + "개 · 착용 " + active.size() + "명";
    }
}
