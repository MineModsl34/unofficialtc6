package thaumcraft.common.entities.ai.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import thaumcraft.common.entities.monster.cult.EntityCultist;

/** TC6 nighttime village-door restriction. 1.12's removed VillageCollection is explicitly
 * adapted to real1.21 village POIs; the source five-block sky/inside geometry and navigation
 * toggle are retained. This is not claimed to recreate the obsolete village simulator. */
public final class AIRestrictCultistDoor extends Goal {
    private final EntityCultist cultist;
    private BlockPos door;
    private Direction inside;
    public AIRestrictCultistDoor(EntityCultist cultist) { this.cultist = cultist; }
    @Override public boolean canUse() {
        if (!(cultist.level() instanceof ServerLevel server) || server.isDay() || !server.isVillage(cultist.blockPosition())) return false;
        BlockPos base = cultist.blockPosition(); double nearest = Double.MAX_VALUE; BlockPos selected = null; Direction side = null;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-4, -2, -4), base.offset(4, 2, 4))) {
            if (!server.hasChunkAt(pos)) continue; var state = server.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock) || !state.is(BlockTags.WOODEN_DOORS) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
            Direction facing = state.getValue(DoorBlock.FACING);
            int front = sky(server, pos, facing, 5), back = sky(server, pos, facing.getOpposite(), front + 1);
            if (front == back) continue;
            Direction interior = front < back ? facing : facing.getOpposite();
            // Source VillageDoorInfo uses int BlockPos distance to its inside block.
            if ((int)pos.relative(interior, 2).distSqr(base) >= 2.25D) continue;
            double distance = pos.distSqr(base); if (distance < nearest) { nearest = distance; selected = pos.immutable(); side = interior; }
        }
        door = selected; inside = side; return door != null;
    }
    private static int sky(ServerLevel level, BlockPos pos, Direction side, int limit) {
        int result = 0; for (int index = 1; index <= 5; index++) { if (level.canSeeSky(pos.relative(side, index))) result++; if (result >= limit) break; } return result;
    }
    @Override public boolean canContinueToUse() {
        return door != null && !cultist.level().isDay() && cultist.level().getBlockState(door).is(BlockTags.WOODEN_DOORS)
            && (cultist.getBlockX() - door.getX()) * inside.getStepX() + (cultist.getBlockZ() - door.getZ()) * inside.getStepZ() >= 0;
    }
    @Override public void start() { var navigation = (GroundPathNavigation)cultist.getNavigation(); navigation.setCanOpenDoors(false); navigation.setCanPassDoors(false); }
    @Override public void stop() { var navigation = (GroundPathNavigation)cultist.getNavigation(); navigation.setCanOpenDoors(true); navigation.setCanPassDoors(true); door = null; }
}
