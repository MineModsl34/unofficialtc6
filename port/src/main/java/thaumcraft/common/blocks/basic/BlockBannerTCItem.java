package thaumcraft.common.blocks.basic;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.tiles.misc.TileBanner;

/** Native BlockItem owns payment and Forge protection; source orientation is set only after success. */
public final class BlockBannerTCItem extends BlockItem {
    public BlockBannerTCItem(BlockBannerTC block){super(block,new Properties());}
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,net.minecraft.world.item.Item.TooltipContext context,java.util.List<net.minecraft.network.chat.Component> tooltip,net.minecraft.world.item.TooltipFlag flag){
        super.appendHoverText(stack,context,tooltip,flag);var data=stack.get(DataComponents.CUSTOM_DATA);
        var aspect=data==null?null:Aspect.getAspect(data.copyTag().getString("aspect"));if(aspect!=null)tooltip.add(net.minecraft.network.chat.Component.literal(aspect.getName()));
    }
    @Override public InteractionResult place(BlockPlaceContext context) {
        if(context.getClickedFace()==Direction.DOWN)return InteractionResult.FAIL;
        // TC6 checks solidity of the clicked support AND StandingBanner.canPlaceBlockAt's floor.
        if(!context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite())).isSolid()
            ||!context.getLevel().getBlockState(context.getClickedPos().below()).isSolid())return InteractionResult.FAIL;
        var held=context.getItemInHand().copy();var result=super.place(context);
        if(result.consumesAction()&&!context.getLevel().isClientSide&&context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TileBanner tile) {
            var face=context.getClickedFace();
            if(face==Direction.UP)tile.setBannerFacing((byte)(Mth.floor((context.getRotation()+180)*16/360+.5)&15));
            else {tile.setWall(true);tile.setBannerFacing((byte)switch(face){case NORTH->8;case WEST->4;case EAST->12;default->0;});}
            var data=held.get(DataComponents.CUSTOM_DATA);if(data!=null)tile.setAspect(Aspect.getAspect(data.copyTag().getString("aspect")));
            if(context.getPlayer()!=null&&context.getPlayer().getAbilities().instabuild)context.getItemInHand().shrink(1); // Source banner placement also pays in creative.
        }
        return result;
    }
}
