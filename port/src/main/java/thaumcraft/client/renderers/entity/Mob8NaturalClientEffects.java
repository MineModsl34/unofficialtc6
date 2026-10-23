package thaumcraft.client.renderers.entity;

import net.minecraft.client.Minecraft;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.common.entities.monster.EntityWisp;
import thaumcraft.common.lib.network.fx.PacketFXWispZap;

/** Original wisp burst, aspect-atlas particles and source FXBolt, without stage-eight surrogates. */
public final class Mob8NaturalClientEffects {
    private Mob8NaturalClientEffects() {}
    public static void ambient(EntityWisp entity) {
        var fx=FXDispatcher.INSTANCE;var random=entity.level().random;
        if(entity.tickCount<=1)fx.burst(entity.getX(),entity.getY(),entity.getZ(),10);
        // Source draws the boolean even if its aspect has not yet arrived from entity data sync.
        if(random.nextBoolean()&&entity.getAspect()!=null)
            fx.drawWispParticles(entity.getX()+(random.nextFloat()-random.nextFloat())*.7F,entity.getY()+(random.nextFloat()-random.nextFloat())*.7F,entity.getZ()+(random.nextFloat()-random.nextFloat())*.7F,0,0,0,entity.getAspect().getColor(),0);
    }
    public static void death(EntityWisp entity) { FXDispatcher.INSTANCE.burst(entity.getX(),entity.getY()+.45F,entity.getZ(),1); }
    public static void zap(PacketFXWispZap packet) {
        var level=Minecraft.getInstance().level;if(level==null)return;
        var source=level.getEntity(packet.source());var target=level.getEntity(packet.target());
        if(source==null||target==null||source.distanceToSqr(target)>1024)return;
        int color=source instanceof EntityWisp wisp&&wisp.getAspect()!=null?wisp.getAspect().getColor():0xffffff;
        FXDispatcher.INSTANCE.arcBolt(source.getX(),source.getY(),source.getZ(),target.getX(),target.getY(),target.getZ(),(color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,.6F);
    }
}
