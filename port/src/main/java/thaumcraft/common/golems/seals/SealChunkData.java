package thaumcraft.common.golems.seals;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.attachment.*;
import thaumcraft.api.golems.seals.SealPos;
/** Chunk-owned state. Unknown add-on types are preserved raw, not silently erased on save. */
public final class SealChunkData {
 public final Map<SealPos,SealEntity> seals=new LinkedHashMap<>();final List<CompoundTag> recovery=new ArrayList<>();private final List<CompoundTag> unresolved=new ArrayList<>();
 public static final IAttachmentSerializer<CompoundTag,SealChunkData> SERIALIZER=new IAttachmentSerializer<>(){
  public SealChunkData read(IAttachmentHolder holder,CompoundTag tag,HolderLookup.Provider lookup){var out=new SealChunkData();var list=tag.getList("seals",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++){var n=list.getCompound(i);try{var s=new SealEntity();s.readNBT(n,lookup);if(out.seals.putIfAbsent(s.sealPos,s)!=null)out.unresolved.add(n.copy());}catch(RuntimeException invalid){out.unresolved.add(n.copy());}}var recovery=tag.getList("recovery",Tag.TAG_COMPOUND);for(int i=0;i<recovery.size();i++)out.recovery.add(recovery.getCompound(i).copy());return out;}
  public CompoundTag write(SealChunkData data,HolderLookup.Provider lookup){var n=new CompoundTag();var list=new ListTag();for(var s:data.seals.values())list.add(s.writeNBT(lookup));for(var raw:data.unresolved)list.add(raw.copy());n.put("seals",list);var recovery=new ListTag();for(var row:data.recovery)recovery.add(row.copy());n.put("recovery",recovery);return n;}
 };
}
