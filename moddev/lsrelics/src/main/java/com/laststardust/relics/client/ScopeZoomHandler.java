package com.laststardust.relics.client;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.item.SolarMusket;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

// 솔라리 스코프 — 시야각 축소(줌).
// 서버는 아무것도 보낼 필요가 없다: 아이템 사용 상태(isUsingItem)와 남은 사용 틱은
// 이미 클라이언트가 알고 있어서, 서버와 완전히 같은 판정을 로컬에서 계산할 수 있다.
@EventBusSubscriber(modid = LSRelics.MODID, value = Dist.CLIENT)
public final class ScopeZoomHandler {
    private ScopeZoomHandler() {}

    private static final float ZOOM = 0.35f;       // 저격 스코프 — FOV 0.35배 (약 3배 확대)
    // 연사 모드에서는 배율만 주고 피해·발사율은 안 바뀐다(SolarMusket.use 주석 참고).
    // 조준을 돕는 정도로만 — 3배까지 주면 라이플이 저격총 노릇까지 하게 된다.
    private static final float RIFLE_ZOOM = 0.55f; // 약 1.8배 확대

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (!(event.getPlayer() instanceof LocalPlayer player)) return;

        // ── 연사 모드 배율 ──
        // 아이템 사용(startUsingItem)이 아니라 custom_data 토글로 판단한다.
        // 사용 상태를 쓰면 바닐라가 이동을 0.2배로 깎기 때문이다(SolarMusket.use 주석 참고).
        ItemStack held = player.getMainHandItem();
        if (held.getItem() == LSRelics.GUNNER.get()) {
            var tag = held.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            if (tag.getBoolean("rifle")) {
                // 토글이라 서서히 들어갈 진행도가 없다 — 바로 적용한다
                if (tag.getBoolean("rifleZoom")) {
                    event.setNewFovModifier(event.getFovModifier() * RIFLE_ZOOM);
                }
                return;   // 연사 중엔 저격 스코프 경로를 타지 않는다
            }
        }

        ItemStack used = player.getUseItem();
        if (used.getItem() != LSRelics.GUNNER.get()) return;
        if (!player.isUsingItem()) return;

        // 진입 0.5초 동안 부드럽게 줌인 — 서버의 피해 판정과 같은 진행도를 쓴다
        float p = SolarMusket.scopeProgress(player);
        event.setNewFovModifier(event.getFovModifier() * (1.0f + (ZOOM - 1.0f) * p));
    }
}
