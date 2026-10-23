package thaumcraft.common.golems.seals;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.BlockPos;
import thaumcraft.api.golems.ISealDisplayer;
public final class ItemSealPlacer extends Item implements ISealDisplayer {
 private final String key;public ItemSealPlacer(String key){super(new Properties());this.key=key;}public String sealKey(){return key;}
 public static ItemStack getSealStack(String key){var item=Golem9SealRegistries.ITEMS.get(key);return item==null?ItemStack.EMPTY:new ItemStack(item);}
 public InteractionResult onItemUseFirst(ItemStack stack,UseOnContext context){var player=context.getPlayer();var world=context.getLevel();var pos=context.getClickedPos();var side=context.getClickedFace();if(player==null||world.isClientSide||key.isEmpty()||player.isShiftKeyDown())return InteractionResult.PASS;if(!world.hasChunkAt(pos)||!player.mayUseItemAt(pos,side,stack)||!world.mayInteract(player,pos))return InteractionResult.FAIL;var seal=SealHandler.newSeal(key);if(seal==null||!seal.canPlaceAt(world,pos,side))return InteractionResult.FAIL;if(SealHandler.addSealEntity(world,pos,side,seal,player)&&!player.getAbilities().instabuild)stack.shrink(1);return InteractionResult.SUCCESS;}
 @Override public boolean doesSneakBypassUse(ItemStack stack,LevelReader world,BlockPos pos,Player player){return true;}
}
