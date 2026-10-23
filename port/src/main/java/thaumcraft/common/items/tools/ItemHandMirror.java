package thaumcraft.common.items.tools;

import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.container.ContainerHandMirror;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.tiles.devices.TileMirror;

public final class ItemHandMirror extends Item {
    public ItemHandMirror(){super(new Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON));}
    @Override public boolean isFoil(ItemStack stack){return stack.has(TCDataComponents.MIRROR_LINK);}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){
        if(!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TileMirror))return InteractionResult.PASS;
        if(!context.getLevel().isClientSide && context.getPlayer()!=null){
            stack.set(TCDataComponents.MIRROR_LINK,GlobalPos.of(context.getLevel().dimension(),context.getClickedPos()));
            context.getLevel().playSound(null,context.getClickedPos(),SoundsTC.jar,SoundSource.BLOCKS,1,2);
            context.getPlayer().displayClientMessage(Component.translatable("tc.handmirrorlinked"),false);
            context.getPlayer().inventoryMenu.broadcastChanges();
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    public static TileMirror destination(ItemStack mirror,Level level){
        GlobalPos link=mirror.get(TCDataComponents.MIRROR_LINK);
        if(link==null || level.getServer()==null)return null;
        var target=level.getServer().getLevel(link.dimension());
        return target!=null && target.hasChunkAt(link.pos()) && target.getBlockEntity(link.pos()) instanceof TileMirror tile?tile:null;
    }
    /** No resource is consumed unless the real far-side queue accepted its copy. */
    public static boolean transport(ItemStack mirror,ItemStack items,Player player,Level level){
        TileMirror target=destination(mirror,level);
        if(target!=null && target.transportDirect(items)){level.playSound(null,player.blockPosition(),SoundEvents.ENDERMAN_TELEPORT,SoundSource.PLAYERS,.1F,1);return true;}
        return false;
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack stack=player.getItemInHand(hand);
        if(!level.isClientSide && player instanceof ServerPlayer server && destination(stack,level)!=null){
            server.openMenu(new SimpleMenuProvider((id,inventory,owner)->new ContainerHandMirror(id,inventory,hand),Component.translatable("container.handmirror")),buffer->buffer.writeEnum(hand));
        } else if(!level.isClientSide && stack.has(TCDataComponents.MIRROR_LINK)){
            // Do not erase a link just because its target chunk/world is currently unavailable.
            GlobalPos link=stack.get(TCDataComponents.MIRROR_LINK);var target=level.getServer().getLevel(link.dimension());
            if(target!=null && target.hasChunkAt(link.pos())){stack.remove(TCDataComponents.MIRROR_LINK);player.playSound(SoundsTC.zap,1,.8F);player.displayClientMessage(Component.translatable("tc.handmirrorerror"),false);}
        }
        return InteractionResultHolder.pass(stack);
    }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flags){
        GlobalPos link=stack.get(TCDataComponents.MIRROR_LINK);
        if(link!=null)tooltip.add(Component.translatable("tc.handmirrorlinkedto").append(" "+link.pos().getX()+","+link.pos().getY()+","+link.pos().getZ()+" in "+link.dimension().location()));
    }
}
