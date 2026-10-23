package thaumcraft.proxies;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.Level;

/** Created only in {@link thaumcraft.ThaumcraftClient}, so it is never loaded on the server. */
public class ClientProxy extends CommonProxy {
    @Override
    public Level getClientWorld() {
        return Minecraft.getInstance().level;
    }

    @Override
    public boolean getSingleplayer() {
        return Minecraft.getInstance().isSingleplayer();
    }

    @Override
    public boolean isShiftKeyDown() {
        return Screen.hasShiftDown();
    }
}
