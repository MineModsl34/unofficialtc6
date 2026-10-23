package thaumcraft.common.lib.utils;
import java.util.Random;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceRandomAdapterTest {
    @Test void adapterDoesNotReseedOrDrawAWorldSeed(){var world=RandomSource.create(617);var actual=new SourceRandomAdapter(world);var expected=new Random(617);assertEquals(expected.nextInt(5),actual.nextInt(5));assertEquals(expected.nextFloat(),actual.nextFloat());assertEquals(expected.nextInt(900),actual.nextInt(900));assertEquals(expected.nextLong(),world.nextLong());}
    @Test void floatingAndGaussianCallsRemainInSameStream(){var world=RandomSource.create(712);var actual=new SourceRandomAdapter(world);var expected=RandomSource.create(712);assertEquals(expected.nextDouble(),actual.nextDouble());assertEquals(expected.nextGaussian(),actual.nextGaussian());assertEquals(expected.nextBoolean(),actual.nextBoolean());assertEquals(expected.nextInt(),world.nextInt());}
}
