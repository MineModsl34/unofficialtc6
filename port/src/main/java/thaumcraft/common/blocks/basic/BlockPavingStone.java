package thaumcraft.common.blocks.basic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Source travel paving and the server-ticked barrier paving. */
public class BlockPavingStone extends Block {
    public static final MapCodec<BlockPavingStone> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(instance -> instance.group(
        com.mojang.serialization.Codec.BOOL.fieldOf("travel").forGetter(block -> block.travel),
        propertiesCodec()).apply(instance, BlockPavingStone::new));
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 15, 16);
    private final boolean travel;

    public BlockPavingStone(boolean travel, Properties properties) {
        super(properties);
        this.travel = travel;
    }


    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (this.travel && !level.isClientSide && entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false));
            living.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 0, false, false));
        }
        super.stepOn(level, pos, state, entity);
    }
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,net.minecraft.util.RandomSource random) {
        if(travel)return;int branch=level.hasNeighborSignal(pos)?1:
            (level.getBlockState(pos.above()).is(thaumcraft.api.blocks.BlocksTC.barrier)||level.getBlockState(pos.above(2)).is(thaumcraft.api.blocks.BlocksTC.barrier)?2:0);
        thaumcraft.common.lib.fx.SourceFX.local(level,"paving",net.minecraft.world.phys.Vec3.atLowerCornerOf(pos),net.minecraft.world.phys.Vec3.ZERO,0,branch);
    }
}
