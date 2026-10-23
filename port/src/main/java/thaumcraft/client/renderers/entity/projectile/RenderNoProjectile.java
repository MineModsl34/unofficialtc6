package thaumcraft.client.renderers.entity.projectile;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import thaumcraft.common.entities.projectile.EntityFocusProjectile;

/** Source renderer contains no projectile mesh; it admits interpolated trail samples every >.2 tick. */
public final class RenderNoProjectile extends EntityRenderer<EntityFocusProjectile> {
    public RenderNoProjectile(EntityRendererProvider.Context context){super(context);shadowRadius=.1F;}
    @Override public void render(EntityFocusProjectile projectile,float yaw,float coefficient,PoseStack poses,MultiBufferSource buffers,int light){if(ProjectileParticleRules.admit(coefficient,projectile.lastRenderTick))projectile.renderParticle(coefficient);}
    @Override public ResourceLocation getTextureLocation(EntityFocusProjectile entity){return InventoryMenu.BLOCK_ATLAS;}
}
