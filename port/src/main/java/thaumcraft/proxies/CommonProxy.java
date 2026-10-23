package thaumcraft.proxies;

import net.minecraft.world.level.Level;

public class CommonProxy implements IProxy {
    @Override
    public Level getClientWorld() {
        return null;
    }

    @Override
    public boolean getSingleplayer() {
        return false;
    }

    @Override
    public boolean isShiftKeyDown() {
        return false;
    }
}
