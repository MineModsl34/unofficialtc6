package thaumcraft.common.blocks.essentia;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.tiles.essentia.TileAlembic;

public class BlockJarItem extends BlockItem implements IEssentiaContainerItem {
    public BlockJarItem(Block block, Properties properties) { super(block,properties); }
    @Override public AspectList getAspects(ItemStack stack) {
        AspectList list = stack.get(TCDataComponents.ASPECTS);
        if (list == null) { var legacy = stack.get(DataComponents.CUSTOM_DATA); if (legacy != null) { list = new AspectList(); list.readFromNBT(legacy.copyTag()); } }
        return list == null || list.size() == 0 ? null : list.copy();
    }
    @Override public void setAspects(ItemStack stack, AspectList list) { stack.set(TCDataComponents.ASPECTS,list.copy()); }
    @Override public boolean ignoreContainedAspects() { return false; }
    public Aspect getFilter(ItemStack stack) {
        String filter = stack.get(TCDataComponents.ASPECT_FILTER);
        if (filter == null) { var legacy = stack.get(DataComponents.CUSTOM_DATA); if (legacy != null) filter = legacy.copyTag().getString("AspectFilter"); }
        return filter == null ? null : Aspect.getAspect(filter);
    }
    @Override public boolean isBarVisible(ItemStack stack) { return getAspects(stack) != null; }
    @Override public int getBarWidth(ItemStack stack) { AspectList list = getAspects(stack); return Math.round(13F * (list == null ? 0 : list.visSize()) / 250F); }
    @Override public int getBarColor(ItemStack stack) { AspectList list = getAspects(stack); return list == null ? 0xffffff : list.getAspects()[0].getColor(); }
    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flags) { Aspect filter = getFilter(stack); if (filter != null) lines.add(Component.literal(filter.getName()).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE)); super.appendHoverText(stack,context,lines,flags); }
    @Override public InteractionResult onItemUseFirst(ItemStack held, UseOnContext context) {
        if (context.getLevel().isClientSide || context.getPlayer() == null || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TileAlembic tile) || tile.amount <= 0) return InteractionResult.PASS;
        AspectList contained = getAspects(held); Aspect filter = getFilter(held);
        if (filter != null && filter != tile.aspect || contained != null && contained.getAspects()[0] != tile.aspect) return InteractionResult.FAIL;
        int base = contained == null ? 0 : contained.visSize(), transfer = Math.min(tile.amount,250 - base);
        if (transfer <= 0) return InteractionResult.FAIL;
        Aspect tag = tile.aspect;
        if (!tile.takeFromContainer(tag,transfer)) return InteractionResult.PASS;
        if (held.getCount() > 1) {
            ItemStack filled = held.copyWithCount(1); setAspects(filled,new AspectList().add(tag,base + transfer)); held.shrink(1);
            if (!context.getPlayer().getInventory().add(filled)) context.getLevel().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(context.getLevel(),context.getPlayer().getX(),context.getPlayer().getY(),context.getPlayer().getZ(),filled));
        } else setAspects(held,new AspectList().add(tag,base + transfer));
        context.getPlayer().playSound(SoundEvents.BOTTLE_FILL,.25F,1F); context.getPlayer().inventoryMenu.broadcastChanges(); return InteractionResult.SUCCESS;
    }
}
