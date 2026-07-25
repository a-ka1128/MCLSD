package com.laststardust.relics.client;

import com.laststardust.relics.network.TownActionPayload;
import com.laststardust.relics.town.TownMenu;
import com.laststardust.relics.town.TownView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// 트랙 하나의 제출 창. 진짜 컨테이너 화면이라 아이템을 실제로 끌어다 놓을 수 있다.
//
// 세로 배치를 상수로 한 곳에 모아둔다. 상수로 둬야 겹침을 눈으로 검산할 수 있다 —
// 각 항목이 차지하는 구간을 주석에 적어두고 아래가 위를 넘지 않는지 확인한다:
//   제목 8~16 · 다음단계 24~32 · 효과 35~43 · 제출슬롯 47~65
//   자원 72~80 · Ducat 84~92 · 버튼 98~116 · 보관함 122~130 · 인벤 132~208
public class TownTrackScreen extends AbstractContainerScreen<TownMenu> {

    private static final int Y_TITLE  = 8;
    private static final int Y_NEXT   = 24;
    private static final int Y_FX     = 35;
    private static final int Y_COST   = 72;   // 제출 슬롯(48~64) 아래
    private static final int Y_DUCAT  = 84;
    private static final int Y_BTN    = 98;   // 98~116
    private static final int Y_INVLBL = 122;  // 버튼(~116) 아래, 인벤 슬롯(132~) 위

    private static final int C_PANEL   = 0xF0101018;
    private static final int C_BORDER  = 0xFF3A3F55;
    private static final int C_SLOT    = 0xFF23262F;
    private static final int C_SLOT_HI = 0xFF2E3648;
    private static final int C_TITLE   = 0xFFFFD98A;
    private static final int C_SUB     = 0xFF9AA4B2;
    private static final int C_BODY    = 0xFFC7CDD6;
    private static final int C_OK      = 0xFF7FD98A;
    private static final int C_BAD     = 0xFFE08A8A;
    private static final int C_ESSENCE = 0xFFC08AE0;

    private TownView view = TownView.empty();
    private LSButton upgrade;

    public TownTrackScreen(TownMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = TownMenu.PANEL_W;
        this.imageHeight = TownMenu.PANEL_H;
    }

    public void setView(TownView v) {
        this.view = v == null ? TownView.empty() : v;
        if (this.minecraft != null) rebuildWidgets();
    }

    private TownView.TrackView track() {
        return view.track(this.menu.trackKey());
    }

    @Override
    protected void init() {
        super.init();
        TownView.TrackView t = track();
        boolean can = t != null && t.canUpgrade();

        addRenderableWidget(LSButton.of(this.leftPos + 8, this.topPos + Y_BTN, 52, 18,
            Component.translatable("lstown.gui.back"),
            b -> PacketDistributor.sendToServer(new TownActionPayload("hub", ""))));

        upgrade = LSButton.of(this.leftPos + this.imageWidth - 76, this.topPos + Y_BTN, 68, 18,
            Component.translatable("lstown.gui.upgrade"),
            b -> PacketDistributor.sendToServer(new TownActionPayload("upgrade", this.menu.trackKey())));
        upgrade.active = can;
        if (can) upgrade.accent();
        addRenderableWidget(upgrade);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, C_PANEL);
        border(g, x, y, this.imageWidth, this.imageHeight);

        // 제출 슬롯 — 조금 밝게 해서 "여기에 놓아라"가 눈에 들어오게
        for (int i = 0; i < 3; i++) {
            slotBg(g, x + TownMenu.depositX(i), y + TownMenu.depositY(), C_SLOT_HI);
        }
        // 위(제출)와 아래(내 가방)를 가르는 선
        g.fill(x + 8, y + Y_INVLBL - 6, x + this.imageWidth - 8, y + Y_INVLBL - 5, C_BORDER);

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                slotBg(g, x + TownMenu.invX(col), y + TownMenu.invY(row), C_SLOT);
        for (int col = 0; col < 9; col++)
            slotBg(g, x + TownMenu.invX(col), y + TownMenu.hotbarY(), C_SLOT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);

        // 빈 제출 슬롯에 요구 아이템을 흐리게 그린다 — 뭘 넣어야 하는지 글로 읽을 필요가 없다.
        var req = this.menu.required();
        if (req != null) {
            ItemStack ghost = new ItemStack(req);
            for (int i = 0; i < 3; i++) {
                if (!this.menu.getSlot(i).getItem().isEmpty()) continue;
                int gx = this.leftPos + TownMenu.depositX(i);
                int gy = this.topPos + TownMenu.depositY();
                g.pose().pushPose();
                g.pose().translate(0, 0, 100);
                g.setColor(1f, 1f, 1f, 0.25f);
                g.renderItem(ghost, gx, gy);
                g.setColor(1f, 1f, 1f, 1f);
                g.pose().popPose();
            }
        }
        this.renderTooltip(g, mouseX, mouseY);
    }

    private void slotBg(GuiGraphics g, int sx, int sy, int color) {
        g.fill(sx - 1, sy - 1, sx + 17, sy + 17, color);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, C_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, C_BORDER);
        g.fill(x, y, x + 1, y + h, C_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, C_BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        TownView.TrackView t = track();
        g.drawString(this.font, this.title, 8, Y_TITLE, C_TITLE, false);

        if (t == null) {
            g.drawString(this.font, Component.translatable("lstown.gui.loading"), 8, Y_NEXT, C_SUB, false);
            return;
        }
        String lv = "Lv " + t.level() + " / " + t.max();
        g.drawString(this.font, lv, this.imageWidth - 8 - this.font.width(lv), Y_TITLE, C_SUB, false);

        if (t.isMax()) {
            g.drawString(this.font, Component.translatable("lstown.gui.max"), 8, Y_NEXT, C_OK, false);
        } else {
            g.drawString(this.font, Component.literal("§7→ §f").append(t.nextName()), 8, Y_NEXT, C_BODY, false);
            g.drawString(this.font, Component.literal("§8").append(t.nextFx()), 8, Y_FX, C_SUB, false);

            // 자원 — 이름은 왼쪽, 수량은 오른쪽 끝. 이름이 길어도 숫자와 겹치지 않는다.
            int resColor = t.have() >= t.need() ? C_OK : (t.essence() ? C_ESSENCE : C_BAD);
            g.drawString(this.font, t.itemName(), 8, Y_COST, resColor, false);
            String amount = t.have() + " / " + t.need();
            g.drawString(this.font, amount, this.imageWidth - 8 - this.font.width(amount), Y_COST, resColor, false);

            boolean ducOk = view.treasury() >= t.ducat();
            int ducColor = ducOk ? C_OK : C_BAD;
            g.drawString(this.font, "Ducat", 8, Y_DUCAT, ducColor, false);
            String duc = Math.min(view.treasury(), t.ducat()) + " / " + t.ducat();
            g.drawString(this.font, duc, this.imageWidth - 8 - this.font.width(duc), Y_DUCAT, ducColor, false);
        }

        g.drawString(this.font, this.playerInventoryTitle, 8, Y_INVLBL, C_SUB, false);
    }
}
