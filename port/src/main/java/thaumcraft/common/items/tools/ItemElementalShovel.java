package thaumcraft.common.items.tools;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import thaumcraft.api.items.IArchitect;

/** Copies the clicked block into the source nine-position plane, paying inventory and durability.
 * Modern protections/placement failure cannot consume a block or leave an unpaid placement.
 */
public final class ItemElementalShovel extends ShovelItem implements IArchitect {
    public ItemElementalShovel(){super(EquipmentMaterials.ELEMENTAL,new Properties().rarity(Rarity.RARE).attributes(DiggerItem.createAttributes(EquipmentMaterials.ELEMENTAL,1.5F,-3)));}
    public static byte getOrientation(ItemStack stack){return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getByte("or");}
    public static void setOrientation(ItemStack stack,byte orientation){CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putByte("or",(byte)Math.floorMod(orientation,3)));}
    public static void cycleOrientation(ItemStack stack,Player player){if(!player.level().isClientSide && stack.getItem() instanceof ItemElementalShovel){setOrientation(stack,(byte)(getOrientation(stack)+1));player.inventoryMenu.broadcastChanges();}}
    private static ArrayList<BlockPos> plane(ItemStack stack,BlockPos pos,Direction side,Player player){
        var result=new ArrayList<BlockPos>();int yaw=Mth.floor(player.getYRot()*4F/360F+.5)&3;
        for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++){int[] o=EquipmentRules.shovelOffset(getOrientation(stack),side.ordinal(),yaw,a,b);result.add(pos.relative(side).offset(o[0],o[1],o[2]));}
        return result;
    }
    @Override public InteractionResult useOn(UseOnContext context){
        Player player=context.getPlayer();Level level=context.getLevel();BlockPos source=context.getClickedPos();ItemStack tool=context.getItemInHand();
        if(player==null || level.getBlockEntity(source)!=null)return InteractionResult.FAIL;
        if(level.isClientSide)return InteractionResult.FAIL;
        var state=level.getBlockState(source);Item preferred=state.getBlock().asItem();
        // Source returns FAIL even after placing. Native CommonHooks only flushes captured
        // notifications for SUCCESS; our paid per-position snapshots therefore own placement.
        boolean captureBefore=level.captureBlockSnapshots;level.captureBlockSnapshots=false;
        try {
        for(BlockPos pos:plane(tool,source,context.getClickedFace(),player)){
            if(tool.isEmpty())break;
            if(!level.hasChunkAt(pos) || !level.mayInteract(player,pos) || !player.mayUseItemAt(pos,context.getClickedFace(),tool) || !level.getBlockState(pos).canBeReplaced() || !state.canSurvive(level,pos))continue;
            int slot=inventorySlot(player,preferred);var placed=state;
            if(!player.isCreative() && slot<0){if(!state.is(Blocks.GRASS_BLOCK))continue;slot=inventorySlot(player,Items.DIRT);if(slot<0)continue;placed=Blocks.DIRT.defaultBlockState();}
            if(preferred==Items.AIR)continue;
            var snapshot=BlockSnapshot.create(level.dimension(),level,pos);
            if(!level.setBlock(pos,placed,3))continue;
            if(EventHooks.onBlockPlace(player,snapshot,context.getClickedFace())){snapshot.restore(3);continue;}
            if(!player.isCreative()){
                Item expected=placed==state?preferred:Items.DIRT;
                if(slot<0 || !player.getInventory().getItem(slot).is(expected)){snapshot.restore(3);continue;}
                player.getInventory().getItem(slot).shrink(1);player.getInventory().setChanged();
            }
            var sound=state.getSoundType(level,source,player);level.playSound(null,pos,sound.getBreakSound(),net.minecraft.sounds.SoundSource.BLOCKS,.6F,.9F+level.random.nextFloat()*.2F);
            tool.hurtAndBreak(1,player,context.getHand()==net.minecraft.world.InteractionHand.MAIN_HAND?net.minecraft.world.entity.EquipmentSlot.MAINHAND:net.minecraft.world.entity.EquipmentSlot.OFFHAND);player.swing(context.getHand());
            thaumcraft.common.lib.fx.SourceFX.bamf(level,net.minecraft.world.phys.Vec3.atCenterOf(pos),8401408,false,false,context.getClickedFace());
        }
        } finally {level.captureBlockSnapshots=captureBefore;}
        return InteractionResult.FAIL; // This unusual source return value is intentional.
    }
    private static int inventorySlot(Player player,Item item){for(int index=0;index<player.getInventory().getContainerSize();index++)if(player.getInventory().getItem(index).is(item))return index;return -1;}
    @Override public ArrayList<BlockPos> getArchitectBlocks(ItemStack stack,Level level,BlockPos pos,Direction side,Player player){
        var result=new ArrayList<BlockPos>();if(!player.isShiftKeyDown())return result;
        var state=level.getBlockState(pos);for(var target:plane(stack,pos,side,player))if(level.hasChunkAt(target) && level.getBlockState(target).canBeReplaced() && state.canSurvive(level,target))result.add(target);
        return result;
    }
    @Override public boolean showAxis(ItemStack stack,Level level,Player player,Direction side,EnumAxis axis){return false;}
    @Override public HitResult getArchitectMOP(ItemStack stack,Level level,LivingEntity entity){return entity.pick(entity instanceof Player player?player.blockInteractionRange():5,1,false);}
    @Override public boolean useBlockHighlight(ItemStack stack){return true;}
}
