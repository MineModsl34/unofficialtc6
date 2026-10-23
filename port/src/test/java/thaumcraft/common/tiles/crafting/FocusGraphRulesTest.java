package thaumcraft.common.tiles.crafting;

import static org.junit.jupiter.api.Assertions.*;
import static thaumcraft.common.tiles.crafting.FocusGraphRules.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FocusGraphRulesTest {
    private static final Set<Supply> BOTH = Set.of(Supply.TARGET,Supply.TRAJECTORY);
    private Node root(int child) { return new Node(0,-1,List.of(child),"ROOT",Kind.ROOT,0,Set.of(),BOTH,false); }
    private Node effect(int id,int parent,int cost) { return new Node(id,parent,List.of(),"FIRE",Kind.EFFECT,cost,Set.of(Supply.TARGET),Set.of(),false); }
    @Test void sourceBasicTiersAndIntegerVisTerm() {
        assertEquals(new Cost(7,3,73),costs(7,15)); assertEquals(new Cost(7,3,75),costs(7,25)); assertEquals(new Cost(7,3,80),costs(7,50));
        assertEquals(1,costs(0,15).experience());
    }
    @Test void repeatedKeysUsePerNodeTruncation() {
        var mod = new Node(1,0,List.of(2),"REPEAT",Kind.MOD,3,Set.of(Supply.TRAJECTORY),BOTH,false);
        var repeated = new Node(2,1,List.of(3),"REPEAT",Kind.MOD,3,Set.of(Supply.TRAJECTORY),BOTH,false);
        assertEquals(9,validate(List.of(root(1),mod,repeated,effect(3,2,2)),15,true));
    }
    @Test void anyOfSupplyMatchesSourcePaletteNotAnd() {
        Node bothNeeded = new Node(1,0,List.of(),"HEAL",Kind.EFFECT,2,BOTH,Set.of(),false);
        Node onlyTargetRoot = new Node(0,-1,List.of(1),"ROOT",Kind.ROOT,0,Set.of(),Set.of(Supply.TARGET),false);
        assertEquals(2,validate(List.of(onlyTargetRoot,bothNeeded),15,true));
    }
    @Test void incompleteDraftCanBeSavedButNotCrafted() {
        var blank = new Node(1,0,List.of(),"",Kind.BLANK,0,Set.of(),Set.of(),false);
        assertEquals(0,validate(List.of(root(1),blank),15,false)); assertThrows(IllegalArgumentException.class,() -> validate(List.of(root(1),blank),15,true));
    }
    @Test void complexityCapEnforcedForEveryTier() { assertThrows(IllegalArgumentException.class,() -> validate(List.of(root(1),effect(1,0,16)),15,true)); }
    @Test void rejectsDuplicateIds() { assertThrows(IllegalArgumentException.class,() -> validate(List.of(root(1),effect(1,0,2),effect(1,0,2)),50,true)); }
    @Test void rejectsMissingOrForgedParent() { assertThrows(IllegalArgumentException.class,() -> validate(List.of(root(1),effect(1,5,2)),15,true)); }
    @Test void rejectsDisconnectedTree() { assertThrows(IllegalArgumentException.class,() -> validate(List.of(root(1),effect(1,0,2),effect(2,0,2)),15,true)); }
    @Test void rejectsBrokenChild() { assertThrows(IllegalArgumentException.class,() -> validate(List.of(root(5),effect(1,0,2)),15,true)); }
    @Test void effectCannotHaveChildren() {
        var bad = new Node(1,0,List.of(2),"FIRE",Kind.EFFECT,2,Set.of(Supply.TARGET),Set.of(),false);
        assertThrows(IllegalArgumentException.class,() -> validate(List.of(root(1),bad,effect(2,1,2)),15,true));
    }
    @Test void splitIsExactlyTwoBranchesAndChargesEachEffect() {
        var split = new Node(1,0,List.of(2,3),"SPLIT",Kind.SPLIT,4,Set.of(Supply.TARGET),Set.of(Supply.TARGET),false);
        assertEquals(11,validate(List.of(root(1),split,effect(2,1,3),effect(3,1,3)),15,true));
    }
    @Test void exclusiveMediumCannotBeCombinedWithOtherMedium() {
        var one = new Node(1,0,List.of(2),"CLOUD",Kind.MEDIUM,2,Set.of(Supply.TRAJECTORY),BOTH,true);
        var two = new Node(2,1,List.of(3),"TOUCH",Kind.MEDIUM,2,Set.of(Supply.TRAJECTORY),BOTH,false);
        assertThrows(IllegalArgumentException.class,() -> validate(List.of(root(1),one,two,effect(3,2,2)),15,true));
    }
    @Test void wrongSupplyRejected() {
        var medium = new Node(1,0,List.of(2),"M",Kind.MEDIUM,2,Set.of(Supply.TRAJECTORY),Set.of(Supply.TRAJECTORY),false);
        assertThrows(IllegalArgumentException.class,() -> validate(List.of(root(1),medium,effect(2,1,2)),15,true));
    }
    @Test void hostileCostsAndIdsRejected() {
        assertThrows(IllegalArgumentException.class,() -> costs(-1,50)); assertThrows(IllegalArgumentException.class,() -> costs(51,50));
        assertThrows(IllegalArgumentException.class,() -> validate(List.of(root(127),effect(127,0,2)),50,true));
    }
    @Test void nodeDescriptorCopiesMutableCollections() {
        List<Integer> children = new ArrayList<>(List.of(1)); Set<Supply> supplies = new HashSet<>(BOTH);
        Node node = new Node(0,-1,children,"ROOT",Kind.ROOT,0,Set.of(),supplies,false); children.clear(); supplies.clear(); assertEquals(List.of(1),node.children()); assertEquals(BOTH,node.supplies());
    }
}
