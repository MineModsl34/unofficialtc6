package thaumcraft.common.lib.crafting;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.crafting.Part;

class InfusionDustBlueprintTest {
    @Test void exactSourceThreeLayersPreserveAllTenMaterialCellsAndEightChanges() {
        var blueprint = InfusionDustTriggers.sourceBlueprint("stone", "matrix", "pedestal", new Object[]{"E", "N", "S", "W"});
        var pattern = new DustMultiblockPattern(null, blueprint);
        var cells = pattern.cells(BlockPos.ZERO, new IDustTrigger.Placement(0, 0, 0, Direction.EAST));
        assertEquals(10, cells.size());
        assertEquals(8, cells.stream().filter(cell -> cell.part().getTarget() != null).count());
        assertEquals(4, cells.stream().filter(cell -> "AIR".equals(cell.part().getTarget())).count());
        assertEquals(new BlockPos(1, 2, 1), cells.getFirst().pos()); assertEquals("matrix", cells.getFirst().part().getSource());
        assertNull(blueprint[0][1][1].getTarget()); assertNull(blueprint[2][1][1].getTarget());
        for (var cell : cells) assertEquals(50, cell.part().getPriority());
    }
    @Test void sourceAbsolutePillarTargetsDoNotRotateWithTheGeometry() {
        var blueprint = InfusionDustTriggers.sourceBlueprint("stone", "matrix", "pedestal", new Object[]{"E", "N", "S", "W"});
        assertEquals("E", blueprint[2][0][0].getTarget()); assertEquals("N", blueprint[2][0][2].getTarget());
        assertEquals("S", blueprint[2][2][0].getTarget()); assertEquals("W", blueprint[2][2][2].getTarget());
        var pattern = new DustMultiblockPattern(null, blueprint);
        for (Direction direction : new Direction[]{Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST}) {
            var cells = pattern.cells(BlockPos.ZERO, new IDustTrigger.Placement(0, 0, 0, direction));
            assertTrue(cells.stream().anyMatch(cell -> cell.part() == blueprint[2][0][0] && "E".equals(cell.part().getTarget())));
        }
    }
    @Test void chargedPedestalGateRemainsAnExplicitSourceDescriptorAndMissingStoneFails() {
        var blueprint = InfusionDustTriggers.sourceBlueprint("stone", "matrix", "pedestal.CHARGE2", new Object[]{"E", "N", "S", "W"});
        assertEquals("pedestal.CHARGE2", blueprint[2][1][1].getSource());
        var pattern = new DustMultiblockPattern(null, blueprint);
        var placement = new IDustTrigger.Placement(-1, -2, -1, Direction.EAST);
        Map<BlockPos, Part> exact = new HashMap<>(); pattern.cells(BlockPos.ZERO, placement).forEach(cell -> exact.put(cell.pos(), cell.part()));
        assertNotNull(pattern.findPlacement(BlockPos.ZERO, (pos, part) -> exact.get(pos) == part));
        exact.remove(new BlockPos(-1, -1, -1));
        assertNull(pattern.findPlacement(BlockPos.ZERO, (pos, part) -> exact.get(pos) == part));
    }
    @Test void eachSourceDefaultPriorityIsFiftyDecrementsThenMutationOnTheFiftyFirstEnd() {
        var blueprint = InfusionDustTriggers.sourceBlueprint("stone", "matrix", "pedestal", new Object[]{"E", "N", "S", "W"});
        var timer = new DustConversionRules.Countdown(blueprint[2][0][0].getPriority());
        for (int tick = 0; tick < 50; tick++) assertFalse(timer.readyOnEnd()); assertTrue(timer.readyOnEnd());
        assertThrows(IllegalArgumentException.class, () -> InfusionDustTriggers.sourceBlueprint("stone", "matrix", "pedestal", new Object[]{"E"}));
    }
}
