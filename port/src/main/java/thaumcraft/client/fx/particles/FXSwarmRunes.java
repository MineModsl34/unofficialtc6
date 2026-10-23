package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXSwarmRunes extends SourceParticle {
    private Entity target;
    private float turnSpeed = 10.0F;
    private float speed = 0.2F;
    int deathtimer = 0;
    float rotationPitch;
    float rotationYaw;
    public int particle = 0;

    public FXSwarmRunes(ClientLevel par1World, double x, double y, double z, Entity target, float r, float g, float b) {
        super(par1World, x, y, z, 0.0, 0.0, 0.0);
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
        this.particleScale = this.random.nextFloat() * 0.5F + 1.0F;
        this.target = target;
        float f3 = 0.2F;
        this.xd = (this.random.nextFloat() - this.random.nextFloat()) * f3;
        this.yd = (this.random.nextFloat() - this.random.nextFloat()) * f3;
        this.zd = (this.random.nextFloat() - this.random.nextFloat()) * f3;
        this.gravity = 0.1F;
    }

    public FXSwarmRunes(ClientLevel par1World, double x, double y, double z, Entity target, float r, float g, float b, float sp, float ts, float pg) {
        this(par1World, x, y, z, target, r, g, b);
        this.speed = sp;
        this.turnSpeed = ts;
        this.gravity = pg;
        this.particle = this.random.nextInt(16);
    }

    public void renderSource(FXBuffer wr, Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
        float bob = FxMath.sin(this.age / 3.0F) * 0.25F + 1.0F;
        FxPoseState.glColor4f(1.0F, 1.0F, 1.0F, 0.75F);
        float var8x = this.particle / 64.0F;
        float var9 = var8x + 0.015625F;
        float var10 = 0.09375F;
        float var11 = var10 + 0.015625F;
        float var12 = 0.07F * this.particleScale * bob;
        float var13 = (float)(this.xo + (this.x - this.xo) * f - interpPosX);
        float var14 = (float)(this.yo + (this.y - this.yo) * f - interpPosY);
        float var15 = (float)(this.zo + (this.z - this.zo) * f - interpPosZ);
        float var16 = 1.0F;
        float trans = (50.0F - this.deathtimer) / 50.0F * 0.66F;
        int i = 240;
        int j = i >> 16 & 65535;
        int k = i & 65535;
        wr.pos(var13 - f1 * var12 - f4 * var12, var14 - f2 * var12, var15 - f3 * var12 - f5 * var12)
            .tex(var9, var11)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, trans)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 - f1 * var12 + f4 * var12, var14 + f2 * var12, var15 - f3 * var12 + f5 * var12)
            .tex(var9, var10)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, trans)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 + f1 * var12 + f4 * var12, var14 + f2 * var12, var15 + f3 * var12 + f5 * var12)
            .tex(var8x, var10)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, trans)
            .lightmap(j, k)
            .endVertex();
        wr.pos(var13 + f1 * var12 - f4 * var12, var14 - f2 * var12, var15 + f3 * var12 - f5 * var12)
            .tex(var8x, var11)
            .color(this.rCol * var16, this.gCol * var16, this.bCol * var16, trans)
            .lightmap(j, k)
            .endVertex();
    }

    public int getFXLayer() {
        return 0;
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.age++;
        if (this.age > 200
            || this.target == null
            || this.target.isRemoved()
            || this.target instanceof LivingEntity && ((LivingEntity)this.target).deathTime > 0) {
            this.deathtimer++;
            this.xd *= 0.9;
            this.zd *= 0.9;
            this.yd = this.yd - this.gravity / 2.0F;
            if (this.deathtimer > 50) {
                this.remove();
            }
        } else {
            this.yd = this.yd + this.gravity;
        }

        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.985;
        this.yd *= 0.985;
        this.zd *= 0.985;
        if (this.age < 200
            && this.target != null
            && !this.target.isRemoved()
            && (!(this.target instanceof LivingEntity) || ((LivingEntity)this.target).deathTime <= 0)) {
            boolean hurt = false;
            if (this.target instanceof LivingEntity) {
                hurt = ((LivingEntity)this.target).hurtTime > 0;
            }

            Vec3 v1 = new Vec3(this.x, this.y, this.z);
            if (v1.distanceToSqr(this.target.getX(), this.target.getY(), this.target.getZ())
                    > this.target.getBbWidth() * this.target.getBbWidth()
                && !hurt) {
                this.faceEntity(
                    this.target,
                    this.turnSpeed / 2.0F + this.random.nextInt((int)(this.turnSpeed / 2.0F)),
                    this.turnSpeed / 2.0F + this.random.nextInt((int)(this.turnSpeed / 2.0F))
                );
            } else {
                if (hurt
                    && v1.distanceToSqr(this.target.getX(), this.target.getY(), this.target.getZ())
                        < this.target.getBbWidth() * this.target.getBbWidth()) {
                    this.age += 100;
                }

                this.faceEntity(
                    this.target,
                    -(this.turnSpeed / 2.0F + this.random.nextInt((int)(this.turnSpeed / 2.0F))),
                    -(this.turnSpeed / 2.0F + this.random.nextInt((int)(this.turnSpeed / 2.0F)))
                );
            }

            this.xd = -FxMath.sin(this.rotationYaw / 180.0F * (float) Math.PI)
                * FxMath.cos(this.rotationPitch / 180.0F * (float) Math.PI);
            this.zd = FxMath.cos(this.rotationYaw / 180.0F * (float) Math.PI)
                * FxMath.cos(this.rotationPitch / 180.0F * (float) Math.PI);
            this.yd = -FxMath.sin(this.rotationPitch / 180.0F * (float) Math.PI);
            this.setHeading(this.xd, this.yd, this.zd, this.speed, 15.0F);
        }
    }

    public void faceEntity(Entity par1Entity, float par2, float par3) {
        double d0 = par1Entity.getX() - this.x;
        double d1 = par1Entity.getZ() - this.z;
        double d2 = (par1Entity.getBoundingBox().minY + par1Entity.getBoundingBox().maxY) / 2.0
            - (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0;
        double d3 = FxMath.sqrt(d0 * d0 + d1 * d1);
        float f2 = (float)(Math.atan2(d1, d0) * 180.0 / Math.PI) - 90.0F;
        float f3 = (float)(-(Math.atan2(d2, d3) * 180.0 / Math.PI));
        this.rotationPitch = this.updateRotation(this.rotationPitch, f3, par3);
        this.rotationYaw = this.updateRotation(this.rotationYaw, f2, par2);
    }

    private float updateRotation(float par1, float par2, float par3) {
        float f3 = FxMath.wrapDegrees(par2 - par1);
        if (f3 > par3) {
            f3 = par3;
        }

        if (f3 < -par3) {
            f3 = -par3;
        }

        return par1 + f3;
    }

    public void setHeading(double par1, double par3, double par5, float par7, float par8) {
        float f2 = FxMath.sqrt(par1 * par1 + par3 * par3 + par5 * par5);
        par1 /= f2;
        par3 /= f2;
        par5 /= f2;
        par1 += this.random.nextGaussian() * (this.random.nextBoolean() ? -1 : 1) * 0.0075F * par8;
        par3 += this.random.nextGaussian() * (this.random.nextBoolean() ? -1 : 1) * 0.0075F * par8;
        par5 += this.random.nextGaussian() * (this.random.nextBoolean() ? -1 : 1) * 0.0075F * par8;
        par1 *= par7;
        par3 *= par7;
        par5 *= par7;
        this.xd = par1;
        this.yd = par3;
        this.zd = par5;
    }
}
