package thaumcraft.client.lib.warp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import thaumcraft.client.fx.particles.FXBreakingFade;
import thaumcraft.client.fx.particles.FXFluxBubble;
import thaumcraft.common.entities.monster.EntityThaumicSlime;
import thaumcraft.common.lib.warp.WarpTaintPresentationHooks;

public final class WarpTaintPresentation implements WarpTaintPresentationHooks.ClientPresentation {
    @Override public void slimeJump(EntityThaumicSlime slime,int size,int count) {
        Minecraft client=Minecraft.getInstance();if(slime.level()!=client.level)return;
        for(int j=0;j<count;j++) {
            float angle=client.level.random.nextFloat()*(float)Math.PI*2,radial=client.level.random.nextFloat()*.5F+.5F;
            float x=Mth.sin(angle)*size*.5F*radial,z=Mth.cos(angle)*size*.5F*radial;
            var effect=new FXBreakingFade(client.level,slime.getX()+x,(slime.getBoundingBox().minY+slime.getBoundingBox().maxY)/2,slime.getZ()+z,(int)(66/(client.level.random.nextFloat()*.9F+.1F)));
            client.particleEngine.add(effect);
        }
    }
    @Override public void fluxBubble(Level world,BlockPos pos,int meta,RandomSource random) {
        Minecraft client=Minecraft.getInstance();if(world!=client.level||!(world instanceof ClientLevel level))return;
        if(random.nextInt(44)<=meta) client.particleEngine.add(new FXFluxBubble(level,pos.getX()+random.nextFloat(),pos.getY()+.125F*meta,pos.getZ()+random.nextFloat()));
    }
}
