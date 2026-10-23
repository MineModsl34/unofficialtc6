package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure identity/order logic, no Minecraft bootstrap. */
class OwnedSourceRuleListTest {
    @Test void addonEntriesSurviveReloadAndShutdownEvenIfEqualToOwnedObjects() {
        var owner = new OwnedSourceRuleList<String,String>();
        var addon = new String("same"); var nativeOld = new String("same");
        var target = new ArrayList<>(List.of(addon));
        owner.rebuild(target,new LinkedHashMap<>(java.util.Map.of("native",nativeOld)));
        var replacement = new String("replacement");
        owner.rebuild(target,new LinkedHashMap<>(java.util.Map.of("native",replacement)));
        assertSame(addon,target.get(0)); assertSame(replacement,target.get(1));
        owner.clear(target); assertEquals(1,target.size()); assertSame(addon,target.get(0));
    }
    @Test void enablingAConditionalRuleRestoresSourceOrderNotAppendOrder() {
        var owner = new OwnedSourceRuleList<Integer,String>();
        var target = new ArrayList<String>();
        var sparse = new LinkedHashMap<Integer,String>(); sparse.put(0,new String("ore")); sparse.put(2,new String("rareearth"));
        owner.rebuild(target,sparse);
        var full = new LinkedHashMap<Integer,String>(); full.put(0,new String("ore")); full.put(1,new String("chunks")); full.put(2,new String("rareearth"));
        owner.rebuild(target,full); assertEquals(List.of("ore","chunks","rareearth"),target);
    }
    @Test void addonRemovalDisablesThatOwnedRuleForThisServerLifetime() {
        var owner = new OwnedSourceRuleList<String,String>(); var target = new ArrayList<String>();
        var rows = new LinkedHashMap<String,String>(); rows.put("first",new String("ore")); rows.put("second",new String("ore"));
        owner.rebuild(target,rows); target.remove(0);
        owner.rebuild(target,rows); assertEquals(1,target.size()); assertSame(rows.get("second"),target.get(0));
        owner.clear(target); owner.rebuild(target,rows); assertEquals(2,target.size());
    }
}
