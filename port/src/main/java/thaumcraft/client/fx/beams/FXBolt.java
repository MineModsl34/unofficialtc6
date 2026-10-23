package thaumcraft.client.fx.beams;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import thaumcraft.client.fx.NativeGLE;
import java.util.ArrayList;
import java.util.Random;
import net.minecraft.client.Minecraft;
import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXBolt extends SourceParticle {
    float width = 0.0F;
    ArrayList<Vec3> points = new ArrayList<>();
    ArrayList<Float> pointsWidth = new ArrayList<>();
    float dr = 0.0F;
    long seed = 0L;
    private Entity targetEntity = null;
    private double tX = 0.0;
    private double tY = 0.0;
    private double tZ = 0.0;
    ResourceLocation beam = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/essentia.png");
    public float length = 1.0F;
    NativeGLE gle = new NativeGLE();

    public FXBolt(ClientLevel par1World, double x, double y, double z, double tx, double ty, double tz, float red, float green, float blue, float width) {
        super(par1World, x, y, z, 0.0, 0.0, 0.0);
        this.rCol = red;
        this.gCol = green;
        this.bCol = blue;
        this.setSize(0.02F, 0.02F);
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.tX = tx - x;
        this.tY = ty - y;
        this.tZ = tz - z;
        this.width = width;
        this.lifetime = 3;
        Vec3 vs = new Vec3(0.0, 0.0, 0.0);
        Vec3 ve = new Vec3(this.tX, this.tY, this.tZ);
        this.length = (float)(ve.length() * Math.PI);
        int steps = (int)this.length;
        this.points.add(vs);
        this.pointsWidth.add(width);
        this.dr = (float)(this.random.nextInt(50) * Math.PI);
        float ampl = 0.1F;

        for (int a = 1; a < steps - 1; a++) {
            float dist = a * (this.length / steps) + this.dr;
            double dx = this.tX / steps * a + FxMath.sin(dist / 4.0F) * ampl;
            double dy = this.tY / steps * a + FxMath.sin(dist / 3.0F) * ampl;
            double dz = this.tZ / steps * a + FxMath.sin(dist / 2.0F) * ampl;
            dx += (this.random.nextFloat() - this.random.nextFloat()) * 0.1F;
            dy += (this.random.nextFloat() - this.random.nextFloat()) * 0.1F;
            dz += (this.random.nextFloat() - this.random.nextFloat()) * 0.1F;
            Vec3 vp = new Vec3(dx, dy, dz);
            this.points.add(vp);
            this.pointsWidth.add(width);
        }

        this.pointsWidth.add(width);
        this.points.add(ve);
        this.seed = this.random.nextInt(1000);
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    private void calcSteps(float f) {
        Random rr = new Random(this.seed);
        this.points.clear();
        this.pointsWidth.clear();
        Vec3 vs = new Vec3(0.0, 0.0, 0.0);
        Vec3 ve = new Vec3(this.tX, this.tY, this.tZ);
        int steps = (int)this.length;
        this.points.add(vs);
        this.pointsWidth.add(this.width);
        float ampl = (this.age + f) / 10.0F;

        for (int a = 1; a < steps - 1; a++) {
            float dist = a * (this.length / steps) + this.dr;
            double dx = this.tX / steps * a + FxMath.sin(dist / 4.0F) * ampl;
            double dy = this.tY / steps * a + FxMath.sin(dist / 3.0F) * ampl;
            double dz = this.tZ / steps * a + FxMath.sin(dist / 2.0F) * ampl;
            dx += (rr.nextFloat() - rr.nextFloat()) * 0.1F;
            dy += (rr.nextFloat() - rr.nextFloat()) * 0.1F;
            dz += (rr.nextFloat() - rr.nextFloat()) * 0.1F;
            Vec3 vp = new Vec3(dx, dy, dz);
            this.points.add(vp);
            this.pointsWidth.add((rr.nextInt(4) == 0 ? 1.0F - this.age * 0.25F : 1.0F) * this.width);
        }

        this.pointsWidth.add(this.width);
        this.points.add(ve);
    }

    public void setRGB(float r, float g, float b) {
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
    }

    public void renderSource(FXBuffer wr, Entity entity, float f, float cosyaw, float cospitch, float sinyaw, float cossinpitch, float f5) {
        FXBuffer.current().draw();
        FxPoseState.glPushMatrix();
        double ePX = this.xo + (this.x - this.xo) * f - interpPosX;
        double ePY = this.yo + (this.y - this.yo) * f - interpPosY;
        double ePZ = this.zo + (this.z - this.zo) * f - interpPosZ;
        FxPoseState.glTranslated(ePX, ePY, ePZ);
        FxPoseState.bindTexture(this.beam);
        FxPoseState.glDepthMask(false);
        FxPoseState.glEnable(3042);
        FxPoseState.glBlendFunc(770, 1);
        FxPoseState.glDisable(2884);
        this.calcSteps(f);
        float alpha = FxMath.clamp(1.0F - (float)this.age / this.lifetime, 0.1F, 1.0F);
        if (this.points != null && this.points.size() > 2) {
            double[][] pp = new double[this.points.size()][3];
            float[][] colours = new float[this.points.size()][4];
            double[] radii = new double[this.points.size()];

            for (int a = 0; a < this.points.size(); a++) {
                pp[a][0] = this.points.get(a).x;
                pp[a][1] = this.points.get(a).y;
                pp[a][2] = this.points.get(a).z;
                colours[a][0] = this.rCol;
                colours[a][1] = this.gCol;
                colours[a][2] = this.bCol;
                colours[a][3] = alpha;
                radii[a] = this.pointsWidth.get(a) / 10.0F;
            }

            this.gle.set_POLYCYL_TESS(5);
            this.gle.gleSetJoinStyle(1042);
            this.gle.glePolyCone(pp.length, pp, colours, radii, 1.0F, 0.0F);

            for (int a = 0; a < this.points.size(); a++) {
                radii[a] /= 3.0;
            }

            this.gle.glePolyCone(pp.length, pp, colours, radii, 1.0F, 0.0F);
        }

        FxPoseState.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        FxPoseState.glEnable(2884);
        FxPoseState.glBlendFunc(770, 771);
        FxPoseState.glDisable(3042);
        FxPoseState.glDepthMask(true);
        FxPoseState.glPopMatrix();
        FxPoseState.bindTexture(ParticleEngine.particleTexture);
        wr.begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
    }
}
