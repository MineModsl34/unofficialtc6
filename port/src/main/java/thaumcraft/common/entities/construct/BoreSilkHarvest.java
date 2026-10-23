package thaumcraft.common.entities.construct;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Source's canSilkHarvest gate only chooses bore fortune/SECOND refining. Native
 * loot tables still exclusively decide actual drops. Tags replace the removed Forge
 * 1.12 block override API for add-ons, without probing loot or consuming extra RNG.
 */
final class BoreSilkHarvest {
    static final TagKey<Block> ALLOWED=TagKey.create(Registries.BLOCK,ResourceLocation.parse("thaumcraft:bore_silk_allowed"));
    static final TagKey<Block> DENIED=TagKey.create(Registries.BLOCK,ResourceLocation.parse("thaumcraft:bore_silk_denied"));
    private BoreSilkHarvest(){}
    static boolean allowed(Level level,BlockPos pos,BlockState state){
        if(state.is(DENIED))return false;if(state.is(ALLOWED))return true;
        var block=state.getBlock();if(block instanceof SlabBlock)return false;
        // Explicit original BlockGlass/BlockPane/BlockWeb/BlockEnderChest overrides.
        if(block instanceof TransparentBlock || block instanceof IronBarsBlock || block instanceof WebBlock || block instanceof EnderChestBlock || state.is(Blocks.SEA_LANTERN))return true;
        return !state.hasBlockEntity() && block.defaultBlockState().isCollisionShapeFullBlock(level,pos);
    }
}
