package thaumcraft.client.fx.other;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import thaumcraft.client.fx.NativeGLE;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.multiplayer.ClientLevel;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.fx.FxQuat;

public class FXVoidStream extends SourceParticle {
    private double targetX;
    private double targetY;
    private double targetZ;
    private double startX;
    private double startY;
    private double startZ;
    private int seed = 0;
    public int length = 20;
    private static final ResourceLocation starsTexture = ResourceLocation.parse("textures/entity/end_portal.png");
    NativeGLE gle = new NativeGLE();
    int layer = 1;
    double[][] points;
    float[][] colours;
    double[] radii;
    int growing = -1;
    ArrayList<FxQuat> vecs = new ArrayList<>();

    public FXVoidStream(ClientLevel w, double par2, double par4, double par6, double tx, double ty, double tz, int seed, float scale) {
        super(w, par2, par4, par6, 0.0, 0.0, 0.0);
        this.particleScale = (float)(scale * (1.0 + this.random.nextGaussian() * 0.15F));
        this.length = 40;
        this.seed = seed;
        this.targetX = tx;
        this.targetY = ty;
        this.targetZ = tz;
        double dx = tx - this.x;
        double dy = ty - this.y;
        double dz = tz - this.z;
        int base = (int)(FxMath.sqrt(dx * dx + dy * dy + dz * dz) * 21.0F);
        if (base < 1) {
            base = 1;
        }

        this.lifetime = base * 2;
        this.xd = FxMath.sin(seed / 4.0F) * 0.025F;
        this.yd = FxMath.sin(seed / 3.0F) * 0.025F;
        this.zd = FxMath.sin(seed / 2.0F) * 0.025F;
        this.gravity = 0.2F;
        this.vecs.add(new FxQuat(0.0, 0.0, 0.0, 0.001));
        this.vecs.add(new FxQuat(0.0, 0.0, 0.0, 0.001));
        this.startX = this.x;
        this.startY = this.y;
        this.startZ = this.z;
    }

    public void renderSource(FXBuffer wr, Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
        FXBuffer.current().draw();
        FxPoseState.glPushMatrix();
        double ePX = this.startX - interpPosX;
        double ePY = this.startY - interpPosY;
        double ePZ = this.startZ - interpPosZ;
        FxPoseState.glTranslated(ePX, ePY, ePZ);

        for (int q = 0; q <= 1; q++) {
            if (q < 1) {
                FxPoseState.depthMask(false);
            }

            FxPoseState.glBlendFunc(770, q < 1 ? 1 : 771);
            if (this.points != null && this.points.length > 2) {
                FxPoseState.bindTexture(starsTexture);
                FxPoseState.useEnder(true);
                double[] r2 = new double[this.radii.length];
                int ri = 0;
                float m = (1.5F - q) / 1.0F;

                for (double d : this.radii) {
                    r2[ri] = this.radii[ri] * m;
                    ri++;
                }

                this.gle.set_POLYCYL_TESS(3);
                this.gle.set__ROUND_TESS_PIECES(1);
                this.gle.gleSetJoinStyle(1042);
                this.gle
                    .glePolyCone(
                        this.points.length, this.points, this.colours, r2, 0.075F, this.growing < 0 ? 0.0F : 0.075F * (this.age - this.growing + f)
                    );
                FxPoseState.useEnder(false);
            }

            if (q < 1) {
                FxPoseState.depthMask(true);
            }
        }

        FxPoseState.depthMask(false);
        FxPoseState.glBlendFunc(770, 771);
        FxPoseState.glPopMatrix();
        FxPoseState.bindTexture(ParticleEngine.particleTexture);
        wr.begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
    }

    public void setFXLayer(int l) {
        this.layer = l;
    }

    public int getFXLayer() {
        return this.layer;
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ < this.lifetime && this.length >= 1) {
            this.yd = this.yd + 0.01 * this.gravity;
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.985;
            this.yd *= 0.985;
            this.zd *= 0.985;
            this.xd = FxMath.clamp((float)this.xd, -0.04F, 0.04F);
            this.yd = FxMath.clamp((float)this.yd, -0.04F, 0.04F);
            this.zd = FxMath.clamp((float)this.zd, -0.04F, 0.04F);
            double dx = this.targetX - this.x;
            double dy = this.targetY - this.y;
            double dz = this.targetZ - this.z;
            double d13 = 0.01;
            double d11 = FxMath.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= d11;
            dy /= d11;
            dz /= d11;
            this.xd = this.xd + (dx * (d13 / Math.min(1.0, d11)) + this.random.nextGaussian() * 0.015F);
            this.yd = this.yd + (dy * (d13 / Math.min(1.0, d11)) + this.random.nextGaussian() * 0.015F);
            this.zd = this.zd + (dz * (d13 / Math.min(1.0, d11)) + this.random.nextGaussian() * 0.015F);
            float scale = this.particleScale * (0.75F + FxMath.sin((this.seed + this.age) / 2.0F) * 0.25F);
            if (d11 < 0.5) {
                float f = FxMath.sin((float)(d11 * (Math.PI / 2)));
                scale *= f;
                this.particleScale *= f;
            }

            if (this.particleScale > 0.001) {
                this.vecs.add(new FxQuat(scale, this.x - this.startX, this.y - this.startY, this.z - this.startZ));
            } else {
                if (this.growing < 0) {
                    this.growing = this.age;
                }

                this.length--;
            }

            if (this.vecs.size() > this.length) {
                this.vecs.remove(0);
            }

            this.points = new double[this.vecs.size()][3];
            this.colours = new float[this.vecs.size()][4];
            this.radii = new double[this.vecs.size()];
            int c = this.vecs.size();

            for (FxQuat v : this.vecs) {
                c--;
                float variance = 1.0F + FxMath.sin((c + this.age) / 3.0F) * 0.2F;
                float xx = FxMath.sin((c + this.age) / 6.0F) * 0.01F;
                float yy = FxMath.sin((c + this.age) / 7.0F) * 0.01F;
                float zz = FxMath.sin((c + this.age) / 8.0F) * 0.01F;
                this.points[c][0] = v.x + xx;
                this.points[c][1] = v.y + yy;
                this.points[c][2] = v.z + zz;
                this.radii[c] = v.s * variance;
                if (c > this.vecs.size() - 10) {
                    this.radii[c] = this.radii[c] * FxMath.cos((float)((c - (this.vecs.size() - 12)) / 10.0F * (Math.PI / 2)));
                }

                if (c == 0) {
                    this.radii[c] = 0.0;
                } else if (c == 1) {
                    this.radii[c] = 0.0;
                } else if (c == 2) {
                    this.radii[c] = (this.particleScale * 0.5 + this.radii[c]) / 2.0;
                } else if (c == 3) {
                    this.radii[c] = (this.particleScale + this.radii[c]) / 2.0;
                } else if (c == 4) {
                    this.radii[c] = (this.particleScale + this.radii[c] * 2.0) / 3.0;
                }

                this.colours[c][0] = 1.0F;
                this.colours[c][1] = 1.0F;
                this.colours[c][2] = 1.0F;
                this.colours[c][3] = 1.0F;
            }

            if (this.vecs.size() > 2 && this.random.nextBoolean()) {
                int q = this.random.nextInt(3);
                if (this.random.nextBoolean()) {
                    q = this.vecs.size() - 2;
                }
            }
        } else {
            this.remove();
        }
    }

    public void setGravity(float value) {
        this.gravity = value;
    }
}
