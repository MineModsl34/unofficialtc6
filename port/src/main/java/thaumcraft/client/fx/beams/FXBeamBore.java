package thaumcraft.client.fx.beams;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import net.minecraft.client.Minecraft;
import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.multiplayer.ClientLevel;
import thaumcraft.client.fx.ParticleEngine;

public class FXBeamBore extends SourceParticle {
    public int particle = 16;
    private double offset = 0.0;
    private double tX = 0.0;
    private double tY = 0.0;
    private double tZ = 0.0;
    private double ptX = 0.0;
    private double ptY = 0.0;
    private double ptZ = 0.0;
    private float length = 0.0F;
    private float rotYaw = 0.0F;
    private float rotPitch = 0.0F;
    private float prevYaw = 0.0F;
    private float prevPitch = 0.0F;
    private Entity targetEntity = null;
    private int type = 0;
    private float endMod = 1.0F;
    private boolean reverse = false;
    private boolean pulse = true;
    private int rotationspeed = 5;
    private float prevSize = 0.0F;
    public int impact;
    ResourceLocation beam = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/beam.png");
    ResourceLocation beam1 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/beam1.png");
    ResourceLocation beam2 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/beam2.png");
    ResourceLocation beam3 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/beam3.png");

    public FXBeamBore(ClientLevel par1World, double px, double py, double pz, double tx, double ty, double tz, float red, float green, float blue, int age) {
        super(par1World, px, py, pz, 0.0, 0.0, 0.0);
        this.rCol = red;
        this.gCol = green;
        this.bCol = blue;
        this.setSize(0.02F, 0.02F);
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.tX = tx;
        this.tY = ty;
        this.tZ = tz;
        this.prevYaw = this.rotYaw;
        this.prevPitch = this.rotPitch;
        this.lifetime = age;
        Entity renderentity = Minecraft.getInstance().getCameraEntity();
        int visibleDistance = 64;
        if (!Minecraft.useFancyGraphics()) {
            visibleDistance = 32;
        }

        if (renderentity != null && FxMath.distance(renderentity,this.x, this.y, this.z) > visibleDistance) {
            this.lifetime = 0;
        }
    }

