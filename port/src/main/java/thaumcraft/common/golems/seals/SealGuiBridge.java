package thaumcraft.common.golems.seals;
import java.util.function.BiFunction;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.common.golems.client.gui.SealBaseContainer;
/** Source GUI hooks without loading Minecraft client classes on the dedicated server. */
public final class SealGuiBridge {
 private static BiFunction<Player,SealBaseContainer,Object> clientFactory;
 private SealGuiBridge(){}
 public static void installClientFactory(BiFunction<Player,SealBaseContainer,Object> factory){clientFactory=factory;}
 public static Object container(Player player,ISealEntity seal){return seal instanceof SealEntity nativeSeal?new SealBaseContainer(0,player.getInventory(),nativeSeal):null;}
 public static Object screen(Player player,ISealEntity seal){var menu=container(player,seal);return menu instanceof SealBaseContainer nativeMenu&&clientFactory!=null?clientFactory.apply(player,nativeMenu):null;}
}

