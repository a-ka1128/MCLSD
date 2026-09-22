package com.laststardust.relics.client;

import com.laststardust.relics.blessing.BlessView;

/**
 * 클라가 들고 있는 «내 축복» 사본. 툴팁이 이걸 읽는다.
 *
 * <p>── 왜 캐시가 필요한가 ──
 * 각성 성급은 유물 {@code custom_data} 에 찍혀 있어서 아이템과 함께 자동으로 클라에 온다
 * ({@code RelicEventHandlers.onRelicTooltip} 주석). <b>축복은 그럴 수가 없다.</b>
 * 저장이 플레이어에 있고(사양 5절), 방어 축복은 애초에 붙일 아이템이 없다 —
 * 갑옷은 갈아입는 물건이라 거기 찍으면 갈아입을 때마다 표시가 사라진다.
 *
 * <p>그래서 서버가 <b>바뀔 때마다 + 접속할 때</b> 현황을 통째로 보내고, 여기 담아 둔다.
 * 아이템에 사본을 찍는 방식과 달리 «찍는 걸 한 곳이라도 빠뜨리면 조용히 옛 값이 보이는»
 * 문제가 없다 — 원본이 하나뿐이라서다.
 */
public final class BlessCache {
    private BlessCache() {}

    private static BlessView view = BlessView.empty();

    public static void set(BlessView v) {
        view = v == null ? BlessView.empty() : v;
    }

    public static BlessView get() { return view; }

    /** 서버를 나갈 때 비운다 — 안 그러면 다른 서버에 들어가 남의 수치를 본다. */
    public static void clear() { view = BlessView.empty(); }
}
