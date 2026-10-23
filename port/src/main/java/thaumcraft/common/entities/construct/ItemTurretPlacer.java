package thaumcraft.common.entities.construct;

import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;

/** Source turret metadata 0/1/2 flattened into three registered items. */
public final class ItemTurretPlacer extends Item {
    private final int variant;
    public ItemTurretPlacer(int variant){super(new Properties());if(variant<0 || variant>2)throw new IllegalArgumentException("Construct variant");this.variant=variant;}
    public int sourceVariant(){return variant;}
    @Override public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){
        var player=context.getPlayer();var level=context.getLevel();var side=context.getClickedFace();if(player==null || side==Direction.DOWN)return InteractionResult.PASS;
        var clicked=context.getClickedPos();var pos=level.getBlockState(clicked).canBeReplaced()?clicked:clicked.relative(side);var above=pos.above();
        if(!player.mayUseItemAt(pos,side,stack) || !level.hasChunkAt(pos) || !level.hasChunkAt(above) || !level.isInWorldBounds(pos) || !level.isInWorldBounds(above))return InteractionResult.PASS;
        var lower=level.getBlockState(pos);var upper=level.getBlockState(above);
        if(!(lower.isAir() || lower.canBeReplaced()) || !(upper.isAir() || upper.canBeReplaced()) || !level.getEntities(null,new AABB(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+2,pos.getZ()+1)).isEmpty())return InteractionResult.PASS;
        if(level.isClientSide)return InteractionResult.PASS;
        // Protection/conservation bridge: do not erase an add-on's replaceable block entity.
        if(!level.mayInteract(player,pos) || !level.mayInteract(player,above) || level.getBlockEntity(pos)!=null || level.getBlockEntity(above)!=null)return InteractionResult.FAIL;
        EntityOwnedConstruct construct=switch(variant){case 0->Mob8ConstructRegistries.BASIC.create(level);case 1->Mob8ConstructRegistries.ADVANCED.create(level);default->Mob8ConstructRegistries.BORE.create(level);};
        if(construct==null)return InteractionResult.FAIL;construct.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);construct.setOwned(true);construct.setOwnerId(player.getUUID());construct.setValidSpawn();if(construct instanceof EntityArcaneBore bore)bore.setFacing(player.getDirection());
        // Join cancellation happens before payment or block changes; no ghost placer refund.
        if(!level.addFreshEntity(construct))return InteractionResult.FAIL;
        level.removeBlock(pos,false);level.removeBlock(above,false);level.playSound(null,construct.getX(),construct.getY(),construct.getZ(),SoundEvents.ARMOR_STAND_PLACE,SoundSource.BLOCKS,.75F,.8F);
        stack.shrink(1);return InteractionResult.SUCCESS; // Source deliberately pays even in creative.
    }
}
