package thaumcraft.common.items.tools;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;

public final class ItemElementalHoe extends HoeItem {
    public ItemElementalHoe(){super(EquipmentMaterials.ELEMENTAL,new Properties().rarity(Rarity.RARE).attributes(DiggerItem.createAttributes(EquipmentMaterials.ELEMENTAL,-3,0)));}
    @Override public int getEnchantmentValue(){return 5;}
    @Override public void postHurtEnemy(ItemStack stack,LivingEntity target,LivingEntity attacker){stack.hurtAndBreak(1,attacker,EquipmentSlot.MAINHAND);}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        if(player.isShiftKeyDown())return super.useOn(context);
        boolean did=false;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var pos=context.getClickedPos().offset(x,0,z);
            if(!context.getLevel().hasChunkAt(pos) || !context.getLevel().mayInteract(player,pos) || !player.mayUseItemAt(pos.relative(context.getClickedFace()),context.getClickedFace(),context.getItemInHand()))continue;
            var hit=new BlockHitResult(context.getClickLocation().add(x,0,z),context.getClickedFace(),pos,context.isInside());
            if(super.useOn(new UseOnContext(context.getLevel(),player,context.getHand(),context.getItemInHand(),hit)).consumesAction()){
                did=true;
                thaumcraft.common.lib.fx.SourceFX.local(context.getLevel(),"hoe",new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()+1.01,pos.getZ()+.5),net.minecraft.world.phys.Vec3.ZERO,0,x==0&&z==0?1:0);
            }
            if(context.getItemInHand().isEmpty())return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if(!did && context.getLevel().mayInteract(player,context.getClickedPos()) && BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL),context.getLevel(),context.getClickedPos(),player)){
            if(!context.getLevel().isClientSide){context.getItemInHand().hurtAndBreak(3,player,context.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND);context.getLevel().levelEvent(1505,context.getClickedPos(),0);}
            else thaumcraft.common.lib.fx.SourceFX.emit(context.getLevel(),thaumcraft.common.lib.fx.SourceFX.event(thaumcraft.common.lib.network.fx.PacketSourceFX.Kind.MIST,net.minecraft.world.phys.Vec3.atLowerCornerOf(context.getClickedPos()),net.minecraft.world.phys.Vec3.ZERO,4259648,0,0,-1,""));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
}
