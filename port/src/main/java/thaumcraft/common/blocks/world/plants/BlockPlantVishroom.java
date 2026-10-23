package thaumcraft.common.blocks.world.plants;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Vishroom: any block with a full top (PlantType.Cave in 1.12), nausea on contact. */
public class BlockPlantVishroom extends BushBlock {
    public static final MapCodec<BlockPlantVishroom> CODEC = simpleCodec(BlockPlantVishroom::new);

    public BlockPlantVishroom(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isFaceSturdy(level, pos, Direction.UP);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity living && level.random.nextInt(5) == 0) {
            living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rand) {
        if(rand.nextInt(3)==0)thaumcraft.common.lib.fx.SourceFX.local(level,"vishroom",new net.minecraft.world.phys.Vec3(
            pos.getX()+.5F+(rand.nextFloat()-rand.nextFloat())*.4F,pos.getY()+.3F,pos.getZ()+.5F+(rand.nextFloat()-rand.nextFloat())*.4F),net.minecraft.world.phys.Vec3.ZERO,0,0);
    }
}
