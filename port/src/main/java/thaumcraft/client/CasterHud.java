package thaumcraft.client;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
/** Compatibility facade; there is exactly one registered source HudHandler layer. */
public final class CasterHud {
    private CasterHud(){}
    public static void render(GuiGraphics graphics){thaumcraft.client.lib.events.RenderEventHandler.hudHandler.renderHuds(graphics,DeltaTracker.ZERO);}
}
