package thaumcraft.api.casters;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
/** BETA26 indexed setting values; serialized values are true values, not list indices. */
public class NodeSetting {
    int value; public final String key; final String description,research; final INodeSettingType type;
    public NodeSetting(String key,String description,INodeSettingType type,String research) { this.key=key;this.description=description;this.type=type;this.research=research;value=type.getDefault(); }
    public NodeSetting(String key,String description,INodeSettingType type) { this(key,description,type,null); }
    public int getValue() { return type.getValue(value); }
    public void setValue(int truevalue) { int last=-1;value=0;while(getValue()!=truevalue && last!=value){last=value;increment();} }
    public String getResearch(){return research;} public String getValueText(){return Component.translatable(type.getValueText(value)).getString();}
    public void increment(){value=type.clamp(value+1);} public void decrement(){value=type.clamp(value-1);}
    public INodeSettingType getType(){return type;} public String getLocalizedName(){return Component.translatable(description).getString();}
    public String getDescriptionKey(){return description;}
    public interface INodeSettingType { int getDefault();int clamp(int value);int getValue(int value);String getValueText(int value); }
    public static class NodeSettingIntList implements INodeSettingType {
        final int[] values;final String[] descriptions;
        public NodeSettingIntList(int[] values,String[] descriptions){this.values=values.clone();this.descriptions=descriptions.clone();if(values.length==0||values.length!=descriptions.length)throw new IllegalArgumentException("Setting values");}
        public int getDefault(){return 0;}public int clamp(int value){return Mth.clamp(value,0,values.length-1);}public int getValue(int value){return values[clamp(value)];}public String getValueText(int value){return descriptions[clamp(value)];}
    }
    public static class NodeSettingIntRange implements INodeSettingType {
        final int min,max;public NodeSettingIntRange(int min,int max){this.min=min;this.max=max;}
        public int getDefault(){return min;}public int clamp(int value){return Mth.clamp(value,min,max);}public int getValue(int value){return clamp(value);}public String getValueText(int value){return Integer.toString(getValue(value));}
    }
}
