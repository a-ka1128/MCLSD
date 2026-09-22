package com.laststardust.relics.data;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.laststardust.relics.data.BlessingCatalog.Blessing;
import com.laststardust.relics.data.BlessingCatalog.Slot;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * 별의 축복 — 사람에 붙는 슬롯 4칸. 사양은 {@code docs/BLESSING.md}.
 *
 * <p>── 왜 아이템이 아니라 플레이어인가 ──
 * 각성 성급이 이미 그렇게 동작한다. {@code HeroData.star} 가 원본이고
 * {@code LSCommands.setStar} 가 <b>인벤토리의 모든 유물에 복사</b>한다 — 아이템 태그는 사본이다.
 * 축복도 같은 구조로 두면 넷이 공짜로 해결된다:
 *
 * <ul>
 *   <li><b>스틱스(쌍단검)</b> — 칼 두 자루를 들어도 축복이 두 배가 안 된다. 전용 예외 코드가 없다</li>
 *   <li><b>유물 분실</b> — {@code /relic} 재지급 후에도 남는다</li>
 *   <li><b>복제</b> — 아이템을 복제해도 안 불어난다</li>
 *   <li><b>갑옷 교체</b> — 가죽→네더라이트로 갈아입어도 안 사라진다. 초판이 Curios 부적을
 *       골랐던 이유가 이거였는데, 저장을 여기로 옮기니 원인부터 없어졌다</li>
 * </ul>
 *
 * <p>키를 UUID 가 아니라 <b>이름</b>으로 쓰는 건 {@link HeroData} 와 맞추기 위해서다. 성급과
 * 축복이 다른 키 체계를 쓰면 「같은 사람인가」를 두 번 판단하게 된다. UUID 전환은 둘을 같이 한다.
 */
public class BlessingData {

    /** 슬롯 하나에 박힌 축복. {@code value} 는 굴린 퍼센트 — 백분위는 범위에서 역산하므로 안 저장한다. */
    public record Roll(String id, float value) {
        public Blessing def() { return BlessingCatalog.byId(id); }

        /** 범위의 몇 %인가 (0~1). 저장하지 않고 매번 역산한다 — 값과 표시가 어긋날 수가 없다. */
        public float percentile() {
            Blessing b = def();
            return b == null ? 0f : b.percentileOf(value);
        }
    }

    private final Map<String, Map<Slot, Roll>> rolls = new HashMap<>();

    // ── 조회 ──

    /** 없으면 null. */
    public Roll get(String name, Slot slot) {
        Map<Slot, Roll> m = rolls.get(name);
        return m == null ? null : m.get(slot);
    }

    public boolean has(String name, Slot slot) {
        return get(name, slot) != null;
    }

    /** 이 사람이 가진 축복 전부 (슬롯 순서대로, 빈 칸은 건너뛴다). */
    public Map<Slot, Roll> all(String name) {
        Map<Slot, Roll> m = rolls.get(name);
        return m == null ? new EnumMap<>(Slot.class) : new EnumMap<>(m);
    }

    /**
     * 이 값이 실제로 «걸려 있는가» — 효과 계산의 단일 창구.
     *
     * <p>슬롯이 아직 안 열린 성급인데 예전 데이터가 남아 있을 수 있다(관리자가 {@code /star} 로
     * 내렸다든가). 그때 효과만 그대로 도는 걸 막는다. 읽는 쪽이 성급을 매번 확인하게 두면
     * <b>한 곳이 빠뜨렸을 때 조용히 어긋난다</b> — {@code HeroData.setStar} 가 상한을 안에서
     * 거는 것과 같은 이유다.
     *
     * @return 걸려 있으면 퍼센트, 아니면 0
     */
    public float active(String name, int star, String blessingId) {
        Map<Slot, Roll> m = rolls.get(name);
        if (m == null) return 0f;
        for (Map.Entry<Slot, Roll> e : m.entrySet()) {
            if (!e.getKey().unlockedAt(star)) continue;
            if (e.getValue().id().equals(blessingId)) return e.getValue().value();
        }
        return 0f;
    }

    /**
     * 이 슬롯에 나올 수 있는 후보. <b>중복 금지</b> — 짝 슬롯에 이미 있는 건 빠진다.
     * 축이 다르면(무기 vs 방어) 목록 자체가 달라 서로 간섭하지 않는다.
     */
    public List<Blessing> candidates(String name, Slot slot) {
        Roll sibling = get(name, slot.sibling());
        List<Blessing> out = new ArrayList<>();
        for (Blessing b : BlessingCatalog.pool(slot.axis)) {
            if (sibling != null && sibling.id().equals(b.id())) continue;
            out.add(b);
        }
        return out;
    }

    // ── 변경 ──

    public void set(String name, Slot slot, String id, float value) {
        rolls.computeIfAbsent(name, k -> new EnumMap<>(Slot.class)).put(slot, new Roll(id, value));
    }

    public void clear(String name, Slot slot) {
        Map<Slot, Roll> m = rolls.get(name);
        if (m == null) return;
        m.remove(slot);
        if (m.isEmpty()) rolls.remove(name);
    }

    /** 사람 하나를 통째로 비운다 — 관리자 도구용. */
    public void clearAll(String name) {
        rolls.remove(name);
    }

    // ── 저장 ──
    // TreeMap 으로 감싸 이름 순으로 쓴다. 저장 파일을 사람이 열어볼 때 순서가 매번 달라지면
    // 「뭐가 바뀌었나」를 눈으로 못 본다 (HeroData 와 같은 규칙).
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        new TreeMap<>(rolls).forEach((name, m) -> {
            CompoundTag per = new CompoundTag();
            for (Slot s : Slot.values()) {
                Roll r = m.get(s);
                if (r == null) continue;
                CompoundTag one = new CompoundTag();
                one.putString("id", r.id());
                one.putFloat("v", r.value());
                per.put(s.name(), one);
            }
            if (!per.isEmpty()) tag.put(name, per);
        });
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        rolls.clear();
        for (String name : tag.getAllKeys()) {
            CompoundTag per = tag.getCompound(name);
            EnumMap<Slot, Roll> m = new EnumMap<>(Slot.class);
            for (Slot s : Slot.values()) {
                if (!per.contains(s.name())) continue;
                CompoundTag one = per.getCompound(s.name());
                String id = one.getString("id");
                // 카탈로그에서 사라진 id 는 버린다. 남겨두면 효과는 안 도는데 슬롯은 차 있어서
                // 「왜 안 걸리지」가 되고, 리롤로도 못 지운다.
                if (BlessingCatalog.byId(id) == null) continue;
                m.put(s, new Roll(id, one.getFloat("v")));
            }
            if (!m.isEmpty()) rolls.put(name, m);
        }
    }

    /** 이관·점검 확인용 — 「들어는 있나」를 한 줄로 본다. */
    public String summary() {
        int slots = 0;
        for (Map<Slot, Roll> m : rolls.values()) slots += m.size();
        return "축복 " + rolls.size() + "명 · " + slots + "칸";
    }
}
