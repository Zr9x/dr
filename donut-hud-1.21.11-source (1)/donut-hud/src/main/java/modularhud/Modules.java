package modularhud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import static modularhud.HudConfig.Anchor.*;

public final class Modules {
    static final int PAD = 3;
    static final int STRIPE = 2;
    static final int TEXT_H = 9;

    // ---- state used by the tick-based modules ----
    private static final Deque<Long> leftClicks = new ArrayDeque<>();
    private static final Deque<Long> rightClicks = new ArrayDeque<>();
    private static boolean lastLeft, lastRight;
    private static double lastX, lastZ, speed;
    private static boolean hasLast;

    private static void trackClicks(MinecraftClient mc) {
        boolean l = mc.options.attackKey.isPressed();
        boolean r = mc.options.useKey.isPressed();
        long now = System.currentTimeMillis();
        if (l && !lastLeft) leftClicks.add(now);
        if (r && !lastRight) rightClicks.add(now);
        lastLeft = l;
        lastRight = r;
        prune(leftClicks, now);
        prune(rightClicks, now);
    }

    private static void prune(Deque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
    }

    private static void trackSpeed(MinecraftClient mc) {
        if (mc.player == null) {
            hasLast = false;
            speed = 0;
            return;
        }
        double x = mc.player.getX();
        double z = mc.player.getZ();
        if (hasLast) speed = Math.hypot(x - lastX, z - lastZ) * 20.0;
        lastX = x;
        lastZ = z;
        hasLast = true;
    }

    private static String cap(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String pingText(MinecraftClient mc) {
        if (mc.player == null || mc.getNetworkHandler() == null) return "Ping: --";
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e == null ? "Ping: --" : "Ping: " + e.getLatency() + " ms";
    }

    // ---- registry (order here = order in the settings screen) ----
    public static final List<HudModule> ALL = List.of(
            new TextModule("fps", "FPS", true, TOP_LEFT,
                    mc -> mc.getCurrentFps() + " FPS", null),
            new TextModule("coords", "Coordinates", true, TOP_LEFT,
                    mc -> mc.player == null ? "" :
                            "XYZ: " + mc.player.getBlockX() + " / " + mc.player.getBlockY() + " / " + mc.player.getBlockZ(),
                    null),
            new TextModule("direction", "Direction", true, TOP_LEFT,
                    mc -> mc.player == null ? "" : "Facing: " + cap(mc.player.getHorizontalFacing().name()),
                    null),
            new TextModule("ping", "Ping", true, TOP_LEFT, Modules::pingText, null),
            new TextModule("cps", "CPS", true, TOP_RIGHT,
                    mc -> "CPS: " + leftClicks.size() + " | " + rightClicks.size(),
                    Modules::trackClicks),
            new TextModule("speed", "Speed", false, TOP_RIGHT,
                    mc -> String.format(Locale.ROOT, "%.2f b/s", speed),
                    Modules::trackSpeed),
            new TextModule("clock", "Clock", false, TOP_RIGHT,
                    mc -> LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), null),
            new ArmorModule(),
            new KeystrokesModule()
    );

    // ---------------------------------------------------------------
    static final class TextModule implements HudModule {
        private final String id, label;
        private final boolean enabledByDefault;
        private final HudConfig.Anchor anchor;
        private final Function<MinecraftClient, String> text;
        private final Consumer<MinecraftClient> tick;

        TextModule(String id, String label, boolean enabledByDefault, HudConfig.Anchor anchor,
                   Function<MinecraftClient, String> text, Consumer<MinecraftClient> tick) {
            this.id = id;
            this.label = label;
            this.enabledByDefault = enabledByDefault;
            this.anchor = anchor;
            this.text = text;
            this.tick = tick;
        }

        @Override public String id() { return id; }
        @Override public String label() { return label; }
        @Override public boolean defaultEnabled() { return enabledByDefault; }
        @Override public HudConfig.Anchor defaultAnchor() { return anchor; }

        @Override public int width(MinecraftClient mc) {
            return mc.textRenderer.getWidth(text.apply(mc)) + PAD * 2 + STRIPE;
        }

        @Override public int height(MinecraftClient mc) {
            return text.apply(mc).isEmpty() ? 0 : TEXT_H + PAD * 2;
        }

