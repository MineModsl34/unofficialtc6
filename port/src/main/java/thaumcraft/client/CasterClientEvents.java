package thaumcraft.client;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.items.IArchitect;
import thaumcraft.client.gui.GuiFocusRadial;
import thaumcraft.common.lib.network.misc.PacketCasterAction;
/** Exactly one F/G route shared with elemental shovel; source misc modifiers Ctrl=1, Shift=2. */
@EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class CasterClientEvents{
    public static final KeyMapping FOCUS=new KeyMapping("key.changefocus",org.lwjgl.glfw.GLFW.GLFW_KEY_F,"key.categories.thaumcraft");
    public static final KeyMapping MISC=new KeyMapping("key.castertoggle",org.lwjgl.glfw.GLFW.GLFW_KEY_G,"key.categories.thaumcraft");
    private CasterClientEvents(){}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null)return;var player=mc.player;while(FOCUS.consumeClick())if(player.getMainHandItem().getItem() instanceof ICaster||player.getOffhandItem().getItem() instanceof ICaster){if(player.isShiftKeyDown())PacketDistributor.sendToServer(PacketCasterAction.focus("REMOVE"));else mc.setScreen(new GuiFocusRadial());}while(MISC.consumeClick())PacketDistributor.sendToServer(PacketCasterAction.misc(net.minecraft.client.gui.screens.Screen.hasControlDown()?1:net.minecraft.client.gui.screens.Screen.hasShiftDown()?2:0));}
    // Architecture highlights are rendered once by source WandRenderingHandler after particles.
}
