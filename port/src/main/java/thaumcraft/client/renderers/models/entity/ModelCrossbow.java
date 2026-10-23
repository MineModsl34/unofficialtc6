package thaumcraft.client.renderers.models.entity;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import thaumcraft.common.entities.construct.EntityTurretCrossbow;

public final class ModelCrossbow extends EntityModel<EntityTurretCrossbow> {
    private final ModelPart root,crossbow;
    private float partial;
    public ModelCrossbow(ModelPart root){this.root=root;crossbow=root.getChild("crossbow");}
    @Override public void prepareMobModel(EntityTurretCrossbow entity,float limb,float amount,float partial){this.partial=partial;}
    @Override public void setupAnim(EntityTurretCrossbow entity,float limb,float amount,float age,float yaw,float pitch){
        crossbow.yRot=yaw/(180F/(float)Math.PI);crossbow.xRot=pitch/(180F/(float)Math.PI);
        float swing=Mth.sin(Mth.sqrt(attackTime)*(float)Math.PI*2F)*.2F;
        for(int i=1;i<=3;i++){crossbow.getChild("crossl"+i).yRot=-.2F+swing;crossbow.getChild("crossr"+i).yRot=.2F-swing;}
        float load=-.5F+Mth.sin(Mth.sqrt(entity.getLoadProgress(partial))*(float)Math.PI*2F)*.5F;
        for(String part:new String[]{"loadbarcross","loadbarl","loadbarr"})crossbow.getChild(part).xRot=load;
        boolean cart=entity.getVehicle() instanceof AbstractMinecart;for(int i=1;i<=4;i++){var leg=root.getChild("leg"+i);leg.y=cart?4:12;leg.xRot=cart?.1F:.5F;}
    }
    @Override public void renderToBuffer(PoseStack poses,VertexConsumer vertices,int light,int overlay,int color){root.render(poses,vertices,light,overlay,color);}
}
