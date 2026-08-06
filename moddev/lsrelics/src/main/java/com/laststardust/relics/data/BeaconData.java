package com.laststardust.relics.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

// 정화 봉화 — 이름과 좌표 (이관 5단계, docs/ARCHITECTURE.md)
//
// ── 왜 옮기는가: 이름 목록과 좌표가 따로 살았다 ──
// 옛 저장은 `pb_names`(CSV) 하나와 `pb_<이름>_x/y/z` 세 개가 **서로 모르는 사이**였다.
// 그래서 `/purifier remove` 가 CSV 에서 이름만 빼고 좌표 셋은 그대로 남겼다 — 지워진 봉화의
// 좌표가 월드 세이브에 영원히 쌓인다. 같은 이름으로 다시 세우면 **옛 좌표를 덮어쓰기 전까지
// 잠깐 남의 자리가 보이는** 상태도 된다.
// 여기서는 이름과 좌표가 한 항목이다. 지우면 같이 사라지고, 따로 남을 방법이 없다.
//
// ── 파일 경계를 넘는 값이다 ──
// 봉화 **개수**를 `ls_siege.js`(위협 하한)와 `ls_hope.js`(희망 게이지)가 각자 `pb_names` 를
// 직접 읽어 세고 있었다. 4단계에서 겪은 그대로다 — 쓰는 쪽만 옮기면 두 곳이 조용히 0 을 읽고,
// **위협 하한 완화가 사라지고 희망 게이지가 봉화를 못 본다.** 오류는 안 난다.
// 그래서 이 이관의 핵심 작업도 자바가 아니라 그 둘을 같이 옮기는 것이다.
public class BeaconData {

    // 이름 -> 좌표. 등록 순서를 지킨다 — `/purifier` 목록이 매번 같은 순서로 나와야
    // 「어느 게 새로 생겼나」를 눈으로 찾을 수 있다.
    private final Map<String, BlockPos> beacons = new LinkedHashMap<>();

    public int count() { return beacons.size(); }

    public List<String> names() { return new ArrayList<>(beacons.keySet()); }

    public boolean has(String name) { return beacons.containsKey(name); }

    // 좌표는 없으면 null 이 아니라 원점을 준다 — 스크립트가 셋을 따로 읽으므로
    // null 이 오면 세 번 다 터진다. 호출 전에 has() 로 거르는 게 정상 경로다.
    public BlockPos pos(String name) {
        return beacons.getOrDefault(name, BlockPos.ZERO);
    }

    // 이미 있는 이름이면 false — 덮어쓰지 않는다. 옛 코드도 거절했지만, 거절하는 자리가
    // 등록 함수 한 곳뿐이라 다른 경로가 생기면 조용히 덮였을 것이다.
    public boolean add(String name, int x, int y, int z) {
        if (name == null || name.isEmpty() || beacons.containsKey(name)) return false;
        beacons.put(name, new BlockPos(x, y, z));
        return true;
    }

    public boolean remove(String name) {
        return beacons.remove(name) != null;
    }

    // 이 좌표에서 가장 가까운 봉화까지의 거리(수평). 봉화가 없으면 -1.
    // 스크립트가 목록을 돌며 직접 재던 계산이다. 여기로 온 이유는 «최소 거리 규칙»이 봉화의
    // 불변식이기 때문이다 — 재는 곳이 흩어지면 규칙도 흩어진다.
    // ※ 거리만 주고 **판정(96m)은 스크립트가 한다.** PB_MIN_DIST 는 `/reload` 로 만지는 튜닝 값이다.
    public double nearestDistance(int x, int z) {
        double best = -1;
        for (BlockPos p : beacons.values()) {
            double dx = x - p.getX(), dz = z - p.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (best < 0 || d < best) best = d;
        }
        return best;
    }

    public String nearestName(int x, int z) {
        String bestName = "";
        double best = -1;
        for (Map.Entry<String, BlockPos> e : beacons.entrySet()) {
            double dx = x - e.getValue().getX(), dz = z - e.getValue().getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (best < 0 || d < best) { best = d; bestName = e.getKey(); }
        }
        return bestName;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<String, BlockPos> e : beacons.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("name", e.getKey());
            t.putInt("x", e.getValue().getX());
            t.putInt("y", e.getValue().getY());
            t.putInt("z", e.getValue().getZ());
            list.add(t);
        }
        tag.put("beacons", list);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        beacons.clear();
        ListTag list = tag.getList("beacons", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            add(t.getString("name"), t.getInt("x"), t.getInt("y"), t.getInt("z"));
        }
    }

    // 이관 확인용 (/lsdata).
    public String summary() {
        if (beacons.isEmpty()) return "없음";
        return beacons.size() + "기 (위협 하한 -" + (beacons.size() / 2) + ")";
    }
}
