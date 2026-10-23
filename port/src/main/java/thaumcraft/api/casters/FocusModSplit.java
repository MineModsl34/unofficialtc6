package thaumcraft.api.casters;
import java.util.ArrayList;
import net.minecraft.nbt.*;
public abstract class FocusModSplit extends FocusMod {
    private final ArrayList<FocusPackage> packages=new ArrayList<>();public final ArrayList<FocusPackage> getSplitPackages(){return packages;}
    public void deserialize(CompoundTag tag){packages.clear();var list=tag.getList("packages",Tag.TAG_COMPOUND);for(int i=0;i<Math.min(64,list.size());i++){var p=new FocusPackage();p.deserialize(list.getCompound(i));packages.add(p);}}
    public CompoundTag serialize(){var tag=new CompoundTag();var list=new ListTag();for(var p:packages)list.add(p.serialize());tag.put("packages",list);return tag;}
    public float getPowerMultiplier(){return .75F;}
}
