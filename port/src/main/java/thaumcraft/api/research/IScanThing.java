package thaumcraft.api.research;

import net.minecraft.world.entity.player.Player;

/** Ordered TC6 scanning extension point; a scan may match more than one handler. */
public interface IScanThing {
    boolean checkThing(Player player, Object object);

    String getResearchKey(Player player, Object object);

    default void onSuccess(Player player, Object object) {}
}
