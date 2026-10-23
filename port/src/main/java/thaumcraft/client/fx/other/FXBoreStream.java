package thaumcraft.client.fx.other;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import thaumcraft.client.fx.NativeGLE;
import java.awt.Color;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.fx.FxQuat;

public class FXBoreStream extends SourceParticle {
    private Entity target;
    private double startX;
    private double startY;
    private double startZ;
    private int count = 0;
    public int length = 5;
    private String key = "";
    private BlockPos startPos = null;
    NativeGLE gle = new NativeGLE();
    private static final ResourceLocation TEX0 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/essentia.png");
    int layer = 1;
    double[][] points;
    float[][] colours;
    double[] radii;
    int growing = -1;
    ArrayList<FxQuat> vecs = new ArrayList<>();

    public FXBoreStream(ClientLevel w, double par2, double par4, double par6, Entity target, int count, int color, float scale, int extend, double my) {
        super(w, par2, par4, par6, 0.0, 0.0, 0.0);
        this.particleScale = (float)(scale * (1.0 + this.random.nextGaussian() * 0.15F));
        this.length = Math.max(5, extend);
        this.count = count;
        this.target = target;
        this.lifetime = this.length * 10;
        this.xd = FxMath.sin(count / 4.0F) * 0.15F;
        this.yd = my + FxMath.sin(count / 3.0F) * 0.15F;
        this.zd = FxMath.sin(count / 2.0F) * 0.15F;
        Color c = new Color(color);
        this.rCol = c.getRed() / 255.0F;
        this.gCol = c.getGreen() / 255.0F;
        this.bCol = c.getBlue() / 255.0F;
        this.gravity = 0.2F;
        this.vecs.add(new FxQuat(0.0, 0.0, 0.0, 0.001));
        this.vecs.add(new FxQuat(0.0, 0.0, 0.0, 0.001));
        this.startX = this.x;
        this.startY = this.y;
        this.startZ = this.z;
        this.startPos = BlockPos.containing(this.startX, this.startY, this.startZ);
    }

    public void renderSource(FXBuffer wr, Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
        FXBuffer.current().draw();
        FxPoseState.glPushMatrix();
        double ePX = this.startX - interpPosX;
        double ePY = this.startY - interpPosY;
        double ePZ = this.startZ - interpPosZ;
        FxPoseState.glTranslated(ePX, ePY, ePZ);
        if (this.points != null && this.points.length > 2) {
            FxPoseState.bindTexture(TEX0);
            this.gle.set_POLYCYL_TESS(8);
            this.gle.set__ROUND_TESS_PIECES(1);
            this.gle.gleSetJoinStyle(1042);
            this.gle
                .glePolyCone(
                    this.points.length,
                    this.points,
                    this.colours,
                    this.radii,
                    0.075F,
                    this.growing < 0 ? 0.0F : 0.075F * (this.age - this.growing + f)
                );
        }

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
            double dx = this.target.getX() - this.x;
            double dy = this.target.getY() + this.target.getEyeHeight() - this.y;
            double dz = this.target.getZ() - this.z;
            double d11 = FxMath.sqrt(dx * dx + dy * dy + dz * dz);
            double clamp = d11 / 10.0;
            this.xd = FxMath.clamp((float)this.xd, -clamp, clamp);
            this.yd = FxMath.clamp((float)this.yd, -clamp, clamp);
            this.zd = FxMath.clamp((float)this.zd, -clamp, clamp);
            dx /= d11;
            dy /= d11;
            dz /= d11;
            this.xd = this.xd + dx * (clamp / Math.min(1.0, d11));
            this.yd = this.yd + dy * (clamp / Math.min(1.0, d11));
            this.zd = this.zd + dz * (clamp / Math.min(1.0, d11));
            float scale = this.particleScale * (0.75F + FxMath.sin((this.count + this.age) / 2.0F) * 0.25F);
            if (d11 < 1.0) {
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
                float xx = FxMath.sin((c + this.age) / 6.0F) * 0.03F;
                float yy = FxMath.sin((c + this.age) / 7.0F) * 0.03F;
                float zz = FxMath.sin((c + this.age) / 8.0F) * 0.03F;
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

                float v2 = 1.0F - FxMath.sin((c + this.age) / 2.0F) * 0.1F;
                this.colours[c][0] = this.rCol * v2;
                this.colours[c][1] = this.gCol * v2;
                this.colours[c][2] = this.bCol * v2;
                this.colours[c][3] = 1.0F;
            }
        } else {
            this.remove();
        }
    }

    public void setGravity(float value) {
        this.gravity = value;
    }
}
