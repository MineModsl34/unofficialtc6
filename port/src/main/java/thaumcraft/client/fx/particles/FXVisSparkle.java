package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXVisSparkle extends SourceParticle {
    private double targetX;
    private double targetY;
    private double targetZ;
    float sizeMod = 0.0F;

    public FXVisSparkle(ClientLevel par1World, double par2, double par4, double par6, double tx, double ty, double tz) {
        super(par1World, par2, par4, par6, 0.0, 0.0, 0.0);
        this.rCol = this.gCol = this.bCol = 0.6F;
        this.particleScale = 0.0F;
        this.targetX = tx;
        this.targetY = ty;
        this.targetZ = tz;
        this.lifetime = 1000;
        float f3 = 0.01F;
        this.xd = (float)this.random.nextGaussian() * f3;
        this.yd = (float)this.random.nextGaussian() * f3;
        this.zd = (float)this.random.nextGaussian() * f3;
        this.sizeMod = 45 + this.random.nextInt(15);
        this.rCol = 0.2F;
        this.gCol = 0.6F + this.random.nextFloat() * 0.3F;
        this.bCol = 0.2F;
        this.gravity = 0.2F;
    }

    public void renderSource(FXBuffer wr, Entity entityIn, float f, float f1, float f2, float f3, float f4, float f5) {
        float bob = FxMath.sin(this.age / 3.0F) * 0.3F + 6.0F;
        FxPoseState.glColor4f(1.0F, 1.0F, 1.0F, 0.75F);
        int part = this.age % 16;
        float var8x = part / 64.0F;
        float var9 = var8x + 0.015625F;
        float var10 = 0.125F;
        float var11 = var10 + 0.015625F;
        float var12 = 0.1F * this.particleScale * bob;
        float var13 = (float)(this.xo + (this.x - this.xo) * f - interpPosX);
        float var14 = (float)(this.yo + (this.y - this.yo) * f - interpPosY);
        float var15 = (float)(this.zo + (this.z - this.zo) * f - interpPosZ);
        float var16 = 1.0F;
        int i = 240;
        int j = i >> 16 & 65535;
        int k = i & 65535;
        wr.pos(var13 - f1 * var12 - f4 * var12, var14 - f2 * var12, var15 - f3 * var12 - f5 * var12)
            .tex(var9, var11)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, 0.5F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 - f1 * var12 + f4 * var12, var14 + f2 * var12, var15 - f3 * var12 + f5 * var12)
            .tex(var9, var10)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, 0.5F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 + f1 * var12 + f4 * var12, var14 + f2 * var12, var15 + f3 * var12 + f5 * var12)
            .tex(var8x, var10)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, 0.5F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 + f1 * var12 - f4 * var12, var14 - f2 * var12, var15 + f3 * var12 - f5 * var12)
            .tex(var8x, var11)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, 0.5F)
            .lightmap(j, k)
            .endVertex();
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.985;
        this.yd *= 0.985;
        this.zd *= 0.985;
        double dx = this.targetX - this.x;
        double dy = this.targetY - this.y;
        double dz = this.targetZ - this.z;
        double d13 = 0.1F;
        double d11 = FxMath.sqrt(dx * dx + dy * dy + dz * dz);
        if (d11 < 2.0) {
            this.particleScale *= 0.95F;
        }

        if (d11 < 0.2) {
            this.lifetime = this.age;
        }

        if (this.age < 10) {
            this.particleScale = this.age / this.sizeMod;
        }

        dx /= d11;
        dy /= d11;
        dz /= d11;
        this.xd += dx * d13;
        this.yd += dy * d13;
        this.zd += dz * d13;
        this.xd = FxMath.clamp((float)this.xd, -0.1F, 0.1F);
        this.yd = FxMath.clamp((float)this.yd, -0.1F, 0.1F);
        this.zd = FxMath.clamp((float)this.zd, -0.1F, 0.1F);
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    public void setGravity(float value) {
        this.gravity = value;
    }
}
