package thaumcraft.client.renderers.models.entity;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import thaumcraft.common.entities.monster.boss.EntityEldritchGolem;
/** All36 BETA26 cubes including individual114px UV widths,8 shoulder child links and source cloth/phase animation. */
public final class ModelEldritchGolem extends EntityModel<EntityEldritchGolem> {
    private final SourceBossGeometry.Rig rig;
    public ModelEldritchGolem(){rig=createGeometry();}
    public static SourceBossGeometry.Rig createGeometry(){var builder=new SourceBossGeometry.Builder();
        builder.part("Cloak1", 0, 47, -5.0F, 1.5F, 4.0F, 10, 12, 1, 0.0F, 0.0F, -2.5F, 0.1396263F, 0.0F, 0.0F, false, 128, 64);
        builder.part("Cloak3", 0, 37, -5.0F, 17.5F, -0.8F, 10, 4, 1, 0.0F, 0.0F, -2.5F, 0.4465716F, 0.0F, 0.0F, false, 128, 64);
        builder.part("Cloak2", 0, 59, -5.0F, 13.5F, 1.7F, 10, 4, 1, 0.0F, 0.0F, -2.5F, 0.3069452F, 0.0F, 0.0F, false, 128, 64);
        builder.part("CloakCL", 0, 43, 3.0F, 0.5F, 2.0F, 2, 1, 3, 0.0F, 0.0F, -2.5F, 0.1396263F, 0.0F, 0.0F, false, 128, 64);
        builder.part("CloakCR", 0, 43, -5.0F, 0.5F, 2.0F, 2, 1, 3, 0.0F, 0.0F, -2.5F, 0.1396263F, 0.0F, 0.0F, false, 128, 64);
        builder.part("Head", 47, 12, -3.5F, -6.0F, -2.5F, 7, 7, 5, 0.0F, 4.5F, -3.8F, -0.1047198F, 0.0F, 0.0F, false, 128, 64);
        builder.part("Head2", 26, 16, -2.0F, -2.0F, -2.0F, 4, 4, 4, 0.0F, 0.0F, -5.0F, -0.1047198F, 0.0F, 0.0F, false, 128, 64);
        builder.part("CollarL", 75, 50, 3.5F, -0.5F, -7.0F, 1, 4, 10, 0.0F, 0.0F, -2.5F, 0.837758F, 0.0F, 0.0F, false, 128, 64);
        builder.part("CollarR", 67, 50, -4.5F, -0.5F, -7.0F, 1, 4, 10, 0.0F, 0.0F, -2.5F, 0.837758F, 0.0F, 0.0F, false, 128, 64);
        builder.part("CollarB", 77, 59, -3.5F, -0.5F, 2.0F, 7, 4, 1, 0.0F, 0.0F, -2.5F, 0.837758F, 0.0F, 0.0F, false, 128, 64);
        builder.part("CollarF", 77, 59, -3.5F, -0.5F, -7.0F, 7, 4, 1, 0.0F, 0.0F, -2.5F, 0.837758F, 0.0F, 0.0F, false, 128, 64);
        builder.part("CollarBlack", 22, 0, -3.5F, 0.0F, -6.0F, 7, 1, 8, 0.0F, 0.0F, -2.5F, 0.837758F, 0.0F, 0.0F, false, 128, 64);
        builder.part("Frontcloth0", 114, 52, -3.0F, 3.2F, -3.5F, 6, 10, 1, 0.0F, 0.0F, -2.5F, 0.1745329F, 0.0F, 0.0F, false, 114, 64);
        builder.part("Frontcloth1", 114, 39, -1.0F, 1.5F, -3.5F, 6, 6, 1, -2.0F, 12.0F, 0.0F, -0.1047198F, 0.0F, 0.0F, false, 114, 64);
        builder.part("Frontcloth2", 114, 47, -1.0F, 8.5F, -1.5F, 6, 3, 1, -2.0F, 11.0F, 0.0F, -0.3316126F, 0.0F, 0.0F, false, 114, 64);
        builder.part("Torso", 34, 45, -5.0F, 2.5F, -3.0F, 10, 10, 6, 0.0F, 0.0F, -2.5F, 0.1745329F, 0.0F, 0.0F, true, 128, 64);
        builder.part("ArmR", 78, 32, -3.5F, 1.5F, -2.0F, 4, 13, 5, -5.0F, 3.0F, -2.0F, 0.0F, 0.0F, 0.1047198F, false, 128, 64);
        builder.part("ShoulderR1", 0, 23, -3.3F, 4.0F, -2.5F, 1, 2, 6, 0, 0, 0, 0.0F, 0.0F, 1.186824F, false, 128, 64);
        builder.part("ShoulderR", 0, 0, -4.3F, -1.0F, -3.0F, 4, 5, 7, 0, 0, 0, 0.0F, 0.0F, 1.186824F, false, 128, 64);
        builder.part("ShoulderR2", 0, 12, -2.3F, 4.0F, -3.0F, 2, 3, 7, 0, 0, 0, 0.0F, 0.0F, 1.186824F, false, 128, 64);
        builder.part("ShoulderR0", 56, 31, -4.5F, -1.5F, -2.5F, 5, 6, 6, 0, 0, 0, 0.0F, 0.0F, 0.0F, false, 128, 64);
        builder.part("ArmL", 78, 32, -0.5F, 1.5F, -2.0F, 4, 13, 5, 5.0F, 3.0F, -2.0F, 0.0F, 0.0F, -0.1047198F, true, 128, 64);
        builder.part("ShoulderL1", 0, 23, 2.3F, 4.0F, -2.5F, 1, 2, 6, 0, 0, 0, 0.0F, 0.0F, -1.186824F, true, 128, 64);
        builder.part("ShoulderL0", 56, 31, -0.5F, -1.5F, -2.5F, 5, 6, 6, 0, 0, 0, 0.0F, 0.0F, 0.0F, true, 128, 64);
        builder.part("ShoulderL", 0, 0, 0.3F, -1.0F, -3.0F, 4, 5, 7, 0, 0, 0, 0.0F, 0.0F, -1.186824F, true, 128, 64);
        builder.part("ShoulderL2", 0, 12, 0.3F, 4.0F, -3.0F, 2, 3, 7, 0, 0, 0, 0.0F, 0.0F, -1.186824F, true, 128, 64);
        builder.part("BackpanelR1", 96, 7, 0.0F, 2.5F, -2.5F, 2, 2, 5, -2.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.1396263F, false, 128, 64);
        builder.part("WaistR1", 96, 14, -3.0F, -0.5F, -2.5F, 5, 3, 5, -2.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.1396263F, false, 128, 64);
        builder.part("WaistR2", 116, 13, -3.0F, 2.5F, -2.5F, 1, 4, 5, -2.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.1396263F, false, 128, 64);
        builder.part("WaistR3", 114, 5, -2.0F, 2.5F, -2.5F, 2, 3, 5, -2.0F, 12.0F, 0.0F, 0.0F, 0.0F, 0.1396263F, true, 128, 64);
        builder.part("LegR", 79, 19, -2.5F, 2.5F, -2.0F, 4, 9, 4, -2.0F, 12.5F, 0.0F, 0.0F, 0.0F, 0.0F, false, 128, 64);
        builder.part("WaistL1", 96, 14, -2.0F, -0.5F, -2.5F, 5, 3, 5, 2.0F, 12.0F, 0.0F, 0.0F, 0.0F, -0.1396263F, true, 128, 64);
        builder.part("WaistL2", 116, 13, 2.0F, 2.5F, -2.5F, 1, 4, 5, 2.0F, 12.0F, 0.0F, 0.0F, 0.0F, -0.1396263F, true, 128, 64);
        builder.part("WaistL3", 114, 5, 0.0F, 2.5F, -2.5F, 2, 3, 5, 2.0F, 12.0F, 0.0F, 0.0F, 0.0F, -0.1396263F, true, 128, 64);
        builder.part("BackpanelL1", 96, 7, -2.0F, 2.5F, -2.5F, 2, 2, 5, 2.0F, 12.0F, 0.0F, 0.0F, 0.0F, -0.1396263F, true, 128, 64);
        builder.part("LegL", 79, 19, -1.5F, 2.5F, -2.0F, 4, 9, 4, 2.0F, 12.5F, 0.0F, 0.0F, 0.0F, 0.0F, true, 128, 64);
        builder.child("ArmL", "ShoulderL");
        builder.child("ArmL", "ShoulderL0");
        builder.child("ArmL", "ShoulderL1");
        builder.child("ArmL", "ShoulderL2");
        builder.child("ArmR", "ShoulderR");
        builder.child("ArmR", "ShoulderR0");
        builder.child("ArmR", "ShoulderR1");
        builder.child("ArmR", "ShoulderR2");
        return builder.build();
    }
    private ModelPart p(String name){return rig.parts().get(name);}
    @Override public void prepareMobModel(EntityEldritchGolem e,float limb,float amount,float partial){
        int attack=e.getAttackTimer();float swing=attack>0?-2+1.5F*tri(attack-partial,10):0;
        p("ArmR").xRot=attack>0?swing:Mth.cos(limb*.4F+(float)Math.PI)*amount;
        p("ArmL").xRot=attack>0?swing:Mth.cos(limb*.4F)*amount;
    }
    @Override public void setupAnim(EntityEldritchGolem e,float limb,float amount,float age,float yaw,float pitch){
        p("Head").visible=!e.isHeadless();p("Head2").visible=e.isHeadless();
        if(e.getSpawnTimer()>0){p("Head").yRot=0;p("Head").xRot=(e.getSpawnTimer()/2)*Mth.DEG_TO_RAD;}
        else{p("Head").yRot=yaw/4*Mth.DEG_TO_RAD;p("Head").xRot=pitch/2*Mth.DEG_TO_RAD;p("Head2").yRot=yaw*Mth.DEG_TO_RAD;p("Head2").xRot=pitch*Mth.DEG_TO_RAD;}
        p("LegR").xRot=Mth.cos(limb*.4662F)*1.4F*amount;p("LegL").xRot=Mth.cos(limb*.4662F+(float)Math.PI)*1.4F*amount;
        float cloth=Math.min(Mth.cos(limb*.44F)*1.4F*amount,Mth.cos(limb*.44F+(float)Math.PI)*1.4F*amount);
        p("Frontcloth1").xRot=cloth-.1047198F;p("Frontcloth2").xRot=cloth-.3316126F;p("Cloak1").xRot=-cloth/3+.1396263F;p("Cloak2").xRot=-cloth/3+.3069452F;p("Cloak3").xRot=-cloth/3+.4465716F;
    }
    @Override public void renderToBuffer(PoseStack poses,VertexConsumer consumer,int light,int overlay,int color){rig.root().render(poses,consumer,light,overlay,color);p("Frontcloth1").render(poses,consumer,light,overlay,color);/* Source renders this cloth twice. */}
    private static float tri(float x,float period){return (Math.abs(x%period-period*.5F)-period*.25F)/(period*.25F);}
}

