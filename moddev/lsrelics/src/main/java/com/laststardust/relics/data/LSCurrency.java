package com.laststardust.relics.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 전용 재화 두 종 — <b>별의 파편</b>(전투)과 <b>별먼지</b>(탐험) — 참조와 소모.
 *
 * <p>둘 다 <b>KubeJS 가 등록</b>한다({@code kubejs/startup_scripts/ls_items.js}). 우리 모드가
 * 만든 게 아니라 {@code DeferredItem} 으로 못 잡고 레지스트리에서 id 로 찾아야 하는데,
 * 모드 로딩 시점엔 아직 없을 수 있으므로 <b>처음 쓸 때 찾아 캐시</b>한다.
 * 못 찾으면 {@code null} — 부르는 쪽이 조용히 건너뛴다. 여기서 예외를 던지면 전리품 생성이
 * 통째로 깨져 <b>상자가 아예 안 열린다.</b>
 *
 * <p>⚠️ id 를 바꾸면 여기도 같이 고쳐야 한다. 안 고치면 재화가 그냥 «안 나오고» 오류도 안 난다 —
 * 그래서 못 찾았을 때 로그를 한 번 남긴다.
 */
public final class LSCurrency {
    private LSCurrency() {}

    /** 별의 파편 — 공성 격퇴·보스·정예 현상금. 축복 <b>종류</b> 리롤. */
    public static final ResourceLocation ESSENCE =
        ResourceLocation.fromNamespaceAndPath("kubejs", "rift_essence");

    /** 별먼지 — 구조물 상자. 축복 <b>수치</b> 리롤. */
    public static final ResourceLocation STARDUST =
        ResourceLocation.fromNamespaceAndPath("kubejs", "stardust");

    private static final Map<ResourceLocation, Item> CACHE = new HashMap<>();
    private static final Set<ResourceLocation> WARNED = new HashSet<>();

    public static Item get(ResourceLocation id) {
        Item c = CACHE.get(id);
        if (c != null) return c;
        Item it = BuiltInRegistries.ITEM.get(id);
        // 없는 id 를 물으면 레지스트리는 예외 대신 AIR 를 준다 — 그걸 «찾았다»로 쓰면
        // 상자에 공기 스택이 들어가거나 비용 검사가 0개로 통과한다.
        if (it == null || it == Items.AIR) {
            if (WARNED.add(id)) {
                org.slf4j.LoggerFactory.getLogger("lsrelics").warn(
                    "[LS] 재화 아이템을 못 찾았다: {} — KubeJS startup_scripts/ls_items.js 를 확인할 것.", id);
            }
            return null;
        }
        CACHE.put(id, it);
        return it;
    }

    public static Item essence()  { return get(ESSENCE); }
    public static Item stardust() { return get(STARDUST); }

    /** 인벤토리(핫바·본칸·보조손)의 보유 개수. */
    public static int count(Player p, Item item) {
        if (item == null) return 0;
        int n = 0;
        for (ItemStack st : p.getInventory().items)   if (st.is(item)) n += st.getCount();
        for (ItemStack st : p.getInventory().offhand) if (st.is(item)) n += st.getCount();
        return n;
    }

    /**
     * 정확히 {@code need} 개를 소모한다. <b>전부 아니면 아무것도</b> —
     * 모자라면 하나도 안 건드리고 false 를 준다.
     *
     * <p>KubeJS 시절 비용 검사가 조용히 고장났던 게 정확히 이 지점이다
     * ({@code runCommandSilent} 가 void 라 반환값이 항상 undefined → {@code undefined < 5} 가
     * false → 각성이 공짜였다). 세는 것과 빼는 것을 <b>같은 함수 안에서</b> 한다.
     */
    public static boolean take(Player p, Item item, int need) {
        if (item == null || need <= 0) return need <= 0;
        if (count(p, item) < need) return false;

        int left = need;
        left = drain(p.getInventory().items, item, left);
        left = drain(p.getInventory().offhand, item, left);
        return left == 0;
    }

    // ── 컨테이너판 ──
    // 제단 화면은 재화를 «슬롯에 올려두고» 쓴다. 인벤토리에서 몰래 빼가면 「올려둔 건 그대로인데
    // 가방에서 사라졌다」가 되어, 무엇을 냈는지 눈으로 못 따라간다.

    public static int count(net.minecraft.world.Container c, Item item) {
        if (c == null || item == null) return 0;
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack st = c.getItem(i);
            if (st.is(item)) n += st.getCount();
        }
        return n;
    }

    /** 컨테이너에서 정확히 {@code need} 개. 인벤토리판과 같은 규칙 — 전부 아니면 아무것도. */
    public static boolean take(net.minecraft.world.Container c, Item item, int need) {
        if (need <= 0) return true;
        if (c == null || item == null) return false;
        if (count(c, item) < need) return false;

        int left = need;
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            ItemStack st = c.getItem(i);
            if (!st.is(item)) continue;
            int take = Math.min(st.getCount(), left);
            st.shrink(take);
            left -= take;
            if (st.isEmpty()) c.setItem(i, ItemStack.EMPTY);
        }
        c.setChanged();
        return left == 0;
    }

    private static int drain(java.util.List<ItemStack> list, Item item, int left) {
        for (int i = 0; i < list.size() && left > 0; i++) {
            ItemStack st = list.get(i);
            if (!st.is(item)) continue;
            int take = Math.min(st.getCount(), left);
            st.shrink(take);
            left -= take;
        }
        return left;
    }
}
