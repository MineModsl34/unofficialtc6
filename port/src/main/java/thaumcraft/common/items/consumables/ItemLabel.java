package thaumcraft.common.items.consumables;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import thaumcraft.api.blocks.ILabelable;
import thaumcraft.api.items.ItemGenericEssentiaContainer;

/** Blank labels do not acquire random aspects; populated labels use the same aspect component. */
public class ItemLabel extends ItemGenericEssentiaContainer {
    public ItemLabel(Properties properties) { super(properties,1); }
    @Override public java.util.List<ItemStack> getTabVariants() { return java.util.List.of(new ItemStack(this)); }
    @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {}
    @Override public void onCraftedBy(ItemStack stack, Level level, Player player) {}
    @Override public boolean ignoreContainedAspects() { return true; }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getLevel().isClientSide || context.getPlayer() == null) return InteractionResult.PASS;
        Object target = context.getLevel().getBlockState(context.getClickedPos()).getBlock();
        if (!(target instanceof ILabelable)) target = context.getLevel().getBlockEntity(context.getClickedPos());
        if (!(target instanceof ILabelable labelable)) return InteractionResult.PASS;
        if (labelable.applyLabel(context.getPlayer(),context.getClickedPos(),context.getClickedFace(),stack)) { stack.shrink(1); context.getPlayer().inventoryMenu.broadcastChanges(); }
        return InteractionResult.SUCCESS;
    }
}
