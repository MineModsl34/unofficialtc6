package thaumcraft.common.golems.seals;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import thaumcraft.api.golems.seals.*;
import thaumcraft.common.golems.tasks.TaskHandler;
import thaumcraft.common.golems.client.gui.SealBaseContainer;
/** Source loaded seal index. Logical sides never share entities, even in integrated servers. */
public final class SealHandler {
 public static final LinkedHashMap<String,ISeal> types=new LinkedHashMap<>();
 private static final Map<String,Supplier<ISeal>> factories=new LinkedHashMap<>();
 private static final Map<ServerLevel,Map<SealPos,SealEntity>> SERVER=new IdentityHashMap<>();
 private static final Map<ResourceKey<Level>,Map<SealPos,SealEntity>> CLIENT=new HashMap<>();
 private static int count;
 private SealHandler(){}
 public static void registerSeal(ISeal seal){registerSeal(seal,()->{try{return seal.getClass().getDeclaredConstructor().newInstance();}catch(ReflectiveOperationException error){throw new IllegalArgumentException("Seal requires source public no-arg constructor",error);}});}
 public static void registerSeal(ISeal seal,Supplier<ISeal> factory){if(types.containsKey(seal.getKey()))return;types.put(seal.getKey(),seal);factories.put(seal.getKey(),factory);}
 public static String[] getRegisteredSeals(){return types.keySet().toArray(String[]::new);}public static ISeal getSeal(String key){return types.get(key);}public static ISeal newSeal(String key){var f=factories.get(key);return f==null?null:f.get();}
 static Map<SealPos,SealEntity> server(ServerLevel level){return SERVER.computeIfAbsent(level,k->new LinkedHashMap<>());}
 public static Map<SealPos,SealEntity> client(ResourceKey<Level> key){return CLIENT.computeIfAbsent(key,k->new LinkedHashMap<>());}
 public static void clearClient(ResourceKey<Level> key){CLIENT.remove(key);}static void unload(ServerLevel level){SERVER.remove(level);}
 /** Server API dimension overload; client code uses Level overload, never the integrated server. */
 public static ISealEntity getSealEntity(ResourceKey<Level> key,SealPos pos){if(pos==null)return null;var server=ServerLifecycleHooks.getCurrentServer();var level=server==null?null:server.getLevel(key);return level==null?null:server(level).get(pos);}
 public static ISealEntity getSealEntity(Level level,SealPos pos){return pos==null?null:level instanceof ServerLevel s?server(s).get(pos):client(level.dimension()).get(pos);}
 public static CopyOnWriteArrayList<SealEntity> getSealsInRange(Level world,BlockPos source,int range){var out=new CopyOnWriteArrayList<SealEntity>();var map=world instanceof ServerLevel s?server(s):client(world.dimension());for(var seal:map.values())if(seal.seal!=null&&seal.sealPos.pos.distSqr(source)<=(long)range*range)out.add(seal);return out;}
 public static CopyOnWriteArrayList<SealEntity> getSealsInChunk(Level world,ChunkPos pos){var out=new CopyOnWriteArrayList<SealEntity>();for(var s:getSealsInRange(world,new BlockPos(pos.getMinBlockX(),world.getMinBuildHeight(),pos.getMinBlockZ()),Integer.MAX_VALUE))if(new ChunkPos(s.sealPos.pos).equals(pos))out.add(s);return out;}
 public static boolean addSealEntity(Level world,BlockPos pos,Direction face,ISeal seal,Player player){if(!(world instanceof ServerLevel level)||!world.hasChunkAt(pos)||!seal.canPlaceAt(world,pos,face))return false;var entity=new SealEntity(world,new SealPos(pos,face),seal);entity.setOwner(player.getUUID().toString());return addSealEntity(level,entity);}
 public static boolean addSealEntity(Level world,SealEntity seal){if(!(world instanceof ServerLevel level)||!world.hasChunkAt(seal.sealPos.pos))return false;LevelChunk chunk=level.getChunkSource().getChunkNow(seal.sealPos.pos.getX()>>4,seal.sealPos.pos.getZ()>>4);if(chunk==null)return false;var data=chunk.getData(Golem9SealRegistries.SEALS);if(data.seals.containsKey(seal.sealPos)||data.seals.size()>=4096)return false;data.seals.put(seal.sealPos,seal);server(level).put(seal.sealPos,seal);chunk.setUnsaved(true);seal.syncToClient(world);return true;}
 public static void removeSealEntity(Level world,SealPos pos,boolean quiet){if(!(world instanceof ServerLevel level))return;var seal=server(level).remove(pos);if(seal==null)return;seal.seal.onRemoval(world,pos.pos,pos.face);for(var t:TaskHandler.getTasks(world.dimension()).values())if(pos.equals(t.getSealPos()))t.setSuspended(true);var chunk=level.getChunkSource().getChunkNow(pos.pos.getX()>>4,pos.pos.getZ()>>4);if(chunk!=null){chunk.getData(Golem9SealRegistries.SEALS).seals.remove(pos);chunk.setUnsaved(true);}if(!quiet){var item=ItemSealPlacer.getSealStack(seal.seal.getKey());if(!item.isEmpty()&&!world.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(world,pos.pos.getX()+.5+pos.face.getStepX()/1.7,pos.pos.getY()+.5+pos.face.getStepY()/1.7,pos.pos.getZ()+.5+pos.face.getStepZ()/1.7,item)))SealRecovery.store(world,pos.pos,item);}Golem9SealNetwork.remove(level,pos);}
 public static void tickSealEntities(Level world){if(!(world instanceof ServerLevel level))return;count++;for(var seal:List.copyOf(server(level).values()))if(world.hasChunkAt(seal.sealPos.pos)){try{if(count%20==0&&!seal.seal.canPlaceAt(world,seal.sealPos.pos,seal.sealPos.face))removeSealEntity(world,seal.sealPos,false);else seal.tickSealEntity(world);}catch(RuntimeException failure){thaumcraft.Thaumcraft.log.warn("Suspending invalid seal at {}",seal.sealPos.pos,failure);for(var t:TaskHandler.getTasks(world.dimension()).values())if(seal.sealPos.equals(t.getSealPos()))t.setSuspended(true);}}}
 public static void markChunkAsDirty(ResourceKey<Level> dim,BlockPos pos){var server=ServerLifecycleHooks.getCurrentServer();var world=server==null?null:server.getLevel(dim);if(world!=null){var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk!=null)chunk.setUnsaved(true);}}
 public static boolean canPlayerEdit(ServerPlayer player,ISealEntity seal){return seal!=null&&player.level().hasChunkAt(seal.getSealPos().pos)&&player.distanceToSqr(Vec3.atCenterOf(seal.getSealPos().pos))<=64&&player.level().mayInteract(player,seal.getSealPos().pos)&&(!seal.isLocked()||seal.getOwner().equals(player.getUUID().toString()));}
 public static boolean openMenu(ServerPlayer player,SealPos pos){var seal=getSealEntity(player.level(),pos);if(!(seal instanceof SealEntity actual)||!canPlayerEdit(player,seal))return false;actual.syncToClient(player.level());player.openMenu(new SimpleMenuProvider((id,inv,p)->new SealBaseContainer(id,inv,actual),net.minecraft.network.chat.Component.translatable("container.seal")),b->b.writeNbt(actual.writeClientNBT(player.registryAccess())));return true;}
 public static boolean openLogisticsMenu(ServerPlayer player,BlockPos pos){return thaumcraft.common.golems.client.gui.ContainerLogistics.open(player,pos);}
 public static ISealEntity getSealRayTrace(Level level,Player player,double reach){reach=Math.max(0,Math.min(5,reach));var start=player.getEyePosition();var end=start.add(player.getLookAngle().scale(reach));SealEntity best=null;double distance=Double.MAX_VALUE;for(var s:getSealsInRange(level,player.blockPosition(),6)){var hit=new AABB(s.sealPos.pos).clip(start,end);if(hit.isEmpty())continue;var result=net.minecraft.world.phys.shapes.Shapes.block().clip(start,end,s.sealPos.pos);if(result==null||result.getDirection()!=s.sealPos.face)continue;double d=start.distanceToSqr(hit.get());if(d<distance){best=s;distance=d;}}return best;}
}
