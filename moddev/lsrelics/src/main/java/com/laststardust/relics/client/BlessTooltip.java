package com.laststardust.relics.client;

import java.util.List;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.RelicEventHandlers;
import com.laststardust.relics.blessing.BlessView;
import com.laststardust.relics.blessing.BlessingService;
import com.laststardust.relics.data.BlessingCatalog.Blessing;
import com.laststardust.relics.data.BlessingCatalog.Slot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 유물·갑옷 툴팁에 «지금 걸려 있는 축복»을 그린다.
 *
 * <p>어느 아이템에 무엇을 띄우는지는 <b>제단 화면과 같은 규칙</b>이다
 * ({@code BlessMenu.slotsFor}) — 유물엔 무기 두 칸, 상의엔 상의칸, 바지엔 하의칸.
 * 두 곳이 다르면 「제단에선 보이는데 툴팁엔 없다」가 된다.
 *
 * <p>방어 축복은 <b>입고 있는 갑옷과 무관하게</b> 걸린다(저장이 플레이어다). 그래서 어떤
 * 상의를 들고 봐도 같은 값이 뜬다 — 그게 맞다. 「이 갑옷의 능력」이 아니라 「내 상의칸」이다.
 */
@EventBusSubscriber(modid = LSRelics.MODID, value = Dist.CLIENT)
public final class BlessTooltip {
    private BlessTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        List<Slot> slots = slotsFor(event.getItemStack());
        if (slots.isEmpty()) return;

        BlessView view = BlessCache.get();
        // 아직 아무것도 못 받았으면 조용히 넘어간다 — 서버에 접속하지 않은 상태(크리에이티브
        // 탭·JEI 등)에서 빈 칸을 줄줄이 그리면 그냥 잡음이다.
        if (view.slots().isEmpty()) return;

        boolean any = false;
        for (Slot s : slots) {
            BlessView.SlotView sv = view.slot(s);
            if (sv == null) continue;

            if (!any) {
                event.getToolTip().add(Component.literal("§b✦ 별의 축복"));
                any = true;
            }
            event.getToolTip().add(line(s, sv, slots.size() > 1));
        }
    }

    private static Component line(Slot s, BlessView.SlotView sv, boolean showSlotName) {
        String prefix = showSlotName
            ? "§8  [" + Component.translatable(s.nameKey()).getString() + "] "
            : "§8  ";

        if (!sv.unlocked()) {
            return Component.literal(prefix + "§8" + s.star + "성에 열린다");
        }
        if (sv.empty()) {
            return Component.literal(prefix + "§8— 비어 있음 §7(/bless)");
        }
        Blessing b = sv.blessing();
        if (b == null) return Component.literal(prefix + "§8?");

        // 「6~11 중 64%」를 같이 띄운다. 곡선은 플레이어에게 안 보이므로, 백분위가 없으면
        // 나쁘게 떠도 «버그인지 운인지»를 못 가린다 (사양 6절).
        return Component.literal(prefix)
            .append(Component.translatable(b.nameKey()).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" §e" + BlessingService.fmt(sv.value()) + "%"
                + " §8(" + BlessingService.fmt(b.min()) + "~" + BlessingService.fmt(b.max())
                + " 중 " + Math.round(sv.percentile() * 100) + "%)"));
    }

    /** {@code BlessMenu.slotsFor} 와 같은 판정. 여기서만 쓰는 클라판이라 따로 두지 않는다. */
    private static List<Slot> slotsFor(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        if (RelicEventHandlers.isRelic(stack)) return List.of(Slot.WEAPON_1, Slot.WEAPON_2);
        if (stack.getItem() instanceof Equipable eq) {
            EquipmentSlot es = eq.getEquipmentSlot();
            if (es == EquipmentSlot.CHEST) return List.of(Slot.CHEST);
            if (es == EquipmentSlot.LEGS) return List.of(Slot.LEGS);
        }
        return List.of();
    }

    /** 서버를 나가면 비운다 — 안 그러면 다음 서버에서 이전 수치가 툴팁에 남는다. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BlessCache.clear();
    }
}
