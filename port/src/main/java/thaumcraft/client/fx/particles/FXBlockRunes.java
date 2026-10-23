package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXBlockRunes extends SourceParticle {
    double ofx = 0.0;
    double ofy = 0.0;
    float rotation = 0.0F;
    int runeIndex = 0;

    public FXBlockRunes(ClientLevel level, double d, double d1, double d2, float f1, float f2, float f3, int m) {
        super(level, d, d1, d2, 0.0, 0.0, 0.0);
        if (f1 == 0.0F) {
            f1 = 1.0F;
        }

        this.rotation = this.random.nextInt(4) * 90;
        this.rCol = f1;
        this.gCol = f2;
        this.bCol = f3;
        this.gravity = 0.0F;
        this.xd = this.yd = this.zd = 0.0;
        this.lifetime = 3 * m;
        this.setSize(0.01F, 0.01F);
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.runeIndex = (int)(Math.random() * 16.0 + 224.0);
        this.ofx = this.random.nextFloat() * 0.2;
        this.ofy = -0.3 + this.random.nextFloat() * 0.6;
        this.particleScale = (float)(1.0 + this.random.nextGaussian() * 0.1F);
        this.alpha = 0.0F;
    }

    public void setScale(float s) {
        this.particleScale = s;
    }

    public void setOffsetX(double f) {
        this.ofx = f;
    }

    public void renderSource(FXBuffer wr, Entity entityIn, float f, float f1, float f2, float f3, float f4, float f5) {
        FXBuffer.current().draw();
        FxPoseState.glPushMatrix();
        FxPoseState.glColor4f(1.0F, 1.0F, 1.0F, this.alpha / 2.0F);
        float var13 = (float)(this.xo + (this.x - this.xo) * f - interpPosX);
        float var14 = (float)(this.yo + (this.y - this.yo) * f - interpPosY);
        float var15 = (float)(this.zo + (this.z - this.zo) * f - interpPosZ);
        FxPoseState.glTranslated(var13, var14, var15);
        FxPoseState.glRotatef(this.rotation, 0.0F, 1.0F, 0.0F);
        FxPoseState.glRotatef(90.0F, 0.0F, 0.0F, 1.0F);
        FxPoseState.glTranslated(this.ofx, this.ofy, -0.51);
        float var8x = this.runeIndex % 16 / 64.0F;
        float var9 = var8x + 0.015625F;
        float var10 = 0.09375F;
        float var11 = var10 + 0.015625F;
        float var12 = 0.3F * this.particleScale;
        float var16 = 1.0F;
        wr.begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
        int i = 240;
        int j = i >> 16 & 65535;
        int k = i & 65535;
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

        this.yd = this.yd - 0.04 * this.gravity;
        this.x = this.x + this.xd;
        this.y = this.y + this.yd;
        this.z = this.z + this.zd;
    }

    public void setGravity(float value) {
        this.gravity = value;
    }
}
