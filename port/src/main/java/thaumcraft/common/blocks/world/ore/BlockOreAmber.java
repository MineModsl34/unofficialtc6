package thaumcraft.common.blocks.world.ore;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.research.theorycraft.AmberCurioRules;

/** TC6 BlockOreTC's real amber-to-preserved-curio survival drop. The roll uses the
 * world's random, after ordinary amber/Fortune loot and before block experience.
 * A successful roll replaces the entire amber stack with ONE curio; Silk Touch's
 * ore-block result neither rolls nor produces a curio. */
public final class BlockOreAmber extends DropExperienceBlock {
    private static final ResourceLocation PRESERVED = ResourceLocation.fromNamespaceAndPath("thaumcraft", "curio_preserved");
    public BlockOreAmber(BlockBehaviour.Properties properties) { super(UniformInt.of(1, 4), properties); }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return replaceAmber(super.getDrops(state, params), params.getLevel().getRandom());
    }
    /** Shared by real block loot and deterministic source-comparison tests; no fixture grants. */
    public static List<ItemStack> replaceAmber(List<ItemStack> source, RandomSource random) {
        List<ItemStack> drops = new ArrayList<>(source);
        for (int index = 0; index < drops.size(); index++) {
            ItemStack stack = drops.get(index);
            if (stack != null && !stack.isEmpty() && stack.is(ItemsTC.amber) && AmberCurioRules.isCurioRoll(random.nextFloat())) {
                // Registration is required: missing content is not substituted with AIR or
                // an unrelated item. Normal game registration provides this finite variant.
                if (!BuiltInRegistries.ITEM.containsKey(PRESERVED) || BuiltInRegistries.ITEM.get(PRESERVED) == Items.AIR)
                    throw new IllegalStateException("Missing source preserved curio for amber ore");
                drops.set(index, new ItemStack(BuiltInRegistries.ITEM.get(PRESERVED)));
            }
        }
        return drops;
    }
}
