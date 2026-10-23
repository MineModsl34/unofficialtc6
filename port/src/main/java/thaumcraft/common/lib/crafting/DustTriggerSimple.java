package thaumcraft.common.lib.crafting;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.IDustTrigger;

/** Source simple dust gate. The water-state cauldron split is the explicit 1.21 identity bridge. */
public class DustTriggerSimple implements IDustTrigger {
    protected final Block target;
    protected final Supplier<ItemStack> result;
    protected final String research;

    public DustTriggerSimple(String research, Block target, ItemStack result) {
        this(research, target, () -> result.copy());
        Objects.requireNonNull(result, "result");
    }
    protected DustTriggerSimple(String research, Block target, Supplier<ItemStack> result) {
        this.target = Objects.requireNonNull(target, "target"); this.result = Objects.requireNonNull(result, "result"); this.research = research;
    }

    protected boolean matches(BlockState state) {
        return target == Blocks.CAULDRON
            ? DustConversionRules.sourceCauldron(state.is(Blocks.CAULDRON), state.is(Blocks.WATER_CAULDRON))
            : state.is(target);
    }
    protected boolean researchKnown(Player player) {
        return player != null && (research == null || ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(research));
    }
    @Override public Placement getValidFace(Level world, Player player, BlockPos pos, Direction face) {
        return world.hasChunkAt(pos) && researchKnown(player) && matches(world.getBlockState(pos)) ? new Placement(0, 0, 0, null) : null;
    }
    @Override public boolean canExecute(Level world, Player player, BlockPos pos, Placement placement) {
        ItemStack output = result.get();
        return DustConversion.isSupportedOutput(output)
            && (!(world instanceof ServerLevel level) || player instanceof ServerPlayer owner && DustConversion.canSchedule(level, owner, pos));
    }
    @Override public void execute(Level world, Player player, BlockPos pos, Placement placement, Direction side) {
        if (world instanceof ServerLevel level && player instanceof ServerPlayer owner) DustConversion.schedulePaid(level, owner, pos, result.get());
    }
}
