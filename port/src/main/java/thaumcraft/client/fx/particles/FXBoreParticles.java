package thaumcraft.client.fx.particles;

import thaumcraft.client.fx.FXBuffer;
import thaumcraft.client.fx.FxPoseState;
import thaumcraft.client.fx.FxMath;
import thaumcraft.client.fx.ParticleEngine;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import thaumcraft.client.fx.SourceParticle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;

public class FXBoreParticles extends SourceParticle {
    private BlockState blockInstance;
    private ItemStack itemInstance;
    private int side;
    private Entity target;
    private double targetX;
    private double targetY;
    private double targetZ;

    public FXBoreParticles(ClientLevel par1World, double par2, double par4, double par6, double tx, double ty, double tz, BlockState par14Block, int par15) {
        super(par1World, par2, par4, par6, 0.0, 0.0, 0.0);
        this.blockInstance = par14Block;

        try {
            this.setParticleTexture(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(par14Block));
        } catch (Exception e) {
            this.setParticleTexture(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(Blocks.STONE.defaultBlockState()));
            this.lifetime = 0;
        }

        this.gravity = 1;
        this.rCol = this.gCol = this.bCol = 0.6F;
        this.particleScale = this.random.nextFloat() * 0.3F + 0.4F;
        this.side = par15;
        this.targetX = tx;
        this.targetY = ty;
        this.targetZ = tz;
        double dx = tx - this.x;
        double dy = ty - this.y;
        double dz = tz - this.z;
        int base = (int)(FxMath.sqrt(dx * dx + dy * dy + dz * dz) * 10.0F);
        if (base < 1) {
            base = 1;
        }

        this.lifetime = base / 2 + this.random.nextInt(base);
        float f3 = 0.01F;
        this.xd = (float)this.level.random.nextGaussian() * f3;
        this.yd = (float)this.level.random.nextGaussian() * f3;
        this.zd = (float)this.level.random.nextGaussian() * f3;
        this.gravity = 0.01F;
    }

    public FXBoreParticles(
        ClientLevel par1World, double par2, double par4, double par6, double tx, double ty, double tz, double sx, double sy, double sz, ItemStack item
    ) {
        super(par1World, par2, par4, par6, 0.0, 0.0, 0.0);
        this.itemInstance = item;
        this.setParticleTexture(Minecraft.getInstance().getItemRenderer().getModel(item,level,null,0).getParticleIcon());
        this.gravity = 1;
        this.rCol = this.gCol = this.bCol = 0.6F;
        this.particleScale = this.random.nextFloat() * 0.3F + 0.4F;
        this.side = 0;
        this.targetX = tx;
        this.targetY = ty;
        this.targetZ = tz;
        double dx = tx - this.x;
        double dy = ty - this.y;
        double dz = tz - this.z;
        int base = (int)(FxMath.sqrt(dx * dx + dy * dy + dz * dz) * 10.0F);
        if (base < 1) {
            base = 1;
        }

        this.lifetime = base / 2 + this.random.nextInt(base);
        float f3 = 0.01F;
        this.xd = sx + (float)this.level.random.nextGaussian() * f3;
        this.yd = sy + (float)this.level.random.nextGaussian() * f3;
        this.zd = sz + (float)this.level.random.nextGaussian() * f3;
        this.gravity = 0.01F;
        Entity renderentity = Minecraft.getInstance().getCameraEntity();
        int visibleDistance = 64;
        if (!Minecraft.useFancyGraphics()) {
            visibleDistance = 32;
        }

        if (FxMath.distance(renderentity,this.x, this.y, this.z) > visibleDistance) {
            this.lifetime = 0;
        }
    }

