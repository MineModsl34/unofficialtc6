package thaumcraft.client.fx.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.world.level.block.state.BlockState;

/** Source FXDigging was an unmodified subclass of Minecraft ParticleDigging. */
public final class FXDigging extends TerrainParticle {
    public FXDigging(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,BlockState state) {
        super(level,x,y,z,vx,vy,vz,state);
    }
}
