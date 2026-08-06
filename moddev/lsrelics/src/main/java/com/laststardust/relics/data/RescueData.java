package com.laststardust.relics.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

// 생존자 구출 — 진행 중인 원정 · 구출 완료 명부 · 성역 인구 (이관 5단계, docs/ARCHITECTURE.md)
//
// ── 왜 옮기는가: 인구가 두 번 셀 수 있었다 ──
// 옛 저장은 «구출 완료 표식»(`rs_done_<키>`)과 «인구»(`town_pop`)가 **따로 사는 두 값**이었다.
// 올리는 곳이 둘(`rsComplete` 와 `/rescue grant`)이고 둘 다 표식을 확인하지 않아서,
// 이미 구출한 생존자에게 `/rescue grant` 를 한 번 더 쓰면 **인구만 늘었다.**
// 그러면 매일 들어오는 금고 수입(인구×8)이 영구히 부풀고, 아무 오류도 안 난다.
//
// 여기서는 인구가 저장되는 값이 아니라 **명부의 크기**다. 두 번 셀 방법이 없다.
//
// ── 파일 경계를 넘는 값이다 ──
// `ls_stats.js` 가 `town_pop` 을 직접 읽어 마을 현황에 찍는다. 쓰는 쪽만 옮기면 거기가
// 조용히 0 을 읽는다 — 명예 보드 사고와 같은 구조라 그쪽도 같이 옮긴다.
//
// ── 명부는 여기 없다 ──
// 생존자 6명의 이름·직업·간수 구성은 `ls_rescue.js` 에 남는다. 여기가 아는 건 **누구를 구했나**
// 뿐이고, 키가 명부에 있는지는 스크립트가 본다.
public class RescueData {

    // 구출 완료한 생존자 키. 순서를 지킨다 — 구출한 순서가 곧 이야기 순서다.
    private final Set<String> rescued = new LinkedHashSet<>();

    // ── 진행 중인 원정 ──
    // `built` 가 따로 있는 이유: 「신호만 잡힌 상태」와 「감금지가 실제로 지어진 상태」는
    // 다르다. 하나로 합치면 재접속 후 캠프가 두 번 지어지거나, 영영 안 지어진다.
    private boolean active;
    private boolean built;
    private int idx;          // SURVIVORS 인덱스 — 명부는 스크립트가 갖는다
    private int x, y, z;
    private int guards;       // 남은 간수

    // 인구 수입을 하루 한 번만 주기 위한 날짜 표식.
    private int day;

    // ── 인구 = 명부의 크기 ──
    // 따로 저장하지 않는다. 저장하면 언젠가 명부와 어긋나고, 어긋난 쪽이 돈을 만든다.
    public int population() { return rescued.size(); }

    public boolean isRescued(String key) { return rescued.contains(key); }

    public List<String> rescuedKeys() { return new ArrayList<>(rescued); }

    // 이미 구출한 생존자면 false — 인구가 두 번 늘지 않는 유일한 관문이다.
    public boolean settle(String key) {
        if (key == null || key.isEmpty()) return false;
        return rescued.add(key);
    }

    // ── 원정 ──
    public boolean active() { return active; }
    public boolean built() { return built; }
    public int idx() { return idx; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public int guards() { return guards; }
    public int day() { return day; }

    public void setDay(int d) { day = d; }

    // 정찰 개시 — 다섯 값이 한 번에 선다. 옛 코드는 여섯 줄이 나란히 있었고,
    // 하나만 빠지면 «지난 원정의 좌표로 가는» 추적이 됐다.
    public void beginScout(int index, int px, int pz) {
        active = true;
        built = false;
        idx = index;
        x = px;
        y = 0;
        z = pz;
        guards = 0;
    }

    // 감금지 노출 — 지면 높이가 이때 정해진다. 이미 지어졌으면 false(두 번 짓지 않는다).
    public boolean reveal(int py, int guardCount) {
        if (!active || built) return false;
        built = true;
        y = py;
        guards = Math.max(0, guardCount);
        return true;
    }

    // 간수 하나 처치 — 남은 수를 돌려준다. 옛 코드는 읽고·빼고·쓰는 세 줄이라
    // 간수 둘이 같은 틱에 죽으면 한쪽이 덮일 수 있었다.
    public int killGuard() {
        if (guards > 0) guards--;
        return guards;
    }

    // 원정 종료(성공·실패·취소 공통). 끝내는 방법이 하나뿐이면 어긋날 자리가 없다.
    public void endRun() {
        active = false;
        built = false;
        guards = 0;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();

        ListTag list = new ListTag();
        for (String k : rescued) list.add(StringTag.valueOf(k));
        tag.put("rescued", list);

        CompoundTag r = new CompoundTag();
        r.putBoolean("active", active);
        r.putBoolean("built", built);
        r.putInt("idx", idx);
        r.putInt("x", x);
        r.putInt("y", y);
        r.putInt("z", z);
        r.putInt("guards", guards);
        tag.put("run", r);

        tag.putInt("day", day);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        rescued.clear();
        ListTag list = tag.getList("rescued", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) settle(list.getString(i));

        CompoundTag r = tag.getCompound("run");
        active = r.getBoolean("active");
        built = r.getBoolean("built");
        idx = r.getInt("idx");
        x = r.getInt("x");
        y = r.getInt("y");
        z = r.getInt("z");
        guards = r.getInt("guards");

        day = tag.getInt("day");
    }

    // 이관 확인용 (/lsdata).
    public String summary() {
        return "인구 " + population() + "명"
             + (active ? (built ? " · §c감금지 노출(간수 " + guards + ")" : " · 추적 중") : "");
    }
}
