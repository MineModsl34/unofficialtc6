package thaumcraft.common.lib.crafting;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Identity-owned ordered data publication. Addon entries/removals survive accepted reloads. */
final class OwnedSourceRuleList<K,V> {
    private final Map<K,V> owned = new LinkedHashMap<>();
    private final Set<K> removedByAddon = new HashSet<>();
    void rebuild(List<V> target, Map<K,V> replacements) {
        Map<K,V> next = new LinkedHashMap<>();
        int insertion = target.size();
        for (var entry : owned.entrySet()) {
            int index = identityIndex(target, entry.getValue());
            if (index < 0) { removedByAddon.add(entry.getKey()); continue; }
            insertion = Math.min(insertion,index);
        }
        // Reinsert a source-ordered group: a newly enabled conditional rule must take its
        // original source position, not append after higher-order rules on /reload.
        for (int index=target.size()-1;index>=0;index--) {
            V candidate = target.get(index);
            if (owned.values().stream().anyMatch(value -> value == candidate)) target.remove(index);
        }
        insertion = Math.min(insertion,target.size());
        for (var entry : replacements.entrySet()) {
            if (removedByAddon.contains(entry.getKey())) continue;
            target.add(insertion++,entry.getValue()); next.put(entry.getKey(),entry.getValue());
        }
        owned.clear(); owned.putAll(next);
    }
    void clear(List<V> target) {
        for (V value : owned.values()) { int index = identityIndex(target,value); if (index >= 0) target.remove(index); }
        owned.clear(); removedByAddon.clear();
    }
    private static <V> int identityIndex(List<V> values,V target) {
        for (int index=0;index<values.size();index++) if (values.get(index) == target) return index;
        return -1;
    }
}
