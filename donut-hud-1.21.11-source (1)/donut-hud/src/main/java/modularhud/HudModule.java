package modularhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public interface HudModule {
    String id();
    String label();
    boolean defaultEnabled();
    HudConfig.Anchor defaultAnchor();

    /** Width/height in scaled GUI pixels. Return a height of 0 to skip drawing this frame. */
    int width(MinecraftClient mc);
    int height(MinecraftClient mc);

    void render(DrawContext ctx, MinecraftClient mc, int x, int y);

    default void tick(MinecraftClient mc) {}
}
