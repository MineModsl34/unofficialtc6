package thaumcraft.common.tiles.crafting;

import java.util.*;

/** Pure server-side tree validation and TC6 repeated-node complexity accounting.
 * Supply requirements are alternatives (source GuiFocalManipulator), not AND. */
public final class FocusGraphRules {
    public static final int MAX_NODES = 127, MAX_DEPTH = 64;
    public enum Kind { ROOT, MEDIUM, EFFECT, MOD, SPLIT, BLANK }
    public enum Supply { TARGET, TRAJECTORY }
    public record Node(int id,int parent,List<Integer> children,String key,Kind kind,
                       int complexity,Set<Supply> needs,Set<Supply> supplies,boolean exclusive) {
        public Node {
            children = List.copyOf(children); needs = Set.copyOf(needs); supplies = Set.copyOf(supplies);
        }
    }
    public record Cost(int complexity,int experience,float vis) {}
    private FocusGraphRules() {}
    public static Cost costs(int complexity,int maximum) {
        if (complexity < 0 || complexity > maximum || maximum <= 0) throw new IllegalArgumentException("Focus complexity");
        return new Cost(complexity,Math.max(1,(int)Math.round(Math.sqrt(complexity))),complexity * 10 + maximum / 5);
    }
    public static int validate(List<Node> nodes,int maximum,boolean complete) {
        if (nodes == null || nodes.size() < 2 || nodes.size() > MAX_NODES) throw new IllegalArgumentException("Focus node bound");
        Map<Integer,Node> byId = new HashMap<>(); Map<String,Integer> exclusive = new HashMap<>();
        int mediums = 0, exclusiveMediums = 0;
        for (Node node : nodes) {
            if (node == null || node.id < 0 || node.id >= MAX_NODES || byId.put(node.id,node) != null
                || node.key == null || node.key.length() > 128 || node.complexity < 0 || node.complexity > 10000)
                throw new IllegalArgumentException("Invalid focus node");
            if (node.kind == Kind.ROOT && node.id != 0) throw new IllegalArgumentException("Multiple roots");
            if (complete && node.kind == Kind.BLANK) throw new IllegalArgumentException("Incomplete focus");
            int expected = switch (node.kind) { case EFFECT, BLANK -> 0; case SPLIT -> 2; default -> 1; };
            if (node.children.size() != expected || new HashSet<>(node.children).size() != expected)
                throw new IllegalArgumentException("Invalid focus arity");
            if (node.exclusive && exclusive.merge(node.key,1,Integer::sum) > 1) throw new IllegalArgumentException("Exclusive focus node");
            if (node.kind == Kind.MEDIUM) { mediums++; if (node.exclusive) exclusiveMediums++; }
        }
        if (exclusiveMediums > 0 && mediums > 1) throw new IllegalArgumentException("Exclusive focus medium");
        Node root = byId.get(0);
        if (root == null || root.kind != Kind.ROOT || root.parent != -1) throw new IllegalArgumentException("Missing root");
        Set<Integer> seen = new HashSet<>(); walk(root,byId,seen,0);
        if (seen.size() != nodes.size()) throw new IllegalArgumentException("Disconnected focus tree");
        // Source iterates its HashMap<Integer,...>, retaining repeated-key truncation per node.
        Map<String,Integer> count = new HashMap<>(); int total = 0;
        for (Node node : byId.values()) {
            float multiplier = .5F * (count.merge(node.key,1,Integer::sum) + 1);
            total = (int)(total + node.complexity * multiplier);
            if (total > maximum) throw new IllegalArgumentException("Focus too complex");
        }
        return total;
    }
    private static void walk(Node node,Map<Integer,Node> nodes,Set<Integer> seen,int depth) {
        if (depth > MAX_DEPTH || !seen.add(node.id)) throw new IllegalArgumentException("Cyclic/deep focus tree");
        for (int id : node.children) {
            Node child = nodes.get(id);
            if (child == null || child.parent != node.id) throw new IllegalArgumentException("Broken focus edge");
            if (child.kind != Kind.BLANK && Collections.disjoint(node.supplies,child.needs)) throw new IllegalArgumentException("Incompatible focus supplies");
            walk(child,nodes,seen,depth + 1);
        }
    }
}
