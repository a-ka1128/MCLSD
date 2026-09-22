package com.laststardust.relics.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

// 마을(성역 재건) 상태. LSData 의 한 섹션.
//
// ── 창고형(A안) ──
// 트랙마다 **진짜 보관함**을 둔다. 플레이어가 슬롯에 자원을 올려두면 거기 그대로 남고,
// 서버를 껐다 켜도 유지되며, 잘못 넣었으면 도로 꺼낼 수 있다.
// "여럿이 며칠에 걸쳐 조금씩 채워 완성한다"가 이 시스템의 본질이라 투입구형(넣는 즉시 소멸)은
// 그 감각을 못 준다 — 그건 버튼에 슬롯 껍데기를 씌운 것에 가깝다.
public class TownData {

    private final Map<String, Integer> levels = new HashMap<>();
    private final Map<String, SimpleContainer> deposits = new LinkedHashMap<>();
    private final Map<String, Integer> contributions = new LinkedHashMap<>();
    private final Map<String, Boolean> flags = new HashMap<>();
    private int treasury;

    public TownData() {
        for (TownCatalog.Track t : TownCatalog.ALL) {
            deposits.put(t.key(), new SimpleContainer(TownCatalog.DEPOSIT_SLOTS));
        }
    }

    // ── 금고 ──
    public int treasury() { return treasury; }
    public void setTreasury(int v) { treasury = Math.max(0, v); }
    public void addTreasury(int delta) { setTreasury(treasury + delta); }
    public boolean spend(int amount) {
        if (amount <= 0 || treasury < amount) return false;
        treasury -= amount;
        return true;
    }

    // ── 단계 ──
    public int level(String track) { return levels.getOrDefault(track, 0); }
    public void setLevel(String track, int v) { levels.put(track, Math.max(0, v)); }

    // ── 월드에 «지어진» 단계 ──
    // 레벨(levels)과 따로 둔다. 레벨은 올랐는데 아직 구조물을 못 세운 상태가 있기 때문이다
    // (성역이 안 정해졌거나, 앵커를 안 잡았거나, 청크가 안 열렸거나).
    // 둘을 한 값으로 합치면 「레벨은 3인데 건물은 2단계」인 상태를 표현할 방법이 없고,
    // 그러면 다음 reconcile 이 «이미 지었다»고 착각해 영영 안 세운다.
    private final Map<String, Integer> built = new HashMap<>();

    public int builtLevel(String track) { return built.getOrDefault(track, 0); }
    public void setBuiltLevel(String track, int v) { built.put(track, Math.max(0, v)); }

    // ── 구조물 앵커 ──
    // 성역 기준 «상대» 좌표다. 절대 좌표로 두면 성역을 옮기는 날 건물만 제자리에 남는다.
    // 값이 없으면 «아직 안 정함» — 그때는 아무것도 안 세운다(엉뚱한 데 짓느니 안 짓는다).
    private final Map<String, int[]> anchors = new HashMap<>();

    public int[] anchor(String track) { return anchors.get(track); }
    public void setAnchor(String track, int dx, int dy, int dz) {
        anchors.put(track, new int[] {dx, dy, dz});
    }

    // ── 보관함 ──
    public SimpleContainer deposit(String track) {
        return deposits.computeIfAbsent(track, k -> new SimpleContainer(TownCatalog.DEPOSIT_SLOTS));
    }

