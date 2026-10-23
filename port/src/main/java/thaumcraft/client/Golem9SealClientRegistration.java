package thaumcraft.client;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.minecraft.network.chat.Component;
import thaumcraft.common.golems.seals.*;
import thaumcraft.common.golems.client.gui.*;
@EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class Golem9SealClientRegistration {
 @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(Golem9SealRegistries.SEAL_MENU,SealBaseGUI::new);event.register(Golem9SealRegistries.LOGISTICS_MENU,GuiLogistics::new);}
 @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->SealGuiBridge.installClientFactory((player,menu)->new SealBaseGUI(menu,player.getInventory(),Component.translatable("container.seal"))));}
}

