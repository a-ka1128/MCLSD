package com.laststardust.relics.loot;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 별먼지 아이템 참조.
 *
 * <p>별먼지는 <b>KubeJS 가 등록</b>한다({@code kubejs/startup_scripts/ls_items.js}).
 * 우리 모드가 만든 게 아니라서 {@code DeferredItem} 으로 못 잡고, 레지스트리에서 id 로 찾아야 한다.
 *
 * <p>모드 로딩 시점에는 KubeJS 아이템이 아직 없을 수 있으므로 <b>처음 쓸 때 찾아 캐시</b>한다.
 * 못 찾으면 {@code null} 을 돌려주고, 부르는 쪽이 조용히 건너뛴다 — 여기서 예외를 던지면
 * 전리품 생성이 통째로 깨져서 <b>상자가 아예 안 열린다.</b>
 *
 * <p>⚠️ id 를 바꾸면 여기도 같이 고쳐야 한다. 안 고치면 별먼지가 그냥 «안 나오고»,
 * 오류도 안 난다 — 그래서 못 찾았을 때 로그를 한 번 남긴다.
 */
public final class StardustRef {
    private StardustRef() {}

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("kubejs", "stardust");

    private static Item cached;
    private static boolean warned;

    /** 별먼지 아이템, 또는 아직 등록 전/없으면 null. */
    public static Item get() {
        if (cached != null) return cached;
        Item it = BuiltInRegistries.ITEM.get(ID);
        // 없는 id 를 물으면 레지스트리는 예외 대신 AIR 를 준다 — 그걸 «찾았다»로 쓰면
        // 상자에 공기 스택이 들어간다.
        if (it == null || it == net.minecraft.world.item.Items.AIR) {
            if (!warned) {
                warned = true;
                org.slf4j.LoggerFactory.getLogger("lsrelics")
                    .warn("[LS-LOOT] {} 를 못 찾았다 — 별먼지가 구조물 상자에 안 나온다. "
                        + "KubeJS startup_scripts/ls_items.js 를 확인할 것.", ID);
            }
            return null;
        }
        cached = it;
        return cached;
    }
}
