package com.laststardust.relics.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.laststardust.relics.blessing.BlessMenu;
import com.laststardust.relics.blessing.BlessView;
import com.laststardust.relics.data.BlessingCatalog;
import com.laststardust.relics.data.BlessingCatalog.Blessing;
import com.laststardust.relics.data.BlessingCatalog.Slot;
import com.laststardust.relics.data.LSCurrency;
import com.laststardust.relics.network.BlessActionPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 별의 제단 화면.
 *
 * <p>세로 배치를 상수로 모아둔다 — 겹침을 눈으로 검산할 수 있어야 한다:
 * <pre>
 *   제목 8~16 · 슬롯 23~41 · 라벨 44~52 · 줄0 58~84 · 줄1 84~110
 *   안내 114~122 · 보관함 136~144 · 인벤 147~223
 * </pre>
 *
 * <p><b>스핀은 연기다.</b> 결과는 서버가 이미 정해서 보냈고({@code view.spun()}), 여기서는
 * 그 값에 «착지»하는 연출만 한다. 그래서 연출 도중에 창을 닫아도 결과가 안 날아간다.
 */
public class BlessScreen extends AbstractContainerScreen<BlessMenu> {

    private static final int Y_TITLE = 8;
    private static final int Y_LABEL = 44;   // 슬롯 아래 라벨
    private static final int Y_ROW0  = 58;
    private static final int ROW_H   = 26;
    private static final int Y_HINT  = 114;
    private static final int Y_INVLBL = 136;

    private static final int C_PANEL   = 0xF0101018;
    private static final int C_BORDER  = 0xFF3A3F55;
    private static final int C_SLOT    = 0xFF23262F;
    private static final int C_SLOT_HI = 0xFF2E3648;
    private static final int C_TITLE   = 0xFFFFD98A;
    private static final int C_SUB     = 0xFF9AA4B2;
    private static final int C_BODY    = 0xFFC7CDD6;
    private static final int C_OK      = 0xFF7FD98A;
    private static final int C_BAD     = 0xFFE08A8A;
    private static final int C_SPIN    = 0xFFFFF3D6;

    /** 스핀 길이(클라 틱). 2초 — 짧으면 연출이 안 읽히고 길면 리롤을 반복할 때 답답하다. */
    private static final int SPIN_TICKS = 40;

    private BlessView view = BlessView.empty();
    private final Map<Slot, Integer> spin = new EnumMap<>(Slot.class);
    /** 스핀 중 이름이 몇 칸 지나갔나 — 슬롯마다 시작점을 달리해 둘이 같이 돌아도 안 겹친다. */
    private final Map<Slot, Integer> spinSeed = new EnumMap<>(Slot.class);
    private int seedTick;

    public BlessScreen(BlessMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = BlessMenu.PANEL_W;
        this.imageHeight = BlessMenu.PANEL_H;
    }

    public void setView(BlessView v) {
        this.view = v == null ? BlessView.empty() : v;
        if (!this.view.spun().isEmpty()) {
            try {
                Slot s = Slot.valueOf(this.view.spun());
                spin.put(s, SPIN_TICKS);
                spinSeed.put(s, seedTick);
            } catch (IllegalArgumentException ignored) { }
        }
        if (this.minecraft != null) rebuildWidgets();
    }

    /** 장비칸에 마지막으로 있던 것. 바뀌면 버튼을 다시 짜야 한다. */
    private ItemStack lastGear = ItemStack.EMPTY;

    @Override
    protected void containerTick() {
        super.containerTick();
        seedTick++;
        boolean wasSpinning = !spin.isEmpty();
        spin.replaceAll((k, v) -> v - 1);
        spin.entrySet().removeIf(e -> e.getValue() <= 0);
        // 스핀이 끝나면 버튼을 되살린다 — 도는 동안은 눌러도 헛돈다.
        if (wasSpinning && spin.isEmpty()) rebuildWidgets();

        // ── 장비칸이 바뀌면 다시 짠다 ──
        // init() 은 화면을 열 때와 크기가 바뀔 때만 불린다. **슬롯에 아이템을 넣는 건
        // init() 을 안 부른다** — 그냥 두면 유물을 올려도 버튼이 안 생기고, 「올렸는데
        // 아무 일도 안 일어난다」가 된다. 여기서 직접 알아채야 한다.
        ItemStack gear = this.menu.gear();
        if (!ItemStack.isSameItem(gear, lastGear)) {
            lastGear = gear.copy();
            rebuildWidgets();
        }
    }

