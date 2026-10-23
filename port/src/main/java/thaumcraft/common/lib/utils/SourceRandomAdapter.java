package thaumcraft.common.lib.utils;
import java.util.Random;
import net.minecraft.util.RandomSource;
/** Uses the existing world stream; does NOT draw a seed and start a different loot RNG. */
public final class SourceRandomAdapter extends Random {
    private final RandomSource source;
    public SourceRandomAdapter(RandomSource source){this.source=java.util.Objects.requireNonNull(source);}
    @Override protected int next(int bits){return source.nextInt() >>> (32-bits);}
    @Override public int nextInt(){return source.nextInt();}
    @Override public int nextInt(int bound){return source.nextInt(bound);}
    @Override public long nextLong(){return source.nextLong();}
    @Override public boolean nextBoolean(){return source.nextBoolean();}
    @Override public float nextFloat(){return source.nextFloat();}
    @Override public double nextDouble(){return source.nextDouble();}
    @Override public double nextGaussian(){return source.nextGaussian();}
}
