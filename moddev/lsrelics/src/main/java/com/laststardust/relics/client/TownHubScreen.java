package com.laststardust.relics.client;

import com.laststardust.relics.data.TownCatalog;
import com.laststardust.relics.network.TownActionPayload;
import com.laststardust.relics.town.TownView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

// [성역] 허브 — 키(기본 N)로 열린다. 4개 트랙의 현황을 한눈에 보고 하나를 골라 들어간다.
//
// ※ super.render() 를 부르지 않는다 — Screen.render() 가 내부에서 renderBackground() 를
//   다시 호출해(1.20.2+) 여기서 그린 글씨가 두 번째 블러에 통째로 뭉개진다. 위젯만 직접 그린다.
public class TownHubScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 250;
    private static final int ROW_H = 42;

    private static final int C_BACKDROP = 0xE0101018;
    private static final int C_BORDER   = 0xFF3A3F55;
    private static final int C_DIVIDER  = 0xFF2A2E40;
    private static final int C_TITLE    = 0xFFFFD98A;
    private static final int C_SUB      = 0xFF9AA4B2;
    private static final int C_BODY     = 0xFFC7CDD6;
    private static final int C_OK       = 0xFF7FD98A;
    private static final int C_BAD      = 0xFFE08A8A;
    private static final int C_BAR_BG   = 0xFF23262F;
    private static final int C_BAR_LV   = 0xFFFFD98A;

    private TownView view;
    private int panelX, panelY;

    public TownHubScreen(TownView view) {
        super(Component.translatable("lstown.gui.title"));
        this.view = view == null ? TownView.empty() : view;
    }

    public void setView(TownView v) {
        this.view = v == null ? TownView.empty() : v;
        if (this.minecraft != null) rebuildWidgets();
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        for (int i = 0; i < TownCatalog.ALL.size(); i++) {
            final String key = TownCatalog.ALL.get(i).key();
            var t = view.track(key);
            var btn = LSButton.of(panelX + PANEL_W - 82, rowY(i) + 10, 70, 18,
                Component.translatable("lstown.gui.enter"),
                b -> PacketDistributor.sendToServer(new TownActionPayload("open_track", key)));
            if (t != null && t.canUpgrade()) btn.accent();   // 완성 가능한 트랙은 초록으로 눈에 띄게
            addRenderableWidget(btn);
        }

        addRenderableWidget(LSButton.of(panelX + PANEL_W - 82, panelY + PANEL_H - 26, 70, 18,
            Component.translatable("lstown.gui.close"), b -> onClose()));
    }

    private int rowY(int i) {
        return panelY + 62 + i * ROW_H;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);

        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, C_BACKDROP);
        border(g, panelX, panelY, PANEL_W, PANEL_H);

        g.drawString(this.font, this.title, panelX + 12, panelY + 12, C_TITLE, false);
        String tre = Component.translatable("lstown.gui.treasury").getString()
            + "  §e" + String.format("%,d", view.treasury()) + " Ducat";
        g.drawString(this.font, tre, panelX + 12, panelY + 30, C_BODY, false);
        g.fill(panelX + 8, panelY + 48, panelX + PANEL_W - 8, panelY + 49, C_DIVIDER);

        for (int i = 0; i < view.tracks().size() && i < 4; i++) {
            TownView.TrackView t = view.tracks().get(i);
            TownCatalog.Track def = TownCatalog.byKey(t.key());
            int y = rowY(i);
            String icon = def == null ? "·" : def.icon();
            String name = def == null ? t.key() : Component.translatable(def.nameKey()).getString();

            g.drawString(this.font, "§f" + icon + " " + name, panelX + 14, y, C_TITLE, false);
            segments(g, panelX + 14 + 70, y + 1, t.max(), t.level());

            if (t.isMax()) {
                g.drawString(this.font, Component.translatable("lstown.gui.max"), panelX + 22, y + 14, C_OK, false);
            } else {
                g.drawString(this.font, Component.literal("§7→ §f").append(t.nextName()), panelX + 22, y + 13, C_BODY, false);
                boolean ok = t.canUpgrade();
                var cost = t.itemName().copy()
                    .append(net.minecraft.network.chat.Component.literal(
                        "  " + t.have() + "/" + t.need() + "   " + t.ducat() + " Ducat"));
                g.drawString(this.font, cost, panelX + 22, y + 24, ok ? C_OK : C_SUB, false);
                if (ok) {
                    String ready = Component.translatable("lstown.gui.ready").getString();
                    g.drawString(this.font, ready, panelX + PANEL_W - 92 - this.font.width(ready), y + 14, C_OK, false);
                }
            }
        }

        if (view.tracks().isEmpty()) {
            g.drawString(this.font, Component.translatable("lstown.gui.loading"), panelX + 14, panelY + 64, C_SUB, false);
        }

        // 기여도 한 줄
        if (!view.board().isEmpty()) {
            StringBuilder sb = new StringBuilder(Component.translatable("lstown.gui.board").getString() + "  ");
            for (int i = 0; i < view.board().size() && i < 3; i++) {
                TownView.Contributor c = view.board().get(i);
                sb.append(i == 0 ? "§e" : i == 1 ? "§f" : "§6").append(c.name()).append(" §8").append(c.points()).append("  ");
            }
            g.drawString(this.font, sb.toString(), panelX + 12, panelY + PANEL_H - 22, C_SUB, false);
        }

        for (Renderable r : this.renderables) r.render(g, mouseX, mouseY, partial);
    }

    private void segments(GuiGraphics g, int x, int y, int total, int filled) {
        for (int i = 0; i < total; i++) {
            int sx = x + i * 11;
            g.fill(sx, y, sx + 8, y + 6, i < filled ? C_BAR_LV : C_BAR_BG);
        }
    }

    private void border(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + 1, C_BORDER);
        g.fill(x, y + h - 1, x + w, y + h, C_BORDER);
        g.fill(x, y, x + 1, y + h, C_BORDER);
        g.fill(x + w - 1, y, x + w, y + h, C_BORDER);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (TownKeybind.OPEN_TOWN.matches(keyCode, scanCode)) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
