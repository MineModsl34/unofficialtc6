package thaumcraft.common.golems.seals;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.golems.client.gui.*;
@EventBusSubscriber(modid="thaumcraft",bus=EventBusSubscriber.Bus.MOD)
public final class Golem9SealRegistries {
 public static final Map<String,Item> ITEMS=new LinkedHashMap<>();public static MenuType<SealBaseContainer> SEAL_MENU;public static MenuType<ContainerLogistics> LOGISTICS_MENU;public static AttachmentType<SealChunkData> SEALS;
 private Golem9SealRegistries(){}
 public static void registerTypes(){
  SealHandler.registerSeal(new SealPickup());SealHandler.registerSeal(new SealPickupAdvanced());SealHandler.registerSeal(new SealFill());SealHandler.registerSeal(new SealFillAdvanced());SealHandler.registerSeal(new SealEmpty());SealHandler.registerSeal(new SealEmptyAdvanced());SealHandler.registerSeal(new SealHarvest());SealHandler.registerSeal(new SealButcher());SealHandler.registerSeal(new SealGuard());SealHandler.registerSeal(new SealGuardAdvanced());SealHandler.registerSeal(new SealLumber());SealHandler.registerSeal(new SealBreaker());SealHandler.registerSeal(new SealUse());SealHandler.registerSeal(new SealProvide());SealHandler.registerSeal(new SealStock());SealHandler.registerSeal(new SealBreakerAdvanced());
 }
 public static Map<String,Item> createItems(){registerTypes();var out=new LinkedHashMap<String,Item>();out.put("seal_blank",ItemsTC.seals=new ItemSealPlacer(""));ITEMS.put("",ItemsTC.seals);for(String key:SealHandler.getRegisteredSeals()){var id=ResourceLocation.parse(key);if(!id.getNamespace().equals("thaumcraft"))continue;var item=new ItemSealPlacer(key);ITEMS.put(key,item);out.put("seal_"+id.getPath(),item);}return out;}
 /** Flattened addon seal items must be genuine items registered by their owning namespace. */
 public static void registerSealItem(String key,Item item){if(SealHandler.getSeal(key)==null||item==null)throw new IllegalArgumentException("Unknown seal/item");ITEMS.put(key,item);}
 public static Map<String,MenuType<?>> createMenus(){SEAL_MENU=IMenuTypeExtension.create((id,inv,buffer)->{var seal=new SealEntity();seal.readNBT(buffer.readNbt(),inv.player.registryAccess());return new SealBaseContainer(id,inv,seal);});LOGISTICS_MENU=IMenuTypeExtension.create((id,inv,b)->new ContainerLogistics(id,inv,b.readBlockPos(),b.readByte()));return Map.of("seal",SEAL_MENU,"logistics",LOGISTICS_MENU);}
 @SubscribeEvent public static void attachment(RegisterEvent event){event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES,helper->{SEALS=AttachmentType.builder(SealChunkData::new).serialize(SealChunkData.SERIALIZER).build();helper.register(ResourceLocation.parse("thaumcraft:seals"),SEALS);});}
 @SubscribeEvent public static void payloads(RegisterPayloadHandlersEvent event){var registrar=event.registrar(thaumcraft.common.lib.network.PacketHandler.PROTOCOL_VERSION);registrar.playToClient(Golem9SealNetwork.Snapshot.TYPE,Golem9SealNetwork.Snapshot.CODEC,Golem9SealNetwork::receive);registrar.playToServer(Golem9SealNetwork.LogisticsAction.TYPE,Golem9SealNetwork.LogisticsAction.CODEC,Golem9SealNetwork::receive);}
 @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event){event.enqueueWork(()->{SealBlockActions.defaults();for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM)if(item instanceof net.minecraft.world.item.ItemNameBlockItem seed)SealBlockActions.registerSeed(seed.getBlock(),new net.minecraft.world.item.ItemStack(seed));});}
}
