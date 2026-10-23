package thaumcraft.common.items.casters;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.casters.*;
import thaumcraft.common.items.casters.foci.*;
import thaumcraft.common.blocks.misc.*;
import thaumcraft.common.tiles.misc.TileHole;
import thaumcraft.common.container.ContainerFocusPouch;
import thaumcraft.common.entities.projectile.*;
import thaumcraft.common.entities.monster.EntitySpellBat;
import thaumcraft.common.lib.network.misc.PacketCasterAction;
/** Shared registry owners call createItems/createBlocks/createEntityTypes/createMenus. BE/payload hooks are self-contained. */
@EventBusSubscriber(modid="thaumcraft",bus=EventBusSubscriber.Bus.MOD)
public final class CasterRegistries {
    public static EntityType<EntityFocusProjectile> PROJECTILE;public static EntityType<EntityFocusMine> MINE;public static EntityType<EntityFocusCloud> CLOUD;public static EntityType<EntitySpellBat> SPELLBAT;
    public static BlockEntityType<TileHole> HOLE;public static MenuType<ContainerFocusPouch> POUCH_MENU;
    private CasterRegistries(){}
    public static Map<String,Item> createItems(){registerNodes();var map=new LinkedHashMap<String,Item>();map.put("focus_1",ItemsTC.focus1=new ItemFocus(new Item.Properties(),15));map.put("focus_2",ItemsTC.focus2=new ItemFocus(new Item.Properties(),25));map.put("focus_3",ItemsTC.focus3=new ItemFocus(new Item.Properties(),50));map.put("focus_pouch",ItemsTC.focusPouch=new ItemFocusPouch(new Item.Properties()));return map;}
    public static Map<String,Block> createBlocks(){return Map.of("hole",BlocksTC.hole=new BlockHole(BlockBehaviour.Properties.of().strength(-1,3600000).sound(SoundType.WOOL).lightLevel(s->10).randomTicks().noOcclusion().noCollission().noLootTable()),"effect_sap",BlocksTC.effectSap=new BlockFocusEffect(effectProperties()),"effect_shock",BlocksTC.effectShock=new BlockFocusEffect(effectProperties()));}
    private static BlockBehaviour.Properties effectProperties(){return BlockBehaviour.Properties.of().air().replaceable().noCollission().noOcclusion().noLootTable().randomTicks().strength(1.5F,199.8F).lightLevel(s->7);}
    /** Temporary air effects and holes have no obtainable block items or drops. */
    public static boolean hasBlockItem(Block block){return block!=BlocksTC.hole&&block!=BlocksTC.effectSap&&block!=BlocksTC.effectShock;}
    public static Map<String,EntityType<?>> createEntityTypes(){PROJECTILE=EntityType.Builder.<EntityFocusProjectile>of(EntityFocusProjectile::new,MobCategory.MISC).sized(.15F,.15F).clientTrackingRange(4).updateInterval(20).build("thaumcraft:focus_projectile");MINE=EntityType.Builder.<EntityFocusMine>of(EntityFocusMine::new,MobCategory.MISC).sized(.15F,.15F).clientTrackingRange(4).updateInterval(20).build("thaumcraft:focus_mine");CLOUD=EntityType.Builder.<EntityFocusCloud>of(EntityFocusCloud::new,MobCategory.MISC).sized(1,.5F).clientTrackingRange(4).updateInterval(20).build("thaumcraft:focus_cloud");SPELLBAT=EntityType.Builder.<EntitySpellBat>of(EntitySpellBat::new,MobCategory.MISC).sized(.5F,.9F).clientTrackingRange(4).updateInterval(3).setShouldReceiveVelocityUpdates(false).build("thaumcraft:spellbat");return Map.of("focus_projectile",PROJECTILE,"focus_mine",MINE,"focus_cloud",CLOUD,"spellbat",SPELLBAT);}
    public static Map<String,MenuType<?>> createMenus(){POUCH_MENU=IMenuTypeExtension.create((id,inv,buffer)->new ContainerFocusPouch(id,inv,buffer.readVarInt()));return Map.of("focus_pouch",POUCH_MENU);}
    @SubscribeEvent public static void entities(RegisterEvent event){event.register(Registries.BLOCK_ENTITY_TYPE,helper->{HOLE=BlockEntityType.Builder.of(TileHole::new,BlocksTC.hole).build(null);helper.register(ResourceLocation.parse("thaumcraft:hole"),HOLE);});}
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event){event.put(SPELLBAT,EntitySpellBat.attributes().build());}
    @SubscribeEvent public static void payloads(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(PacketCasterAction.TYPE,PacketCasterAction.STREAM_CODEC,PacketCasterAction::handle);}
    public static void registerNodes(){
        FocusEngine.registerElement(FocusMediumRoot.class,ResourceLocation.parse("thaumcraft:textures/foci/root.png"),10066329);
        FocusEngine.registerElement(FocusMediumTouch.class,ResourceLocation.parse("thaumcraft:textures/foci/touch.png"),11371909);
        FocusEngine.registerElement(FocusMediumBolt.class,ResourceLocation.parse("thaumcraft:textures/foci/bolt.png"),11377029);
        FocusEngine.registerElement(FocusMediumProjectile.class,ResourceLocation.parse("thaumcraft:textures/foci/projectile.png"),11382149);
        FocusEngine.registerElement(FocusMediumCloud.class,ResourceLocation.parse("thaumcraft:textures/foci/cloud.png"),10071429);
        FocusEngine.registerElement(FocusMediumMine.class,ResourceLocation.parse("thaumcraft:textures/foci/mine.png"),8760709);
        FocusEngine.registerElement(FocusMediumPlan.class,ResourceLocation.parse("thaumcraft:textures/foci/plan.png"),8760728);
        FocusEngine.registerElement(FocusMediumSpellBat.class,ResourceLocation.parse("thaumcraft:textures/foci/spellbat.png"),8760748);
        FocusEngine.registerElement(FocusEffectFire.class,ResourceLocation.parse("thaumcraft:textures/foci/fire.png"),16734721);
        FocusEngine.registerElement(FocusEffectFrost.class,ResourceLocation.parse("thaumcraft:textures/foci/frost.png"),14811135);
        FocusEngine.registerElement(FocusEffectAir.class,ResourceLocation.parse("thaumcraft:textures/foci/air.png"),16777086);
        FocusEngine.registerElement(FocusEffectEarth.class,ResourceLocation.parse("thaumcraft:textures/foci/earth.png"),5685248);
        FocusEngine.registerElement(FocusEffectFlux.class,ResourceLocation.parse("thaumcraft:textures/foci/flux.png"),8388736);
        FocusEngine.registerElement(FocusEffectBreak.class,ResourceLocation.parse("thaumcraft:textures/foci/break.png"),9063176);
        FocusEngine.registerElement(FocusEffectRift.class,ResourceLocation.parse("thaumcraft:textures/foci/rift.png"),3084645);
        FocusEngine.registerElement(FocusEffectExchange.class,ResourceLocation.parse("thaumcraft:textures/foci/exchange.png"),5735255);
        FocusEngine.registerElement(FocusEffectCurse.class,ResourceLocation.parse("thaumcraft:textures/foci/curse.png"),6946821);
        FocusEngine.registerElement(FocusEffectHeal.class,ResourceLocation.parse("thaumcraft:textures/foci/heal.png"),14548997);
        FocusEngine.registerElement(FocusModScatter.class,ResourceLocation.parse("thaumcraft:textures/foci/scatter.png"),10066329);
        FocusEngine.registerElement(FocusModSplitTarget.class,ResourceLocation.parse("thaumcraft:textures/foci/split_target.png"),10066329);
        FocusEngine.registerElement(FocusModSplitTrajectory.class,ResourceLocation.parse("thaumcraft:textures/foci/split_trajectory.png"),10066329);
    }
}
