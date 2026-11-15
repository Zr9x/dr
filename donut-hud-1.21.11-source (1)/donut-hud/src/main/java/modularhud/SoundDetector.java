package modularhud;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Finds blocks/entities that make sound within a chunk range and draws a box around each.
 * Boxes are drawn on the 2D HUD layer using our own camera projection, so they show through walls.
 */
public final class SoundDetector {
    private record Hit(SoundTarget target, int x, int y, int z) {}
    private record EntHit(Entity entity, SoundTarget target) {}
    private record Candidate(Box box, SoundTarget target, double dist) {}

    private static final int CHUNKS_PER_TICK = 8;
    private static final int MAX_BOXES = 120;

    private static final Map<Long, List<Hit>> cache = new HashMap<>();
    private static final List<int[]> order = new ArrayList<>();
    private static final List<EntHit> entities = new ArrayList<>();
    private static int orderIdx, lastCx = Integer.MIN_VALUE, lastCz = Integer.MIN_VALUE, lastR = -1;

    // player position tracking for smooth (sub-tick) camera interpolation
    private static double prevX, prevY, prevZ, curX, curY, curZ;
    private static long tickNanos;
    private static boolean havePos;

    private SoundDetector() {}

    // ------------------------------------------------------------------ classification

    private static boolean lit(BlockState s) {
        return s.contains(Properties.LIT) && s.get(Properties.LIT);
    }