    public void setTarget(Entity target) {
        this.target = target;
    }

    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.target != null) {
            this.targetX = this.target.getX();
            this.targetY = this.target.getY() + this.target.getEyeHeight();
            this.targetZ = this.target.getZ();
        }

        if (this.age++ < this.lifetime
            && (
                FxMath.floor(this.x) != FxMath.floor(this.targetX)
                    || FxMath.floor(this.y) != FxMath.floor(this.targetY)
                    || FxMath.floor(this.z) != FxMath.floor(this.targetZ)
            )) {
            this.move(this.xd, this.yd, this.zd);
            this.xd *= 0.985;
            this.yd *= 0.95;
            this.zd *= 0.985;
            double dx = this.targetX - this.x;
            double dy = this.targetY - this.y;
            double dz = this.targetZ - this.z;
            double d11 = FxMath.sqrt(dx * dx + dy * dy + dz * dz);
            double clamp = Math.min(0.25, d11 / 15.0);
            if (d11 < 2.0) {
                this.particleScale *= 0.9F;
            }

            dx /= d11;
            dy /= d11;
            dz /= d11;
            this.xd += dx * clamp;
            this.yd += dy * clamp;
            this.zd += dz * clamp;
            this.xd = FxMath.clamp((float)this.xd, -clamp, clamp);
            this.yd = FxMath.clamp((float)this.yd, -clamp, clamp);
            this.zd = FxMath.clamp((float)this.zd, -clamp, clamp);
            this.xd = this.xd + this.random.nextGaussian() * 0.005;
            this.yd = this.yd + this.random.nextGaussian() * 0.005;
            this.zd = this.zd + this.random.nextGaussian() * 0.005;
        } else {
            this.remove();
        }
    }

    public int getFXLayer() {
        return 1;
    }

    public FXBoreParticles getObjectColor(BlockPos pos) {
        if (this.blockInstance == null || this.level.getBlockState(pos) != this.blockInstance) {
            try {
                int var4 = Minecraft.getInstance().getItemColors().getColor(this.itemInstance, 0);
                this.rCol *= (var4 >> 16 & 0xFF) / 255.0F;
                this.gCol *= (var4 >> 8 & 0xFF) / 255.0F;
                this.bCol *= (var4 & 0xFF) / 255.0F;
            } catch (Exception var41) {
            }

            return this;
        } else {
            // Source compared IBlockState to Blocks.GRASS: that early-return branch never ran.

            try {
                int var4 = Minecraft.getInstance().getBlockColors().getColor(this.blockInstance, this.level, pos, 0);
                this.rCol *= (var4 >> 16 & 0xFF) / 255.0F;
                this.gCol *= (var4 >> 8 & 0xFF) / 255.0F;
                this.bCol *= (var4 & 0xFF) / 255.0F;
            } catch (Exception var3) {
            }

            return this;
        }
    }

    public void renderSource(
        FXBuffer buffer,
        Entity entityIn,
        float partialTicks,
        float rotationX,
        float rotationZ,
        float rotationYZ,
        float rotationXY,
        float rotationXZ
    ) {
        float f6 = (this.particleTextureIndexX + this.particleTextureJitterX / 4.0F) / 16.0F;
        float f7 = f6 + 0.015609375F;
        float f8 = (this.particleTextureIndexY + this.particleTextureJitterY / 4.0F) / 16.0F;
        float f9 = f8 + 0.015609375F;
        float f10 = 0.1F * this.particleScale;
        if (this.particleTexture != null) {
            f6 = this.particleTexture.getU((this.particleTextureJitterX / 4.0F * 16.0F) / 16.0F);
            f7 = this.particleTexture.getU(((this.particleTextureJitterX + 1.0F) / 4.0F * 16.0F) / 16.0F);
            f8 = this.particleTexture.getV((this.particleTextureJitterY / 4.0F * 16.0F) / 16.0F);
            f9 = this.particleTexture.getV(((this.particleTextureJitterY + 1.0F) / 4.0F * 16.0F) / 16.0F);
        }

        int i = this.getLightColor(partialTicks);
        int j = i >> 16 & 65535;
        int k = i & 65535;
        float f11 = (float)(this.xo + (this.x - this.xo) * partialTicks - interpPosX);
        float f12 = (float)(this.yo + (this.y - this.yo) * partialTicks - interpPosY);
        float f13 = (float)(this.zo + (this.z - this.zo) * partialTicks - interpPosZ);
        buffer.pos(f11 - rotationX * f10 - rotationXY * f10, f12 - rotationZ * f10, f13 - rotationYZ * f10 - rotationXZ * f10)
            .tex(f6, f9)
            .color(this.rCol, this.gCol, this.bCol, 1.0F)
            .lightmap(j, k)
            .endVertex();
        buffer.pos(f11 - rotationX * f10 + rotationXY * f10, f12 + rotationZ * f10, f13 - rotationYZ * f10 + rotationXZ * f10)
            .tex(f6, f8)
            .color(this.rCol, this.gCol, this.bCol, 1.0F)
            .lightmap(j, k)
            .endVertex();
        buffer.pos(f11 + rotationX * f10 + rotationXY * f10, f12 + rotationZ * f10, f13 + rotationYZ * f10 + rotationXZ * f10)
            .tex(f7, f8)
            .color(this.rCol, this.gCol, this.bCol, 1.0F)
            .lightmap(j, k)
            .endVertex();
        buffer.pos(f11 + rotationX * f10 - rotationXY * f10, f12 - rotationZ * f10, f13 + rotationYZ * f10 - rotationXZ * f10)
            .tex(f7, f9)
            .color(this.rCol, this.gCol, this.bCol, 1.0F)
            .lightmap(j, k)
            .endVertex();
    }
}
