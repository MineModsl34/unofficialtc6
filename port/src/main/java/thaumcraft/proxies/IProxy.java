package thaumcraft.proxies;

import net.minecraft.world.level.Level;

/**
 * What remains of 1.12.2's @SidedProxy. The lifecycle (preInit/init/postInit) is replaced by mod bus events;
 * only the common code's calls into the client are left here.
 */
public interface IProxy {
    Level getClientWorld();

    boolean getSingleplayer();

    boolean isShiftKeyDown();
}