    private boolean spinning() { return !spin.isEmpty(); }

    // ══════════════════════════════════════════════════════════════════
    //  버튼 — 줄마다 자기 것을 갖는다.
    //  빈 칸이면 [축복하기] 하나, 차 있으면 [종류][수치] 둘.
    //  전역 버튼 하나로 묶으면 "어느 칸에?"를 또 물어야 한다.
    // ══════════════════════════════════════════════════════════════════
    @Override
    protected void init() {
        super.init();
        List<Slot> visible = this.menu.visibleSlots();
        if (visible.isEmpty() || spinning()) return;

        for (int i = 0; i < visible.size(); i++) {
            Slot s = visible.get(i);
            BlessView.SlotView sv = view.slot(s);
            if (sv == null || !sv.unlocked()) continue;

            int y = this.topPos + Y_ROW0 + i * ROW_H + 8;
            if (sv.empty()) {
                addRenderableWidget(LSButton.of(this.leftPos + this.imageWidth - 88, y, 80, 16,
                    Component.translatable("lsblessing.gui.bless"),
                    b -> send(s, "bless")).accent());
            } else {
                addRenderableWidget(LSButton.of(this.leftPos + this.imageWidth - 88, y, 38, 16,
                    Component.translatable("lsblessing.gui.kind"), b -> send(s, "kind")));
                addRenderableWidget(LSButton.of(this.leftPos + this.imageWidth - 46, y, 38, 16,
                    Component.translatable("lsblessing.gui.value"), b -> send(s, "value")));
            }
        }
    }

    private void send(Slot s, String action) {
        PacketDistributor.sendToServer(new BlessActionPayload(s.name(), action));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        g.fill(x, y, x + this.imageWidth, y + this.imageHeight, C_PANEL);
        border(g, x, y, this.imageWidth, this.imageHeight);

        slotBg(g, x + BlessMenu.gearX(), y + BlessMenu.gearY(), C_SLOT_HI);
        slotBg(g, x + BlessMenu.costX(), y + BlessMenu.costY(), C_SLOT_HI);

        g.fill(x + 8, y + Y_INVLBL - 6, x + this.imageWidth - 8, y + Y_INVLBL - 5, C_BORDER);

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                slotBg(g, x + BlessMenu.invX(col), y + BlessMenu.invY(row), C_SLOT);
        for (int col = 0; col < 9; col++)
            slotBg(g, x + BlessMenu.invX(col), y + BlessMenu.hotbarY(), C_SLOT);
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
        g.drawString(this.font, this.title, 8, Y_TITLE, C_TITLE, false);

        String star = "✦ " + view.star();
        g.drawString(this.font, star, this.imageWidth - 8 - this.font.width(star), Y_TITLE, C_SUB, false);

        // 슬롯 라벨
        center(g, Component.translatable("lsblessing.gui.gear").getString(),
            BlessMenu.gearX() + 8, Y_LABEL, C_SUB);
        center(g, Component.translatable("lsblessing.gui.cost").getString(),
            BlessMenu.costX() + 8, Y_LABEL, C_SUB);

        // 제단이 아직 안 열렸으면 그것부터 알려준다 — 버튼을 눌러 봐야 채팅만 나온다.
        if (!view.altarUnlocked()) {
            wrap(g, Component.translatable("lsblessing.gui.locked").getString(), 8, Y_ROW0, C_BAD);
            g.drawString(this.font, this.playerInventoryTitle, 8, Y_INVLBL, C_SUB, false);
            return;
        }

        List<Slot> visible = this.menu.visibleSlots();
        if (visible.isEmpty()) {
            wrap(g, Component.translatable("lsblessing.gui.put_gear").getString(), 8, Y_ROW0, C_SUB);
        } else {
            for (int i = 0; i < visible.size(); i++) row(g, visible.get(i), Y_ROW0 + i * ROW_H);
        }

        // 비용 안내 — 무엇을 올려야 하는지 글로도 한 번.
        String hint = Component.translatable("lsblessing.gui.costs",
            view.kindCost(), view.valueCost()).getString();
        g.drawString(this.font, hint, 8, Y_HINT, C_SUB, false);

        g.drawString(this.font, this.playerInventoryTitle, 8, Y_INVLBL, C_SUB, false);
    }

