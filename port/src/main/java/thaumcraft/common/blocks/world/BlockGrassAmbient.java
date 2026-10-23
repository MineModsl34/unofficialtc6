package thaumcraft.common.blocks.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrassBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Magical forest grass. In 1.12 BlockGrass spread normal grass, while in 1.21 SpreadingSnowyDirtBlock
 * spreads itself, so the tick is delegated to vanilla grass.
 */
public class BlockGrassAmbient extends GrassBlock {
    public static final MapCodec<GrassBlock> CODEC = simpleCodec(BlockGrassAmbient::new);

    public BlockGrassAmbient(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<GrassBlock> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState vanilla = Blocks.GRASS_BLOCK.defaultBlockState().setValue(SNOWY, state.getValue(SNOWY));
        vanilla.randomTick(level, pos, random);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int light=level.getBrightness(net.minecraft.world.level.LightLayer.SKY,pos.above())-level.getSkyDarken();
        float angle=level.getTimeOfDay(1)*(float)(Math.PI*2);float end=angle<(float)Math.PI?0:(float)(Math.PI*2);
        angle+=(end-angle)*.2F;light=net.minecraft.util.Mth.clamp(Math.round(light*net.minecraft.util.Mth.cos(angle)),0,15);
        if(4+light*2<1+random.nextInt(13)) {
            BlockPos target=pos.offset(random.nextInt(17)-8,5,random.nextInt(17)-8);
            for(int n=0;n<10&&target.getY()>50&&!level.getBlockState(target).is(Blocks.GRASS_BLOCK);n++)target=target.below();
            if(level.getBlockState(target).is(Blocks.GRASS_BLOCK))thaumcraft.common.lib.fx.SourceFX.local(level,"grass",net.minecraft.world.phys.Vec3.atLowerCornerOf(target.above()),net.minecraft.world.phys.Vec3.ZERO,0,0);
        }
    }
}
