package thaumcraft.client.renderers.entity.construct;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.renderers.models.entity.ModelCrossbow;
import thaumcraft.common.entities.construct.EntityTurretCrossbow;

public final class RenderTurretCrossbow extends MobRenderer<EntityTurretCrossbow,ModelCrossbow> {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.parse("thaumcraft:turret_basic"),"main");
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/entity/crossbow.png");
    public RenderTurretCrossbow(EntityRendererProvider.Context context){super(context,new ModelCrossbow(context.bakeLayer(LAYER)),.5F);}
    @Override public ResourceLocation getTextureLocation(EntityTurretCrossbow entity){return TEXTURE;}
    @Override public void render(EntityTurretCrossbow entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){float body=entity.yBodyRot,old=entity.yBodyRotO;entity.yBodyRot=entity.yBodyRotO=0;super.render(entity,yaw,partial,poses,buffers,light);entity.yBodyRot=body;entity.yBodyRotO=old;}
}
