package thaumcraft.client.fx.beams;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.fx.particles.FXGeneric;

public class FXArc extends SourceParticle {
    public int particle = 16;
    ArrayList<Vec3> points = new ArrayList<>();
    private Entity targetEntity = null;
    private double tX = 0.0;
    private double tY = 0.0;
    private double tZ = 0.0;
    ResourceLocation beam = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/beamh.png");
    public int blendmode = 1;
    public float length = 1.0F;

    public FXArc(ClientLevel par1World, double x, double y, double z, double tx, double ty, double tz, float red, float green, float blue, double hg) {
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
        this.lifetime = 3;
        double xx = 0.0;
        double yy = 0.0;
        double zz = 0.0;
        double gravity = 0.115;
        double noise = 0.25;
        Vec3 vs = new Vec3(xx, yy, zz);
        Vec3 ve = new Vec3(this.tX, this.tY, this.tZ);
        Vec3 vc = new Vec3(xx, yy, zz);
        this.length = (float)ve.length();
        Vec3 vv = FxMath.calculateVelocity(vs, ve, hg, gravity);
        double l = FxMath.distanceSquared3d(new Vec3(0.0, 0.0, 0.0), vv);
        this.points.add(vs);

        for (int c = 0; FxMath.distanceSquared3d(ve, vc) > l && c < 50; c++) {
            Vec3 vt = vc.add(vv.x, vv.y, vv.z);
            vc = new Vec3(vt.x, vt.y, vt.z);
            vt = vt.add(
                (this.random.nextDouble() - this.random.nextDouble()) * noise,
                (this.random.nextDouble() - this.random.nextDouble()) * noise,
                (this.random.nextDouble() - this.random.nextDouble()) * noise
            );
            this.points.add(vt);
            FXGeneric fb = new FXGeneric(par1World, x + vt.x, y + vt.y, z + vt.z, 0.0, 0.0, 0.0);
            int age = 30 + this.random.nextInt(20);
            fb.setMaxAge(age);
            fb.setRBGColorF(
                FxMath.clamp(red * 3.0F, 0.0F, 1.0F),
                FxMath.clamp(green * 3.0F, 0.0F, 1.0F),
                FxMath.clamp(blue * 3.0F, 0.0F, 1.0F),
                this.random.nextFloat(),
                this.random.nextFloat(),
                this.random.nextFloat()
            );
            float[] alphas = new float[6 + this.random.nextInt(age / 3)];

            for (int a = 1; a < alphas.length - 1; a++) {
                alphas[a] = this.random.nextFloat();
            }

            alphas[0] = 1.0F;
            fb.setAlphaF(alphas);
            boolean sp = this.random.nextFloat() < 0.2;
            fb.setParticles(sp ? 320 : 512, 16, 1);
            fb.setLoop(true);
            fb.setGravity(sp ? 0.0F : 0.125F);
            fb.setScale(0.5F, 0.125F);
            fb.setLayer(0);
            fb.setSlowDown(0.995);
            fb.setRandomMovementScale(0.0025F, 0.001F, 0.0025F);
            ParticleEngine.addEffectWithDelay(par1World, fb, 2 + this.random.nextInt(3));
            vv = vv.subtract(0.0, gravity / 1.9, 0.0);
        }

        this.points.add(ve);
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    public void setRGB(float r, float g, float b) {
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
    }

    public void renderSource(FXBuffer wr, Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
        FXBuffer.current().draw();
        FxPoseState.glPushMatrix();
        double ePX = this.xo + (this.x - this.xo) * f - interpPosX;
        double ePY = this.yo + (this.y - this.yo) * f - interpPosY;
        double ePZ = this.zo + (this.z - this.zo) * f - interpPosZ;
        FxPoseState.glTranslated(ePX, ePY, ePZ);
        float size = 0.125F;
        FxPoseState.bindTexture(this.beam);
        FxPoseState.glDepthMask(false);
        FxPoseState.glEnable(3042);
        FxPoseState.glBlendFunc(770, 1);
        FxPoseState.glDisable(2884);
        int i = 220;
        int j = i >> 16 & 65535;
        int k = i & 65535;
        float alpha = 1.0F - (this.age + f) / this.lifetime;
        wr.begin(5, FXBuffer.POSITION_TEX_LMAP_COLOR);
        float f9 = 0.0F;
        float f10 = 1.0F;

        for (int c = 0; c < this.points.size(); c++) {
            Vec3 v = this.points.get(c);
            float f13 = c / this.length;
            double dx = v.x;
            double dy = v.y;
            double dz = v.z;
            wr.pos(dx, dy - size, dz)
                .tex(f13, f10)
                .lightmap(j, k)
                .color(this.rCol, this.gCol, this.bCol, alpha)
                .endVertex();
            wr.pos(dx, dy + size, dz)
                .tex(f13, f9)
                .lightmap(j, k)
                .color(this.rCol, this.gCol, this.bCol, alpha)
                .endVertex();
        }

        FXBuffer.current().draw();
        wr.begin(5, FXBuffer.POSITION_TEX_LMAP_COLOR);

        for (int c = 0; c < this.points.size(); c++) {
            Vec3 v = this.points.get(c);
            float f13 = c / this.length;
            double dx = v.x;
            double dy = v.y;
            double dz = v.z;
            wr.pos(dx - size, dy, dz - size)
                .tex(f13, f10)
                .lightmap(j, k)
                .color(this.rCol, this.gCol, this.bCol, alpha)
                .endVertex();
            wr.pos(dx + size, dy, dz + size)
                .tex(f13, f9)
                .lightmap(j, k)
                .color(this.rCol, this.gCol, this.bCol, alpha)
                .endVertex();
        }

        FXBuffer.current().draw();
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