    /** 줄 하나 — 이름·수치·백분위, 또는 스핀 중이면 돌아가는 이름. */
    private void row(GuiGraphics g, Slot s, int y) {
        BlessView.SlotView sv = view.slot(s);
        String label = Component.translatable(s.nameKey()).getString();

        if (sv == null || !sv.unlocked()) {
            g.drawString(this.font, "§8[" + label + "] "
                + Component.translatable("lsblessing.gui.need_star", s.star).getString(),
                8, y + 8, C_SUB, false);
            return;
        }

        g.drawString(this.font, "§8[" + label + "]", 8, y, C_SUB, false);

        Integer left = spin.get(s);
        if (left != null) {
            drawSpin(g, s, sv, left, y + 10);
            return;
        }

        if (sv.empty()) {
            g.drawString(this.font, Component.translatable("lsblessing.gui.empty"), 8, y + 10, C_SUB, false);
            return;
        }

        Blessing b = sv.blessing();
        if (b == null) return;
        g.drawString(this.font, Component.translatable(b.nameKey()), 8, y + 10, C_BODY, false);
        String val = com.laststardust.relics.blessing.BlessingService.fmt(sv.value()) + "%";
        g.drawString(this.font, val, 8 + 78, y + 10, C_OK, false);
        String pct = "§8" + com.laststardust.relics.blessing.BlessingService.fmt(b.min()) + "~"
            + com.laststardust.relics.blessing.BlessingService.fmt(b.max())
            + " · " + Math.round(sv.percentile() * 100) + "%";
        g.drawString(this.font, pct, 8, y + 19, C_SUB, false);
    }

    /**
     * 스핀 — 후보 이름을 훑다가 감속해 결과에 착지한다.
     *
     * <p>후보 목록은 <b>클라가 직접 만든다</b>({@link BlessingCatalog} 는 공용 코드다). 서버에
     * 묻지 않으므로 지연이 없다. 그리고 도는 동안 <b>후보 이름이 전부 지나가므로</b>
     * 「당첨/꽝」이 아니라 「이 중 어느 것」으로 읽힌다 — 사양 7절의 그 요구다.
     */
    private void drawSpin(GuiGraphics g, Slot s, BlessView.SlotView sv, int left, int y) {
        List<Blessing> pool = new ArrayList<>(BlessingCatalog.pool(s.axis));
        if (pool.isEmpty()) return;

        float t = 1f - (left / (float) SPIN_TICKS);          // 0 → 1
        float eased = 1f - (float) Math.pow(1f - t, 3);      // 빨리 시작해 느리게 멎는다
        int steps = Math.round(eased * pool.size() * 4);     // 네 바퀴쯤 돈다
        int seed = spinSeed.getOrDefault(s, 0);
        Blessing shown = pool.get(Math.floorMod(seed + steps, pool.size()));

        g.drawString(this.font, Component.translatable(shown.nameKey()), 8, y, C_SPIN, false);
        // 수치는 «범위 안 아무 값»으로 흔든다. 착지 값을 미리 보여주면 연출이 무의미해진다.
        float shake = shown.min() + (shown.max() - shown.min()) * ((seed * 37 + steps * 17) % 100) / 100f;
        g.drawString(this.font, com.laststardust.relics.blessing.BlessingService.fmt(shake) + "%",
            8 + 78, y, C_SPIN, false);
    }

    private void center(GuiGraphics g, String text, int cx, int y, int color) {
        g.drawString(this.font, text, cx - this.font.width(text) / 2, y, color, false);
    }

    /** 패널 폭에 맞춰 줄바꿈. 안내 문구가 길어 한 줄로는 안 들어간다. */
    private void wrap(GuiGraphics g, String text, int x, int y, int color) {
        int max = this.imageWidth - 16;
        int line = 0;
        for (var s : this.font.split(Component.literal(text), max)) {
            g.drawString(this.font, s, x, y + line * 10, color, false);
            line++;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);

        // 빈 재료칸에 «지금 필요한» 재화를 흐리게 — 무엇을 올려야 하는지 글로 읽을 필요가 없다.
        if (this.menu.cost().isEmpty()) {
            ItemStack ghost = new ItemStack(LSCurrency.essence() == null
                ? net.minecraft.world.item.Items.AMETHYST_SHARD : LSCurrency.essence());
            int gx = this.leftPos + BlessMenu.costX();
            int gy = this.topPos + BlessMenu.costY();
            g.pose().pushPose();
            g.pose().translate(0, 0, 100);
            g.setColor(1f, 1f, 1f, 0.25f);
            g.renderItem(ghost, gx, gy);
            g.setColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
        }
        this.renderTooltip(g, mouseX, mouseY);
    }
}
