package thaumcraft.client.lib.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.common.lib.network.fx.PacketSourceFX;

/** Literal client-only Salis Mundus and elemental hoe presentation; no crafting authority. */
final class SourceToolFX {
    private SourceToolFX() {}
    static boolean render(ClientLevel world,PacketSourceFX p){
        var fx=FXDispatcher.INSTANCE;var random=world.random;
        switch(p.key()){
            case "dust_hand" -> {
                for(int a=0;a<50;a++){
                    boolean floaty=a<50/3;
                    float r=Mth.nextInt(random,255,255)/255F,g=Mth.nextInt(random,189,255)/255F,b=Mth.nextInt(random,64,255)/255F;
                    fx.drawSimpleSparkle(random,p.x(),p.y(),p.z(),p.tx()/6+random.nextGaussian()*.05,
                        p.ty()/6+random.nextGaussian()*.05+(floaty?.05:.15),p.tz()/6+random.nextGaussian()*.05,
                        .5F,r,g,b,random.nextInt(5),floaty?.3F+random.nextFloat()*.5F:.85F,floaty?.2F:.5F,16);
                }
            }
            case "dust_block" -> fx.drawBlockSparkles(BlockPos.containing(p.x(),p.y(),p.z()),new Vec3(p.tx(),p.ty(),p.tz()));
            case "hoe" -> fx.drawBamf(p.x(),p.y(),p.z(),.3F,.12F,.1F,p.extra()==1,false,Direction.UP);
            default -> {return false;}
        }
        return true;
    }
}
