package com.laststardust.relics.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
//
// ── 남이 가진 가호는 회색으로 (2026-08-06) ──
// 중복 방지는 원래도 서버가 했다. 그런데 화면이 그걸 모르니 **고르고 확정을 누른 뒤에야**
// 거절당했고, 그때는 화면이 이미 닫힌 뒤라 채팅으로만 알게 됐다. 8종·8인이라 마지막 사람은
// 그 왕복을 일곱 번 겪는다.
//
// **여기 있는 건 표시일 뿐 판정이 아니다.** 서버는 여전히 `ftChoose` 에서 막는다 —
// 이 목록은 화면을 연 순간의 스냅샷이라, 그 사이에 남이 먼저 고르면 여기선 여전히 「가능」으로
// 보인다. 그 경우 서버가 거절하고 **새 목록으로 화면을 다시 연다**(LSNetwork.handleChoose).
public class FateSelectScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int LIST_W = 132;
    private static final int ROW_H = 20;

    // ── 패널 높이는 «가호 수»에서 나온다 (2026-08-09) ──
    // 272 로 박혀 있었다. 8종일 때 맞춰 잡은 값이라 10종이 되자 마지막 줄(하르모니아)이
    // 바닥 안내문 위로 겹쳐 찍혔다. 상수로 두면 가호를 늘릴 때마다 같은 일이 난다.
    //   34  머리말(제목 + 구분선)   ·  N × 20  목록
    //   72  목록 아래 여백 + 안내문 두 줄 + 확정 버튼
    private static final int HEAD_H = 34;
    private static final int FOOT_H = 72;

    private static int panelH() {
        return HEAD_H + FateCatalog.ALL.size() * ROW_H + FOOT_H;
    }

    /** 화면이 낮으면 패널이 잘린다 — 세로 여백 8px 을 남기고 그 안으로 접는다. */
    private int fitH() {
        return Math.min(panelH(), this.height - 16);
    }

    private static final int C_BACKDROP   = 0xE0101018; // 패널 바탕
    private static final int C_BORDER     = 0xFF3A3F55;
    private static final int C_ROW_HOVER  = 0x40FFFFFF;
    private static final int C_ROW_PICKED = 0x60FFD98A;
    private static final int C_DIVIDER    = 0xFF2A2E40;
    private static final int C_SUB        = 0xFF9AA4B2;
    private static final int C_BODY       = 0xFFC7CDD6;
    private static final int C_TAKEN      = 0xFF5A6070; // 남이 가진 가호 — 읽히되 고를 수 없어 보이게

    private final String current;   // 이미 받은 가호 키 (없으면 "")
    private final boolean locked;   // 이미 가호가 있으면 선택 불가 — 가호는 1회 선택이다
    private final Map<String, String> taken;  // 가호 키 -> 그걸 가진 사람 (내 것은 안 들어온다)
    private int selected = 0;
    private int panelX, panelY;
    private Button confirm;

    public FateSelectScreen(String current, String takenCsv) {
        super(Component.translatable("lsfate.gui.title"));
        this.current = current == null ? "" : current;
        this.locked = !this.current.isEmpty();
        this.taken = parseTaken(takenCsv);

        // 처음 고를 때는 **고를 수 있는 첫 칸**을 잡는다. 0번(이지스)이 이미 남의 것이면
        // 열자마자 「선택 불가」가 떠 있고, 그건 화면이 고장난 것처럼 보인다.
        this.selected = firstSelectable();
        for (int i = 0; i < FateCatalog.ALL.size(); i++) {
            if (FateCatalog.ALL.get(i).key().equals(this.current)) {
                this.selected = i;   // 이미 받았으면 내 가호를 보여준다
                break;
            }
        }
    }

    // `키:주인,키:주인`. 형식이 깨진 조각은 버린다 — 화면 하나 때문에 예외를 던질 이유가 없고,
    // 못 읽은 항목은 「비어 있음」으로 보일 뿐이라 서버 판정이 그대로 받아낸다.
    private static Map<String, String> parseTaken(String csv) {
        Map<String, String> out = new HashMap<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String part : csv.split(",")) {
            int cut = part.indexOf(':');
            if (cut <= 0 || cut == part.length() - 1) continue;
            out.put(part.substring(0, cut), part.substring(cut + 1));
        }
        return out;
    }

    private boolean isTaken(int i) {
        return taken.containsKey(FateCatalog.ALL.get(i).key());
    }

    private int firstSelectable() {
        for (int i = 0; i < FateCatalog.ALL.size(); i++) {
            if (!isTaken(i)) return i;
        }
        return 0;   // 여덟 개가 전부 남의 것 — 8인 만석이면 실제로 가능하다
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - fitH()) / 2;

        confirm = Button.builder(
                Component.translatable(locked ? "lsfate.gui.locked" : "lsfate.gui.confirm"),
                b -> choose())
            .bounds(panelX + PANEL_W - 122, panelY + fitH() - 30, 110, 20)
            .build();
        addRenderableWidget(confirm);
        refreshConfirm();
    }

    // 고른 칸이 바뀔 때마다 부른다. 버튼 라벨까지 바꾸는 이유: 회색 줄을 고르고 나서
    // 버튼이 그냥 «흐린 확정»이면 **왜 안 눌리는지**를 알 수 없다.
    private void refreshConfirm() {
        if (confirm == null) return;
        boolean blocked = locked || isTaken(selected);
        confirm.active = !blocked;
        confirm.setMessage(Component.translatable(
            locked ? "lsfate.gui.locked" : isTaken(selected) ? "lsfate.gui.taken" : "lsfate.gui.confirm"));
    }

    private void choose() {
        if (locked || isTaken(selected)) return;
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
            // 회색 줄도 **고를 수는 있다.** 눌러도 아무 반응이 없으면 「화면이 멈췄나」가 되는데,
            // 실제로는 남이 가진 것이라 못 고르는 것이다. 골라서 오른쪽에 그 이유를 보여준다.
            selected = row;
            refreshConfirm();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        // 패널
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + fitH(), C_BACKDROP);
        drawBorder(g, panelX, panelY, PANEL_W, fitH(), C_BORDER);

        // 제목
        g.drawCenteredString(this.font, this.title, panelX + PANEL_W / 2, panelY + 12, 0xFFFFD98A);
        g.fill(panelX + 10, panelY + 28, panelX + PANEL_W - 10, panelY + 29, C_DIVIDER);

        renderList(g, mouseX, mouseY);
        renderDetail(g);

        // 바닥 안내. 잠겨 있으면 그 이유를, 아니면 "유물은 지금 주는 게 아니다"를 알린다.
        // 큰 유물 아이콘 때문에 "고르면 저 무기를 준다"고 오해하기 쉬워서 반드시 붙여둔다.
        int noteY = panelY + fitH() - 46;
        if (locked) {
            g.drawString(this.font, Component.translatable("lsfate.gui.already"),
                panelX + 12, noteY, 0xFFE86A6A, false);
        } else if (isTaken(selected)) {
            // 잠긴 칸을 골랐을 때는 «누가 가졌나»가 제일 알고 싶은 것이다. 그걸 먼저 말한다.
            g.drawString(this.font,
                Component.translatable("lsfate.gui.taken_by", taken.get(FateCatalog.ALL.get(selected).key())),
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
            // 남이 가진 가호는 이름을 죽인다. **이름을 지우지는 않는다** — 뭐가 없어졌는지
            // 알 수 없으면 그건 정보가 아니라 구멍이다.
            boolean gone = isTaken(i);
            g.drawString(this.font, Component.translatable(f.nameKey()),
                x + 22, y + 6, gone ? C_TAKEN : f.color(), false);
            if (gone) {
                // 목록 폭이 좁아 이름만 흐리면 「비활성」인지 「이미 골랐다」인지 구분이 안 된다.
                // 오른쪽 끝에 자물쇠 한 글자를 붙인다. 누가 가졌는지는 오른쪽 상세가 말한다.
                g.drawString(this.font, "✖", x + LIST_W - 12, y + 6, C_TAKEN, false);
            }
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
