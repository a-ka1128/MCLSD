package com.laststardust.relics.client;

import com.laststardust.relics.relic.RelicView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 유물 제단 화면 — 제단을 우클릭하면 열린다.
 *
 * <p>보여주는 것: 내 유물 · 현재 성급 · <b>실제 수치</b> · 성급 사다리 · 전승.
 * 여태 이 숫자들은 문서에만 있었고 게임 안에서는 어디에도 안 보였다.
 *
 * <p>⚠️ {@code super.render()} 를 부르지 않는다 — {@code Screen.render()} 가 내부에서
 * {@code renderBackground()} 를 다시 불러(1.20.2+) 여기서 그린 글씨가 두 번째 블러에
 * 통째로 뭉개진다. {@code TownHubScreen} 이 같은 이유로 같은 선택을 하고 있다.
 */
public class RelicAltarScreen extends Screen {

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 268;

    private static final int C_BACKDROP = 0xE0101018;
    private static final int C_BORDER   = 0xFF3A3F55;
    private static final int C_DIVIDER  = 0xFF2A2E40;
    private static final int C_TITLE    = 0xFFFFD98A;
    private static final int C_SUB      = 0xFF9AA4B2;
    private static final int C_BODY     = 0xFFC7CDD6;
    private static final int C_DIM      = 0xFF6B7280;
    private static final int C_STAR_ON  = 0xFFFFD98A;
    private static final int C_STAR_OFF = 0xFF3A3F55;
    private static final int C_LORE     = 0xFF8FA0C0;

    private final RelicView view;
    private int panelX, panelY;

    public RelicAltarScreen(RelicView view) {
        super(Component.literal("별의 제단"));
        this.view = view == null ? RelicView.empty() : view;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
    }

    /** 정보를 읽는 창이라 조작을 막지 않는다 — 게임이 멈추면 공성 중에 못 연다. */
    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);

        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, C_BACKDROP);
        border(g, panelX, panelY, PANEL_W, PANEL_H, C_BORDER);

        int x = panelX + 16;
        int y = panelY + 14;

        // ── 머리 ──
        g.drawString(this.font, Component.literal(view.title()), x, y, C_TITLE, false);
        y += 12;
        if (!view.subtitle().isEmpty()) {
            g.drawString(this.font, Component.literal(view.subtitle()), x, y, C_SUB, false);
            y += 11;
        }
        // 성급은 «칸»으로 그린다 — 숫자보다 한눈에 들어오고, 남은 칸이 목표로 읽힌다.
        y += 3;
        int sx = x;
        for (int i = 1; i <= view.maxStar(); i++) {
            boolean on = i <= view.star();
            g.drawString(this.font, Component.literal("✦"), sx, y, on ? C_STAR_ON : C_STAR_OFF, false);
            sx += 11;
        }
        g.drawString(this.font, Component.literal(view.star() + " / " + view.maxStar() + "성"),
            sx + 6, y, C_SUB, false);
        y += 16;

        if (!view.kind().isEmpty()) {
            // 조작법은 길어서 접는다 — 자르면 「쉬프트+좌클릭」 같은 마지막 항목이 통째로 사라진다.
            for (var line : this.font.split(Component.literal(view.kind()), PANEL_W - 32)) {
                g.drawString(this.font, line, x, y, C_DIM, false);
                y += 10;
            }
        }

        y += 4;
        hr(g, x, y, PANEL_W - 32);
        y += 7;

        // ── 수치 ──
        // 왼쪽 이름 / 오른쪽 값. 값을 오른쪽에 «맞춰» 그려야 여러 줄이 표로 읽힌다.
        int valueRight = panelX + PANEL_W - 16;
        for (RelicView.Row r : view.rows()) {
            g.drawString(this.font, Component.literal(r.label()), x, y, C_SUB, false);
            Component val = Component.literal(r.value());
            int w = this.font.width(val);
            int color = r.color() == 0 ? C_BODY : (0xFF000000 | r.color());
            g.drawString(this.font, val, valueRight - w, y, color, false);
            y += 11;
        }

        // ── 성급 사다리 ──
        if (!view.ladder().isEmpty()) {
            y += 3;
            hr(g, x, y, PANEL_W - 32);
            y += 7;
            for (RelicView.Ladder l : view.ladder()) {
                int c = l.reached() ? C_BODY : C_DIM;
                String head = (l.reached() ? "✔ " : "· ") + l.star() + "성";
                g.drawString(this.font, Component.literal(head), x, y, l.reached() ? C_STAR_ON : C_STAR_OFF, false);
                g.drawString(this.font, Component.literal(l.desc()), x + 40, y, c, false);
                if (l.cost() > 0 && !l.reached()) {
                    Component cost = Component.literal("파편 " + l.cost());
                    g.drawString(this.font, cost, valueRight - this.font.width(cost), y, C_SUB, false);
                }
                y += 11;
            }
        }

        // ── 전승 ──
        if (!view.lore().isEmpty() || !view.echo().isEmpty()) {
            y += 3;
            hr(g, x, y, PANEL_W - 32);
            y += 7;
            if (!view.lore().isEmpty()) {
                for (var line : this.font.split(Component.literal(view.lore()), PANEL_W - 32)) {
                    g.drawString(this.font, line, x, y, C_LORE, false);
                    y += 10;
                }
            }
            // 「마지막으로 쥐었던 자」의 한 줄 — 유물을 가진 사람에게만 뜻이 있다.
            if (view.owned() && !view.echo().isEmpty()) {
                y += 2;
                g.drawString(this.font, Component.literal("\"" + view.echo() + "\""), x, y, C_DIM, false);
                y += 10;
                g.drawString(this.font, Component.literal("   — 이 무기를 마지막으로 쥐었던 자"), x, y, C_STAR_OFF, false);
                y += 10;
            }
        }

        // ── 바닥 안내 ──
        if (!view.footer().isEmpty()) {
            int fy = panelY + PANEL_H - 18;
            hr(g, x, fy - 6, PANEL_W - 32);
            g.drawString(this.font, Component.literal(view.footer()), x, fy, C_SUB, false);
        }

        // 위젯이 없으므로 super.render() 대신 아무것도 안 부른다(머리말 참고).
    }

    private void hr(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, C_DIVIDER);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
