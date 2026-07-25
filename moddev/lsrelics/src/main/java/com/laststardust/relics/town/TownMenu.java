package com.laststardust.relics.town;

import com.laststardust.relics.LSRelics;
import com.laststardust.relics.data.TownCatalog;
import com.laststardust.relics.data.TownData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// 트랙 하나의 제출 창. 진짜 컨테이너라 아이템을 실제로 올려두고 꺼낼 수 있다.
//
// 보관함은 서버(LSData)에 있고 메뉴는 그걸 가리키기만 한다. 그래서 창을 닫아도,
// 서버를 껐다 켜도 올려둔 자원이 그대로 남는다. 여럿이 나눠 채우는 것도 자연히 된다.
//
// ── 요구 아이템을 클라에도 보내는 이유 ──
// 예전엔 서버만 "이 트랙이 받는 아이템"을 알았고 클라는 전부 허용했다. 그러면 클라가 일단
// 넣는 시늉을 하고 서버가 되돌려 **아이템이 들어갔다 튕겨 나온다.** 양쪽이 같은 판정을 해야
// 애초에 안 들어가는 게 보이고, 빈 슬롯에 "무엇을 넣어야 하는지" 미리보기도 그릴 수 있다.
public class TownMenu extends AbstractContainerMenu {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, LSRelics.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<TownMenu>> TYPE =
        MENUS.register("town", () -> IMenuTypeExtension.create(TownMenu::new));

    // ── 화면 배치 (TownTrackScreen 과 공유하는 좌표) ──
    public static final int PANEL_W = 208;
    public static final int PANEL_H = 214;
    private static final int DEPOSIT_X = 73;   // 3칸을 가운데 정렬한 시작 X
    private static final int DEPOSIT_Y = 48;
    private static final int DEPOSIT_STEP = 22;
    private static final int INV_X = 23;       // 9칸 가운데 정렬
    private static final int INV_Y = 133;
    private static final int HOTBAR_Y = 191;

    private final Container deposit;
    private final String trackKey;
    private final Item required;   // 이번 단계가 받는 아이템 (없으면 null = 최대 단계)

    // ── 서버 ──
    public TownMenu(int id, Inventory playerInv, Container deposit, String trackKey, Item required) {
        super(TYPE.get(), id);
        this.deposit = deposit;
        this.trackKey = trackKey;
        this.required = required;
        layout(playerInv);
    }

    // ── 클라 ──
    public TownMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(id, playerInv, new SimpleContainer(TownCatalog.DEPOSIT_SLOTS),
            buf.readUtf(), readItem(buf));
    }

    private static Item readItem(RegistryFriendlyByteBuf buf) {
        String id = buf.readUtf();
        if (id.isEmpty()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl) ? BuiltInRegistries.ITEM.get(rl) : null;
    }

    public String trackKey() { return trackKey; }
    public Item required() { return required; }

    public static int depositX(int i) { return DEPOSIT_X + i * DEPOSIT_STEP; }
    public static int depositY() { return DEPOSIT_Y; }
    public static int invX(int col) { return INV_X + col * 18; }
    public static int invY(int row) { return INV_Y + row * 18; }
    public static int hotbarY() { return HOTBAR_Y; }

    private void layout(Inventory playerInv) {
        for (int i = 0; i < TownCatalog.DEPOSIT_SLOTS; i++) {
            addSlot(new Slot(deposit, i, depositX(i), DEPOSIT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // 이번 단계에 필요한 자원만 받는다. 아무거나 받으면 보관함이 잡동사니로 차고,
                    // 무엇을 더 넣어야 하는지도 화면에서 읽기 어려워진다.
                    return required != null && !stack.isEmpty() && stack.is(required);
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, invX(col), invY(row)));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, invX(col), HOTBAR_Y));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int depositEnd = TownCatalog.DEPOSIT_SLOTS;

        if (index < depositEnd) {
            if (!moveItemStackTo(stack, depositEnd, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // 받는 자원이 아니면 인벤토리 안에서 굴리지 않고 그냥 둔다 —
            // 안 그러면 시프트클릭이 엉뚱한 칸으로 아이템을 옮겨버린다.
            if (required == null || !stack.is(required)) return ItemStack.EMPTY;
            if (!moveItemStackTo(stack, 0, depositEnd, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        // 블록이 아니라 키로 여는 창이라 거리 제한이 없다.
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 보관함은 서버 소유라 창을 닫아도 내용물을 돌려주지 않는다 — 그게 이 창의 요점이다.
        // 다만 저장 표시는 반드시 해야 한다. 안 하면 서버가 꺼질 때 올려둔 자원이 사라진다.
        if (player.level() instanceof ServerLevel sl) {
            com.laststardust.relics.data.LSData.get(sl).dirty();
        }
    }

    // 서버에서 트랙 보관함을 물고 여는 헬퍼
    public static TownMenu server(int id, Inventory inv, TownData town, String trackKey) {
        TownCatalog.Track track = TownCatalog.byKey(trackKey);
        TownCatalog.Level need = track == null ? null : track.next(town.level(trackKey));
        Item required = need == null ? null : need.itemOrNull();
        return new TownMenu(id, inv, town.deposit(trackKey), trackKey, required);
    }
}