    static SoundTarget classify(BlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER) {
            return lit(s) ? SoundTarget.FURNACES : null;
        }
        if (b == Blocks.CAMPFIRE || b == Blocks.SOUL_CAMPFIRE) {
            return lit(s) ? SoundTarget.CAMPFIRES : null;
        }
        if (b == Blocks.NETHER_PORTAL) return SoundTarget.PORTALS;
        if (b == Blocks.BEE_NEST || b == Blocks.BEEHIVE) return SoundTarget.BEES;
        if (b == Blocks.BEACON || b == Blocks.CONDUIT) return SoundTarget.BEACONS;
        if (b == Blocks.JUKEBOX) {
            return s.contains(Properties.HAS_RECORD) && s.get(Properties.HAS_RECORD)
                    ? SoundTarget.JUKEBOXES : null;
        }
        if (b == Blocks.SCULK_SENSOR || b == Blocks.CALIBRATED_SCULK_SENSOR
                || b == Blocks.SCULK_SHRIEKER || b == Blocks.SCULK_CATALYST) {
            return SoundTarget.SCULK;
        }
        if (b == Blocks.NOTE_BLOCK || b == Blocks.BELL) return SoundTarget.NOTES;
        return null;
    }

    private static boolean matches(BlockState s) {
        return classify(s) != null;
    }

    private static SoundTarget classify(Entity e) {
        if (e instanceof PlayerEntity) return SoundTarget.PLAYERS;
        if (e instanceof MerchantEntity) return SoundTarget.VILLAGERS;
        if (e instanceof Monster) return SoundTarget.HOSTILE;
        if (e instanceof PassiveEntity || e instanceof WaterCreatureEntity) return SoundTarget.ANIMALS;
        if (e instanceof AbstractMinecartEntity) return SoundTarget.MINECARTS;
        return null;
    }

    // ------------------------------------------------------------------ scanning (per tick)

    public static void tick(MinecraftClient mc) {
        HudConfig cfg = HudConfig.get();
        if (mc.player == null || mc.world == null) {
            cache.clear();
            entities.clear();
            havePos = false;
            return;
        }

        prevX = havePos ? curX : mc.player.getX();
        prevY = havePos ? curY : mc.player.getY();
        prevZ = havePos ? curZ : mc.player.getZ();
        curX = mc.player.getX();
        curY = mc.player.getY();
        curZ = mc.player.getZ();
        tickNanos = System.nanoTime();
        havePos = true;

        if (!cfg.soundEnabled) {
            if (!cache.isEmpty() || !entities.isEmpty()) {
                cache.clear();
                entities.clear();
            }
            return;
        }

        scanBlocks(mc, cfg);
        scanEntities(mc, cfg);
    }

    private static void rebuildOrder(int cx, int cz, int r) {
        order.clear();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r + r) order.add(new int[]{dx, dz});
            }
        }
        order.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        orderIdx = 0;

        // drop cached chunks that fell outside the new window
        Iterator<Long> it = cache.keySet().iterator();
        while (it.hasNext()) {
            long key = it.next();
            int kx = (int) (key & 0xFFFFFFFFL);
            int kz = (int) (key >>> 32);
            int dx = kx - cx, dz = kz - cz;
            if (dx * dx + dz * dz > r * r + r) it.remove();
        }
        lastCx = cx;
        lastCz = cz;
        lastR = r;
    }

    private static void scanBlocks(MinecraftClient mc, HudConfig cfg) {
        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        int r = cfg.range();
        if (cx != lastCx || cz != lastCz || r != lastR) rebuildOrder(cx, cz, r);
        if (order.isEmpty()) return;

        for (int n = 0; n < CHUNKS_PER_TICK; n++) {
            int[] o = order.get(orderIdx);
            orderIdx = (orderIdx + 1) % order.size();
            int ccx = cx + o[0], ccz = cz + o[1];
            long key = ChunkPos.toLong(ccx, ccz);
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(ccx, ccz);
            if (chunk == null) {
                cache.remove(key);
                continue;
            }
            List<Hit> hits = scanChunk(chunk);
            if (hits.isEmpty()) cache.remove(key);
            else cache.put(key, hits);
        }
    }

    private static List<Hit> scanChunk(WorldChunk chunk) {
        List<Hit> out = new ArrayList<>();
        ChunkSection[] sections = chunk.getSectionArray();
        int bottom = chunk.getBottomSectionCoord();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection sec = sections[i];
            if (sec == null || sec.isEmpty() || !sec.hasAny(SoundDetector::matches)) continue;
            int baseY = (bottom + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        SoundTarget t = classify(sec.getBlockState(x, y, z));
                        if (t != null) out.add(new Hit(t, startX + x, baseY + y, startZ + z));
                    }
                }
            }
        }
        return out;
    }

    private static void scanEntities(MinecraftClient mc, HudConfig cfg) {
        entities.clear();
        double maxD = cfg.range() * 16.0;
        double maxD2 = maxD * maxD;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || e.isRemoved()) continue;
            SoundTarget t = classify(e);
            if (t == null || !cfg.targetOn(t)) continue;
            double dx = e.getX() - mc.player.getX();
            double dz = e.getZ() - mc.player.getZ();
            if (dx * dx + dz * dz > maxD2) continue;
            entities.add(new EntHit(e, t));
        }
    }

    // ------------------------------------------------------------------ rendering (per frame)

    public static void render(DrawContext ctx, MinecraftClient mc) {
        HudConfig cfg = HudConfig.get();
        if (!cfg.soundEnabled || mc.player == null || !havePos) return;
        if (!mc.options.getPerspective().isFirstPerson()) return;

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        // camera position, interpolated between ticks so boxes don't judder
        double t = Math.min(1.0, (System.nanoTime() - tickNanos) / 5.0e7);
        double ex = prevX + (curX - prevX) * t;
        double ey = prevY + (curY - prevY) * t + (mc.player.getEyeY() - mc.player.getY());
        double ez = prevZ + (curZ - prevZ) * t;

        // camera basis from yaw/pitch
        double yaw = Math.toRadians(mc.player.getYaw());
        double pitch = Math.toRadians(mc.player.getPitch());
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), ry = 0, rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) sw / sh;

        // gather boxes
        List<Candidate> list = new ArrayList<>();
        for (Map.Entry<Long, List<Hit>> en : cache.entrySet()) {
            for (Hit h : en.getValue()) {
                if (!cfg.targetOn(h.target())) continue;
                Box box = new Box(h.x(), h.y(), h.z(), h.x() + 1, h.y() + 1, h.z() + 1);
                double d = Math.sqrt(sq(h.x() + 0.5 - ex) + sq(h.y() + 0.5 - ey) + sq(h.z() + 0.5 - ez));
                list.add(new Candidate(box, h.target(), d));
            }
        }
        for (EntHit eh : entities) {
            if (eh.entity().isRemoved()) continue;
            Box box = eh.entity().getBoundingBox();
            double d = Math.sqrt(sq(box.getCenter().x - ex) + sq(box.getCenter().y - ey) + sq(box.getCenter().z - ez));
            list.add(new Candidate(box, eh.target(), d));
        }
        list.sort(Comparator.comparingDouble(Candidate::dist));
        if (list.size() > MAX_BOXES) list = list.subList(0, MAX_BOXES);

        // draw far-to-near so near boxes sit on top
        for (int i = list.size() - 1; i >= 0; i--) {
            Candidate c = list.get(i);
            drawBox(ctx, mc, c, cfg.soundLabels, ex, ey, ez, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalf, aspect, sw, sh);
        }
    }

    private static double sq(double v) { return v * v; }

    private static void drawBox(DrawContext ctx, MinecraftClient mc, Candidate c, boolean labels,
                                double ex, double ey, double ez,
                                double fx, double fy, double fz,
                                double rx, double ry, double rz,
                                double ux, double uy, double uz,
                                double tanHalf, double aspect, int sw, int sh) {
        Box b = c.box();
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};

        // centre must be in front of the camera
        double cdx = (b.minX + b.maxX) / 2 - ex, cdy = (b.minY + b.maxY) / 2 - ey, cdz = (b.minZ + b.maxZ) / 2 - ez;
        if (cdx * fx + cdy * fy + cdz * fz < 0.1) return;

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    double dx = x - ex, dy = y - ey, dz = z - ez;
                    double zc = Math.max(0.1, dx * fx + dy * fy + dz * fz);
                    double xc = dx * rx + dy * ry + dz * rz;
                    double yc = dx * ux + dy * uy + dz * uz;
                    double sx = (xc / (zc * tanHalf * aspect) * 0.5 + 0.5) * sw;
                    double sy = (0.5 - yc / (zc * tanHalf) * 0.5) * sh;
                    minX = Math.min(minX, sx);
                    maxX = Math.max(maxX, sx);
                    minY = Math.min(minY, sy);
                    maxY = Math.max(maxY, sy);
                }
            }
        }
        if (maxX < 0 || minX > sw || maxY < 0 || minY > sh) return;

        int x1 = (int) Math.floor(minX), y1 = (int) Math.floor(minY);
        int x2 = (int) Math.ceil(maxX), y2 = (int) Math.ceil(maxY);
        if (x2 - x1 < 6) { int m = (x1 + x2) / 2; x1 = m - 3; x2 = m + 3; }
        if (y2 - y1 < 6) { int m = (y1 + y2) / 2; y1 = m - 3; y2 = m + 3; }

        int col = c.target().color;
        ctx.fill(x1, y1, x2, y2, (col & 0x00FFFFFF) | 0x1E000000);   // soft fill
        ctx.fill(x1, y1, x2, y1 + 1, col);                            // outline
        ctx.fill(x1, y2 - 1, x2, y2, col);
        ctx.fill(x1, y1, x1 + 1, y2, col);
        ctx.fill(x2 - 1, y1, x2, y2, col);
        int k = Math.min(5, Math.max(2, (x2 - x1) / 3));              // brighter corner brackets
        int hi = 0xFFFFFFFF;
        ctx.fill(x1, y1, x1 + k, y1 + 1, hi); ctx.fill(x1, y1, x1 + 1, y1 + k, hi);
        ctx.fill(x2 - k, y1, x2, y1 + 1, hi); ctx.fill(x2 - 1, y1, x2, y1 + k, hi);
        ctx.fill(x1, y2 - 1, x1 + k, y2, hi); ctx.fill(x1, y2 - k, x1 + 1, y2, hi);
        ctx.fill(x2 - k, y2 - 1, x2, y2, hi); ctx.fill(x2 - 1, y2 - k, x2, y2, hi);

        if (labels) {
            String text = c.target().label + " " + (int) c.dist() + "m";
            int tw = mc.textRenderer.getWidth(text);
            int lx = Math.max(1, Math.min(sw - tw - 8, x1));
            int ly = Math.max(1, y1 - 12);
            ctx.fill(lx, ly, lx + tw + 7, ly + 11, Theme.BG);
            ctx.fill(lx, ly, lx + 2, ly + 11, col);
            ctx.drawTextWithShadow(mc.textRenderer, text, lx + 4, ly + 2, Theme.WHITE);
        }
    }
}
