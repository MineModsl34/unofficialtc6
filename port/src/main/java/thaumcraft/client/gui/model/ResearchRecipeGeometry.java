package thaumcraft.client.gui.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Headless source recipe slot geometry, including integer infusion angle division and the
 * original aspect final-full-row shift quirk. Coordinates are relative to insert center.
 */
public final class ResearchRecipeGeometry {
    private ResearchRecipeGeometry() {}
    public record Slot(int index, int x, int y) {}
    public record Voxel(int x, int y, int z) {}
    public record Thumbnail(List<Slot> cells, float scale, float offsetX, float offsetY) {
        public Thumbnail { cells = List.copyOf(cells); }
    }
    public enum Control { NONE, PREVIOUS, NEXT, SLICE }

    /** Only rendered/available arrows consume a click. A single-page blueprint's slice
     * control overlaps the next-arrow hitbox, but there IS no next arrow on that page.
     */
    public static Control control(double mouseX, double mouseY, int left, int top, boolean blueprint, int variant, int count) {
        if (variant > 0 && inside(mouseX, mouseY, left + 38, top + 230, 16, 14)) return Control.PREVIOUS;
        if (variant < count - 1 && inside(mouseX, mouseY, left + 202, top + 230, 16, 14)) return Control.NEXT;
        if (blueprint && inside(mouseX, mouseY, left + 208, top + 226, 10, 10)) return Control.SLICE;
        return Control.NONE;
    }

    private static boolean inside(double mx, double my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }

    /** Source renderBluePrint's separate ingredient list is not its one-block-per-cell mesh. */
    public static List<Slot> blueprintIngredients(int count) {
        if (count < 0 || count > 256) throw new IllegalArgumentException("Unbounded blueprint ingredients");
        List<Slot> result = new ArrayList<>();
        for (int index = 0; index < count; index++) result.add(new Slot(index, -85 + index * 17, 90));
        return List.copyOf(result);
    }

    /** An intentionally empty source/vanilla ingredient is not missing future content. */
    public static boolean emptyIngredient(boolean captured, int declaredAlternatives, int resolvedAlternatives) {
        if (declaredAlternatives < 0 || resolvedAlternatives < 0) throw new IllegalArgumentException("Negative ingredient alternatives");
        return (captured ? declaredAlternatives : resolvedAlternatives) == 0;
    }

    /** Modern isometric bookmark adaptation for source blueprints with no display ItemStack.
     * Preserve actual captured cells; fit their item projections into a sixteen-pixel icon.
     */
    public static Thumbnail blueprintThumbnail(List<Voxel> cells) {
        if (cells.size() > 4096) throw new IllegalArgumentException("Unbounded blueprint preview");
        if (cells.isEmpty()) return new Thumbnail(List.of(), 1F, 0F, 0F);
        List<Slot> projected = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int index = 0; index < cells.size(); index++) {
            Voxel cell = cells.get(index);
            if (Math.abs((long) cell.x()) > 32 || Math.abs((long) cell.y()) > 32 || Math.abs((long) cell.z()) > 32)
                throw new IllegalArgumentException("Unsafe blueprint preview coordinate");
            int x = (cell.x() - cell.z()) * 8, y = (cell.x() + cell.z()) * 4 - cell.y() * 10;
            projected.add(new Slot(index, x, y));
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + 16); maxY = Math.max(maxY, y + 16);
        }
        projected.sort(Comparator.comparingInt(slot -> {
            Voxel cell = cells.get(slot.index()); return cell.x() + cell.z() + cell.y() * 4;
        }));
        float scale = Math.min(1F, Math.min(16F / (maxX - minX), 16F / (maxY - minY)));
        return new Thumbnail(projected, scale, -(minX + maxX) / 2F, -(minY + maxY) / 2F);
    }

    public static List<Slot> grid(int width, int height, int ingredientCount) {
        if (width < 0 || height < 0 || ingredientCount < 0 || width > 16 || height > 16 || ingredientCount > 256)
            throw new IllegalArgumentException("Unbounded recipe grid");
        List<Slot> result = new ArrayList<>();
        for (int column = 0; column < Math.min(width, 3); column++) for (int row = 0; row < Math.min(height, 3); row++) {
            int index = column + row * width;
            if (index < ingredientCount) result.add(new Slot(index, -40 + column * 32, -40 + row * 32));
        }
        return List.copyOf(result);
    }
    public static List<Slot> infusionRing(int count) {
        if (count < 0 || count > 256) throw new IllegalArgumentException("Unbounded infusion ring");
        List<Slot> result = new ArrayList<>();
        float angle = -90F, step = count == 0 ? 0 : 360 / count;
        for (int index = 0; index < count; index++, angle += step) {
            int x = (int) ((float) Math.cos(angle / 180F * (float) Math.PI) * 40F) - 8;
            int y = (int) ((float) Math.sin(angle / 180F * (float) Math.PI) * 40F) - 16;
            result.add(new Slot(index, x, y));
        }
        return List.copyOf(result);
    }
    public static List<Slot> aspects(int count, int columns) {
        if (count < 0 || count > 256 || columns < 1 || columns > 6) throw new IllegalArgumentException("Unbounded aspect layout");
        List<Slot> result = new ArrayList<>();
        if (count == 0) return List.of();
        int rows = (count - 1) / columns, shift = (columns - count % columns) * 10;
        for (int index = 0; index < count; index++) {
            boolean shifted = index / columns >= rows && (rows > 1 || count < columns);
            result.add(new Slot(index, -(columns * 20 - 4) / 2 + index % columns * 20 + (shifted ? shift : 0),
                -10 * rows + index / columns * 20));
        }
        return List.copyOf(result);
    }
}
