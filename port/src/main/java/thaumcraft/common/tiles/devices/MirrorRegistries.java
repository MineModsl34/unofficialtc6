package thaumcraft.common.tiles.devices;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.blocks.devices.BlockMirror;
import thaumcraft.common.container.ContainerHandMirror;

@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class MirrorRegistries {
    public static BlockEntityType<TileMirror> MIRROR;
    public static BlockEntityType<TileMirrorEssentia> ESSENTIA;
    public static MenuType<ContainerHandMirror> HAND_MENU;
    private MirrorRegistries(){}
    public static Map<String,Block> createBlocks(){
        var blocks=new LinkedHashMap<String,Block>();
        BlocksTC.mirror=new BlockMirror(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(.1F).sound(SoundType.GLASS).noOcclusion());blocks.put("mirror",BlocksTC.mirror);
        BlocksTC.mirrorEssentia=new BlockMirror(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(.1F).sound(SoundType.GLASS).noOcclusion());blocks.put("mirror_essentia",BlocksTC.mirrorEssentia);
        return blocks;
    }
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.BLOCK_ENTITY_TYPE,helper->{
            MIRROR=BlockEntityType.Builder.of(TileMirror::new,BlocksTC.mirror).build(null);ESSENTIA=BlockEntityType.Builder.of(TileMirrorEssentia::new,BlocksTC.mirrorEssentia).build(null);
            helper.register(ResourceLocation.parse("thaumcraft:mirror"),MIRROR);helper.register(ResourceLocation.parse("thaumcraft:mirror_essentia"),ESSENTIA);
        });
        event.register(Registries.MENU,helper->{HAND_MENU=IMenuTypeExtension.create((id,inventory,buffer)->new ContainerHandMirror(id,inventory,buffer.readEnum(net.minecraft.world.InteractionHand.class)));helper.register(ResourceLocation.parse("thaumcraft:hand_mirror"),HAND_MENU);});
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event){event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,MIRROR,(tile,side)->tile.insertion);}
}
