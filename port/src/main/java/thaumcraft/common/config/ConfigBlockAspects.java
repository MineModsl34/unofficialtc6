package thaumcraft.common.config;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;

/** Explicit BETA26 block aspects and recipe-derived additions, in source order.
 * Native flattened blocks already represent their old metadata variant; wildcard damage is not item NBT.
 */
public final class ConfigBlockAspects {
    private ConfigBlockAspects() {}
    public static void register() {
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.crystalAir), new AspectList().add(Aspect.AIR, 15).add(Aspect.CRYSTAL, 10));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.crystalFire), new AspectList().add(Aspect.FIRE, 15).add(Aspect.CRYSTAL, 10));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.crystalWater), new AspectList().add(Aspect.WATER, 15).add(Aspect.CRYSTAL, 10));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.crystalEarth), new AspectList().add(Aspect.EARTH, 15).add(Aspect.CRYSTAL, 10));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.crystalOrder), new AspectList().add(Aspect.ORDER, 15).add(Aspect.CRYSTAL, 10));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.crystalEntropy), new AspectList().add(Aspect.ENTROPY, 15).add(Aspect.CRYSTAL, 10));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.crystalTaint), new AspectList().add(Aspect.FLUX, 15).add(Aspect.CRYSTAL, 10));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.taintFibre), new AspectList().add(Aspect.PLANT, 5).add(Aspect.FLUX, 10));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.taintCrust), new AspectList().add(Aspect.LIFE, 5).add(Aspect.FLUX, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.taintSoil), new AspectList().add(Aspect.EARTH, 5).add(Aspect.FLUX, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.taintGeyser), new AspectList().add(Aspect.AURA, 5).add(Aspect.WATER, 5).add(Aspect.FLUX, 10));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.taintRock), new AspectList().add(Aspect.EARTH, 10).add(Aspect.FLUX, 5));
        ThaumcraftApi.registerObjectTag(
            new ItemStack(BlocksTC.taintFeature), new AspectList().add(Aspect.AURA, 5).add(Aspect.BEAST, 5).add(Aspect.FLUX, 10)
        );
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.taintLog), new AspectList().add(Aspect.PLANT, 5).add(Aspect.FLUX, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.logGreatwood), new AspectList().add(Aspect.PLANT, 20).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.logSilverwood), new AspectList().add(Aspect.PLANT, 20).add(Aspect.AURA, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.leafGreatwood), new AspectList().add(Aspect.PLANT, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.leafSilverwood), new AspectList().add(Aspect.PLANT, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.saplingGreatwood), new AspectList().add(Aspect.PLANT, 15).add(Aspect.LIFE, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.saplingSilverwood), new AspectList().add(Aspect.PLANT, 15).add(Aspect.AURA, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.shimmerleaf), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AURA, 10).add(Aspect.ENERGY, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.cinderpearl), new AspectList().add(Aspect.PLANT, 5).add(Aspect.AURA, 5).add(Aspect.FIRE, 10));
        ThaumcraftApi.registerObjectTag(
            new ItemStack(BlocksTC.vishroom), new AspectList().add(Aspect.PLANT, 2).add(Aspect.DEATH, 1).add(Aspect.MAGIC, 1).add(Aspect.ENTROPY, 1)
        );
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.stoneAncient), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.stoneAncientTile), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.stoneAncientRock), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.stoneEldritchTile), new AspectList().add(Aspect.EARTH, 5).add(Aspect.ELDRITCH, 5));
        ThaumcraftApi.registerObjectTag(
            new ItemStack(BlocksTC.stoneAncientDoorway), new AspectList().add(Aspect.METAL, 5).add(Aspect.ELDRITCH, 5).add(Aspect.TRAP, 5)
        );
        ThaumcraftApi.registerObjectTag(
            new ItemStack(BlocksTC.stoneAncientGlyphed), new AspectList().add(Aspect.METAL, 5).add(Aspect.ELDRITCH, 5).add(Aspect.MIND, 5)
        );
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.stonePorous), new AspectList().add(Aspect.EARTH, 5).add(Aspect.VOID, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.researchTable), new AspectList(new ItemStack(BlocksTC.tableWood)).add(Aspect.MIND, 5));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.lootUrnCommon), new AspectList().add(Aspect.DESIRE, 10).add(Aspect.EARTH, 2));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.lootUrnUncommon), new AspectList().add(Aspect.DESIRE, 20).add(Aspect.EARTH, 2));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.lootUrnRare), new AspectList().add(Aspect.DESIRE, 30).add(Aspect.EARTH, 2));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.lootCrateCommon), new AspectList().add(Aspect.DESIRE, 10).add(Aspect.PLANT, 2));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.lootCrateUncommon), new AspectList().add(Aspect.DESIRE, 20).add(Aspect.PLANT, 2));
        ThaumcraftApi.registerObjectTag(new ItemStack(BlocksTC.lootCrateRare), new AspectList().add(Aspect.DESIRE, 30).add(Aspect.PLANT, 2));
        ThaumcraftApi.registerObjectTag(
            new ItemStack(BlocksTC.crucible), new AspectList(new ItemStack(Items.CAULDRON)).add(Aspect.CRAFT, 20).add(Aspect.ALCHEMY, 20)
        );
        for (Block ca : BlocksTC.candles.values()) {
            ThaumcraftApi.registerComplexObjectTag(new ItemStack(ca), new AspectList().add(Aspect.LIGHT, 5));
        }
        ThaumcraftApi.registerComplexObjectTag(new ItemStack(BlocksTC.pedestalArcane), new AspectList().add(Aspect.MAGIC, 3).add(Aspect.AIR, 3));
        ThaumcraftApi.registerComplexObjectTag(new ItemStack(BlocksTC.pedestalAncient), new AspectList().add(Aspect.MAGIC, 3).add(Aspect.ELDRITCH, 3));
        ThaumcraftApi.registerComplexObjectTag(new ItemStack(BlocksTC.pedestalEldritch), new AspectList().add(Aspect.MAGIC, 3).add(Aspect.ELDRITCH, 3));
        ThaumcraftApi.registerComplexObjectTag(new ItemStack(BlocksTC.arcaneEar), new AspectList().add(Aspect.SENSES, 20));
        for (Block ca : BlocksTC.banners.values()) {
            ThaumcraftApi.registerComplexObjectTag(new ItemStack(ca), new AspectList().add(Aspect.ELDRITCH, 5));
        }
        // ConfigBlocks BETA26 never registers BlocksTC.eldritch. Its eight old metadata
        // stacks were empty, not eight existing flattened blocks. Do not dereference null
        // or collapse those different source values onto one invented native item.
    }
}
