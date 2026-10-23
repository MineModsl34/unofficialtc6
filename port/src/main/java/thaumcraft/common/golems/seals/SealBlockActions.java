package thaumcraft.common.golems.seals;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.*;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.golems.IGolemAPI;

/** Original seal block helpers, adapted to actual native harvest and placement events. */
public final class SealBlockActions {
 private static final GameProfile PROFILE=new GameProfile(UUID.nameUUIDFromBytes("Thaumcraft:FakeThaumcraftGolem".getBytes(StandardCharsets.UTF_8)),"FakeThaumcraftGolem");
 private static final GameProfile SOIL_PROFILE=new GameProfile(UUID.nameUUIDFromBytes("Thaumcraft:SealHarvestSoil".getBytes(StandardCharsets.UTF_8)),"SealHarvestSoil");
 private static final Set<FakePlayer> SOIL_ACTIVE=Collections.newSetFromMap(new IdentityHashMap<>());
 public static final Map<Block,Predicate<BlockState>> standardCrops=new IdentityHashMap<>(),clickableCrops=new IdentityHashMap<>(),stackedCrops=new IdentityHashMap<>();
 private SealBlockActions(){}
 public static void registerSeed(Block block,ItemStack seed){ThaumcraftApi.registerSeed(block,seed);}
 public static ItemStack getSeed(Block block){var seed=ThaumcraftApi.getSeed(block);return seed==null?ItemStack.EMPTY:seed.copy();}
 public static void defaults(){
  registerSeed(Blocks.WHEAT,new ItemStack(Items.WHEAT_SEEDS));registerSeed(Blocks.CARROTS,new ItemStack(Items.CARROT));registerSeed(Blocks.POTATOES,new ItemStack(Items.POTATO));registerSeed(Blocks.BEETROOTS,new ItemStack(Items.BEETROOT_SEEDS));registerSeed(Blocks.NETHER_WART,new ItemStack(Items.NETHER_WART));registerSeed(Blocks.COCOA,new ItemStack(Items.COCOA_BEANS));
  standardCrops.put(Blocks.MELON,s->true);standardCrops.put(Blocks.PUMPKIN,s->true);standardCrops.put(Blocks.NETHER_WART,s->s.getValue(NetherWartBlock.AGE)==3);stackedCrops.put(Blocks.SUGAR_CANE,s->true);stackedCrops.put(Blocks.CACTUS,s->true);
 }
 public static boolean grown(Level level,BlockPos pos){if(!level.hasChunkAt(pos))return false;var s=level.getBlockState(pos);if(s.isAir())return false;var b=s.getBlock();boolean registered=matches(standardCrops,s)||matches(clickableCrops,s)||matches(stackedCrops,s);
  return b instanceof BonemealableBlock grow && !(b instanceof StemBlock) && !(b instanceof AttachedStemBlock) && !grow.isValidBonemealTarget(level,pos,s) || b instanceof CropBlock crop && crop.isMaxAge(s)&&!registered || matches(standardCrops,s)||matches(clickableCrops,s)||matches(stackedCrops,s)&&level.hasChunkAt(pos.below())&&level.getBlockState(pos.below()).getBlock()==b;
 }
 private static boolean matches(Map<Block,Predicate<BlockState>> map,BlockState state){var p=map.get(state.getBlock());return p!=null&&p.test(state);}
 public static boolean clickable(BlockState state){return matches(clickableCrops,state);}
 public static FakePlayer fake(ServerLevel level,LivingEntity golem){var p=FakePlayerFactory.get(level,PROFILE);p.setPos(golem.position());p.setYRot(golem.getYRot());p.setXRot(golem.getXRot());p.setShiftKeyDown(false);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ItemStack.EMPTY);return p;}
 /** Source invokes the diamond-hoe ITEM without owning a hoe. The native ability probe uses
  * a context-only virtual tool, never inserts that tool into player/golem inventory. */
 public static void till(ServerLevel level,IGolemAPI golem,BlockPos pos){if(!level.hasChunkAt(pos))return;var p=FakePlayerFactory.get(level,SOIL_PROFILE);if(!SOIL_ACTIVE.add(p))return;var inv=p.getInventory();var saved=new ItemStack[inv.getContainerSize()];for(int i=0;i<saved.length;i++)saved[i]=inv.getItem(i);var oldPos=p.position();float yaw=p.getYRot(),pitch=p.getXRot();boolean sneak=p.isShiftKeyDown();int selected=inv.selected;var oldMenu=p.containerMenu;var cursor=p.inventoryMenu.getCarried();
  try{inv.clearContent();inv.selected=0;p.containerMenu=p.inventoryMenu;p.inventoryMenu.setCarried(ItemStack.EMPTY);p.moveTo(golem.getGolemEntity().getX(),golem.getGolemEntity().getY(),golem.getGolemEntity().getZ(),golem.getGolemEntity().getYRot(),golem.getGolemEntity().getXRot());p.setShiftKeyDown(false);if(!level.mayInteract(p,pos)||p.blockActionRestricted(level,pos,p.gameMode.getGameModeForPlayer()))return;var hand=net.minecraft.world.InteractionHand.MAIN_HAND;var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false);Items.DIAMOND_HOE.useOn(new net.minecraft.world.item.context.UseOnContext(level,p,hand,new ItemStack(Items.DIAMOND_HOE),hit));}
  finally{try{if(p.containerMenu!=p.inventoryMenu)p.closeContainer();recover(golem,p.inventoryMenu.getCarried());p.inventoryMenu.setCarried(ItemStack.EMPTY);for(int i=0;i<inv.getContainerSize();i++){var actual=inv.getItem(i);inv.setItem(i,ItemStack.EMPTY);recover(golem,actual);}}finally{for(int i=0;i<saved.length;i++)inv.setItem(i,saved[i]);inv.selected=selected;p.inventoryMenu.setCarried(cursor);p.containerMenu=oldMenu;p.moveTo(oldPos.x,oldPos.y,oldPos.z,yaw,pitch);p.setShiftKeyDown(sneak);SOIL_ACTIVE.remove(p);}}
 }
 private static void recover(IGolemAPI golem,ItemStack actual){if(actual==null||actual.isEmpty())return;var remainder=golem.holdItem(actual);if(!remainder.isEmpty())SealInventoryOps.dropItemAtEntity(golem.getGolemWorld(),remainder,golem.getGolemEntity());}
 /** Source alwaysDrop uses handless harvest. Native loot uses an actual placeholder stack. */
 public static boolean harvest(Level world,LivingEntity golem,BlockPos pos,boolean silk){
  if(!(world instanceof ServerLevel level)||!level.hasChunkAt(pos))return false;var player=fake(level,golem);var state=level.getBlockState(pos);
  if(state.isAir()||state.getDestroySpeed(level,pos)<0||!level.mayInteract(player,pos)||player.blockActionRestricted(level,pos,player.gameMode.getGameModeForPlayer())||state.getBlock() instanceof GameMasterBlock&&!player.canUseGameMasterBlocks())return false;
  if(CommonHooks.fireBlockBreak(level,player.gameMode.getGameModeForPlayer(),player,pos,state).isCanceled())return false;
  var block=state.getBlock();var tile=level.getBlockEntity(pos);var tool=ItemsTC.enchantedPlaceholder==null?ItemStack.EMPTY:new ItemStack(ItemsTC.enchantedPlaceholder);if(silk&&!tool.isEmpty())tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);
  state=block.playerWillDestroy(level,pos,state,player);level.levelEvent(player,2001,pos,Block.getId(state));boolean removed=state.onDestroyedByPlayer(level,pos,player,true,state.getFluidState());if(removed){block.destroy(level,pos,state);block.playerDestroy(level,player,pos,state,tile,tool);}return removed;
 }
 public static boolean isWoodLog(Level level,BlockPos pos){return level.hasChunkAt(pos)&&level.getBlockState(pos).is(BlockTags.LOGS);}
 /** Exact source monotonic furthest-neighbour walk and bounds, without shared static cursor. */
 public static BlockPos furthest(Level level,BlockPos origin,BlockState block){BlockPos last=origin;double distance=0;int reach=isWoodLog(level,origin)?2:1;
  for(int walk=0;walk<16384;walk++){boolean advanced=false;scan:for(int x=-reach;x<=reach;x++)for(int y=reach;y>=-reach;y--)for(int z=-reach;z<=reach;z++){
   var p=last.offset(x,y,z);if(Math.abs(p.getX()-origin.getX())>24||Math.abs(p.getY()-origin.getY())>48||Math.abs(p.getZ()-origin.getZ())>24)return last;if(!level.hasChunkAt(p))continue;var next=level.getBlockState(p);double d=p.distSqr(origin);if(next.getBlock()==block.getBlock()&&next.getDestroySpeed(level,p)>=0&&d>distance){last=p;distance=d;advanced=true;break scan;}}
   if(!advanced)break;
  }return last;
 }
 public static boolean breakFurthestBlock(Level level,BlockPos origin,BlockState block,LivingEntity golem){var p=furthest(level,origin,block);boolean worked=harvest(level,golem,p,false);level.sendBlockUpdated(origin,block,block,3);if(worked&&isWoodLog(level,origin)){level.sendBlockUpdated(origin,block,block,3);for(int x=-3;x<=3;x++)for(int y=-3;y<=3;y++)for(int z=-3;z<=3;z++){var n=p.offset(x,y,z);if(level.hasChunkAt(n))level.scheduleTick(n,level.getBlockState(n).getBlock(),50+level.random.nextInt(75));}}return worked;}
}
