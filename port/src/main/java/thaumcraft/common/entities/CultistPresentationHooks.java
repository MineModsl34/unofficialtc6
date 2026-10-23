package thaumcraft.common.entities;

import net.minecraft.world.level.Level;

/** Client-only presentation bridge; the dedicated server never resolves client classes.
 * This has no spawn, inventory, damage, research or other authoritative side effects. */
public final class CultistPresentationHooks {
    public interface Presentation {
        void spawn(Level level, double x, double y, double z, double dx, double dy, double dz);
        void burst(Level level, double x, double y, double z, float size);
    }
    private static volatile Presentation presentation;
    private CultistPresentationHooks() {}
    public static void install(Presentation value) { presentation = java.util.Objects.requireNonNull(value); }
    public static void spawn(Level level, double x, double y, double z, double dx, double dy, double dz) {
        Presentation current = presentation; if (level.isClientSide && current != null) current.spawn(level, x, y, z, dx, dy, dz);
    }
    public static void burst(Level level, double x, double y, double z, float size) {
        Presentation current = presentation; if (level.isClientSide && current != null) current.burst(level, x, y, z, size);
    }
}
