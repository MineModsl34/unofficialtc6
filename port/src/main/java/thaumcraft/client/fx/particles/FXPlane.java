package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXPlane extends SourceParticle {
    float angle;
    float angleYaw;
    float anglePitch;

    public FXPlane(ClientLevel level, double d, double d1, double d2, double m, double m1, double m2, int life) {
        super(level, d, d1, d2, 0.0, 0.0, 0.0);
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.gravity = 0.0F;
        this.xd = this.yd = this.zd = 0.0;
        this.lifetime = life;
        this.setSize(0.01F, 0.01F);
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.particleScale = 1.0F;
        this.alpha = 0.0F;
        double dx = m - this.x;
        double dy = m1 - this.y;
        double dz = m2 - this.z;
        this.xd = dx / this.lifetime;
        this.yd = dy / this.lifetime;
        this.zd = dz / this.lifetime;
        this.particleTextureIndexX = 22;
        this.particleTextureIndexY = 10;
        double d3 = FxMath.sqrt(dx * dx + dz * dz);
        this.angleYaw = 0.0F;
        this.anglePitch = 0.0F;
        if (d3 >= 1.0E-7) {
            this.angleYaw = (float)(FxMath.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
            this.anglePitch = (float)(-(FxMath.atan2(dy, d3) * 180.0 / Math.PI));
        }

        this.angle = (float)(this.random.nextGaussian() * 20.0);
    }

    public int getFXLayer() {
        return 0;
    }

    public void renderSource(FXBuffer wr, Entity entityIn, float f, float f1, float f2, float f3, float f4, float f5) {
        FXBuffer.current().draw();
        FxPoseState.glPushMatrix();
        FxPoseState.glColor4f(1.0F, 1.0F, 1.0F, this.alpha / 2.0F);
        float var13 = (float)(this.xo + (this.x - this.xo) * f - interpPosX);
        float var14 = (float)(this.yo + (this.y - this.yo) * f - interpPosY);
        float var15 = (float)(this.zo + (this.z - this.zo) * f - interpPosZ);
        FxPoseState.glTranslated(var13, var14, var15);
        FxPoseState.glRotatef(-this.angleYaw + 90.0F, 0.0F, 1.0F, 0.0F);
        FxPoseState.glRotatef(this.anglePitch + 90.0F, 0.0F, 0.0F, 1.0F);
        FxPoseState.glRotatef(this.angle, 0.0F, 1.0F, 0.0F);
        this.particleTextureIndexX = 22 + Math.round((this.age + f) / this.lifetime * 8.0F);
        float var8x = this.particleTextureIndexX / 32.0F;
        float var9 = var8x + 0.03125F;
        float var10 = this.particleTextureIndexY / 32.0F;
        float var11 = var10 + 0.03125F;
        float var12 = this.particleScale * (0.5F + (this.age + f) / this.lifetime);
        float var16 = 1.0F;
        int i = 240;
        int j = i >> 16 & 65535;
        int k = i & 65535;
        FxPoseState.glDisable(2884);
        wr.begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
        wr.pos(-0.5 * var12, 0.5 * var12, 0.0)
            .tex(var9, var11)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, this.alpha / 2.0F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(0.5 * var12, 0.5 * var12, 0.0)
            .tex(var9, var10)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, this.alpha / 2.0F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(0.5 * var12, -0.5 * var12, 0.0)
            .tex(var8x, var10)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, this.alpha / 2.0F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(-0.5 * var12, -0.5 * var12, 0.0)
            .tex(var8x, var11)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, this.alpha / 2.0F)
            .lightmap(j, k)
            .endVertex();
        FXBuffer.current().draw();
        FxPoseState.glEnable(2884);
        FxPoseState.glPopMatrix();
        wr.begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        float threshold = this.lifetime / 5.0F;
        if (this.age <= threshold) {
            this.alpha = this.age / threshold;
        } else {
            this.alpha = (float)(this.lifetime - this.age) / this.lifetime;
        }

        if (this.age++ >= this.lifetime) {
            this.remove();
        }

        this.x = this.x + this.xd;
        this.y = this.y + this.yd;
        this.z = this.z + this.zd;
    }

    public void setGravity(float value) {
        this.gravity = value;
    }
}
