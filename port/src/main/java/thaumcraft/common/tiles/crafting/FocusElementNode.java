package thaumcraft.common.tiles.crafting;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import thaumcraft.api.casters.*;

/** Source editable graph row; server derives supply flags/cost instead of trusting them. */
public final class FocusElementNode {
    public int x,y,id,parent = -1;
    public boolean target,trajectory;
    public int[] children = new int[0];
    public float complexityMultiplier = 1;
    public FocusNode node;
    public float getPower(HashMap<Integer,FocusElementNode> data) {
        float power = 1; Set<Integer> seen = new HashSet<>(); FocusElementNode current = this;
        while (current != null && seen.add(current.id) && seen.size() <= FocusGraphRules.MAX_DEPTH) {
            if (current.node != null) power *= current.node.getPowerMultiplier(); current = data.get(current.parent);
        }
        return power;
    }
    public void deserialize(CompoundTag tag) {
        id = tag.getInt("id"); parent = tag.getInt("parent"); x = tag.getInt("x"); y = tag.getInt("y"); children = tag.getIntArray("children");
        String key = tag.getString("key"); if (key.length() > 128) throw new IllegalArgumentException("Focus key");
        IFocusElement element = key.isEmpty() ? null : FocusEngine.getElement(key);
        if (element != null && !(element instanceof FocusNode) || !key.isEmpty() && element == null) throw new IllegalArgumentException("Unknown focus node");
        node = (FocusNode)element;
        if (node != null) for (String setting : node.getSettingList()) {
            if (tag.contains("setting." + setting)) {
                int value = tag.getInt("setting." + setting); node.getSetting(setting).setValue(value);
                if (node.getSettingValue(setting) != value) throw new IllegalArgumentException("Focus setting outside domain");
            }
        }
        target = node != null && node.canSupply(FocusNode.EnumSupplyType.TARGET);
        trajectory = node != null && node.canSupply(FocusNode.EnumSupplyType.TRAJECTORY);
    }
    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag(); tag.putInt("id",id); tag.putInt("parent",parent); tag.putInt("x",x); tag.putInt("y",y); tag.putIntArray("children",children);
        tag.putBoolean("target",target); tag.putBoolean("trajectory",trajectory); tag.putFloat("complexity",complexityMultiplier);
        if (node != null) { tag.putString("key",node.getKey()); for (String setting : node.getSettingList()) tag.putInt("setting." + setting,node.getSettingValue(setting)); }
        return tag;
    }
    public FocusGraphRules.Node rule() {
        var kind = node == null ? FocusGraphRules.Kind.BLANK : node instanceof FocusMediumRoot ? FocusGraphRules.Kind.ROOT : node instanceof FocusModSplit ? FocusGraphRules.Kind.SPLIT
            : node instanceof FocusEffect ? FocusGraphRules.Kind.EFFECT : node instanceof FocusMedium ? FocusGraphRules.Kind.MEDIUM : FocusGraphRules.Kind.MOD;
        return new FocusGraphRules.Node(id,parent,Arrays.stream(children).boxed().toList(),node == null ? "" : node.getKey(),kind,node == null ? 0 : node.getComplexity(),
            supplies(node == null ? null : node.mustBeSupplied()),supplies(node == null ? null : node.willSupply()),node != null && node.isExclusive());
    }
    private static Set<FocusGraphRules.Supply> supplies(FocusNode.EnumSupplyType[] values) {
        Set<FocusGraphRules.Supply> result = EnumSet.noneOf(FocusGraphRules.Supply.class);
        if (values != null) for (var value : values) result.add(FocusGraphRules.Supply.valueOf(value.name())); return result;
    }
}
