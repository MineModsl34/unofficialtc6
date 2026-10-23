package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXGeneric extends SourceParticle {
    boolean doneFrames = false;
    boolean flipped = false;
    double windX;
    double windZ;
    int layer = 0;
    float dr = 0.0F;
    float dg = 0.0F;
    float db = 0.0F;
    boolean loop = false;
    float rotationSpeed = 0.0F;
    int startParticle = 0;
    int numParticles = 1;
    int particleInc = 1;
    float[] scaleKeys = new float[]{1.0F};
    float[] scaleFrames = new float[]{0.0F};
    float[] alphaKeys = new float[]{1.0F};
    float[] alphaFrames = new float[]{0.0F};
    double slowDown = 0.98F;
    float randomX;
    float randomY;
    float randomZ;
    int[] finalFrames = null;
    boolean angled = false;
    float angleYaw;
    float anglePitch;
    int gridSize = 64;

    public FXGeneric(ClientLevel level, double x, double y, double z, double xx, double yy, double zz) {
        super(level, x, y, z, xx, yy, zz);
        this.setSize(0.1F, 0.1F);
        this.setPos(x, y, z);
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.particleTextureJitterX = 0.0F;
        this.particleTextureJitterY = 0.0F;
        this.xd = xx;
        this.yd = yy;
        this.zd = zz;
    }

    public FXGeneric(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.setSize(0.1F, 0.1F);
        this.setPos(x, y, z);
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.particleTextureJitterX = 0.0F;
        this.particleTextureJitterY = 0.0F;
    }

    void calculateFrames() {
        this.doneFrames=true;
        this.alphaFrames=thaumcraft.client.fx.FxAnimationRules.frames(lifetime,alphaKeys);
        this.scaleFrames=thaumcraft.client.fx.FxAnimationRules.frames(lifetime,scaleKeys);
    }

    public void tick() {
        if (!this.doneFrames) {
            this.calculateFrames();
        }

        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }

        this.oRoll = this.roll;
        this.roll = this.roll + (float) Math.PI * this.rotationSpeed * 2.0F;
        this.yd = this.yd - 0.04 * this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd = this.xd * this.slowDown;
        this.yd = this.yd * this.slowDown;
        this.zd = this.zd * this.slowDown;
        this.xd = this.xd + this.level.random.nextGaussian() * this.randomX;
        this.yd = this.yd + this.level.random.nextGaussian() * this.randomY;
        this.zd = this.zd + this.level.random.nextGaussian() * this.randomZ;
        this.xd = this.xd + this.windX;
        this.zd = this.zd + this.windZ;
        if (this.onGround && this.slowDown != 1.0) {
            this.xd *= 0.7F;
            this.zd *= 0.7F;
        }
    }

    public void renderSource(FXBuffer wr, Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
        if(!doneFrames)calculateFrames();
        this.setParticleTextureIndex(thaumcraft.client.fx.FxAnimationRules.textureFrame(age,lifetime,startParticle,numParticles,particleInc,loop,finalFrames));
        this.alpha = this.alphaFrames.length <= 0 ? 0.0F : this.alphaFrames[Math.min(this.age, this.alphaFrames.length - 1)];
        this.particleScale = this.scaleFrames.length <= 0 ? 0.0F : this.scaleFrames[Math.min(this.age, this.scaleFrames.length - 1)];
        this.draw(wr, entity, f, f1, f2, f3, f4, f5);
    }

    public boolean isFlipped() {
        return this.flipped;
    }

    public void setFlipped(boolean flip) {
        this.flipped = flip;
    }

    public void draw(
        FXBuffer wr, Entity entityIn, float partialTicks, float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ
    ) {
        float tx1 = (float)this.particleTextureIndexX / this.gridSize;
        float tx2 = tx1 + 1.0F / this.gridSize;
        float ty1 = (float)this.particleTextureIndexY / this.gridSize;
        float ty2 = ty1 + 1.0F / this.gridSize;
        float ts = 0.1F * this.particleScale;
        if (this.particleTexture != null) {
            tx1 = this.particleTexture.getU0();
            tx2 = this.particleTexture.getU1();
            ty1 = this.particleTexture.getV0();
            ty2 = this.particleTexture.getV1();
        }

        if (this.flipped) {
            float t = tx1;
            tx1 = tx2;
            tx2 = t;
        }

        float fs = FxMath.clamp((this.age + partialTicks) / this.lifetime, 0.0F, 1.0F);
        float pr = this.rCol + (this.dr - this.rCol) * fs;
        float pg = this.gCol + (this.dg - this.gCol) * fs;
        float pb = this.bCol + (this.db - this.bCol) * fs;
        int i = this.getLightColor(partialTicks);
        int j = i >> 16 & 65535;
        int k = i & 65535;
        float f5 = (float)(this.xo + (this.x - this.xo) * partialTicks - interpPosX);
        float f6 = (float)(this.yo + (this.y - this.yo) * partialTicks - interpPosY);
        float f7 = (float)(this.zo + (this.z - this.zo) * partialTicks - interpPosZ);
        if (this.angled) {
            FXBuffer.current().draw();
            FxPoseState.glPushMatrix();
            FxPoseState.glTranslated(f5, f6, f7);
            FxPoseState.glRotatef(-this.angleYaw + 90.0F, 0.0F, 1.0F, 0.0F);
            FxPoseState.glRotatef(this.anglePitch + 90.0F, 1.0F, 0.0F, 0.0F);
            if (this.roll != 0.0F) {
                float f8 = this.roll + (this.roll - this.oRoll) * partialTicks;
                FxPoseState.glRotated(f8 * (180.0 / Math.PI), 0.0, 0.0, 1.0);
            }

            wr.begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
            wr.pos(-ts, -ts, 0.0).tex(tx2, ty2).color(pr, pg, pb, this.alpha).lightmap(j, k).endVertex();
            wr.pos(-ts, ts, 0.0).tex(tx2, ty1).color(pr, pg, pb, this.alpha).lightmap(j, k).endVertex();
            wr.pos(ts, ts, 0.0).tex(tx1, ty1).color(pr, pg, pb, this.alpha).lightmap(j, k).endVertex();
            wr.pos(ts, -ts, 0.0).tex(tx1, ty2).color(pr, pg, pb, this.alpha).lightmap(j, k).endVertex();
            FXBuffer.current().draw();
            FxPoseState.glPopMatrix();
            wr.begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
        } else {
            Vec3[] avec3d = new Vec3[]{
                new Vec3(-rotationX * ts - rotationXY * ts, -rotationZ * ts, -rotationYZ * ts - rotationXZ * ts),
                new Vec3(-rotationX * ts + rotationXY * ts, rotationZ * ts, -rotationYZ * ts + rotationXZ * ts),
                new Vec3(rotationX * ts + rotationXY * ts, rotationZ * ts, rotationYZ * ts + rotationXZ * ts),
                new Vec3(rotationX * ts - rotationXY * ts, -rotationZ * ts, rotationYZ * ts - rotationXZ * ts)
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

            wr.pos(f5 + avec3d[0].x, f6 + avec3d[0].y, f7 + avec3d[0].z)
                .tex(tx2, ty2)
                .color(pr, pg, pb, this.alpha)
                .lightmap(j, k)
                .endVertex();
            wr.pos(f5 + avec3d[1].x, f6 + avec3d[1].y, f7 + avec3d[1].z)
                .tex(tx2, ty1)
                .color(pr, pg, pb, this.alpha)
                .lightmap(j, k)
                .endVertex();
            wr.pos(f5 + avec3d[2].x, f6 + avec3d[2].y, f7 + avec3d[2].z)
                .tex(tx1, ty1)
                .color(pr, pg, pb, this.alpha)
                .lightmap(j, k)
                .endVertex();
            wr.pos(f5 + avec3d[3].x, f6 + avec3d[3].y, f7 + avec3d[3].z)
                .tex(tx1, ty2)
                .color(pr, pg, pb, this.alpha)
                .lightmap(j, k)
                .endVertex();
        }
    }

    public void setWind(double d) {
        int m = this.level.getMoonPhase();
        Vec3 vsource = new Vec3(0.0, 0.0, 0.0);
        Vec3 vtar = new Vec3(0.1, 0.0, 0.0);
        vtar = FxMath.rotateAroundY(vtar, m * (40 + this.level.random.nextInt(10)) / 180.0F * (float) Math.PI);
        Vec3 vres = vsource.add(vtar.x, vtar.y, vtar.z);
        this.windX = vres.x * d;
        this.windZ = vres.z * d;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public void setRBGColorF(float particleRedIn, float particleGreenIn, float particleBlueIn) {
        super.setRBGColorF(particleRedIn, particleGreenIn, particleBlueIn);
        this.dr = particleRedIn;
        this.dg = particleGreenIn;
        this.db = particleBlueIn;
    }

    public void setRBGColorF(float particleRedIn, float particleGreenIn, float particleBlueIn, float r2, float g2, float b2) {
        super.setRBGColorF(particleRedIn, particleGreenIn, particleBlueIn);
        this.dr = r2;
        this.dg = g2;
        this.db = b2;
    }

    public int getFXLayer() {
        return this.layer;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public void setRotationSpeed(float rot) {
        this.rotationSpeed = (float)(rot * 0.017453292519943);
    }

    public void setRotationSpeed(float start, float rot) {
        this.roll = (float)(start * Math.PI * 2.0);
        this.rotationSpeed = (float)(rot * 0.017453292519943);
    }

    public void setMaxAge(int max) {
        this.lifetime = max;
    }

    public void setParticles(int startParticle, int numParticles, int particleInc) {
        this.numParticles = numParticles;
        this.particleInc = particleInc;
        this.startParticle = startParticle;
        this.setParticleTextureIndex(startParticle);
    }

    public void setParticle(int startParticle) {
        this.numParticles = 1;
        this.particleInc = 1;
        this.startParticle = startParticle;
        this.setParticleTextureIndex(startParticle);
    }

    public void setScale(float... scale) {
        this.particleScale = scale[0];
        this.scaleKeys = scale;
    }

    public void setAlphaF(float... a1) {
        super.setAlphaF(a1[0]);
        this.alphaKeys = a1;
    }

    public void setAlphaF(float a1) {
        super.setAlphaF(a1);
        this.alphaKeys = new float[1];
        this.alphaKeys[0] = a1;
    }

    public void setSlowDown(double slowDown) {
        this.slowDown = slowDown;
    }

    public void setRandomMovementScale(float x, float y, float z) {
        this.randomX = x;
        this.randomY = y;
        this.randomZ = z;
    }

    public void setFinalFrames(int... frames) {
        this.finalFrames = frames;
    }

    public void setAngles(float yaw, float pitch) {
        this.angleYaw = yaw;
        this.anglePitch = pitch;
        this.angled = true;
    }

    public void setGravity(float g) {
        this.gravity = g;
    }

    public void setParticleTextureIndex(int index) {
        if (index < 0) {
            index = 0;
        }

        this.particleTextureIndexX = index % this.gridSize;
        this.particleTextureIndexY = index / this.gridSize;
    }

    public void setGridSize(int gridSize) {
        this.gridSize = gridSize;
    }

    public void setNoClip(boolean clip) {
        this.hasPhysics = clip;
    }
}
