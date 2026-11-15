package modularhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

public final class HudRenderer {
    private static final int MARGIN = 4;
    private static final int GAP = 2;

    private HudRenderer() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        HudConfig cfg = HudConfig.get();

        SoundDetector.render(ctx, mc);

        for (HudConfig.Anchor anchor : HudConfig.Anchor.values()) {
            List<HudModule> stack = new ArrayList<>();
            for (HudModule m : Modules.ALL) {
                HudConfig.Entry e = cfg.entry(m);
                if (e.enabled && e.anchor == anchor) stack.add(m);
            }

            boolean bottom = anchor == HudConfig.Anchor.BOTTOM_LEFT || anchor == HudConfig.Anchor.BOTTOM_RIGHT;
            boolean right = anchor == HudConfig.Anchor.TOP_RIGHT || anchor == HudConfig.Anchor.BOTTOM_RIGHT;
            int y = bottom ? sh - MARGIN : MARGIN;

            for (HudModule m : stack) {
                int h = m.height(mc);
                if (h <= 0) continue;
                int w = m.width(mc);
                int x = right ? sw - MARGIN - w : MARGIN;
                if (bottom) {
                    y -= h;
                    m.render(ctx, mc, x, y);
                    y -= GAP;
                } else {
                    m.render(ctx, mc, x, y);
                    y += h + GAP;
                }
            }
        }
    }
}
