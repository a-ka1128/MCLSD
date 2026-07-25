package com.laststardust.relics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

// Last Stardust 전용 버튼. 바닐라의 회색 돌 텍스처 대신 화면 톤에 맞춘 납작한 사각형으로 그린다.
//
// 텍스처 파일은 쓰지 않는다 — 리소스팩 없이 색만으로 만들어야 배포가 단순하고,
// 이 UI 의 다른 요소(패널·게이지·구분선)가 이미 전부 사각형이라 결이 맞는다.
//
// 상태별로 테두리와 글자색이 갈린다:
//   비활성 → 전부 어둡게 (누를 수 없음이 한눈에)
//   기본   → 어두운 남색 + 회청 테두리
//   호버   → 금색 테두리 + 밝은 글자 (선택 가능한 것이 빛난다)
public class LSButton extends Button {

    private static final int BG          = 0xFF1A1E2B;
    private static final int BG_HOVER    = 0xFF262C40;
    private static final int BG_DISABLED = 0xFF15171F;

    private static final int LINE          = 0xFF3A3F55;
    private static final int LINE_HOVER    = 0xFFFFD98A;
    private static final int LINE_DISABLED = 0xFF24262E;

    private static final int TEXT          = 0xFFC7CDD6;
    private static final int TEXT_HOVER    = 0xFFFFF3D6;
    private static final int TEXT_DISABLED = 0xFF5A5F6B;

    // 강조 버튼(완성 등) — 조건이 갖춰졌을 때 초록으로 눈에 띄게
    private boolean accent = false;

    protected LSButton(int x, int y, int w, int h, Component msg, OnPress onPress) {
        super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
    }

    public static LSButton of(int x, int y, int w, int h, Component msg, OnPress onPress) {
        return new LSButton(x, y, w, h, msg, onPress);
    }

    public LSButton accent() {
        this.accent = true;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int x = getX(), y = getY(), w = this.width, h = this.height;
        boolean hover = isHovered() && this.active;

        int bg = !this.active ? BG_DISABLED : hover ? BG_HOVER : BG;
        int line = !this.active ? LINE_DISABLED : hover ? LINE_HOVER : LINE;
        int text = !this.active ? TEXT_DISABLED : hover ? TEXT_HOVER : TEXT;

        if (accent && this.active) {
            bg = hover ? 0xFF1E3326 : 0xFF17251C;
            line = hover ? 0xFF9FE8A8 : 0xFF4E7A57;
            text = hover ? 0xFFD8FFDE : 0xFF9FE8A8;
        }

        g.fill(x, y, x + w, y + h, bg);
        // 테두리 — 위/아래/좌/우
        g.fill(x, y, x + w, y + 1, line);
        g.fill(x, y + h - 1, x + w, y + h, line);
        g.fill(x, y, x + 1, y + h, line);
        g.fill(x + w - 1, y, x + w, y + h, line);

        // 위쪽 하이라이트 한 줄 — 납작한 사각형에 살짝 입체감만 준다
        if (this.active) g.fill(x + 1, y + 1, x + w - 1, y + 2, hover ? 0x22FFFFFF : 0x11FFFFFF);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        int tx = x + (w - font.width(getMessage())) / 2;
        int ty = y + (h - 8) / 2;
        g.drawString(font, getMessage(), tx, ty, text, false);
    }
}
