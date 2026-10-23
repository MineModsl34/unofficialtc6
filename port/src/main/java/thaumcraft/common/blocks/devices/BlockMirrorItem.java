package thaumcraft.common.blocks.devices;

import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.tiles.devices.TileMirrorBase;

/** Source first-use linking, which happens BEFORE block activation/placement. */
public final class BlockMirrorItem extends BlockItem {
    public BlockMirrorItem(Block block,Properties properties){super(block,properties.rarity(net.minecraft.world.item.Rarity.UNCOMMON));}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){
        if(!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TileMirrorBase mirror) || !mirror.getBlockState().is(getBlock()))return InteractionResult.PASS;
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
        if(mirror.isLinkValid()){player.displayClientMessage(Component.translatable("tc.mirror.alreadylinked"),false);return InteractionResult.CONSUME;}
        ItemStack linked=stack.copyWithCount(1);linked.set(TCDataComponents.MIRROR_LINK,GlobalPos.of(context.getLevel().dimension(),context.getClickedPos()));
        if(!player.getAbilities().instabuild)stack.shrink(1);
        if(!player.getInventory().add(linked))player.drop(linked,false);
        context.getLevel().playSound(null,context.getClickedPos(),SoundsTC.jar,SoundSource.BLOCKS,1,2);
        player.inventoryMenu.broadcastChanges();return InteractionResult.CONSUME;
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flags){
        super.appendHoverText(stack,context,lines,flags);GlobalPos link=stack.get(TCDataComponents.MIRROR_LINK);
        if(link!=null)lines.add(Component.literal("Linked to "+link.pos().getX()+","+link.pos().getY()+","+link.pos().getZ()+" in "+link.dimension().location()));
    }
}
