package thaumcraft.common.entities;

import java.util.function.Consumer;
import thaumcraft.common.entities.projectile.EntityAlumentum;

/** Client-installed presentation only; no client classes are loaded by dedicated servers. */
public final class AlumentumPresentation {
    private static Consumer<EntityAlumentum> trail = projectile -> {};
    private AlumentumPresentation() {}
    public static void install(Consumer<EntityAlumentum> presentation) { trail = java.util.Objects.requireNonNull(presentation); }
    public static void trail(EntityAlumentum projectile) { trail.accept(projectile); }
}
