package thaumcraft.common.lib.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolMiningRulesTest {
    @Test void burrowingChoosesFirstOutwardBranchNotWholeVein() {
        var blocks=java.util.Set.of(new BlockPos(-1,0,0),new BlockPos(-2,0,0),new BlockPos(1,0,0),new BlockPos(2,0,0),new BlockPos(3,0,0));
        assertEquals(new BlockPos(-2,0,0),ToolMiningRules.furthest(BlockPos.ZERO,1,blocks::contains));
    }
    @Test void sourceBoundsStopAtFirstViolatingNeighbor() {
        assertEquals(new BlockPos(-24,0,0),ToolMiningRules.furthest(BlockPos.ZERO,1,pos -> pos.getY()==0&&pos.getZ()==0&&pos.getX()<0));
    }
    @Test void destructivePlaneAndCostsAreLiteralSource() {
        assertEquals(new BlockPos(1,0,2),ToolMiningRules.destructiveOffset(BlockPos.ZERO,Direction.UP,1,2));
        assertEquals(new BlockPos(1,2,0),ToolMiningRules.destructiveOffset(BlockPos.ZERO,Direction.NORTH,1,2));
        assertEquals(new BlockPos(0,2,1),ToolMiningRules.destructiveOffset(BlockPos.ZERO,Direction.EAST,1,2));
        assertEquals(.625F,ToolMiningRules.refiningChance(4));
        assertEquals(20,ToolMiningRules.soundingRange(4));
    }
}
