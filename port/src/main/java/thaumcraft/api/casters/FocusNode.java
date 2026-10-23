package thaumcraft.api.casters;
import java.util.*;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.aspects.Aspect;
public abstract class FocusNode implements IFocusElement {
    FocusPackage pack;private FocusNode parent;final HashMap<String,NodeSetting> settings=new HashMap<>();
    public FocusNode(){initialize();}
    public String getUnlocalizedName(){return getKey()+".name";}public String getUnlocalizedText(){return getKey()+".text";}
    public abstract int getComplexity();public abstract Aspect getAspect();public abstract EnumSupplyType[] mustBeSupplied();public abstract EnumSupplyType[] willSupply();
    public boolean canSupply(EnumSupplyType type){if(willSupply()!=null)for(var entry:willSupply())if(entry==type)return true;return false;}
    public HitResult[] supplyTargets(){return null;}public Trajectory[] supplyTrajectories(){return null;}
    public final void setPackage(FocusPackage pack){this.pack=pack;}public final FocusPackage getPackage(){return pack;}
    public final FocusPackage getRemainingPackage(){var p=getPackage();if(p==null||p.index+1>=p.nodes.size())return null;var next=new FocusPackage();next.setUniqueID(p.getUniqueID());next.world=p.world;next.multiplyPower(p.getPower());next.nodes=Collections.synchronizedList(new ArrayList<>(p.nodes.subList(p.index+1,p.nodes.size())));next.setCasterUUID(p.getCasterUUID());return next;}
    public final FocusNode getParent(){return parent;}public void setParent(FocusNode parent){this.parent=parent;}
    public final Set<String> getSettingList(){return settings.keySet();}public final NodeSetting getSetting(String key){return settings.get(key);}public final int getSettingValue(String key){return settings.containsKey(key)?settings.get(key).getValue():0;}
    public NodeSetting[] createSettings(){return null;}public final void initialize(){var list=createSettings();if(list!=null)for(var setting:list)settings.put(setting.key,setting);}
    public float getPowerMultiplier(){return 1;}public boolean isExclusive(){return false;}
    public enum EnumSupplyType {TARGET,TRAJECTORY}
}