    // 보관함에 들어 있는 '요구 자원 하나'의 개수.
    // 다른 아이템이 섞여 있어도 세지 않는다 — 슬롯 자체가 필요한 것만 받도록 막지만,
    // 레벨이 오르면 요구가 바뀌므로 남아 있던 이전 자원은 여기서 자연히 0으로 잡힌다.
    // (그 자원은 사라지지 않는다. 보관함에 그대로 남아 있어서 도로 꺼낼 수 있다.)
    public int depositCount(String track, TownCatalog.Req req) {
        if (req == null) return 0;
        int n = 0;
        SimpleContainer c = deposit(track);
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack st = c.getItem(i);
            if (req.matches(st)) n += st.getCount();
        }
        return n;
    }

    /** 이번 레벨의 요구가 <b>전부</b> 채워졌는가. 하나라도 모자라면 false. */
    public boolean depositSatisfied(String track, TownCatalog.Level need) {
        if (need == null) return false;
        for (TownCatalog.Req r : need.reqs()) {
            if (depositCount(track, r) < r.count()) return false;
        }
        return true;
    }

    /** 아직 모자란 요구 중 첫 번째. 전부 채워졌으면 null — 안내 메시지에 쓴다. */
    public TownCatalog.Req firstMissing(String track, TownCatalog.Level need) {
        if (need == null) return null;
        for (TownCatalog.Req r : need.reqs()) {
            if (depositCount(track, r) < r.count()) return r;
        }
        return null;
    }

    // 완성 시 요구 수량만큼만 소모한다. 남는 건 보관함에 그대로 둔다(플레이어가 도로 꺼낼 수 있게).
    public void consumeDeposit(String track, TownCatalog.Level need) {
        if (need == null) return;
        SimpleContainer c = deposit(track);
        for (TownCatalog.Req req : need.reqs()) {
            int left = req.count();
            for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
                ItemStack st = c.getItem(i);
                if (!req.matches(st)) continue;
                int take = Math.min(st.getCount(), left);
                st.shrink(take);
                left -= take;
                if (st.isEmpty()) c.setItem(i, ItemStack.EMPTY);
            }
        }
        c.setChanged();
    }

    // ── 기여도 ──
    public void addContribution(String name, int pts) {
        if (pts <= 0) return;
        contributions.merge(name, pts, Integer::sum);
    }
    public List<Map.Entry<String, Integer>> topContributors(int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(contributions.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    // ── 구역 해금 플래그 (상인 광장·기록관 등) ──
    public boolean flag(String key) { return flags.getOrDefault(key, false); }
    public void setFlag(String key, boolean v) { flags.put(key, v); }

    // ── 저장 ──
    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("treasury", treasury);

        CompoundTag lv = new CompoundTag();
        levels.forEach(lv::putInt);
        tag.put("levels", lv);

        CompoundTag bl = new CompoundTag();
        built.forEach(bl::putInt);
        tag.put("built", bl);

        // 앵커는 int 배열 셋으로 적는다. 문자열로 말아 넣으면(「12,0,-8」) 파싱이 실패했을 때
        // 조용히 «앵커 없음» 이 되어 건물이 안 서는데 이유는 아무 데도 안 남는다.
        CompoundTag an = new CompoundTag();
        anchors.forEach((k, v) -> an.putIntArray(k, v));
        tag.put("anchors", an);

        CompoundTag dep = new CompoundTag();
        deposits.forEach((k, c) -> {
            ListTag list = new ListTag();
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack st = c.getItem(i);
                if (st.isEmpty()) continue;
                CompoundTag slot = new CompoundTag();
                slot.putInt("slot", i);
                // 아이템은 컴포넌트까지 포함해 저장한다 — 인챈트·이름 붙은 것도 그대로 보관됐다가 돌아간다.
                slot.put("item", st.save(registries));
                list.add(slot);
            }
            dep.put(k, list);
        });
        tag.put("deposits", dep);

        CompoundTag con = new CompoundTag();
        contributions.forEach(con::putInt);
        tag.put("contrib", con);

        CompoundTag fl = new CompoundTag();
        flags.forEach(fl::putBoolean);
        tag.put("flags", fl);
        return tag;
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        treasury = tag.getInt("treasury");

        levels.clear();
        CompoundTag lv = tag.getCompound("levels");
        for (String k : lv.getAllKeys()) levels.put(k, lv.getInt(k));

        built.clear();
        CompoundTag bl = tag.getCompound("built");
        for (String k : bl.getAllKeys()) built.put(k, bl.getInt(k));

        anchors.clear();
        CompoundTag an = tag.getCompound("anchors");
        for (String k : an.getAllKeys()) {
            int[] v = an.getIntArray(k);
            if (v.length == 3) anchors.put(k, v);   // 길이가 다르면 버린다 — 반쯤 읽은 좌표가 더 나쁘다
        }

        CompoundTag dep = tag.getCompound("deposits");
        for (String k : dep.getAllKeys()) {
            SimpleContainer c = deposit(k);
            c.clearContent();
            ListTag list = dep.getList(k, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag slot = list.getCompound(i);
                int idx = slot.getInt("slot");
                if (idx < 0 || idx >= c.getContainerSize()) continue;
                ItemStack.parse(registries, slot.getCompound("item")).ifPresent(st -> c.setItem(idx, st));
            }
        }

        contributions.clear();
        CompoundTag con = tag.getCompound("contrib");
        for (String k : con.getAllKeys()) contributions.put(k, con.getInt(k));

        flags.clear();
        CompoundTag fl = tag.getCompound("flags");
        for (String k : fl.getAllKeys()) flags.put(k, fl.getBoolean(k));
    }
}
