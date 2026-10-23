package thaumcraft.common.lib.research;

/** Pure TC6 ScanSky/vanilla 1.12.2 WorldProvider and Entity arithmetic.
 * Float operations, cast-before-remainder, negative Java remainder and strict thresholds are
 * intentional. Do not replace this with normalized yaw or modern double timeOfDay arithmetic. */
public final class LegacySkyScanGeometry {
    private LegacySkyScanGeometry() {}

    public record Aim(int yaw, int pitch, int arc, boolean night,
                      boolean inRangeYaw, boolean inRangePitch) {
        public boolean aligned() { return inRangeYaw && inRangePitch; }
        public boolean scannable() { return aligned() || night; }
    }

    public record Note(String suffix, int metadata) {}

    /** WorldProvider.calculateCelestialAngle(dayTime, 0), including its float rounding. */
    public static float celestialAngle(long dayTime) {
        int tick = (int) (dayTime % 24000L);
        float angle = (float) tick / 24000.0F - 0.25F;
        if (angle < 0.0F) ++angle;
        if (angle > 1.0F) --angle;
        float eased = 1.0F - (float) ((Math.cos((double) angle * Math.PI) + 1.0D) / 2.0D);
        return angle + (eased - angle) / 3.0F;
    }

    public static Aim aim(float rotationYaw, float rotationPitch, float celestialAngle) {
        int yaw = (int) (rotationYaw + 90.0F) % 360;
        int pitch = (int) Math.abs(rotationPitch);
        int arc = (int) ((celestialAngle + 0.25D) * 360.0D) % 360;
        boolean night = arc > 180;
        if (night) arc -= 180;
        boolean yawMatch;
        boolean pitchMatch;
        if (arc > 90) {
            yawMatch = Math.abs(Math.abs(yaw) - 180) < 10;
            pitchMatch = Math.abs(180 - arc - pitch) < 7;
        } else {
            yawMatch = Math.abs(yaw) < 10;
            pitchMatch = Math.abs(arc - pitch) < 7;
        }
        return new Aim(yaw, pitch, arc, night, yawMatch, pitchMatch);
    }

    /** getTotalWorldTime is gameTime, not the adjustable dayTime used by angle/moon phase. */
    public static int worldDay(long gameTime) { return (int) (gameTime / 24000L); }

    public static String dayPrefix(long gameTime) { return "CEL_" + worldDay(gameTime) + "_"; }

    public static int moonPhase(long dayTime) { return (int) (dayTime / 24000L % 8L + 8L) % 8; }

    /** Entity.getHorizontalFacing -> EnumFacing.getIndex()-2: N=0, S=1, W=2, E=3. */
    public static int starNumber(float rotationYaw) {
        double direction = (double) (rotationYaw * 4.0F / 360.0F) + 0.5D;
        int truncated = (int) direction;
        int floor = direction < truncated ? truncated - 1 : truncated;
        return switch (floor & 3) {
            case 0 -> 1; // SOUTH (old index 3)
            case 1 -> 2; // WEST (old index 4)
            case 2 -> 0; // NORTH (old index 2)
            default -> 3; // EAST (old index 5)
        };
    }

    public static Note note(Aim aim, long dayTime, float rotationYaw) {
        if (aim.aligned()) {
            int phase = moonPhase(dayTime);
            return aim.night() ? new Note("Moon" + phase, 5 + phase) : new Note("Sun", 0);
        }
        if (!aim.night()) return null;
        int direction = starNumber(rotationYaw);
        return new Note("Star" + direction, 1 + direction);
    }
}
