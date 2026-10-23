package thaumcraft.common.tiles.crafting;

import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.items.wrapper.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.crafting.BlockGolemBuilder;
import thaumcraft.common.blocks.misc.BlockGolemPlaceholder;
import thaumcraft.common.container.ContainerGolemBuilder;
import thaumcraft.common.golems.ItemGolemPlacer;
import thaumcraft.common.golems.ItemGolemBell;
import thaumcraft.common.lib.network.golems.*;

/** Root owns actual block/item registration and shared tags/lang/loot; this factory owns runtime. */
@EventBusSubscriber(modid="thaumcraft",bus=EventBusSubscriber.Bus.MOD)
public final class Golem9BuilderRegistries {
    public static BlockEntityType<TileGolemBuilder> TYPE;
    public static MenuType<ContainerGolemBuilder> MENU;
    private static Map<String,Block> blocks;
    private static Map<String,Item> items;
    private Golem9BuilderRegistries() {}
    private static BlockBehaviour.Properties rock() { return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F).sound(SoundType.STONE).noOcclusion().pushReaction(PushReaction.BLOCK); }
    public static synchronized Map<String,Block> createBlocks() {
        if (blocks != null) return blocks; var result = new LinkedHashMap<String,Block>();
        result.put("golem_builder",BlocksTC.golemBuilder = new BlockGolemBuilder(rock().strength(2,12)));
        result.put("placeholder_bars",BlocksTC.placeholderBars = new BlockGolemPlaceholder(rock()));
        result.put("placeholder_anvil",BlocksTC.placeholderAnvil = new BlockGolemPlaceholder(rock()));
        result.put("placeholder_cauldron",BlocksTC.placeholderCauldron = new BlockGolemPlaceholder(rock().lightLevel(state -> 13)));
        result.put("placeholder_table",BlocksTC.placeholderTable = new BlockGolemPlaceholder(rock()));
        blocks = Collections.unmodifiableMap(result); return blocks;
    }
    public static synchronized Map<String,Item> createItems() {
        if (items == null) { var result = new LinkedHashMap<String,Item>(); result.put("golem",ItemsTC.golemPlacer = new ItemGolemPlacer()); result.put("golem_bell",ItemsTC.golemBell = new ItemGolemBell()); items = Collections.unmodifiableMap(result); }
        return items;
    }
    public static List<ItemStack> creativeStacks(Item item) { return item instanceof ItemGolemPlacer golem ? golem.creativeStacks() : List.of(new ItemStack(item)); }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE,helper -> { TYPE = BlockEntityType.Builder.of(TileGolemBuilder::new,BlocksTC.golemBuilder).build(null); helper.register(ResourceLocation.parse("thaumcraft:golem_builder"),TYPE); });
        event.register(Registries.MENU,helper -> { MENU = new MenuType<>(ContainerGolemBuilder::new,FeatureFlags.DEFAULT_FLAGS); helper.register(ResourceLocation.parse("thaumcraft:golem_builder"),MENU); });
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,TYPE,(tile,side) -> side == null ? new InvWrapper(tile) : new SidedInvWrapper(tile,side));
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT,TYPE,(tile,side) -> side == null || tile.isConnectable(side) ? tile : null);
    }
    @SubscribeEvent public static void network(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(thaumcraft.common.lib.network.PacketHandler.PROTOCOL_VERSION);
        registrar.playToServer(PacketGolemBuilderSelect.TYPE,PacketGolemBuilderSelect.STREAM_CODEC,(packet,context) -> context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player && player.containerMenu instanceof ContainerGolemBuilder menu) menu.handle(player,packet);
        }));
        registrar.playToClient(PacketGolemBuilderState.TYPE,PacketGolemBuilderState.STREAM_CODEC,(packet,context) -> context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ContainerGolemBuilder menu) menu.acceptState(packet);
        }));
    }
}
