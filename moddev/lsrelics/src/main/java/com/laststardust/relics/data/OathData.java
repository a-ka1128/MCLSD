package com.laststardust.relics.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * <h2>균열 서약 — 자발적 난이도 상향</h2>
 *
 * <p>열쇠를 쓰기 <b>전에</b> 어픽스를 걸어 둔다. 관문이 열리는 순간 그 설정이 그 판에
 * 통째로 복사되고, 도는 동안 몹이 세지고 보상이 늘어난다. WoW 의 M+ 열쇠 등급과 같은
 * 자리다 — <b>어려워지는 쪽을 내가 고른다</b>는 게 이 장치의 전부다.
 *
 * <p>── 왜 아이템이 아니라 사람에 붙나 ──
 * 「입장권 어픽스」라는 이름대로면 열쇠 아이템의 NBT 에 새기는 게 자연스럽다. 그런데
 * <b>이 저장소에서 KubeJS 가 아이템 NBT 를 다룬 전례가 하나도 없다</b> — 1.21 에서
 * NBT 가 데이터 컴포넌트로 갈렸고, KubeJS 쪽 접근법이 버전마다 달라 확인 없이 쓰면
 * 조용히 안 붙는다. {@code ARCHITECTURE.md} 원칙 1(데이터는 Java 소유)도 같은 방향이다.
 * 별의 축복이 <b>아이템이 아니라 플레이어에 저장</b>되는 것과 같은 선택이고, 같은 이득을
 * 본다 — 열쇠를 잃어버리거나 남에게 줘도 서약이 어긋나지 않는다.
 *
 * <p>── 카탈로그는 여기 없다 ──
 * 어픽스의 이름·배율·보상은 {@code ls_keys.js} 에 있다. {@code /reload} 로 고치는 값이다.
 * 여기가 아는 건 «누가 무엇을 걸어 뒀나» 뿐이고, 그래서 <b>모르는 id 도 그대로 저장한다</b> —
 * 검증을 양쪽에 두면 스크립트가 어픽스를 하나 늘릴 때마다 자바를 같이 고쳐야 한다.
 */
public class OathData {

    /** 한 사람이 동시에 걸 수 있는 서약 수. 셋을 넘기면 배율이 곱해져 감당이 안 된다. */
    public static final int MAX = 3;

    private final Map<String, Set<String>> oaths = new HashMap<>();

    private Set<String> set(String name) {
        // TreeSet — 표시 순서가 매번 같아야 「내가 뭘 걸었더라」가 눈에 익는다.
        return oaths.computeIfAbsent(name == null ? "" : name, k -> new TreeSet<>());
    }

    /** 걸린 서약 목록. 순서는 항상 같다. */
    public String[] list(String name) {
        return set(name).toArray(new String[0]);
    }

    public int count(String name) { return set(name).size(); }

    public boolean has(String name, String id) { return set(name).contains(id); }

    /** 건다. 이미 걸렸거나 {@link #MAX} 를 넘으면 false. */
    public boolean add(String name, String id) {
        if (id == null || id.isEmpty()) return false;
        Set<String> s = set(name);
        if (s.contains(id) || s.size() >= MAX) return false;
        s.add(id);
        return true;
    }

    /** 뗀다. 안 걸려 있었으면 false. */
    public boolean remove(String name, String id) {
        return set(name).remove(id);
    }

    public void clear(String name) {
        if (name == null || name.isEmpty()) oaths.clear();
        else set(name).clear();
    }

    // ── 저장 ──
    //
    // ⚠️ 이어붙이지 않는다. 옛 현상금이 `'hunt|minecraft:zombie|좀비|25|60'` 이었다가
    //    이름에 `|` 하나로 조용히 사라졌던 일(BountyData 머리말)을 여기서 반복하지 않는다.
    //    어픽스 id 는 우리가 계속 늘리는 값이라 같은 시간 문제다.
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        oaths.clear();
        CompoundTag p = tag.getCompound("players");
        for (String name : p.getAllKeys()) {
            Set<String> s = new TreeSet<>();
            ListTag l = p.getList(name, Tag.TAG_STRING);
            for (int i = 0; i < l.size(); i++) s.add(l.getString(i));
            if (!s.isEmpty()) oaths.put(name, s);
        }
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        CompoundTag p = new CompoundTag();
        oaths.forEach((name, s) -> {
            if (s.isEmpty()) return;
            ListTag l = new ListTag();
            for (String id : s) l.add(StringTag.valueOf(id));
            p.put(name, l);
        });
        tag.put("players", p);
        return tag;
    }
}
