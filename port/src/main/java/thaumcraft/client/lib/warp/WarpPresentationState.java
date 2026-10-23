package thaumcraft.client.lib.warp;

/** Source render-side counters, deliberately independent of storage/progression. */
public final class WarpPresentationState {
    private boolean fog;
    private int duration;
    private float density;
    public void mist(boolean shortEvent) {
        fog = true;
        if (!shortEvent) duration = 2400;
        else if (duration < 200) duration = 200;
    }
    public void tick() {
        if (!fog) return;
        if (duration < 100) density = 0.1F * (duration / 100.0F);
        else if (density < 0.1F) density += 0.001F;
        if (--duration < 0) fog = false;
    }
    public boolean fogActive() { return fog && density > 0.0F; }
    public float density() { return density; }
    public int duration() { return duration; }
    public void clear() { fog = false; duration = 0; density = 0; }

    public record Feedback(String translation, boolean whispers) {}
    /** Preserve PacketWarpMessage's permanent-warp missing-text and negative-temp silence. */
    public static Feedback feedback(int amount, byte type) {
        if (amount == 0) return new Feedback(null, false);
        if (type == 0 && amount > 0) return new Feedback(null, true);
        if (type == 1) return new Feedback(amount < 0 ? "tc.removewarpsticky" : "tc.addwarpsticky", amount > 0);
        if (amount > 0) return new Feedback("tc.addwarptemp", false);
        return new Feedback(null, false);
    }
}