    public void updateBeam(double sx, double sy, double sz, double x, double y, double z) {
        this.x = sx;
        this.y = sy;
        this.z = sz;
        this.tX = x;
        this.tY = y;
        this.tZ = z;

        while (this.lifetime - this.age < 4) {
            this.lifetime++;
        }
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y + this.offset;
        this.zo = this.z;
        this.ptX = this.tX;
        this.ptY = this.tY;
        this.ptZ = this.tZ;
        this.prevYaw = this.rotYaw;
        this.prevPitch = this.rotPitch;
        float xd = (float)(this.x - this.tX);
        float yd = (float)(this.y - this.tY);
        float zd = (float)(this.z - this.tZ);
        this.length = FxMath.sqrt(xd * xd + yd * yd + zd * zd);
        double var7 = FxMath.sqrt(xd * xd + zd * zd);
        this.rotYaw = (float)(Math.atan2(xd, zd) * 180.0 / Math.PI);
        this.rotPitch = (float)(Math.atan2(yd, var7) * 180.0 / Math.PI);
        this.prevYaw = this.rotYaw;
        this.prevPitch = this.rotPitch;
        if (this.impact > 0) {
            this.impact--;
        }

        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    public void setRGB(float r, float g, float b) {
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setEndMod(float endMod) {
        this.endMod = endMod;
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    public void setPulse(boolean pulse) {
        this.pulse = pulse;
    }

    public void setRotationspeed(int rotationspeed) {
        this.rotationspeed = rotationspeed;
    }

    public void renderSource(FXBuffer wr, Entity entityIn, float f, float f1, float f2, float f3, float f4, float f5) {
        FXBuffer.current().draw();
        FxPoseState.glPushMatrix();
        float var9 = 1.0F;
        float slide = Minecraft.getInstance().player.tickCount;
        float rot = (float)(this.level.getGameTime() % (360 / this.rotationspeed) * this.rotationspeed) + this.rotationspeed * f;
        float size = 1.0F;
        if (this.pulse) {
            size = Math.min(this.age / 4.0F, 1.0F);
            size = (float)(this.prevSize + (double)(size - this.prevSize) * f);
        }

        float op = 0.4F;
        if (this.pulse && this.lifetime - this.age <= 4) {
            op = 0.4F - (4 - (this.lifetime - this.age)) * 0.1F;
        }

        switch (this.type) {
            case 1:
                FxPoseState.bindTexture(this.beam1);
                break;
            case 2:
                FxPoseState.bindTexture(this.beam2);
                break;
            case 3:
                FxPoseState.bindTexture(this.beam3);
                break;
            default:
                FxPoseState.bindTexture(this.beam);
        }

        FxPoseState.glTexParameterf(3553, 10242, 10497.0F);
        FxPoseState.glTexParameterf(3553, 10243, 10497.0F);
        FxPoseState.glDisable(2884);
        float var11 = slide + f;
        if (this.reverse) {
            var11 *= -1.0F;
        }

        float var12 = -var11 * 0.2F - FxMath.floor(-var11 * 0.1F);
        FxPoseState.glEnable(3042);
        FxPoseState.glBlendFunc(770, 1);
        FxPoseState.glDepthMask(false);
        float xx = (float)(this.xo + (this.x - this.xo) * f - interpPosX);
        float yy = (float)(this.yo + (this.y - this.yo) * f - interpPosY);
        float zz = (float)(this.zo + (this.z - this.zo) * f - interpPosZ);
        FxPoseState.glTranslated(xx, yy, zz);
        float ry = (float)(this.prevYaw + (double)(this.rotYaw - this.prevYaw) * f);
        float rp = (float)(this.prevPitch + (double)(this.rotPitch - this.prevPitch) * f);
        FxPoseState.glRotatef(90.0F, 1.0F, 0.0F, 0.0F);
        FxPoseState.glRotatef(180.0F + ry, 0.0F, 0.0F, -1.0F);
        FxPoseState.glRotatef(rp, 1.0F, 0.0F, 0.0F);
        double var44 = -0.15 * size;
        double var17 = 0.15 * size;
        double var44b = -0.15 * size * this.endMod;
        double var17b = 0.15 * size * this.endMod;
        int i = 200;
        int j = i >> 16 & 65535;
        int k = i & 65535;
        FxPoseState.glRotatef(rot, 0.0F, 1.0F, 0.0F);

        for (int t = 0; t < 3; t++) {
            double var29 = this.length * size * var9;
            double var31 = 0.0;
            double var33 = 1.0;
            double var35 = -1.0F + var12 + t / 3.0F;
            double var37 = this.length * size * var9 + var35;
            FxPoseState.glRotatef(60.0F, 0.0F, 1.0F, 0.0F);
            wr.begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
            wr.pos(var44b, var29, 0.0)
                .tex(var33, var37)
                .color(this.rCol, this.gCol, this.bCol, op)
                .lightmap(j, k)
                .endVertex();
            wr.pos(var44, 0.0, 0.0)
                .tex(var33, var35)
                .color(this.rCol, this.gCol, this.bCol, op)
                .lightmap(j, k)
                .endVertex();
            wr.pos(var17, 0.0, 0.0)
                .tex(var31, var35)
                .color(this.rCol, this.gCol, this.bCol, op)
                .lightmap(j, k)
                .endVertex();
            wr.pos(var17b, var29, 0.0)
                .tex(var31, var37)
                .color(this.rCol, this.gCol, this.bCol, op)
                .lightmap(j, k)
                .endVertex();
            FXBuffer.current().draw();
        }

        FxPoseState.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        FxPoseState.glDepthMask(true);
        FxPoseState.glBlendFunc(770, 771);
        FxPoseState.glDisable(3042);
        FxPoseState.glEnable(2884);
        FxPoseState.glPopMatrix();
        if (this.impact > 0) {
            this.renderImpact(FXBuffer.current(), f, f1, f2, f3, f4, f5);
        }

        this.renderSource(FXBuffer.current(), f, f1, f2, f3, f4, f5);
        FxPoseState.bindTexture(ParticleEngine.particleTexture);
        wr.begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
        this.prevSize = size;
    }

    public void renderSource(FXBuffer tessellator, float f, float f1, float f2, float f3, float f4, float f5) {
        FxPoseState.glPushMatrix();
        FxPoseState.glDepthMask(false);
        FxPoseState.glEnable(3042);
        FxPoseState.glBlendFunc(770, 1);
        FxPoseState.bindTexture(ResourceLocation.parse("thaumcraft:textures/misc/auranodes.png"));
        FxPoseState.glColor4f(1.0F, 1.0F, 1.0F, 0.66F);
        int part = this.age % 32;
        float op = 0.8F;
        if (this.pulse && this.lifetime - this.age <= 4) {
            op = 0.8F - (4 - (this.lifetime - this.age)) * 0.2F;
        }

        float var8 = part / 32.0F;
        float var9 = var8 + 0.03125F;
        float var10 = 0.09375F;
        float var11 = var10 + 0.03125F;
        float var12 = 0.33F;
        float var13 = (float)(this.xo + (this.x - this.xo) * f - interpPosX);
        float var14 = (float)(this.yo + (this.y - this.yo) * f - interpPosY);
        float var15 = (float)(this.zo + (this.z - this.zo) * f - interpPosZ);
        int i = 200;
        int j = i >> 16 & 65535;
        int k = i & 65535;
        tessellator.getBuffer().begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
        tessellator.getBuffer()
            .pos(var13 - f1 * var12 - f4 * var12, var14 - f2 * var12, var15 - f3 * var12 - f5 * var12)
            .tex(var9, var11)
            .color(this.rCol, this.gCol, this.bCol, op)
            .lightmap(j, k)
            .endVertex();
        tessellator.getBuffer()
            .pos(var13 - f1 * var12 + f4 * var12, var14 + f2 * var12, var15 - f3 * var12 + f5 * var12)
            .tex(var9, var10)
            .color(this.rCol, this.gCol, this.bCol, op)
            .lightmap(j, k)
            .endVertex();
        tessellator.getBuffer()
            .pos(var13 + f1 * var12 + f4 * var12, var14 + f2 * var12, var15 + f3 * var12 + f5 * var12)
            .tex(var8, var10)
            .color(this.rCol, this.gCol, this.bCol, op)
            .lightmap(j, k)
            .endVertex();
        tessellator.getBuffer()
            .pos(var13 + f1 * var12 - f4 * var12, var14 - f2 * var12, var15 + f3 * var12 - f5 * var12)
            .tex(var8, var11)
            .color(this.rCol, this.gCol, this.bCol, op)
            .lightmap(j, k)
            .endVertex();
        tessellator.draw();
        FxPoseState.glBlendFunc(770, 771);
        FxPoseState.glDisable(3042);
        FxPoseState.glDepthMask(true);
        FxPoseState.glPopMatrix();
    }

    public void renderImpact(FXBuffer tessellator, float f, float f1, float f2, float f3, float f4, float f5) {
        FxPoseState.glPushMatrix();
        FxPoseState.glDepthMask(false);
        FxPoseState.glEnable(3042);
        FxPoseState.glBlendFunc(770, 1);
        FxPoseState.bindTexture(ParticleEngine.particleTexture);
        FxPoseState.glColor4f(1.0F, 1.0F, 1.0F, 0.66F);
        int part = this.age % 16;
        float var8 = part / 16.0F;
        float var9 = var8 + 0.0624375F;
        float var10 = 0.3125F;
        float var11 = var10 + 0.0624375F;
        float var12 = this.endMod / 2.0F / (6 - this.impact);
        float var13 = (float)(this.ptX + (this.tX - this.ptX) * f - interpPosX);
        float var14 = (float)(this.ptY + (this.tY - this.ptY) * f - interpPosY);
        float var15 = (float)(this.ptZ + (this.tZ - this.ptZ) * f - interpPosZ);
        int i = 200;
        int j = i >> 16 & 65535;
        int k = i & 65535;
        tessellator.getBuffer().begin(7, FXBuffer.PARTICLE_POSITION_TEX_COLOR_LMAP);
        tessellator.getBuffer()
            .pos(var13 - f1 * var12 - f4 * var12, var14 - f2 * var12, var15 - f3 * var12 - f5 * var12)
            .tex(var9, var11)
            .color(this.rCol, this.gCol, this.bCol, 0.66F)
            .lightmap(j, k)
            .endVertex();
        tessellator.getBuffer()
            .pos(var13 - f1 * var12 + f4 * var12, var14 + f2 * var12, var15 - f3 * var12 + f5 * var12)
            .tex(var9, var10)
            .color(this.rCol, this.gCol, this.bCol, 0.66F)
            .lightmap(j, k)
            .endVertex();
        tessellator.getBuffer()
            .pos(var13 + f1 * var12 + f4 * var12, var14 + f2 * var12, var15 + f3 * var12 + f5 * var12)
            .tex(var8, var10)
            .color(this.rCol, this.gCol, this.bCol, 0.66F)
            .lightmap(j, k)
            .endVertex();
        tessellator.getBuffer()
            .pos(var13 + f1 * var12 - f4 * var12, var14 - f2 * var12, var15 + f3 * var12 - f5 * var12)
            .tex(var8, var11)
            .color(this.rCol, this.gCol, this.bCol, 0.66F)
            .lightmap(j, k)
            .endVertex();
        tessellator.draw();
        FxPoseState.glBlendFunc(770, 771);
        FxPoseState.glDisable(3042);
        FxPoseState.glDepthMask(true);
        FxPoseState.glPopMatrix();
    }
}
