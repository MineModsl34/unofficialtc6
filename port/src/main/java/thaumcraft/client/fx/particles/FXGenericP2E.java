package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import net.minecraft.world.entity.Entity;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXGenericP2E extends FXGeneric {
    private Entity target;

    public FXGenericP2E(ClientLevel level, double x, double y, double z, Entity target) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.setSize(0.1F, 0.1F);
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.target = target;
        double dx = target.getX() - this.x;
        double dy = target.getY() - this.y;
        double dz = target.getZ() - this.z;
        int base = (int)(FxMath.sqrt(dx * dx + dy * dy + dz * dz) * 5.0F);
        if (base < 1) {
            base = 1;
        }

        this.lifetime = base;
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.particleTextureJitterX = 0.0F;
        this.particleTextureJitterY = 0.0F;
        float f3 = 0.01F;
        this.xd = (float)level.random.nextGaussian() * f3;
        this.yd = (float)level.random.nextGaussian() * f3;
        this.zd = (float)level.random.nextGaussian() * f3;
        this.gravity = 0.2F;
    }

    @Override
    public void tick() {
        super.tick();
        double dx = this.target.getX() - this.x;
        double dy = this.target.getY() - this.y;
        double dz = this.target.getZ() - this.z;
        double d13 = 0.3;
        double d11 = FxMath.sqrt(dx * dx + dy * dy + dz * dz);
        if (d11 < 4.0) {
            this.particleScale *= 0.9F;
            d13 = 0.6;
        }

        if (d11 < 0.25) {
            this.remove();
        }

        dx /= d11;
        dy /= d11;
        dz /= d11;
        this.xd += dx * d13;
        this.yd += dy * d13;
        this.zd += dz * d13;
        this.xd = FxMath.clamp((float)this.xd, -0.35F, 0.35F);
        this.yd = FxMath.clamp((float)this.yd, -0.35F, 0.35F);
        this.zd = FxMath.clamp((float)this.zd, -0.35F, 0.35F);
    }
}
