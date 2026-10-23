package thaumcraft.common.entities;

public final class ToolPresentation {
    public interface Presentation { void followingTrail(EntityFollowingItem item, int mode); }
    private static Presentation presentation = (item, mode) -> {};
    private ToolPresentation() {}
    public static void install(Presentation value) { presentation = java.util.Objects.requireNonNull(value); }
    public static void followingTrail(EntityFollowingItem item, int mode) { presentation.followingTrail(item, mode); }
}
