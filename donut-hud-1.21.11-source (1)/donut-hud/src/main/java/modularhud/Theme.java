package modularhud;

import net.minecraft.client.gui.DrawContext;

/** Shared look: dark glass panels with a pink-to-violet accent. */
public final class Theme {
    public static final int ACCENT_A = 0xFFFF6FB5;
    public static final int ACCENT_B = 0xFF9B6BFF;
    public static final int BG = 0xB00B0B14;
    public static final int BG_PANEL = 0xE60E0E18;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int MUTED = 0xFFB8B8C8;

    private Theme() {}

    public static int lerp(int a, int b, float t) {
        int aa = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t);
        int ar = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int ag = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int ab = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    /** Horizontal 1px line fading from colA to colB. */
    public static void hLine(DrawContext c, int x, int y, int w, int colA, int colB) {
        int seg = Math.max(1, w / 48);
        for (int i = 0; i < w; i += seg) {
            c.fill(x + i, y, Math.min(x + i + seg, x + w), y + 1, lerp(colA, colB, i / (float) w));
        }
    }

    /** HUD module background: dark glass, accent stripe on the left, faint top highlight. */
    public static void box(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, BG);
        c.fillGradient(x, y, x + 2, y + h, ACCENT_A, ACCENT_B);
        c.fill(x + 2, y, x + w, y + 1, 0x28FFFFFF);
    }

    /** Settings panel with a gradient border. */
    public static void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, BG_PANEL);
        hLine(c, x, y, w, ACCENT_A, ACCENT_B);
        hLine(c, x, y + h - 1, w, ACCENT_B, ACCENT_A);
        c.fillGradient(x, y, x + 1, y + h, ACCENT_A, ACCENT_B);
        c.fillGradient(x + w - 1, y, x + w, y + h, ACCENT_A, ACCENT_B);
    }

    /** One keystroke-style key. */
    public static void key(DrawContext c, int x, int y, int w, int h, boolean down) {
        c.fill(x, y, x + w, y + h, down ? 0xD0FF6FB5 : BG);
        c.fill(x, y, x + w, y + 1, down ? 0x80FFFFFF : 0x28FFFFFF);
    }
}
