package thaumcraft.api.casters;
import java.util.ArrayDeque;
import net.minecraft.nbt.*;
/** Iterative preflight applies to both package branches and split branches; never recursively walks untrusted NBT. */
public final class FocusSerializationRules {
    private FocusSerializationRules(){}
    private record Entry(Tag tag,int depth){}
    public static boolean isBounded(CompoundTag root){if(root==null)return false;var pending=new ArrayDeque<Entry>();pending.push(new Entry(root,0));int entries=0,bytes=0;while(!pending.isEmpty()){var entry=pending.pop();if(++entries>16384||entry.depth>128)return false;Tag tag=entry.tag;if(tag instanceof CompoundTag compound){for(String key:compound.getAllKeys()){bytes+=key.length()*2;pending.push(new Entry(compound.get(key),entry.depth+1));}}else if(tag instanceof ListTag list){if(list.size()>256)return false;for(Tag value:list)pending.push(new Entry(value,entry.depth+1));}else if(tag instanceof StringTag string){int length=string.getAsString().length();if(length>8192)return false;bytes+=length*2;}else if(tag instanceof ByteArrayTag array)bytes+=array.size();else if(tag instanceof IntArrayTag array)bytes+=array.size()*4;else if(tag instanceof LongArrayTag array)bytes+=array.size()*8;else bytes+=8;if(bytes>1048576)return false;}return true;}
}
