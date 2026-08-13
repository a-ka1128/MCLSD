package com.laststardust.relics.client;

import com.laststardust.relics.network.ShopBuyPayload;
import com.laststardust.relics.network.ShopSellPayload;
import com.laststardust.relics.shop.ShopView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 상인 광장 — 사기 / 팔기.
 *
 * <p>⚠️ {@code super.render()} 를 부르지 않는다 — {@code Screen.render()} 가 내부에서
 * {@code renderBackground()} 를 다시 불러(1.20.2+) 여기서 그린 글씨가 두 번째 블러에
 * 뭉개진다. 위젯은 직접 그린다({@code TownHubScreen} 과 같은 선택).
 */
public class ShopScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 268;
    private static final int ROW_H = 22;
    private static final int LIST_TOP = 62;
    private static final int LIST_ROWS = 8;

    private static final int C_BACKDROP = 0xE0101018;
    private static final int C_BORDER   = 0xFF3A3F55;
    private static final int C_DIVIDER  = 0xFF2A2E40;
    private static final int C_TITLE    = 0xFFFFD98A;
    private static final int C_SUB      = 0xFF9AA4B2;
    private static final int C_BODY     = 0xFFC7CDD6;
    private static final int C_DIM      = 0xFF6B7280;
    private static final int C_COIN     = 0xFFFFD98A;
    private static final int C_TOWN     = 0xFF7FD98A;
    private static final int C_NO       = 0xFFE08A8A;

    private ShopView view;
    private int panelX, panelY;
    private int scroll;
    /** 팔기 탭인가. 화면 안에서만 바뀐다 — 탭을 누를 때마다 서버에 묻지 않는다. */
    private boolean selling;

    public ShopScreen(ShopView view) {
        super(Component.literal("상인 광장"));
        this.view = view == null ? ShopView.empty() : view;
    }

    public void setView(ShopView v) {
        this.view = v == null ? ShopView.empty() : v;
        // ⚠️ 스크롤과 탭은 그대로 둔다. 사고 팔 때마다 맨 위로 튀거나 탭이 바뀌면
        //    연달아 거래하기가 괴롭다.
        if (this.minecraft != null) rebuildWidgets();
    }

    /** 정보를 읽고 거래하는 창이라 게임을 멈추지 않는다 — 공성 중에도 열 수 있어야 한다. */
    @Override
    public boolean isPauseScreen() { return false; }

    private int rowCount() { return selling ? view.sell().size() : view.buy().size(); }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        int max = Math.max(0, rowCount() - LIST_ROWS);
        if (scroll > max) scroll = max;

        // ── 탭 ──
        var buyTab = LSButton.of(panelX + 14, panelY + 36, 70, 18, Component.literal("사기"),
            b -> { if (selling) { selling = false; scroll = 0; rebuildWidgets(); } });
        var sellTab = LSButton.of(panelX + 88, panelY + 36, 70, 18, Component.literal("팔기"),
            b -> { if (!selling) { selling = true; scroll = 0; rebuildWidgets(); } });
        // 지금 보고 있는 탭은 못 누르게 — 눌러도 아무 일이 없는 버튼이 눌리면 고장으로 읽힌다.
        buyTab.active = selling;
        sellTab.active = !selling;
        addRenderableWidget(buyTab);
        addRenderableWidget(sellTab);

        for (int i = 0; i < LIST_ROWS && i + scroll < rowCount(); i++) {
            int y = panelY + LIST_TOP + i * ROW_H + 2;
            if (selling) {
                final ShopView.SellRow r = view.sell().get(i + scroll);
                var btn = LSButton.of(panelX + PANEL_W - 74, y, 62, 18,
                    Component.literal("+" + r.total()),
                    b -> PacketDistributor.sendToServer(new ShopSellPayload(r.index())));
                btn.active = r.have() > 0;
                addRenderableWidget(btn);
            } else {
                final ShopView.Row r = view.buy().get(i + scroll);
                var btn = LSButton.of(panelX + PANEL_W - 74, y, 62, 18,
                    Component.literal(r.price() + "d"),
                    b -> PacketDistributor.sendToServer(new ShopBuyPayload(r.index())));
                btn.active = r.afford();
                addRenderableWidget(btn);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int max = Math.max(0, rowCount() - LIST_ROWS);
        int next = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy)));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, C_BACKDROP);
        border(g, panelX, panelY, PANEL_W, PANEL_H, C_BORDER);

        int x = panelX + 14;
        int right = panelX + PANEL_W - 14;
        g.drawString(this.font, Component.literal("상인 광장"), x, panelY + 14, C_TITLE, false);

        Component bal = Component.literal(view.balance() + " Ducat");
        g.drawString(this.font, bal, right - this.font.width(bal), panelY + 14, C_COIN, false);
        Component tre = Component.literal("마을 금고 " + view.treasury());
        g.drawString(this.font, tre, right - this.font.width(tre), panelY + 25, C_TOWN, false);

        // 판매 분배는 «화면에 보여야» 한다. 안 보이면 그냥 떼인 것으로만 느껴진다.
        g.drawString(this.font, Component.literal(selling
                ? "판매액의 " + view.townCut() + "%는 마을 금고로 들어갑니다"
                : "개인 지갑 — 치장·편의·소모품"),
            x, panelY + 25, C_DIM, false);
        g.fill(x, panelY + LIST_TOP - 6, right, panelY + LIST_TOP - 5, C_DIVIDER);

        String lastGroup = "";
        for (int i = 0; i < LIST_ROWS && i + scroll < rowCount(); i++) {
            int y = panelY + LIST_TOP + i * ROW_H;
            String group;
            net.minecraft.world.item.ItemStack st;
            Component name;
            int color;
            Component note = null;

            if (selling) {
                ShopView.SellRow r = view.sell().get(i + scroll);
                group = r.group();
                st = r.stack();
                boolean has = r.have() > 0;
                name = Component.literal(st.getHoverName().getString()
                    + (has ? " ×" + r.have() : ""));
                color = has ? C_BODY : C_DIM;
                note = Component.literal("개당 " + r.unit());
            } else {
                ShopView.Row r = view.buy().get(i + scroll);
                group = r.group();
                st = r.stack();
                name = Component.literal(st.getHoverName().getString()
                    + (r.count() > 1 ? " ×" + r.count() : ""));
                color = r.afford() ? C_BODY : C_DIM;
                if (!r.afford()) note = Component.literal("부족");
            }

            // 묶음 이름은 «바뀔 때만» 찍는다. 매 줄에 찍으면 목록이 안 읽힌다.
            if (!group.equals(lastGroup)) {
                g.drawString(this.font, Component.literal(group), x, y - 1, C_SUB, false);
                lastGroup = group;
            }
            g.renderItem(st, x + 38, y);
            g.drawString(this.font, name, x + 58, y + 4, color, false);
            if (note != null) {
                int nc = selling ? C_DIM : C_NO;
                g.drawString(this.font, note, panelX + PANEL_W - 82 - this.font.width(note), y + 4, nc, false);
            }
        }

        if (rowCount() > LIST_ROWS) {
            g.drawString(this.font, Component.literal("휠로 넘기기  " + (scroll + 1) + "-"
                + Math.min(rowCount(), scroll + LIST_ROWS) + " / " + rowCount()),
                x, panelY + PANEL_H - 18, C_DIM, false);
        }

        for (var w : this.renderables) w.render(g, mouseX, mouseY, partial);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
