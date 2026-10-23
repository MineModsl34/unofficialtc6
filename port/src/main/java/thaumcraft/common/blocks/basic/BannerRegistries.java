package thaumcraft.common.blocks.basic;

import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.misc.TileBanner;

@EventBusSubscriber(modid=Thaumcraft.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class BannerRegistries {
    public static BlockEntityType<TileBanner> BANNER;
    private BannerRegistries() {}
    public static Map<String,Block> createBlocks(){
        Map<String,Block> blocks=new LinkedHashMap<>();
        blocks.put("banner_crimson_cult",BlocksTC.bannerCrimsonCult=new BlockBannerTC());
        for(DyeColor dye:DyeColor.values()) {
            Block banner=new BlockBannerTC(dye); BlocksTC.banners.put(dye,banner);
            blocks.put("banner_"+dye.getName(),banner);
        }
        return blocks;
    }
    @SubscribeEvent public static void register(RegisterEvent event){event.register(Registries.BLOCK_ENTITY_TYPE,helper->{
        var blocks=new java.util.ArrayList<Block>(BlocksTC.banners.values());blocks.add(BlocksTC.bannerCrimsonCult);
        BANNER=BlockEntityType.Builder.of(TileBanner::new,blocks.toArray(Block[]::new)).build(null);helper.register(ResourceLocation.parse("thaumcraft:banner"),BANNER);
    });}
}
