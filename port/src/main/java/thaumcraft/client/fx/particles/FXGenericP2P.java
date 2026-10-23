package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import net.minecraft.client.multiplayer.ClientLevel;

public class FXGenericP2P extends FXGeneric {
    private double targetX;
    private double targetY;
    private double targetZ;

    public FXGenericP2P(ClientLevel level, double x, double y, double z, double xx, double yy, double zz) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.setSize(0.1F, 0.1F);
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.targetX = xx;
        this.targetY = yy;
        this.targetZ = zz;
        double dx = xx - this.x;
        double dy = yy - this.y;
        double dz = zz - this.z;
        int base = (int)(FxMath.sqrt(dx * dx + dy * dy + dz * dz) * 3.0F);
        if (base < 1) {
            base = 1;
        }

        this.lifetime = base / 2 + this.random.nextInt(base);
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
        double dx = this.targetX - this.x;
        double dy = this.targetY - this.y;
        double dz = this.targetZ - this.z;
        double d13 = 0.3;
        double d11 = FxMath.sqrt(dx * dx + dy * dy + dz * dz);
        if (d11 < 4.0) {
            this.particleScale *= 0.9F;
            d13 = 0.6;
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

