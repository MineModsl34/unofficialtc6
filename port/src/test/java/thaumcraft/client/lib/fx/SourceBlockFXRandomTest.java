package thaumcraft.client.lib.fx;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure RNG boundary tests: no world, registry, particle, client or GPU is constructed. */
class SourceBlockFXRandomTest {
    @Test void sampledAnimateRandomFloatDoesNotConsumeWorldRandom(){
        var world=new CountingRandom();var sample=new SourceBlockFX.SampledFloat(world,Math.nextDown(.2F));
        assertTrue(sample.nextFloat()<.2F);assertEquals(0,world.draws);
        assertEquals(.75F,sample.nextFloat());assertEquals(1,world.draws);
    }
    @Test void sparkleThresholdAndFloatBitsAreNotQuantized(){
        var world=new CountingRandom();float value=.2F;
        var sample=new SourceBlockFX.SampledFloat(world,Float.intBitsToFloat(Float.floatToRawIntBits(value)));
        float actual=sample.nextFloat();assertEquals(Float.floatToRawIntBits(value),Float.floatToRawIntBits(actual));assertFalse(actual<.2F);assertEquals(0,world.draws);
    }
    private static final class CountingRandom implements RandomSource {
        private int draws;
        @Override public float nextFloat(){draws++;return .75F;}
        @Override public RandomSource fork(){throw new AssertionError();}
        @Override public PositionalRandomFactory forkPositional(){throw new AssertionError();}
        @Override public void setSeed(long seed){throw new AssertionError();}
        @Override public int nextInt(){throw new AssertionError();}
        @Override public int nextInt(int bound){throw new AssertionError();}
        @Override public long nextLong(){throw new AssertionError();}
        @Override public boolean nextBoolean(){throw new AssertionError();}
        @Override public double nextDouble(){throw new AssertionError();}
        @Override public double nextGaussian(){throw new AssertionError();}
    }
}
