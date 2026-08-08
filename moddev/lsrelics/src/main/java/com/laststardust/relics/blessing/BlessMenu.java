package com.laststardust.relics.blessing;

import java.util.List;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.RelicEventHandlers;
import com.laststardust.relics.data.BlessingCatalog.Slot;
import com.laststardust.relics.data.LSCurrency;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 별의 제단 — 축복을 굴리는 창.
 *
 * <p>슬롯 둘뿐이다:
 * <ul>
 *   <li><b>장비칸</b> — «어느 칸을 굴릴지»를 가리키는 <b>지시자</b>다. 저장은 플레이어에 있으므로
 *       아이템에 실제로 새기는 건 없다(사양 5절). 유물을 올리면 무기 두 칸, 상의를 올리면
 *       상의칸, 바지를 올리면 하의칸이 보인다. 스틱스는 아무 단검이나 올리면 된다.</li>
 *   <li><b>재료칸</b> — 리롤 비용. 인벤토리에서 몰래 빼가지 않는다.</li>
 * </ul>
 *
 * <p>둘 다 <b>임시 보관함</b>이라 창을 닫으면 돌려준다 — 마을 트랙 창(서버 소유 보관함)과
 * 정반대다. 여기 올리는 건 «맡기는» 게 아니라 «지금 쓰는» 것이라서다.
 */
public class BlessMenu extends AbstractContainerMenu {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, LSRelics.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<BlessMenu>> TYPE =
        MENUS.register("bless", () -> IMenuTypeExtension.create(BlessMenu::new));

    // ── 화면 배치 (BlessScreen 과 공유) ──
    public static final int PANEL_W = 216;
    public static final int PANEL_H = 228;
    public static final int GEAR_X = 66, GEAR_Y = 24;
    public static final int COST_X = 116, COST_Y = 24;
    private static final int INV_X = 27;
    private static final int INV_Y = 147;
    private static final int HOTBAR_Y = 205;

    private final Container work;

    public BlessMenu(int id, Inventory playerInv, Container work) {
        super(TYPE.get(), id);
        this.work = work;
        layout(playerInv);
    }

    /** 클라 — 임시 보관함이라 동기화할 내용이 없다. 슬롯 내용은 바닐라가 알아서 맞춘다. */
    public BlessMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(id, playerInv, new SimpleContainer(2));
    }

    public Container work() { return work; }
    public ItemStack gear() { return work.getItem(0); }
    public ItemStack cost() { return work.getItem(1); }

    // ══════════════════════════════════════════════════════════════════
    //  장비 → 슬롯 판정. **서버와 클라가 같은 함수를 쓴다** — 다르면 아이템이
    //  들어갔다 튕기거나, 화면에 보이는 칸과 서버가 굴리는 칸이 달라진다.
    // ══════════════════════════════════════════════════════════════════

    /** 이 장비가 여는 칸들. 유물이면 둘, 갑옷이면 하나, 아무것도 아니면 빈 목록. */
    public static List<Slot> slotsFor(ItemStack gear) {
        if (gear.isEmpty()) return List.of();
        if (RelicEventHandlers.isRelic(gear)) return List.of(Slot.WEAPON_1, Slot.WEAPON_2);
        if (gear.getItem() instanceof Equipable eq) {
            EquipmentSlot es = eq.getEquipmentSlot();
            if (es == EquipmentSlot.CHEST) return List.of(Slot.CHEST);
            if (es == EquipmentSlot.LEGS) return List.of(Slot.LEGS);
        }
        return List.of();
    }

    public List<Slot> visibleSlots() { return slotsFor(gear()); }

    private static boolean isGear(ItemStack st) { return !slotsFor(st).isEmpty(); }

    private static boolean isCost(ItemStack st) {
        if (st.isEmpty()) return false;
        return st.is(LSCurrency.essence()) || st.is(LSCurrency.stardust());
    }

    public static int gearX() { return GEAR_X; }
    public static int gearY() { return GEAR_Y; }
    public static int costX() { return COST_X; }
    public static int costY() { return COST_Y; }
    public static int invX(int col) { return INV_X + col * 18; }
    public static int invY(int row) { return INV_Y + row * 18; }
    public static int hotbarY() { return HOTBAR_Y; }

    private void layout(Inventory playerInv) {
        addSlot(new net.minecraft.world.inventory.Slot(work, 0, GEAR_X, GEAR_Y) {
            @Override public boolean mayPlace(ItemStack st) { return isGear(st); }
            @Override public int getMaxStackSize() { return 1; }
        });
        addSlot(new net.minecraft.world.inventory.Slot(work, 1, COST_X, COST_Y) {
            @Override public boolean mayPlace(ItemStack st) { return isCost(st); }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new net.minecraft.world.inventory.Slot(
                    playerInv, col + row * 9 + 9, invX(col), invY(row)));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new net.minecraft.world.inventory.Slot(playerInv, col, invX(col), HOTBAR_Y));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        net.minecraft.world.inventory.Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < 2) {
            if (!moveItemStackTo(stack, 2, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (isGear(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (isCost(stack)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else {
            // 받는 게 아니면 인벤토리 안에서 굴리지 않는다 — 시프트클릭이 엉뚱한 칸으로 옮긴다.
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        // 거리 판정은 여는 쪽(BlessGui)이 한다. 여기서 또 보면 판정이 두 곳에 생긴다.
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 마을 트랙 창과 정반대로 **전부 돌려준다.** 여기 올린 건 맡긴 게 아니다 —
        // 안 돌려주면 유물이 창 닫는 순간 사라진다.
        clearContainer(player, work);
    }
}
