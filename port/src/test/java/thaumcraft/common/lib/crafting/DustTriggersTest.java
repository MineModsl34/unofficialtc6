package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import thaumcraft.api.crafting.IDustTrigger;

class DustTriggersTest {
    @Test void onlyTheFirstMatchingTriggerAndItsOriginalPlacementAreReturned() {
        IDustTrigger first = new Stub(), match = new Stub(), later = new Stub();
        var placement = new IDustTrigger.Placement(-1, -2, -3, Direction.WEST);
        List<IDustTrigger> visited = new ArrayList<>();
        var found = DustTriggers.firstMatch(List.of(first, match, later), trigger -> {
            visited.add(trigger); return trigger == first ? null : placement;
        });
        assertEquals(List.of(first, match), visited); assertSame(match, found.trigger()); assertSame(placement, found.placement());
    }
    @Test void noMatchAndBoundedAddonRegistryDoNotExecuteOrInventAPlacement() {
        assertNull(DustTriggers.firstMatch(List.of(new Stub()), trigger -> null));
        List<IDustTrigger> oversized = new ArrayList<>();
        for (int i = 0; i < DustTriggers.MAX_TRIGGERS_PER_USE + 1; i++) oversized.add(new Stub());
        int[] count = {0};
        assertNull(DustTriggers.firstMatch(oversized, trigger -> { count[0]++; return null; }));
        assertEquals(DustTriggers.MAX_TRIGGERS_PER_USE, count[0]);
    }
    @Test void legacyTagNamesAreExplicitAndNeverBecomeAnInventedAddonAlias() {
        assertEquals("c:bookshelves", DustTriggerOre.legacyTag("bookshelf").location().toString());
        assertEquals("c:workbenches", DustTriggerOre.legacyTag("workbench").location().toString());
        assertThrows(IllegalArgumentException.class, () -> DustTriggerOre.legacyTag("workbenchAddon"));
        assertThrows(IllegalArgumentException.class, () -> DustTriggerOre.legacyTag(null));
    }
    private static final class Stub implements IDustTrigger {
        @Override public Placement getValidFace(Level world, Player player, BlockPos pos, Direction face) { fail("pure matcher must not touch engine"); return null; }
        @Override public void execute(Level world, Player player, BlockPos pos, Placement placement, Direction side) { fail("matching alone must not execute/consume"); }
    }
}
