package thaumcraft.common.tiles.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.blocks.BlocksTC;

/** Source five-tick airborne repulsion and two barrier cells, refreshed every 100 ticks. */
public final class TileBarrierStone extends BlockEntity {
    private int count;
    public TileBarrierStone(BlockPos pos, BlockState state) { super(BarrierRegistries.TYPE, pos, state); }
    public boolean gettingPower() { return level != null && level.hasNeighborSignal(worldPosition); }
    public static void tick(Level level, BlockPos pos, BlockState state, TileBarrierStone tile) {
        if (level.isClientSide) return;
        if (tile.count == 0) tile.count = level.random.nextInt(100);
        if (tile.count % 5 == 0 && !tile.gettingPower()) {
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1).inflate(.1))) {
                if (!entity.onGround() && !(entity instanceof Player)) {
                    float yaw = (entity.getYRot() + 180F) * (float)Math.PI / 180F;
                    entity.push(-Mth.sin(yaw) * .2F, -.1, Mth.cos(yaw) * .2F);
                }
            }
        }
        if (++tile.count % 100 == 0) for (int height = 1; height < 3; height++) {
            BlockPos above = pos.above(height);
            if (level.isEmptyBlock(above)) level.setBlock(above, BlocksTC.barrier.defaultBlockState(), 3);
        }
        // A state change invalidates native cached path types; collision still reads live power.
        if (tile.count % 5 == 0) for (int height = 1; height < 3; height++) {
            BlockPos above = pos.above(height); BlockState barrier = level.getBlockState(above);
            var powered = thaumcraft.common.blocks.misc.BlockBarrier.POWERED;
            if (barrier.is(BlocksTC.barrier) && barrier.getValue(powered) != tile.gettingPower())
                level.setBlock(above,barrier.setValue(powered,tile.gettingPower()),3);
        }
    }
}
