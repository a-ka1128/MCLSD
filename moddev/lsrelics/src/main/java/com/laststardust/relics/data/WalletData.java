package com.laststardust.relics.data;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * <h2>개인 지갑 — Ducat</h2>
 *
 * <p>{@code DESIGN.md} P2 의 «이중 지갑» 중 개인 쪽. 여태 구현이 없어서 Ducat 은
 * <b>공동 금고 하나뿐</b>이었다({@code TownData.treasury}).
 *
 * <p>── 왜 갈라야 하나 ──
 * 공동 금고는 <b>마을 발전·방어</b>에 쓰는 돈이다. 거기서 개인 치장을 사면
 * 「누가 마을 돈으로 모자를 샀다」가 되고, 그 순간 <b>협동이 아니라 눈치 게임</b>이 된다.
 * 설계가 처음부터 둘로 나눈 이유가 그거다 —
 * 진짜 파워 스케일링은 금고(협동), 개인 지갑은 치장·편의(수평).
 *
 * <p>── 열쇠는 «계정 이름» ──
 * {@code LSData} 의 다른 섹션과 같다. 별명({@code Nick})이 아니다 —
 * 별명을 바꾸면 잔액이 사라진다.
 *
 * <p>⚠️ 잔액은 음수가 안 된다. 여기서 막는다 — 호출부에 맡기면 한 곳이 빠뜨렸을 때
 * 조용히 마이너스가 되고, 그러면 「사면 살수록 돈이 는다」가 된다.
 */
public class WalletData {

    private final Map<String, Integer> balance = new LinkedHashMap<>();

    public int get(String name) {
        return balance.getOrDefault(name, 0);
    }

    public void set(String name, int v) {
        balance.put(name, Math.max(0, v));
    }

    public void add(String name, int delta) {
        if (delta == 0) return;
        set(name, get(name) + delta);
    }

    /** 낼 수 있으면 내고 {@code true}. 모자라면 <b>아무것도 안 하고</b> {@code false}. */
    public boolean spend(String name, int amount) {
        if (amount <= 0) return false;
        int have = get(name);
        if (have < amount) return false;
        set(name, have - amount);
        return true;
    }

    /** 순위표용. 잔액이 0 인 사람은 안 넣는다. */
    public java.util.List<Map.Entry<String, Integer>> top(int limit) {
        var list = new java.util.ArrayList<>(balance.entrySet());
        list.removeIf(e -> e.getValue() <= 0);
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        balance.forEach(tag::putInt);
        return tag;
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        balance.clear();
        for (String k : tag.getAllKeys()) balance.put(k, tag.getInt(k));
    }
}
