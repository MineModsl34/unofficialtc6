package thaumcraft.client.gui.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure sprite layout of TC6 GuiResearchBrowser.drawLine (1008-1123), without GL state,
 * tint/z-level, screen offsets or rendering. Coordinates are relative to the research-map
 * origin: one column/row is 24 pixels. The caller adds its startX/locX and startY/locY offsets.
 * Source arrow orientation/priority, small/large corners and sprite order are retained.
 *
 * Modern safety adaptations: exact sprite budget is checked BEFORE any loop/allocation;
 * oversized or non-int-representable pixel endpoints are rejected, not wrapped/clamped.
 * Distance/sign arithmetic uses long to avoid source Math.abs(int MIN_VALUE) overflow.
 * No source viewport bounds, colors, texture binding or graphical-parity claim lives here.
 */
public final class ResearchLineGeometry {
    public static final int MAX_SPRITES = 4096;
    private static final int PIXELS_PER_NODE = 24;
    private static final int ENDPOINT_MARGIN = 64;

    private ResearchLineGeometry() {}

    public record Sprite(int x, int y, int u, int v, int width, int height) {}

    public static List<Sprite> layout(int col1, int row1, int col2, int row2, boolean arrow, boolean flipped) {
        long dx = Math.abs((long) col2 - col1);
        long dy = Math.abs((long) row2 - row1);
        boolean bigCorner = dx > 1 && dy > 1;
        long vertical = Math.max(0L, dy - (bigCorner ? 1L : 0L) - 1L);
        long horizontal = Math.max(0L, dx - (bigCorner ? 2L : 1L));
        long corner = dx > 0 && dy > 0 ? 1L : 0L;
        long arrowCount = arrow && (dx > 0 || dy > 0) ? 1L : 0L;
        long count = vertical + horizontal + corner + arrowCount;
        if (count > MAX_SPRITES) throw new IllegalArgumentException("Research link sprite budget exceeded");
        checkPixelEndpoint(col1); checkPixelEndpoint(row1);
        checkPixelEndpoint(col2); checkPixelEndpoint(row2);
        int xd = Math.toIntExact(dx), yd = Math.toIntExact(dy);
        int startColumn = flipped ? col2 : col1;
        int startRow = flipped ? row2 : row1;
        int endColumn = flipped ? col1 : col2;
        int endRow = flipped ? row1 : row2;
        int xm = Integer.compare(endColumn, startColumn);
        int ym = Integer.compare(endRow, startRow);
        int xx = Math.toIntExact((long) startColumn * PIXELS_PER_NODE - 4L);
        int yy = Math.toIntExact((long) startRow * PIXELS_PER_NODE - 4L);
        List<Sprite> result = new ArrayList<>((int) count);
        if (arrow) {
            if (flipped) {
                int xx3 = Math.toIntExact((long) col1 * PIXELS_PER_NODE - 8L);
                int yy3 = Math.toIntExact((long) row1 * PIXELS_PER_NODE - 8L);
                if (xm < 0) result.add(new Sprite(xx3, yy3, 160, 112, 32, 32));
                else if (xm > 0) result.add(new Sprite(xx3, yy3, 128, 112, 32, 32));
                else if (ym > 0) result.add(new Sprite(xx3, yy3, 64, 112, 32, 32));
                else if (ym < 0) result.add(new Sprite(xx3, yy3, 96, 112, 32, 32));
            } else if (ym < 0) result.add(new Sprite(xx - 4, yy - 4, 64, 112, 32, 32));
            else if (ym > 0) result.add(new Sprite(xx - 4, yy - 4, 96, 112, 32, 32));
            else if (xm > 0) result.add(new Sprite(xx - 4, yy - 4, 160, 112, 32, 32));
            else if (xm < 0) result.add(new Sprite(xx - 4, yy - 4, 128, 112, 32, 32));
        }

        int v = 1;
        int h = 0;
        while (v < yd - (bigCorner ? 1 : 0)) {
            result.add(new Sprite(xx + xm * 24 * h, yy + ym * 24 * v, 0, 228, 24, 24));
            v++;
        }
        if (bigCorner) {
            if (xm < 0 && ym > 0) result.add(new Sprite(xx + xm * 24 * h - 24, yy + ym * 24 * v, 0, 180, 48, 48));
            if (xm > 0 && ym > 0) result.add(new Sprite(xx + xm * 24 * h, yy + ym * 24 * v, 48, 180, 48, 48));
            if (xm < 0 && ym < 0) result.add(new Sprite(xx + xm * 24 * h - 24, yy + ym * 24 * v - 24, 96, 180, 48, 48));
            if (xm > 0 && ym < 0) result.add(new Sprite(xx + xm * 24 * h, yy + ym * 24 * v - 24, 144, 180, 48, 48));
        } else {
            if (xm < 0 && ym > 0) result.add(new Sprite(xx + xm * 24 * h, yy + ym * 24 * v, 48, 228, 24, 24));
            if (xm > 0 && ym > 0) result.add(new Sprite(xx + xm * 24 * h, yy + ym * 24 * v, 72, 228, 24, 24));
            if (xm < 0 && ym < 0) result.add(new Sprite(xx + xm * 24 * h, yy + ym * 24 * v, 96, 228, 24, 24));
            if (xm > 0 && ym < 0) result.add(new Sprite(xx + xm * 24 * h, yy + ym * 24 * v, 120, 228, 24, 24));
        }
        v += bigCorner ? 1 : 0;
        for (int segment = h + (bigCorner ? 2 : 1); segment < xd; segment++) {
            result.add(new Sprite(xx + xm * 24 * segment, yy + ym * 24 * v, 24, 228, 24, 24));
        }
        return List.copyOf(result);
    }

    private static void checkPixelEndpoint(int coordinate) {
        long pixel = (long) coordinate * PIXELS_PER_NODE;
        if (pixel < (long) Integer.MIN_VALUE + ENDPOINT_MARGIN
            || pixel > (long) Integer.MAX_VALUE - ENDPOINT_MARGIN) {
            throw new IllegalArgumentException("Research link endpoint cannot safely fit pixel coordinates");
        }
    }
}
