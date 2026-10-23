package thaumcraft.api.casters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NodeSettingTest{
    @Test void listSerializesTrueValueAndInvalidValuesClampToLast(){var setting=new NodeSetting("cone","test",new NodeSetting.NodeSettingIntList(new int[]{10,30,60,90,180,270,360},new String[]{"a","b","c","d","e","f","g"}));assertEquals(10,setting.getValue());setting.setValue(180);assertEquals(180,setting.getValue());setting.setValue(17);assertEquals(360,setting.getValue());setting.increment();assertEquals(360,setting.getValue());setting.decrement();assertEquals(270,setting.getValue());}
    @Test void rangeDefaultAndTrueValueDeserializationRetainSourceClamp(){var setting=new NodeSetting("power","test",new NodeSetting.NodeSettingIntRange(2,10));assertEquals(2,setting.getValue());setting.setValue(5);assertEquals(5,setting.getValue());setting.setValue(-10);assertEquals(10,setting.getValue());setting.setValue(2);setting.decrement();assertEquals(2,setting.getValue());}
}
