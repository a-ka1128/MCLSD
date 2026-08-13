package com.laststardust.relics.client;

import com.laststardust.relics.network.ShopBuyPayload;
import com.laststardust.relics.shop.ShopView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 상인 광장 상점 — 상인 NPC 를 클릭하면 열린다.
 *
 * <p>⚠️ {@code super.render()} 를 부르지 않는다 — {@code Screen.render()} 가 내부에서
 * {@code renderBackground()} 를 다시 불러(1.20.2+) 여기서 그린 글씨가 두 번째 블러에
 * 뭉개진다. 위젯은 직접 그린다({@code TownHubScreen} 과 같은 선택).
 */
public class ShopScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int PANEL_H = 268;
    private static final int ROW_H = 22;
    private static final int LIST_TOP = 46;
    private static final int LIST_ROWS = 9;   // 한 화면에 보이는 줄 수

    private static final int C_BACKDROP = 0xE0101018;
    private static final int C_BORDER   = 0xFF3A3F55;
    private static final int C_DIVIDER  = 0xFF2A2E40;
    private static final int C_TITLE    = 0xFFFFD98A;
    private static final int C_SUB      = 0xFF9AA4B2;
    private static final int C_BODY     = 0xFFC7CDD6;
    private static final int C_DIM      = 0xFF6B7280;
    private static final int C_COIN     = 0xFFFFD98A;
    private static final int C_NO       = 0xFFE08A8A;

    private ShopView view;
    private int panelX, panelY;
    private int scroll;

    public ShopScreen(ShopView view) {
        super(Component.literal("상인 광장"));
        this.view = view == null ? ShopView.empty() : view;
    }

    public void setView(ShopView v) {
        this.view = v == null ? ShopView.empty() : v;
        // ⚠️ 스크롤은 그대로 둔다. 산 뒤에 목록이 맨 위로 튀면 연달아 사기가 괴롭다.
        if (this.minecraft != null) rebuildWidgets();
    }

    /** 정보를 읽고 사는 창이라 게임을 멈추지 않는다 — 공성 중에도 열 수 있어야 한다. */
    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        var rows = view.rows();
        int max = Math.max(0, rows.size() - LIST_ROWS);
        if (scroll > max) scroll = max;

        for (int i = 0; i < LIST_ROWS && i + scroll < rows.size(); i++) {
            final ShopView.Row r = rows.get(i + scroll);
            var btn = LSButton.of(panelX + PANEL_W - 62, panelY + LIST_TOP + i * ROW_H + 2, 50, 18,
                Component.literal(r.price() + "d"),
                b -> PacketDistributor.sendToServer(new ShopBuyPayload(r.index())));
            // 못 사는 것은 눌리지 않게. 눌러 보고 실패 메시지를 받는 것보다
            // 애초에 안 눌리는 쪽이 「왜 안 되지」를 안 만든다.
            btn.active = r.afford();
            addRenderableWidget(btn);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int max = Math.max(0, view.rows().size() - LIST_ROWS);
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
        g.drawString(this.font, Component.literal("상인 광장"), x, panelY + 14, C_TITLE, false);

        // 잔액은 오른쪽 위 — 무엇을 살지 고르는 내내 눈에 있어야 한다.
        Component bal = Component.literal(view.balance() + " Ducat");
        g.drawString(this.font, bal, panelX + PANEL_W - 14 - this.font.width(bal), panelY + 14, C_COIN, false);
        g.drawString(this.font, Component.literal("개인 지갑 — 치장·편의·소모품"), x, panelY + 26, C_DIM, false);
        g.fill(x, panelY + LIST_TOP - 6, panelX + PANEL_W - 14, panelY + LIST_TOP - 5, C_DIVIDER);

        var rows = view.rows();
        String lastGroup = "";
        for (int i = 0; i < LIST_ROWS && i + scroll < rows.size(); i++) {
            ShopView.Row r = rows.get(i + scroll);
            int y = panelY + LIST_TOP + i * ROW_H;

            // 묶음 이름은 «바뀔 때만» 왼쪽에 찍는다. 매 줄에 찍으면 목록이 안 읽힌다.
            if (!r.group().equals(lastGroup)) {
                g.drawString(this.font, Component.literal(r.group()), x, y - 1, C_SUB, false);
                lastGroup = r.group();
            }
            var st = r.stack();
            g.renderItem(st, x + 38, y);
            Component nm = Component.literal(st.getHoverName().getString()
                + (r.count() > 1 ? " ×" + r.count() : ""));
            g.drawString(this.font, nm, x + 58, y + 4, r.afford() ? C_BODY : C_DIM, false);
            if (!r.afford()) {
                Component no = Component.literal("부족");
                g.drawString(this.font, no, panelX + PANEL_W - 70 - this.font.width(no), y + 4, C_NO, false);
            }
        }

        if (rows.size() > LIST_ROWS) {
            g.drawString(this.font, Component.literal("휠로 넘기기  " + (scroll + 1) + "-"
                + Math.min(rows.size(), scroll + LIST_ROWS) + " / " + rows.size()),
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
