package thaumcraft.client.lib.events;

/** Arithmetic from TC6 HudHandler, kept independent of client/registry bootstrapping. */
public final class HudGeometry {
    private HudGeometry() {}
    public record AuraBars(float base, float vis, float flux) {}
    public static AuraBars aura(float base, float vis, float flux) {
        float b = clamp(base / 525F), v = clamp(vis / 525F), f = clamp(flux / 525F);
        if (v + f > 1F) { float m = 1F / (v + f); b *= m; v *= m; f *= m; }
        return new AuraBars(b, v, f);
    }
    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    public static float tagScale(float value, float limit) { return value < limit ? value + (.031F - value / 10F) : value; }
    public static boolean showTags(boolean shift, boolean configured) { return shift != configured; }
    public static int chargeColor(int charge) {
        float f = charge == 0 ? .3F : charge / 15F * .5F + .5F;
        int channel = Math.max(0, Math.min(255, (int)(f * 255F)));
        return 0xff000000 | channel << 16 | channel << 8 | channel;
    }
    public record KnowledgeMotion(float size, float xDistance, float yDistance, float elapsed) {}
    public static KnowledgeMotion knowledge(int progress, int max, float partial, float x, float y) {
        float size = 16, elapsed = 0;
        if (progress < max * .66F) {
            float q = (progress - partial) / (max * .66F); size *= q;
            float m = (float)Math.sin(q * Math.PI - Math.PI / 2) * .5F + .5F;
            y *= m; x *= (float)Math.sin(m * Math.PI * .5);
        } else {
            elapsed = max - progress + partial; float q = elapsed / (max * .33F);
            float m = (float)Math.sin(q * Math.PI * 2 - Math.PI / 2) * .5F + 1.5F;
            if (q < .5) size *= q * 2; size *= m;
        }
        return new KnowledgeMotion(size, x, y, elapsed);
    }
}
