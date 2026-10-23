package thaumcraft.client;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.world.ore.BlockCrystal;
import thaumcraft.common.config.ConfigBlocks;

/** Source colors, with the armor/caster/golem/filter registrations owned by their existing native slices. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ColorHandler {
    private ColorHandler() {
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (stack.getItem() instanceof ItemGenericEssentiaContainer item) {
                AspectList aspects = item.getAspects(stack);
                if (aspects != null && aspects.size() > 0) {
                    return FastColor.ARGB32.opaque(aspects.getAspects()[0].getColor());
                }
            }
            return 0xFFFFFFFF;
        }, ItemsTC.crystalEssence);
        event.register((stack, tintIndex) -> {
            if (tintIndex == 1 && stack.getItem() instanceof ItemGenericEssentiaContainer item) {
                AspectList aspects = item.getAspects(stack);
                if (aspects != null && aspects.size() > 0) return FastColor.ARGB32.opaque(aspects.getAspects()[0].getColor());
            }
            return 0xFFFFFFFF;
        }, thaumcraft.common.items.consumables.ItemPhial.filledVariant);
        for (BlockCrystal crystal : ConfigBlocks.CRYSTALS) {
            int color = FastColor.ARGB32.opaque(crystal.aspect.getColor());
            event.register((stack, tintIndex) -> color, crystal);
        }
        // In TC6 the items of these blocks took the colour from the block without a level (blockColors.colorMultiplier(state, null, null))
        for (Block candle : BlocksTC.candles.values()) {
            int color = FastColor.ARGB32.opaque(candle.defaultMapColor().col);
            event.register((stack, tintIndex) -> color, candle);
        }
        event.register((stack, tintIndex) -> FastColor.ARGB32.opaque(FoliageColor.getDefaultColor()), BlocksTC.leafGreatwood);
        event.register((stack, tintIndex) -> 0xFFFFFFFF, BlocksTC.leafSilverwood);
        for (Block nitor : BlocksTC.nitor.values()) {
            int color = FastColor.ARGB32.opaque(nitor.defaultMapColor().col);
            event.register((stack, tintIndex) -> color, nitor);
        }
        event.register((stack, tintIndex) -> FastColor.ARGB32.opaque(GrassColor.get(0.5, 1.0)), BlocksTC.grassAmbient);
        event.register((stack,tintIndex) -> {
            if (tintIndex == 1 && stack.getItem() instanceof thaumcraft.common.blocks.essentia.BlockJarItem jar) { var tags = jar.getAspects(stack); if (tags != null && tags.size() > 0) return FastColor.ARGB32.opaque(tags.getAspects()[0].getColor()); }
            return 0xffffffff;
        },BlocksTC.jarNormal,BlocksTC.jarVoid);
        event.register((stack,tintIndex) -> {
            var tags = stack.get(thaumcraft.api.items.TCDataComponents.ASPECTS);
            return tintIndex == 1 && tags != null && tags.size() > 0 ? FastColor.ARGB32.opaque(tags.getAspects()[0].getColor()) : 0xffffffff;
        },ItemsTC.label);
        for (Block banner : BlocksTC.banners.values()) event.register((stack,index) -> {
            if(index==2){var data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);var aspect=data==null?null:thaumcraft.api.aspects.Aspect.getAspect(data.copyTag().getString("aspect"));if(aspect!=null)return FastColor.ARGB32.opaque(aspect.getColor());}
            return index==1||index==2?FastColor.ARGB32.opaque(banner.defaultMapColor().col):0xFFFFFFFF;
        },banner);
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state,level,pos,tintIndex) -> {
            return tintIndex == 0 ? thaumcraft.client.lib.events.HudGeometry.chargeColor(state.getValue(thaumcraft.common.blocks.devices.BlockInlay.CHARGE)) : 0xFFFFFFFF;
        },BlocksTC.inlay);
        event.register((state,level,pos,index) -> {
            int energy=level!=null&&pos!=null&&level.getBlockEntity(pos) instanceof thaumcraft.common.tiles.devices.TileStabilizer tile?tile.getEnergy():0;
            return index==0?thaumcraft.client.lib.events.HudGeometry.chargeColor(energy):0xFFFFFFFF;
        },BlocksTC.stabilizer);
        for (BlockCrystal crystal : ConfigBlocks.CRYSTALS) {
            int color = FastColor.ARGB32.opaque(crystal.aspect.getColor());
            event.register((state, level, pos, tintIndex) -> color, crystal);
        }
        for (Block candle : BlocksTC.candles.values()) {
            event.register((state, level, pos, tintIndex) -> FastColor.ARGB32.opaque(candle.defaultMapColor().col), candle);
        }
        for (Block nitor : BlocksTC.nitor.values()) event.register((state,level,pos,index) -> FastColor.ARGB32.opaque(nitor.defaultMapColor().col),nitor);
        for (Block banner : BlocksTC.banners.values()) event.register((state,level,pos,index) -> FastColor.ARGB32.opaque(banner.defaultMapColor().col),banner);
        // Silverwood is not tinted (white), greatwood takes the biome foliage colour
        event.register((state, level, pos, tintIndex) -> level != null && pos != null
            ? BiomeColors.getAverageFoliageColor(level, pos) : FoliageColor.getDefaultColor(), BlocksTC.leafGreatwood);
        event.register((state,level,pos,index) -> 0xFFFFFFFF,BlocksTC.leafSilverwood);
        event.register((state, level, pos, tintIndex) -> level != null && pos != null
            ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.get(0.5, 1.0), BlocksTC.grassAmbient);
    }
}
