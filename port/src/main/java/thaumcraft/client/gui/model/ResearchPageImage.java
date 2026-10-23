package thaumcraft.client.gui.model;

import net.minecraft.resources.ResourceLocation;

/** TC6 PageImage.parse's seven colon-delimited fields and truncating scaled dimensions.
 * Positive finite geometry and PNG resource paths are modern safety constraints: source
 * accepted negative/NaN geometry, which is never useful content and can stall its image queue.
 * The original renderer's UV coordinate space is always 256, regardless of actual file size.
 */
public record ResearchPageImage(ResourceLocation texture, int x, int y, int width, int height,
                                float scale, int drawnWidth, int drawnHeight) {
    public static ResearchPageImage parse(String raw) {
        if (raw == null || raw.length() > 2048) return null;
        String[] fields = raw.split(":", -1);
        if (fields.length != 7) return null;
        try {
            ResourceLocation texture = ResourceLocation.tryBuild(fields[0], fields[1]);
            if (texture == null || !texture.getPath().startsWith("textures/") || !texture.getPath().endsWith(".png")) return null;
            int x = Integer.parseInt(fields[2]), y = Integer.parseInt(fields[3]);
            int w = Integer.parseInt(fields[4]), h = Integer.parseInt(fields[5]);
            float scale = Float.parseFloat(fields[6]);
            if (x < 0 || y < 0 || x > 16384 || y > 16384 || w < 1 || h < 1 || w > 16384 || h > 16384
                || !Float.isFinite(scale) || scale <= 0F) return null;
            double aw = w * (double) scale, ah = h * (double) scale;
            if (aw < 1 || ah < 1 || aw >= 141 || ah >= 209) return null;
            return new ResearchPageImage(texture, x, y, w, h, scale, (int) (w * scale), (int) (h * scale));
        } catch (IllegalArgumentException malformed) { return null; }
    }
}
