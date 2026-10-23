package thaumcraft.common.lib.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Native form of InventoryUtils.ejectStackAt: try the adjoining real inventory first. */
public final class ProcessingInventory {
    private ProcessingInventory() {}
    public static void eject(Level level,BlockPos pos,Direction face,ItemStack stack) {
        if (level == null || level.isClientSide || stack.isEmpty()) return;
        ItemStack remaining = stack.copy();
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK,pos.relative(face),face.getOpposite());
        if (handler != null) remaining = ItemHandlerHelper.insertItem(handler,remaining,false);
        if (remaining.isEmpty()) return;
        if (level.getBlockState(pos.relative(face)).isCollisionShapeFullBlock(level,pos.relative(face))) pos = pos.relative(face.getOpposite());
        ItemEntity entity = new ItemEntity(level,pos.getX() + .5 + face.getStepX(),pos.getY() + face.getStepY(),pos.getZ() + .5 + face.getStepZ(),remaining);
        entity.setDeltaMovement(.3 * face.getStepX(),.3 * face.getStepY(),.3 * face.getStepZ());
        level.addFreshEntity(entity);
    }
}
