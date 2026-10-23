package thaumcraft.client.fx.other;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import thaumcraft.client.fx.NativeGLE;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.fx.FxQuat;
import thaumcraft.common.blocks.essentia.BlockEssentiaTransport;

public class FXEssentiaStream extends SourceParticle {
    private double targetX;
    private double targetY;
    private double targetZ;
    private double startX;
    private double startY;
    private double startZ;
    private int count = 0;
    public int length = 20;
    private String key = "";
    private BlockPos startPos = null;
    private BlockPos endPos = null;
    static HashMap<String, FXEssentiaStream> pt = new HashMap<>();
    public static void clearTrails() { pt.clear(); }
    NativeGLE gle = new NativeGLE();
    private static final ResourceLocation TEX0 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/essentia.png");
    int layer = 1;
    double[][] points;
    float[][] colours;
    double[] radii;
    int growing = -1;
    ArrayList<FxQuat> vecs = new ArrayList<>();

    public FXEssentiaStream(
        ClientLevel w, double par2, double par4, double par6, double tx, double ty, double tz, int count, int color, float scale, int extend, double my
    ) {
        super(w, par2, par4, par6, 0.0, 0.0, 0.0);
        this.particleScale = (float)(scale * (1.0 + this.random.nextGaussian() * 0.15F));
        this.length = Math.max(20, extend);
        this.count = count;
        this.targetX = tx;
        this.targetY = ty;
        this.targetZ = tz;
        BlockPos bp1 = BlockPos.containing(this.x, this.y, this.z);
        BlockPos bp2 = BlockPos.containing(this.targetX, this.targetY, this.targetZ);
        BlockState bs = w.getBlockState(bp1);
        if (bs.getBlock() instanceof BlockEssentiaTransport) {
            Direction f = bs.getValue(BlockEssentiaTransport.FACING);
            this.x = this.x + f.getStepX() * 0.05F;
            this.y = this.y + f.getStepY() * 0.05F;
            this.z = this.z + f.getStepZ() * 0.05F;
        }

        double dx = tx - this.x;
        double dy = ty - this.y;
        double dz = tz - this.z;
        int base = (int)(FxMath.sqrt(dx * dx + dy * dy + dz * dz) * 21.0F);
        if (base < 1) {
            base = 1;
        }

        this.lifetime = base;
        String k = w.dimension().location() + ":" + bp1.asLong() + "" + bp2.asLong() + "" + color;
        if (pt.containsKey(k)) {
            FXEssentiaStream trail2 = pt.get(k);
            if (!trail2.removed && trail2.vecs.size() < trail2.length) {
                trail2.length = trail2.length + Math.max(extend, 5);
                trail2.lifetime = trail2.lifetime + Math.max(extend, 5);
                this.lifetime = 0;
            }
        }

        if (this.lifetime > 0) {
            pt.put(k, this);
            this.key = k;
        }

        this.xd = FxMath.sin(count / 4.0F) * 0.015F;
        this.yd = my + FxMath.sin(count / 3.0F) * 0.015F;
        this.zd = FxMath.sin(count / 2.0F) * 0.015F;
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
        this.endPos = bp2;
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
            this.xd = FxMath.clamp((float)this.xd, -0.05F, 0.05F);
            this.yd = FxMath.clamp((float)this.yd, -0.05F, 0.05F);
            this.zd = FxMath.clamp((float)this.zd, -0.05F, 0.05F);
            double dx = this.targetX - this.x;
            double dy = this.targetY - this.y;
            double dz = this.targetZ - this.z;
            double d13 = 0.01;
            double d11 = FxMath.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= d11;
            dy /= d11;
            dz /= d11;
            this.xd = this.xd + dx * (d13 / Math.min(1.0, d11));
            this.yd = this.yd + dy * (d13 / Math.min(1.0, d11));
            this.zd = this.zd + dz * (d13 / Math.min(1.0, d11));
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
                FXDispatcher.INSTANCE
                    .essentiaDropFx(
                        this.targetX + this.random.nextGaussian() * 0.075F,
                        this.targetY + this.random.nextGaussian() * 0.075F,
                        this.targetZ + this.random.nextGaussian() * 0.075F,
                        this.rCol,
                        this.gCol,
                        this.bCol,
                        0.5F
                    );
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

            if (this.vecs.size() > 2 && this.random.nextBoolean()) {
                int q = this.random.nextInt(3);
                if (this.random.nextBoolean()) {
                    q = this.vecs.size() - 2;
                }

                FXDispatcher.INSTANCE
                    .essentiaDropFx(
                        this.vecs.get(q).x + this.startX,
                        this.vecs.get(q).y + this.startY,
                        this.vecs.get(q).z + this.startZ,
                        this.rCol,
                        this.gCol,
                        this.bCol,
                        0.5F
                    );
            }
        } else {
            this.remove();
            if (pt.containsKey(this.key) && pt.get(this.key).removed) {
                pt.remove(this.key);
            }
        }
    }

    public void setGravity(float value) {
        this.gravity = value;
    }
}
