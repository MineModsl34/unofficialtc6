package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import java.awt.Color;
import net.minecraft.client.Minecraft;
import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXVent extends SourceParticle {
    float psm = 1.0F;

    public FXVent(ClientLevel par1World, double par2, double par4, double par6, double par8, double par10, double par12, int color) {
        super(par1World, par2, par4, par6, par8, par10, par12);
        this.setSize(0.02F, 0.02F);
        this.particleScale = this.random.nextFloat() * 0.1F + 0.05F;
        this.xd = par8;
        this.yd = par10;
        this.zd = par12;
        Color c = new Color(color);
        this.rCol = c.getRed() / 255.0F;
        this.bCol = c.getBlue() / 255.0F;
        this.gCol = c.getGreen() / 255.0F;
        this.setHeading(this.xd, this.yd, this.zd, 0.125F, 5.0F);
        Entity renderentity = Minecraft.getInstance().getCameraEntity();
        int visibleDistance = 50;
        if (!Minecraft.useFancyGraphics()) {
            visibleDistance = 25;
        }

        if (FxMath.distance(renderentity,this.x, this.y, this.z) > visibleDistance) {
            this.lifetime = 0;
        }

        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    public void setScale(float f) {
        this.particleScale *= f;
        this.psm *= f;
    }

    public void setHeading(double par1, double par3, double par5, float par7, float par8) {
        float f2 = FxMath.sqrt(par1 * par1 + par3 * par3 + par5 * par5);
        par1 /= f2;
        par3 /= f2;
        par5 /= f2;
        par1 += this.random.nextGaussian() * (this.random.nextBoolean() ? -1 : 1) * 0.0075F * par8;
        par3 += this.random.nextGaussian() * (this.random.nextBoolean() ? -1 : 1) * 0.0075F * par8;
        par5 += this.random.nextGaussian() * (this.random.nextBoolean() ? -1 : 1) * 0.0075F * par8;
        par1 *= par7;
        par3 *= par7;
        par5 *= par7;
        this.xd = par1;
        this.yd = par3;
        this.zd = par5;
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.age++;
        if (this.particleScale >= this.psm) {
            this.remove();
        }

        this.yd += 0.0025;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.8500000190734863;
        this.yd *= 0.8500000190734863;
        this.zd *= 0.8500000190734863;
        if (this.particleScale < this.psm) {
            this.particleScale = (float)(this.particleScale * 1.15);
        }

        if (this.particleScale > this.psm) {
            this.particleScale = this.psm;
        }

        if (this.onGround) {
            this.xd *= 0.7F;
            this.zd *= 0.7F;
        }
    }

    public void setRGB(float r, float g, float b) {
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
    }

    public void renderSource(FXBuffer wr, Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
        FxPoseState.glColor4f(1.0F, 1.0F, 1.0F, 0.33F);
        int part = (int)(1.0F + this.particleScale / this.psm * 4.0F);
        float var8x = part % 16 / 64.0F;
        float var9 = var8x + 0.015625F;
        float var10 = part / 64 / 64.0F;
        float var11 = var10 + 0.015625F;
        float var12 = 0.3F * this.particleScale;
        float var13 = (float)(this.xo + (this.x - this.xo) * f - interpPosX);
        float var14 = (float)(this.yo + (this.y - this.yo) * f - interpPosY);
        float var15 = (float)(this.zo + (this.z - this.zo) * f - interpPosZ);
        float var16 = 1.0F;
        int i = this.getLightColor(f);
        int j = i >> 16 & 65535;
        int k = i & 65535;
        float alpha = this.alpha * ((this.psm - this.particleScale) / this.psm);
        wr.pos(var13 - f1 * var12 - f4 * var12, var14 - f2 * var12, var15 - f3 * var12 - f5 * var12)
            .tex(var9, var11)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, alpha)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 - f1 * var12 + f4 * var12, var14 + f2 * var12, var15 - f3 * var12 + f5 * var12)
            .tex(var9, var10)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, alpha)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 + f1 * var12 + f4 * var12, var14 + f2 * var12, var15 + f3 * var12 + f5 * var12)
            .tex(var8x, var10)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, alpha)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 + f1 * var12 - f4 * var12, var14 - f2 * var12, var15 + f3 * var12 - f5 * var12)
            .tex(var8x, var11)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, alpha)
            .lightmap(j, k)
            .endVertex();
    }

    public int getFXLayer() {
        return 1;
    }
}
