package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.blocks.devices.BlockBellows;
import thaumcraft.common.lib.crafting.ProcessingRules;

/** Source redstone-controlled bellows: engine furnace acceleration and local model inflation. */
public final class TileBellows extends BlockEntity {
    public float inflation = 1;
    private boolean direction,firstRun = true;
    public int delay;
    public TileBellows(BlockPos pos,BlockState state) { super(ProcessingRegistries.BELLOWS,pos,state); }
    public static void tick(Level level,BlockPos pos,BlockState state,TileBellows tile) {
        if (!state.getValue(BlockBellows.ENABLED)) return;
        if (level.isClientSide) {
            if (tile.firstRun) tile.inflation = .35F + level.random.nextFloat() * .55F;
            tile.firstRun = false;
            if (tile.inflation > .35F && !tile.direction) tile.inflation -= .075F;
            if (tile.inflation <= .35F && !tile.direction) tile.direction = true;
            if (tile.inflation < 1 && tile.direction) tile.inflation += .025F;
            if (tile.inflation >= 1 && tile.direction) {
                tile.direction = false;
                level.playLocalSound(pos.getX(),pos.getY(),pos.getZ(),SoundEvents.GHAST_SHOOT,SoundSource.BLOCKS,.01F,.5F + (level.random.nextFloat() - level.random.nextFloat()) * .2F,false);
            }
        } else if (++tile.delay >= 2) {
            tile.delay = 0;
            // Source TileEntityFurnace excludes blast furnaces/smokers; deliberately keep this.
            if (level.getBlockEntity(pos.relative(state.getValue(BlockBellows.FACING))) instanceof FurnaceBlockEntity furnace) {
                int progress = ProcessingRules.vanillaBellowsProgress(tile.getCooktime(furnace),furnace.cookingTotalTime);
                if (progress != tile.getCooktime(furnace)) { tile.setCooktime(furnace,progress); furnace.setChanged(); }
            }
        }
    }
    public void setCooktime(FurnaceBlockEntity furnace,int progress) { furnace.cookingProgress = progress; }
    public int getCooktime(FurnaceBlockEntity furnace) { return furnace.cookingProgress; }
    public static int getBellows(Level level,BlockPos pos,Direction[] directions) {
        int bellows = 0;
        for (Direction dir : directions) {
            BlockPos neighbor = pos.relative(dir);
            if (!level.hasChunkAt(neighbor)) continue;
            BlockState state = level.getBlockState(neighbor);
            if (level.getBlockEntity(neighbor) instanceof TileBellows && state.hasProperty(BlockBellows.FACING)
                && state.getValue(BlockBellows.FACING) == dir.getOpposite() && state.getValue(BlockBellows.ENABLED)) bellows++;
        }
        return bellows;
    }
}
