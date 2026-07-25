package com.laststardust.relics.client;

import java.util.List;

import com.laststardust.relics.FateCatalog;
import com.laststardust.relics.network.FateChoosePayload;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

// 별의 가호 선택 화면.
// 왼쪽에 8가호 목록(유물 아이콘 + 이름), 오른쪽에 고른 가호의 소개와 패시브.
// 선택을 확정하면 서버로 fate_choose 를 보내고, 실제 처리는 KubeJS(ls_fate.js)가 한다.
//
// 텍스처 에셋은 쓰지 않는다 — 패널은 사각형으로 그리고, 각 가호의 "그림"은 그 유물 아이템을 그대로 렌더한다.
public class FateSelectScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 272;
    private static final int LIST_W = 132;
    private static final int ROW_H = 20;

    private static final int C_BACKDROP   = 0xE0101018; // 패널 바탕
    private static final int C_BORDER     = 0xFF3A3F55;
    private static final int C_ROW_HOVER  = 0x40FFFFFF;
    private static final int C_ROW_PICKED = 0x60FFD98A;
    private static final int C_DIVIDER    = 0xFF2A2E40;
    private static final int C_SUB        = 0xFF9AA4B2;
    private static final int C_BODY       = 0xFFC7CDD6;

    private final String current;   // 이미 받은 가호 키 (없으면 "")
    private final boolean locked;   // 이미 가호가 있으면 선택 불가 — 가호는 1회 선택이다
    private int selected = 0;
    private int panelX, panelY;
    private Button confirm;

    public FateSelectScreen(String current) {
        super(Component.translatable("lsfate.gui.title"));
        this.current = current == null ? "" : current;
        this.locked = !this.current.isEmpty();
        for (int i = 0; i < FateCatalog.ALL.size(); i++) {
            if (FateCatalog.ALL.get(i).key().equals(this.current)) {
                this.selected = i;
                break;
            }
        }
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        confirm = Button.builder(
                Component.translatable(locked ? "lsfate.gui.locked" : "lsfate.gui.confirm"),
                b -> choose())
            .bounds(panelX + PANEL_W - 122, panelY + PANEL_H - 30, 110, 20)
            .build();
        confirm.active = !locked;
        addRenderableWidget(confirm);
    }

    private void choose() {
        if (locked) return;
        PacketDistributor.sendToServer(new FateChoosePayload(FateCatalog.ALL.get(selected).key()));
        onClose();
    }

    // 목록 행의 화면상 y 좌표
    private int rowY(int i) {
        return panelY + 34 + i * ROW_H;
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < panelX + 10 || mouseX > panelX + 10 + LIST_W) return -1;
        for (int i = 0; i < FateCatalog.ALL.size(); i++) {
            if (mouseY >= rowY(i) && mouseY < rowY(i) + ROW_H) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            selected = row;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // 패널
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, C_BACKDROP);
        drawBorder(g, panelX, panelY, PANEL_W, PANEL_H, C_BORDER);

        // 제목
        g.drawCenteredString(this.font, this.title, panelX + PANEL_W / 2, panelY + 12, 0xFFFFD98A);
        g.fill(panelX + 10, panelY + 28, panelX + PANEL_W - 10, panelY + 29, C_DIVIDER);

        renderList(g, mouseX, mouseY);
        renderDetail(g);

        // 바닥 안내. 잠겨 있으면 그 이유를, 아니면 "유물은 지금 주는 게 아니다"를 알린다.
        // 큰 유물 아이콘 때문에 "고르면 저 무기를 준다"고 오해하기 쉬워서 반드시 붙여둔다.
        int noteY = panelY + PANEL_H - 46;
        if (locked) {
            g.drawString(this.font, Component.translatable("lsfate.gui.already"),
                panelX + 12, noteY, 0xFFE86A6A, false);
        } else {
            drawWrapped(g, Component.translatable("lsfate.gui.hint"), panelX + 12, noteY, 280, 0xFF8A8F9E);
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < FateCatalog.ALL.size(); i++) {
            FateCatalog.Fate f = FateCatalog.ALL.get(i);
            int y = rowY(i);
            int x = panelX + 10;
            if (i == selected) {
                g.fill(x, y, x + LIST_W, y + ROW_H, C_ROW_PICKED);
            } else if (i == hovered) {
                g.fill(x, y, x + LIST_W, y + ROW_H, C_ROW_HOVER);
            }
            // 아이콘 = 그 가호의 유물
            g.renderItem(new ItemStack(f.relic().get()), x + 2, y + 2);
            g.drawString(this.font, Component.translatable(f.nameKey()), x + 22, y + 6, f.color(), false);
        }
    }

    private void renderDetail(GuiGraphics g) {
        FateCatalog.Fate f = FateCatalog.ALL.get(selected);
        int x = panelX + LIST_W + 22;
        int y = panelY + 34;
        int w = PANEL_W - LIST_W - 34;

        // 큰 유물 아이콘 (2배)
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.renderItem(new ItemStack(f.relic().get()), 0, 0);
        g.pose().popPose();

        g.drawString(this.font, Component.translatable(f.nameKey()), x + 40, y + 3, f.color(), false);
        g.drawString(this.font,
            Component.translatable(f.epithetKey()).append(" · ").append(Component.translatable(f.roleKey())),
            x + 40, y + 16, C_SUB, false);

        int ty = y + 40;

        // 이 가호가 "장차" 다루게 될 유물. 지금 주는 게 아니라는 건 아래 안내문이 설명한다.
        Component relicLabel = Component.translatable("lsfate.gui.relic");
        g.drawString(this.font, relicLabel, x, ty, C_SUB, false);
        int off = this.font.width(relicLabel) + 4;
        g.drawString(this.font, new ItemStack(f.relic().get()).getHoverName(), x + off, ty, f.color(), false);
        ty += this.font.lineHeight + 5;

        // 반대로 "지금 당장" 받는 것 — 패시브와 시작 키트
        g.drawString(this.font, Component.translatable("lsfate.gui.grant"), x, ty, C_SUB, false);
        ty += this.font.lineHeight + 1;
        ty = drawWrapped(g, Component.translatable(f.passiveKey()).withStyle(ChatFormatting.YELLOW), x, ty, w) + 5;

        g.fill(x, ty, x + w, ty + 1, C_DIVIDER);
        ty += 7;
        ty = drawWrapped(g, Component.translatable(f.hookKey()).withStyle(ChatFormatting.ITALIC), x, ty, w) + 4;
        drawWrapped(g, Component.translatable(f.loreKey()), x, ty, w);
    }

    // 폭에 맞춰 줄바꿈해 그리고, 다음 줄이 시작될 y 를 돌려준다
    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int width) {
        return drawWrapped(g, text, x, y, width, C_BODY);
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int color) {
        List<FormattedCharSequence> lines = this.font.split(text, width);
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x, y, color, false);
            y += this.font.lineHeight + 1;
        }
        return y;
    }

    private void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
