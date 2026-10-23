package thaumcraft.client.fx.other;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import net.minecraft.client.Minecraft;
import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXBlockWard extends SourceParticle {
    ResourceLocation[] tex1 = new ResourceLocation[15];
    Direction side;
    int rotation = 0;
    float sx = 0.0F;
    float sy = 0.0F;
    float sz = 0.0F;

    public FXBlockWard(ClientLevel level, double d, double d1, double d2, Direction side, float f, float f1, float f2) {
        super(level, d, d1, d2, 0.0, 0.0, 0.0);
        this.side = side;
        this.gravity = 0.0F;
        this.xd = this.yd = this.zd = 0.0;
        this.lifetime = 12 + this.random.nextInt(5);
        this.setSize(0.01F, 0.01F);
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.particleScale = (float)(1.4 + this.random.nextGaussian() * 0.3F);
        this.rotation = this.random.nextInt(360);
        this.sx = FxMath.clamp(f - 0.6F + this.random.nextFloat() * 0.2F, -0.4F, 0.4F);
        this.sy = FxMath.clamp(f1 - 0.6F + this.random.nextFloat() * 0.2F, -0.4F, 0.4F);
        this.sz = FxMath.clamp(f2 - 0.6F + this.random.nextFloat() * 0.2F, -0.4F, 0.4F);
        if (side.getStepX() != 0) {
            this.sx = 0.0F;
        }

        if (side.getStepY() != 0) {
            this.sy = 0.0F;
        }

        if (side.getStepZ() != 0) {
            this.sz = 0.0F;
        }

        for (int a = 0; a < 15; a++) {
            this.tex1[a] = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/models/hemis" + (a + 1) + ".png");
        }
    }

    public void renderSource(FXBuffer wr, Entity entityIn, float f, float f1, float f2, float f3, float f4, float f5) {
        FXBuffer.current().draw();
        FxPoseState.glPushMatrix();
        float fade = (this.age + f) / this.lifetime;
        int frame = Math.max(1, Math.min(15, (int)(15.0F * fade)));
        FxPoseState.bindTexture(this.tex1[frame - 1]);
        FxPoseState.glDepthMask(false);
        FxPoseState.glEnable(3042);
        FxPoseState.glBlendFunc(770, 1);
        FxPoseState.glColor4f(1.0F, 1.0F, 1.0F, this.alpha / 2.0F);
        float var13 = (float)(this.xo + (this.x - this.xo) * f - interpPosX);
        float var14 = (float)(this.yo + (this.y - this.yo) * f - interpPosY);
        float var15 = (float)(this.zo + (this.z - this.zo) * f - interpPosZ);
        FxPoseState.glTranslated(var13 + this.sx, var14 + this.sy, var15 + this.sz);
        FxPoseState.glRotatef(90.0F, this.side.getStepY(), -this.side.getStepX(), this.side.getStepZ());
        FxPoseState.glRotatef(this.rotation, 0.0F, 0.0F, 1.0F);
        if (this.side.getStepZ() > 0) {
            FxPoseState.glTranslated(0.0, 0.0, 0.505F);
            FxPoseState.glRotatef(180.0F, 0.0F, -1.0F, 0.0F);
        } else {
            FxPoseState.glTranslated(0.0, 0.0, -0.505F);
        }

        float var12 = this.particleScale;
        float var16 = 1.0F;
        wr.begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
        int i = 240;
        int j = i >> 16 & 65535;
        int k = i & 65535;
        wr.pos(-0.5 * var12, 0.5 * var12, 0.0)
            .tex(0.0, 1.0)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, this.alpha / 2.0F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(0.5 * var12, 0.5 * var12, 0.0)
            .tex(1.0, 1.0)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, this.alpha / 2.0F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(0.5 * var12, -0.5 * var12, 0.0)
            .tex(1.0, 0.0)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, this.alpha / 2.0F)
            .lightmap(j, k)
            .endVertex();
        wr.pos(-0.5 * var12, -0.5 * var12, 0.0)
            .tex(0.0, 0.0)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, this.alpha / 2.0F)
            .lightmap(j, k)
            .endVertex();
        FXBuffer.current().draw();
        FxPoseState.glDisable(3042);
        FxPoseState.glDepthMask(true);
        FxPoseState.glPopMatrix();
        FxPoseState.bindTexture(ParticleEngine.particleTexture);
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
