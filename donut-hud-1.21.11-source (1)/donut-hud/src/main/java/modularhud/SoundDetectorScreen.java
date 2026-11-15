package modularhud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Settings for the sound detector: on/off, chunk-range slider, and which blocks/entities to box. */
public class SoundDetectorScreen extends Screen {
    private static final int PAD = 14;
    private static final int BTN_W = 124, GAP = 6, ROW_H = 22;

    private final Screen parent;
    private int panelX, panelY, panelW, panelH, headerY, leftX;

    public SoundDetectorScreen(Screen parent) {
        super(Text.literal("Sound Detector"));
        this.parent = parent;
    }

    // ---- slider ----
    private static final class RangeSlider extends SliderWidget {
        RangeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Text.empty(), toValue(HudConfig.get().range()));
            updateMessage();
        }

        private static double toValue(int range) {
            return (range - HudConfig.MIN_RANGE) / (double) (HudConfig.MAX_RANGE - HudConfig.MIN_RANGE);
        }

        @Override
        protected void updateMessage() {
            int r = HudConfig.get().range();
            this.setMessage(Text.literal("Chunk range: " + r + " (" + (r * 16) + " blocks)"));
        }

        @Override
        protected void applyValue() {
            int r = HudConfig.MIN_RANGE
                    + (int) Math.round(this.value * (HudConfig.MAX_RANGE - HudConfig.MIN_RANGE));
            HudConfig.get().soundRange = r;
            this.value = toValue(r);   // snap to whole chunks
            HudConfig.save();
        }
    }

    @Override
    protected void init() {
        HudConfig cfg = HudConfig.get();
        int contentW = BTN_W * 3 + GAP * 2;
        int half = (contentW - GAP) / 2;

        List<SoundTarget> blocks = SoundTarget.blocks();
        List<SoundTarget> ents = SoundTarget.entities();
        int rows = 5;

        panelW = contentW + PAD * 2;
        panelH = 34 + ROW_H + ROW_H + 16 + rows * ROW_H + 6 + 20 + PAD;
        panelX = this.width / 2 - panelW / 2;
        panelY = Math.max(2, (this.height - panelH) / 2);
        leftX = panelX + PAD;

        int y = panelY + 34;

        // enable + labels
        this.addDrawableChild(ButtonWidget.builder(enabledText(cfg), b -> {
            cfg.soundEnabled = !cfg.soundEnabled;
            b.setMessage(enabledText(cfg));
            HudConfig.save();
        }).dimensions(leftX, y, half, 20).build());

        this.addDrawableChild(ButtonWidget.builder(labelsText(cfg), b -> {
            cfg.soundLabels = !cfg.soundLabels;
            b.setMessage(labelsText(cfg));
            HudConfig.save();
        }).dimensions(leftX + half + GAP, y, half, 20).build());
        y += ROW_H;

        // chunk-range slider
        this.addDrawableChild(new RangeSlider(leftX, y, contentW, 20));
        y += ROW_H;

        headerY = y + 3;
        y += 16;

        // blocks fill columns 0 and 1, entities column 2
        for (int i = 0; i < blocks.size(); i++) {
            int col = i / rows, row = i % rows;
            addTargetButton(cfg, blocks.get(i), leftX + col * (BTN_W + GAP), y + row * ROW_H);
        }
        for (int i = 0; i < ents.size(); i++) {
            addTargetButton(cfg, ents.get(i), leftX + 2 * (BTN_W + GAP), y + i * ROW_H);
        }

        y += rows * ROW_H + 6;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> this.close())
                .dimensions(this.width / 2 - 50, y, 100, 20).build());
    }

    private void addTargetButton(HudConfig cfg, SoundTarget t, int x, int y) {
        this.addDrawableChild(ButtonWidget.builder(targetText(cfg, t), b -> {
            cfg.setTarget(t, !cfg.targetOn(t));
            b.setMessage(targetText(cfg, t));
            HudConfig.save();
        }).dimensions(x, y, BTN_W, 20).build());
    }

    private static Text enabledText(HudConfig c) {
        return Text.literal("Detector: " + (c.soundEnabled ? "ON" : "OFF"));
    }

    private static Text labelsText(HudConfig c) {
        return Text.literal("Labels: " + (c.soundLabels ? "ON" : "OFF"));
    }

    private static Text targetText(HudConfig c, SoundTarget t) {
        return Text.literal(t.label + ": " + (c.targetOn(t) ? "ON" : "OFF"));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        Theme.panel(ctx, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, panelY + 10, Theme.ACCENT_A);
        Theme.hLine(ctx, panelX + PAD, panelY + 24, panelW - PAD * 2, Theme.ACCENT_A, Theme.ACCENT_B);

        int twoCols = BTN_W * 2 + GAP;
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Blocks", leftX + twoCols / 2, headerY, Theme.MUTED);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Entities",
                leftX + 2 * (BTN_W + GAP) + BTN_W / 2, headerY, Theme.MUTED);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public void removed() {
        HudConfig.save();
    }
}
