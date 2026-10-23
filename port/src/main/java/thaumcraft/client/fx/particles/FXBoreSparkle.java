package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import thaumcraft.client.fx.SourceParticle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXBoreSparkle extends SourceParticle {
    private Entity target;
    private double targetX;
    private double targetY;
    private double targetZ;
    public int particle = 24;

    public FXBoreSparkle(ClientLevel par1World, double par2, double par4, double par6, double tx, double ty, double tz) {
        super(par1World, par2, par4, par6, 0.0, 0.0, 0.0);
        this.rCol = this.gCol = this.bCol = 0.6F;
        this.particleScale = this.random.nextFloat() * 0.5F + 0.5F;
        this.targetX = tx;
        this.targetY = ty;
        this.targetZ = tz;
        double dx = tx - this.x;
        double dy = ty - this.y;
        double dz = tz - this.z;
        int base = (int)(FxMath.sqrt(dx * dx + dy * dy + dz * dz) * 10.0F);
        if (base < 1) {
            base = 1;
        }

        this.lifetime = base / 2 + this.random.nextInt(base);
        float f3 = 0.01F;
        this.xd = (float)this.random.nextGaussian() * f3;
        this.yd = (float)this.random.nextGaussian() * f3;
        this.zd = (float)this.random.nextGaussian() * f3;
        this.rCol = 0.2F;
        this.gCol = 0.6F + this.random.nextFloat() * 0.3F;
        this.bCol = 0.2F;
        this.gravity = 0.2F;
        Entity renderentity = Minecraft.getInstance().getCameraEntity();
        int visibleDistance = 64;
        if (!Minecraft.useFancyGraphics()) {
            visibleDistance = 32;
        }

        if (FxMath.distance(renderentity,this.x, this.y, this.z) > visibleDistance) {
            this.lifetime = 0;
        }
    }

    public FXBoreSparkle(ClientLevel par1World, double par2, double par4, double par6, Entity t) {
        this(par1World, par2, par4, par6, t.getX(), t.getY() + t.getEyeHeight(), t.getZ());
        this.target = t;
    }

    public void renderSource(FXBuffer wr, Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
        float bob = FxMath.sin(this.age / 3.0F) * 0.5F + 1.0F;
        FxPoseState.glColor4f(1.0F, 1.0F, 1.0F, 0.75F);
        int part = this.age % 4;
        float var8x = part / 64.0F;
        float var9 = var8x + 0.015625F;
        float var10 = 0.0625F;
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
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, 1.0F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 - f1 * var12 + f4 * var12, var14 + f2 * var12, var15 - f3 * var12 + f5 * var12)
            .tex(var9, var10)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, 1.0F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 + f1 * var12 + f4 * var12, var14 + f2 * var12, var15 + f3 * var12 + f5 * var12)
            .tex(var8x, var10)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, 1.0F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 + f1 * var12 - f4 * var12, var14 - f2 * var12, var15 + f3 * var12 - f5 * var12)
            .tex(var8x, var11)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, 1.0F)
            .lightmap(j, k)
            .endVertex();
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.target != null) {
            this.targetX = this.target.getX();
            this.targetY = this.target.getY() + this.target.getEyeHeight();
            this.targetZ = this.target.getZ();
        }

        if (this.age++ < this.lifetime
            && (
                FxMath.floor(this.x) != FxMath.floor(this.targetX)
                    || FxMath.floor(this.y) != FxMath.floor(this.targetY)
                    || FxMath.floor(this.z) != FxMath.floor(this.targetZ)
            )) {
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.985;
            this.yd *= 0.95;
            this.zd *= 0.985;
            double dx = this.targetX - this.x;
            double dy = this.targetY - this.y;
            double dz = this.targetZ - this.z;
            double d11 = FxMath.sqrt(dx * dx + dy * dy + dz * dz);
            double clamp = Math.min(0.25, d11 / 15.0);
            if (d11 < 2.0) {
                this.particleScale *= 0.9F;
            }

            dx /= d11;
            dy /= d11;
            dz /= d11;
            this.xd += dx * clamp;
            this.yd += dy * clamp;
            this.zd += dz * clamp;
            this.xd = FxMath.clamp((float)this.xd, -clamp, clamp);
            this.yd = FxMath.clamp((float)this.yd, -clamp, clamp);
            this.zd = FxMath.clamp((float)this.zd, -clamp, clamp);
            this.xd = this.xd + this.random.nextGaussian() * 0.01;
            this.yd = this.yd + this.random.nextGaussian() * 0.01;
            this.zd = this.zd + this.random.nextGaussian() * 0.01;
        } else {
            this.remove();
        }
    }

    public void setGravity(float value) {
        this.gravity = value;
    }
}
