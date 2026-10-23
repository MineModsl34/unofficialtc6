package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXGenericGui extends FXGeneric {
    boolean inGame = false;

    public FXGenericGui(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
        this.inGame = (Minecraft.getInstance().screen == null);
    }

    public FXGenericGui(ClientLevel level, double x, double y, double z, double xx, double yy, double zz) {
        super(level, x, y, z, xx, yy, zz);
        this.inGame = (Minecraft.getInstance().screen == null);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.inGame && (Minecraft.getInstance().screen == null)) {
            this.remove();
        }
    }

    @Override
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
        float f5 = (float)(this.xo + (this.x - this.xo) * partialTicks);
        float f6 = (float)(this.yo + (this.y - this.yo) * partialTicks);
        float f7 = (float)(this.zo + (this.z - this.zo) * partialTicks);
        FxPoseState.glPushMatrix();
        FxPoseState.glTranslatef(f5, f6, -90.0F + f7);
        if (this.angled) {
            FxPoseState.glRotatef(-this.angleYaw + 90.0F, 0.0F, 1.0F, 0.0F);
            FxPoseState.glRotatef(this.anglePitch + 90.0F, 1.0F, 0.0F, 0.0F);
        }

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
    }
}