        @Override public void render(DrawContext ctx, MinecraftClient mc, int x, int y) {
            String s = text.apply(mc);
            Theme.box(ctx, x, y, width(mc), height(mc));
            ctx.drawTextWithShadow(mc.textRenderer, s, x + PAD + STRIPE, y + PAD, Theme.WHITE);
        }

        @Override public void tick(MinecraftClient mc) {
            if (tick != null) tick.accept(mc);
        }
    }

    // ---------------------------------------------------------------
    static final class ArmorModule implements HudModule {
        private static final EquipmentSlot[] SLOTS = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        private static final int ROW = 18;

        @Override public String id() { return "armor"; }
        @Override public String label() { return "Armor"; }
        @Override public boolean defaultEnabled() { return true; }
        @Override public HudConfig.Anchor defaultAnchor() { return BOTTOM_RIGHT; }

        private List<ItemStack> worn(MinecraftClient mc) {
            List<ItemStack> list = new ArrayList<>();
            if (mc.player == null) return list;
            for (EquipmentSlot slot : SLOTS) {
                ItemStack s = mc.player.getEquippedStack(slot);
                if (!s.isEmpty()) list.add(s);
            }
            return list;
        }

        @Override public int width(MinecraftClient mc) {
            return PAD * 2 + STRIPE + ROW + mc.textRenderer.getWidth("100%");
        }

        @Override public int height(MinecraftClient mc) {
            int n = worn(mc).size();
            return n == 0 ? 0 : n * ROW - 2 + PAD * 2;
        }

        @Override public void render(DrawContext ctx, MinecraftClient mc, int x, int y) {
            List<ItemStack> items = worn(mc);
            Theme.box(ctx, x, y, width(mc), height(mc));
            int rowY = y + PAD;
            for (ItemStack stack : items) {
                ctx.drawItem(stack, x + PAD + STRIPE, rowY);
                if (stack.isDamageable()) {
                    int max = stack.getMaxDamage();
                    int pct = Math.max(0, (max - stack.getDamage()) * 100 / max);
                    int color = pct > 60 ? 0xFF55FF55 : pct > 25 ? 0xFFFFFF55 : 0xFFFF5555;
                    ctx.drawTextWithShadow(mc.textRenderer, pct + "%", x + PAD + STRIPE + ROW, rowY + 4, color);
                }
                rowY += ROW;
            }
        }
    }

    // ---------------------------------------------------------------
    static final class KeystrokesModule implements HudModule {
        private static final int KEY = 20, GAP = 2, MOUSE_H = 14;

        @Override public String id() { return "keystrokes"; }
        @Override public String label() { return "Keystrokes"; }
        @Override public boolean defaultEnabled() { return true; }
        @Override public HudConfig.Anchor defaultAnchor() { return BOTTOM_LEFT; }

        @Override public int width(MinecraftClient mc) { return KEY * 3 + GAP * 2; }
        @Override public int height(MinecraftClient mc) { return KEY * 2 + MOUSE_H + GAP * 2; }

        private void key(DrawContext ctx, MinecraftClient mc, int x, int y, int w, int h,
                         String label, boolean down) {
            Theme.key(ctx, x, y, w, h, down);
            int tx = x + (w - mc.textRenderer.getWidth(label)) / 2;
            int ty = y + (h - TEXT_H) / 2 + 1;
            ctx.drawTextWithShadow(mc.textRenderer, label, tx, ty, down ? 0xFF0B0B14 : Theme.WHITE);
        }

        @Override public void render(DrawContext ctx, MinecraftClient mc, int x, int y) {
            var o = mc.options;
            int col = KEY + GAP;
            key(ctx, mc, x + col, y, KEY, KEY, "W", o.forwardKey.isPressed());
            int row2 = y + KEY + GAP;
            key(ctx, mc, x, row2, KEY, KEY, "A", o.leftKey.isPressed());
            key(ctx, mc, x + col, row2, KEY, KEY, "S", o.backKey.isPressed());
            key(ctx, mc, x + col * 2, row2, KEY, KEY, "D", o.rightKey.isPressed());
            int row3 = row2 + KEY + GAP;
            int half = (width(mc) - GAP) / 2;
            key(ctx, mc, x, row3, half, MOUSE_H, "LMB", o.attackKey.isPressed());
            key(ctx, mc, x + half + GAP, row3, half, MOUSE_H, "RMB", o.useKey.isPressed());
        }
    }
}
