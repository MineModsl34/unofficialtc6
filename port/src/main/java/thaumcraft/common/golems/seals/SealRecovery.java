package thaumcraft.common.golems.seals;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
/** Conservation bridge, not new gameplay: canceling a spawned refund never destroys its actual cargo.
 * Queues are chunk-persistent, server-private; retry work is bounded, items are never truncated.
 */
public final class SealRecovery {
 private static final Map<ServerLevel,Set<LevelChunk>> ACTIVE=new IdentityHashMap<>();
 private static final Set<CompoundTag> BORROWED=Collections.newSetFromMap(new IdentityHashMap<>());
 private SealRecovery(){}
 /** Caller owns the item and must not retain/use it after true. Does not modify or synthesize cargo. */
 public static boolean store(Level world,BlockPos pos,ItemStack owned){if(owned==null||owned.isEmpty())return true;if(!(world instanceof ServerLevel level))return false;var row=(CompoundTag)owned.save(level.registryAccess());row.putLong("recovery_pos",pos.asLong());var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null){var fallback=fallback(level);fallback.rows.add(row);fallback.setDirty();return true;}chunk.getData(Golem9SealRegistries.SEALS).recovery.add(row);chunk.setUnsaved(true);ACTIVE.computeIfAbsent(level,k->Collections.newSetFromMap(new IdentityHashMap<>())).add(chunk);return true;}
 static void load(ServerLevel level,LevelChunk chunk,SealChunkData data){if(!data.recovery.isEmpty())ACTIVE.computeIfAbsent(level,k->Collections.newSetFromMap(new IdentityHashMap<>())).add(chunk);}
 static void unload(ServerLevel level,LevelChunk chunk){var set=ACTIVE.get(level);if(set!=null)set.remove(chunk);}
 static void unload(ServerLevel level){ACTIVE.remove(level);}
 static void tick(ServerLevel level){if(level.getGameTime()%20!=0)return;int budget=16;var chunks=ACTIVE.get(level);if(chunks!=null)for(var chunk:List.copyOf(chunks)){if(budget<=0)break;if(level.getChunkSource().getChunkNow(chunk.getPos().x,chunk.getPos().z)!=chunk){chunks.remove(chunk);continue;}var data=chunk.getData(Golem9SealRegistries.SEALS);for(var row:List.copyOf(data.recovery)){if(budget--<=0)break;if(retry(level,row))data.recovery.removeIf(candidate->candidate==row);chunk.setUnsaved(true);}if(data.recovery.isEmpty())chunks.remove(chunk);}
  var fallback=level.getDataStorage().get(Fallback.FACTORY,"thaumcraft_seal_recovery");if(fallback!=null)for(var row:List.copyOf(fallback.rows)){if(budget--<=0)break;if(retry(level,row))fallback.rows.removeIf(candidate->candidate==row);fallback.setDirty();}
 }
 private static boolean retry(ServerLevel level,CompoundTag row){var pos=BlockPos.of(row.getLong("recovery_pos"));if(!level.hasChunkAt(pos)||!BORROWED.add(row))return false;try{var stack=ItemStack.parseOptional(level.registryAccess(),row);if(stack.isEmpty())return false;var item=new ItemEntity(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,stack);item.setDefaultPickUpDelay();if(level.addFreshEntity(item))return true;var actual=item.getItem();if(actual.isEmpty())return true;var replacement=(CompoundTag)actual.save(level.registryAccess());for(String key:List.copyOf(row.getAllKeys()))row.remove(key);row.merge(replacement);row.putLong("recovery_pos",pos.asLong());item.setItem(ItemStack.EMPTY);return false;}finally{BORROWED.remove(row);}}
 private static Fallback fallback(ServerLevel level){return level.getDataStorage().computeIfAbsent(Fallback.FACTORY,"thaumcraft_seal_recovery");}
 /** An event may unload/teleport the origin during a failed spawn. This dimension-owned
  * saved queue preserves the paid remainder without loading a replacement chunk. */
 private static final class Fallback extends SavedData {final List<CompoundTag> rows=new ArrayList<>();static final Factory<Fallback> FACTORY=new Factory<>(Fallback::new,(tag,lookup)->{var value=new Fallback();var list=tag.getList("items",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++)value.rows.add(list.getCompound(i).copy());return value;});public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){var list=new ListTag();for(var row:rows)list.add(row.copy());tag.put("items",list);return tag;}}
}
