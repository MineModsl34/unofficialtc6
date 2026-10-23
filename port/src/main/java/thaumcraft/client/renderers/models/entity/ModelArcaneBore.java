package thaumcraft.client.renderers.models.entity;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import thaumcraft.common.entities.construct.EntityArcaneBore;

public final class ModelArcaneBore extends EntityModel<EntityArcaneBore> {
    private final ModelPart root,base;
    public ModelArcaneBore(ModelPart root){this.root=root;base=root.getChild("base");}
    @Override public void setupAnim(EntityArcaneBore entity,float limb,float amount,float age,float yaw,float pitch){base.yRot=yaw/(180F/(float)Math.PI);base.xRot=pitch/(180F/(float)Math.PI);}
    @Override public void renderToBuffer(PoseStack poses,VertexConsumer vertices,int light,int overlay,int color){for(String part:new String[]{"leg2","tripod","leg3","leg4","leg1"})root.getChild(part).render(poses,vertices,light,overlay,color);base.render(poses,vertices,light,overlay,color);}
}
