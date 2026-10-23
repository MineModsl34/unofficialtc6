package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXFireMote extends SourceParticle {
    float baseScale = 0.0F;
    float baseAlpha = 1.0F;
    int glowlayer = 0;

    public FXFireMote(ClientLevel worldIn, double x, double y, double z, double vx, double vy, double vz, float r, float g, float b, float scale, int layer) {
        super(worldIn, x, y, z, 0.0, 0.0, 0.0);
        float colorR = r;
        float colorG = g;
        float colorB = b;
        if (colorR > 1.0) {
            colorR /= 255.0F;
        }

        if (colorG > 1.0) {
            colorG /= 255.0F;
        }

        if (colorB > 1.0) {
            colorB /= 255.0F;
        }

        this.glowlayer = layer;
        this.setRBGColorF(colorR, colorG, colorB);
        this.lifetime = 16;
        this.particleScale = scale;
        this.baseScale = scale;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.roll = (float) (Math.PI * 2);
        this.setParticleTextureIndex(7);
    }

    public void setParticleTextureIndex(int particleTextureIndex) {
        this.particleTextureIndexX = particleTextureIndex % 64;
        this.particleTextureIndexY = particleTextureIndex / 64;
    }

    public void renderSource(
        FXBuffer worldRendererIn,
        Entity entityIn,
        float partialTicks,
        float rotationX,
        float rotationZ,
        float rotationYZ,
        float rotationXY,
        float rotationXZ
    ) {
        float f = this.particleTextureIndexX / 64.0F;
        float f1 = f + 0.015625F;
        float f2 = this.particleTextureIndexY / 64.0F;
        float f3 = f2 + 0.015625F;
        float f4 = 0.1F * this.particleScale;
        if (this.particleTexture != null) {
            f = this.particleTexture.getU0();
            f1 = this.particleTexture.getU1();
            f2 = this.particleTexture.getV0();
            f3 = this.particleTexture.getV1();
        }

        float f5 = (float)(this.xo + (this.x - this.xo) * partialTicks - interpPosX);
        float f6 = (float)(this.yo + (this.y - this.yo) * partialTicks - interpPosY);
        float f7 = (float)(this.zo + (this.z - this.zo) * partialTicks - interpPosZ);
        int i = this.getLightColor(partialTicks);
        int j = i >> 16 & 65535;
        int k = i & 65535;
        Vec3[] avec3d = new Vec3[]{
            new Vec3(-rotationX * f4 - rotationXY * f4, -rotationZ * f4, -rotationYZ * f4 - rotationXZ * f4),
            new Vec3(-rotationX * f4 + rotationXY * f4, rotationZ * f4, -rotationYZ * f4 + rotationXZ * f4),
            new Vec3(rotationX * f4 + rotationXY * f4, rotationZ * f4, rotationYZ * f4 + rotationXZ * f4),
            new Vec3(rotationX * f4 - rotationXY * f4, -rotationZ * f4, rotationYZ * f4 - rotationXZ * f4)
        };
        if (this.roll != 0.0F) {
            float f8 = this.roll + (this.roll - this.oRoll) * partialTicks;
            float f9 = FxMath.cos(f8 * 0.5F);
            float f10 = FxMath.sin(f8 * 0.5F) * (float)cameraViewDir.x;
            float f11 = FxMath.sin(f8 * 0.5F) * (float)cameraViewDir.y;
            float f12 = FxMath.sin(f8 * 0.5F) * (float)cameraViewDir.z;
            Vec3 vec3d = new Vec3(f10, f11, f12);

            for (int l = 0; l < 4; l++) {
                avec3d[l] = vec3d.scale(2.0 * avec3d[l].dot(vec3d))
                    .add(avec3d[l].scale(f9 * f9 - vec3d.dot(vec3d)))
                    .add(vec3d.cross(avec3d[l]).scale(2.0F * f9));
            }
        }

        worldRendererIn.pos(f5 + avec3d[0].x, f6 + avec3d[0].y, f7 + avec3d[0].z)
            .tex(f1, f3)
            .color(this.rCol, this.gCol, this.bCol, this.alpha * this.baseAlpha)
            .lightmap(j, k)
            .endVertex();
        worldRendererIn.pos(f5 + avec3d[1].x, f6 + avec3d[1].y, f7 + avec3d[1].z)
            .tex(f1, f2)
            .color(this.rCol, this.gCol, this.bCol, this.alpha * this.baseAlpha)
            .lightmap(j, k)
            .endVertex();
        worldRendererIn.pos(f5 + avec3d[2].x, f6 + avec3d[2].y, f7 + avec3d[2].z)
            .tex(f, f2)
            .color(this.rCol, this.gCol, this.bCol, this.alpha * this.baseAlpha)
            .lightmap(j, k)
            .endVertex();
        worldRendererIn.pos(f5 + avec3d[3].x, f6 + avec3d[3].y, f7 + avec3d[3].z)
            .tex(f, f3)
            .color(this.rCol, this.gCol, this.bCol, this.alpha * this.baseAlpha)
            .lightmap(j, k)
            .endVertex();
    }

    public int getLightColor(float pTicks) {
        return 255;
    }

    public int getFXLayer() {
        return this.glowlayer;
    }

    public void tick() {
        super.tick();
        if (this.level.random.nextInt(6) == 0) {
            this.age++;
        }

        if (this.age >= this.lifetime) {
            this.remove();
        }

        float lifespan = (float)this.age / this.lifetime;
        this.particleScale = this.baseScale - this.baseScale * lifespan;
        this.baseAlpha = 1.0F - lifespan;
        this.oRoll = this.roll++;
    }
}
