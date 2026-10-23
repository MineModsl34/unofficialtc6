package thaumcraft.api.casters;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Source caster/interactive-machine extension point; the real caster item supplies this context. */
public interface IInteractWithCaster {
    boolean onCasterRightClick(Level world, ItemStack caster, Player player, BlockPos pos, Direction side, InteractionHand hand);
}
