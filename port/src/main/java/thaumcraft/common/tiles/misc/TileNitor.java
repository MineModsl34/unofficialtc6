package thaumcraft.common.tiles.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import thaumcraft.common.blocks.misc.BlockNitor;
import thaumcraft.common.tiles.crafting.CrucibleRegistries;

/** Source flame/core emission cadence and positions through the actual TC particle engine. */
public final class TileNitor extends BlockEntity {
    private int count;
    public TileNitor(BlockPos pos, BlockState state) { super(CrucibleRegistries.NITOR, pos, state); }
    public static void tick(Level level, BlockPos pos, BlockState state, TileNitor tile) {
        if (!level.isClientSide || !(state.getBlock() instanceof BlockNitor block)) return;
        int color = block.dye.getMapColor().col;
        var random = level.random;
        thaumcraft.common.lib.fx.SourceFX.local(level,"nitor_flame",new net.minecraft.world.phys.Vec3(
            pos.getX()+.5F+random.nextGaussian()*.025,pos.getY()+.45F+random.nextGaussian()*.025,pos.getZ()+.5F+random.nextGaussian()*.025),
            new net.minecraft.world.phys.Vec3(random.nextGaussian()*.0025,random.nextFloat()*.06,random.nextGaussian()*.0025),color,0);
        if(tile.count++%10==0)thaumcraft.common.lib.fx.SourceFX.local(level,"nitor_core",new net.minecraft.world.phys.Vec3(pos.getX()+.5F,pos.getY()+.49F,pos.getZ()+.5F),net.minecraft.world.phys.Vec3.ZERO,0,0);
    }
}
