package modularhud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public class HudConfigScreen extends Screen {
    private static final int ROW_H = 22;
    private static final int PAD = 14;

    private int panelX, panelY, panelW, panelH;

    public HudConfigScreen() {
        super(Text.translatable("modularhud.config.title"));
    }

    @Override
    protected void init() {
        HudConfig cfg = HudConfig.get();
        List<HudModule> mods = Modules.ALL;

        int toggleW = 104, anchorW = 86, gap = 4, colGap = 12;
        int pairW = toggleW + gap + anchorW;
        int contentW = pairW * 2 + colGap;
        int perCol = (mods.size() + 1) / 2;

        panelW = contentW + PAD * 2;
        panelH = 34 + perCol * ROW_H + 10 + ROW_H + 8 + 20 + PAD;
        panelX = this.width / 2 - panelW / 2;
        panelY = Math.max(2, (this.height - panelH) / 2);

        int left = panelX + PAD;
        int top = panelY + 34;

        for (int i = 0; i < mods.size(); i++) {
            HudModule m = mods.get(i);
            HudConfig.Entry e = cfg.entry(m);
            int col = i / perCol, row = i % perCol;
            int x = left + col * (pairW + colGap);
            int y = top + row * ROW_H;

            this.addDrawableChild(ButtonWidget.builder(toggleText(m, e), b -> {
                e.enabled = !e.enabled;
                b.setMessage(toggleText(m, e));
                HudConfig.save();
            }).dimensions(x, y, toggleW, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal(e.anchor.label), b -> {
                e.anchor = e.anchor.next();
                b.setMessage(Text.literal(e.anchor.label));
                HudConfig.save();
            }).dimensions(x + toggleW + gap, y, anchorW, 20).build());
        }

        // Sound detector row: on/off + settings page
        int y = top + perCol * ROW_H + 10;
        int settingsW = 80;
        this.addDrawableChild(ButtonWidget.builder(soundText(cfg), b -> {
            cfg.soundEnabled = !cfg.soundEnabled;
            b.setMessage(soundText(cfg));
            HudConfig.save();
        }).dimensions(left, y, contentW - settingsW - gap, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Settings \u2699"),
                b -> this.client.setScreen(new SoundDetectorScreen(this)))
                .dimensions(left + contentW - settingsW, y, settingsW, 20).build());

        y += ROW_H + 8;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> this.close())
                .dimensions(this.width / 2 - 50, y, 100, 20).build());
    }

    private static Text toggleText(HudModule m, HudConfig.Entry e) {
        return Text.literal(m.label() + ": " + (e.enabled ? "ON" : "OFF"));
    }

    private static Text soundText(HudConfig c) {
        return Text.literal("Sound Detector: " + (c.soundEnabled ? "ON" : "OFF"));
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
    }

    @Override
    public void removed() {
        HudConfig.save();
    }
}
