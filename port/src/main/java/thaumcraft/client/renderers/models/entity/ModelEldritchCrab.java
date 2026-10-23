package thaumcraft.client.renderers.models.entity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.monster.EntityEldritchCrab;
/** Exact 21 source cuboids, rather than an enlarged native spider. */
public final class ModelEldritchCrab extends HierarchicalModel<EntityEldritchCrab> {
    private final ModelPart root;
    public ModelEldritchCrab(ModelPart root){this.root=root;}@Override public ModelPart root(){return root;}
    public static LayerDefinition createBodyLayer(){MeshDefinition mesh=new MeshDefinition();var r=mesh.getRoot();
        p(r,"TailHelm",0,0,-4.5F,-4.5F,-.4F,9,9,9,0,18,0,.1047198F,0,0,false);p(r,"TailBare",64,0,-4,-4,-.4F,8,8,8,0,18,0,.1047198F,0,0,false);
        p(r,"RClaw1",0,47,-2,-1,-5.066667F,4,3,5,-6,15.5F,-10,0,0,0,false);p(r,"Head1",0,38,-2,-1.5F,-9.066667F,4,4,1,0,18,0,0,0,0,false);p(r,"RClaw0",0,55,-2,-2.5F,-3.066667F,4,5,3,-6,17,-7,0,0,0,false);p(r,"RClaw2",14,54,-1.5F,-1,-4.066667F,3,2,5,-6,18.5F,-10,.3141593F,0,0,false);
        p(r,"RArm",44,4,-1,-1,-5.066667F,2,2,6,-3,17,-4,0,.7504916F,0,false);p(r,"LClaw2",14,54,-1.5F,-1,-4.066667F,3,2,5,6,18.5F,-10,.3141593F,0,0,false);p(r,"LClaw1",0,47,-2,-1,-5.066667F,4,3,5,6,15.5F,-10,0,0,0,true);p(r,"LClaw0",0,55,-2,-2.5F,-3.066667F,4,5,3,6,17,-7,0,0,0,true);p(r,"LArm",44,4,-1,-1,-4.066667F,2,2,6,4,17,-5,0,-.7504916F,0,false);
        p(r,"Torso",0,18,-3.5F,-3.5F,-6.066667F,7,7,6,0,18,0,.0523599F,0,0,false);p(r,"Head0",0,31,-2.5F,-2,-8.066667F,5,5,2,0,18,0,0,0,0,false);
        p(r,"RRLeg1",36,4,-4.5F,1,-.9F,2,5,2,-4,20,-1.5F,0,0,0,false);p(r,"RFLeg1",36,4,-5,1,-1.066667F,2,5,2,-4,20,-3.5F,0,0,0,false);p(r,"LRLeg1",36,4,2.5F,1,-.9F,2,5,2,4,20,-1.5F,0,0,0,false);p(r,"LFLeg1",36,4,3,1,-1.066667F,2,5,2,4,20,-3.5F,0,0,0,false);
        p(r,"RRLeg0",36,0,-4.5F,-1,-.9F,6,2,2,-4,20,-1.5F,0,0,0,false);p(r,"RFLeg0",36,0,-5,-1,-1.066667F,6,2,2,-4,20,-3.5F,0,0,0,false);p(r,"LFLeg0",36,0,-1,-1,-1.066667F,6,2,2,4,20,-3.5F,0,0,0,false);p(r,"LRLeg0",36,0,-1.5F,-1,-.9F,6,2,2,4,20,-1.5F,0,0,0,false);return LayerDefinition.create(mesh,128,64);}
    private static void p(PartDefinition root,String name,int u,int v,float x,float y,float z,float w,float h,float d,float px,float py,float pz,float rx,float ry,float rz,boolean mirror){root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).mirror(mirror).addBox(x,y,z,w,h,d),PartPose.offsetAndRotation(px,py,pz,rx,ry,rz));}
    @Override public void setupAnim(EntityEldritchCrab entity,float limb,float amount,float age,float yaw,float pitch){root.getChild("TailHelm").visible=entity.hasHelm();root.getChild("TailBare").visible=!entity.hasHelm();float back=-Mth.cos(limb*.6662F*2)*.4F*amount,front=-Mth.cos(limb*.6662F*2+Mth.PI)*.4F*amount;for(int i=0;i<2;i++){leg("RRLeg"+i,.2094395F,.4363323F,back);leg("RFLeg"+i,-.2094395F,.4363323F,front);leg("LRLeg"+i,-.2094395F,-.4363323F,-back);leg("LFLeg"+i,.2094395F,-.4363323F,-front);}for(String name:new String[]{"TailHelm","TailBare"}){var tail=root.getChild(name);tail.yRot=Mth.cos(limb*.6662F)*2*amount*.125F;tail.zRot=Mth.cos(limb*.6662F)*amount*.125F;}root.getChild("RClaw2").xRot=.3141593F-Mth.sin(entity.tickCount/4F)*.25F;root.getChild("LClaw2").xRot=.3141593F+Mth.sin(entity.tickCount/4.1F)*.25F;root.getChild("RClaw1").xRot=Mth.sin(entity.tickCount/4F)*.125F;root.getChild("LClaw1").xRot=-Mth.sin(entity.tickCount/4.1F)*.125F;}
    private void leg(String name,float y,float z,float movement){var leg=root.getChild(name);leg.xRot=0;leg.yRot=y+movement;leg.zRot=z+movement;}
}
