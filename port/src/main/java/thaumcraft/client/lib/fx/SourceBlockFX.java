package thaumcraft.client.lib.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.common.lib.network.fx.PacketSourceFX;

/** Source-only block presentation. All positions/random arguments were sampled at their original caller. */
public final class SourceBlockFX {
    private SourceBlockFX() {}
    /** Returns false for keys belonging to another native presentation consumer. */
    public static boolean render(ClientLevel world,PacketSourceFX p){
        var fx=FXDispatcher.INSTANCE;
        switch(p.key()){
            case "tube_vent" -> fx.drawVentParticles(p.x(),p.y(),p.z(),p.tx(),p.ty(),p.tz(),p.color());
            case "tube_break" -> {
                var random=world.random;
                world.playLocalSound(p.x()+.5,p.y()+.5,p.z()+.5,SoundEvents.LAVA_EXTINGUISH,SoundSource.BLOCKS,.1F,1F+random.nextFloat()*.1F,false);
                for(int n=0;n<5;n++)fx.drawVentParticles(p.x()+.33+random.nextFloat()*.33,p.y()+.33+random.nextFloat()*.33,p.z()+.33+random.nextFloat()*.33,0,0,0,p.color());
            }
            case "inlay" -> {
                float sample=Float.intBitsToFloat(p.extra());if(!Float.isFinite(sample)||sample<0||sample>=1)return true;
                float red=(p.color()>>16&255)/255F,green=(p.color()>>8&255)/255F;
                fx.drawLineSparkle(new SampledFloat(world.random,sample),p.x(),p.y(),p.z(),p.tx(),p.ty(),p.tz(),.33F,red,green,green/2F,0,1F,0,16);
            }
            case "pedestal_charge" -> fx.blockRunes2(p.x(),p.y(),p.z(),1F,0,0,10,0);
            case "block_spark" -> {
                float red=Float.intBitsToFloat(p.color()),size=Float.intBitsToFloat(p.extra());
                if(Float.isFinite(red)&&Float.isFinite(size)&&size>0&&size<=8)fx.spark(p.x(),p.y(),p.z(),size,red,(float)p.tx(),(float)p.ty(),(float)p.tz());
            }
            default -> {return false;}
        }
        return true;
    }
    /** The source dispatcher needs exactly one float from animateTick's independent random.
     * Transport that sampled float verbatim; do not consume an extra world RNG draw. */
    static final class SampledFloat implements RandomSource {
        private final RandomSource rest;private final float sample;private boolean used;
        SampledFloat(RandomSource rest,float sample){this.rest=rest;this.sample=sample;}
        @Override public float nextFloat(){if(!used){used=true;return sample;}return rest.nextFloat();}
        @Override public RandomSource fork(){return rest.fork();}
        @Override public PositionalRandomFactory forkPositional(){return rest.forkPositional();}
        @Override public void setSeed(long seed){rest.setSeed(seed);}
        @Override public int nextInt(){return rest.nextInt();}
        @Override public int nextInt(int bound){return rest.nextInt(bound);}
        @Override public long nextLong(){return rest.nextLong();}
        @Override public boolean nextBoolean(){return rest.nextBoolean();}
        @Override public double nextDouble(){return rest.nextDouble();}
        @Override public double nextGaussian(){return rest.nextGaussian();}
    }
}
