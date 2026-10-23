package thaumcraft.client.renderers.entity.mob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.boss.EntityCultistLeader;
public final class RenderCultistLeader extends HumanoidMobRenderer<EntityCultistLeader,HumanoidModel<EntityCultistLeader>> {
    public RenderCultistLeader(EntityRendererProvider.Context context){super(context,new HumanoidModel<>(context.bakeLayer(RenderCultist.LAYER)),.5F);addLayer(new HumanoidArmorLayer<>(this,new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_INNER_ARMOR)),new HumanoidModel<>(context.bakeLayer(ModelLayers.SKELETON_OUTER_ARMOR)),context.getModelManager()));}
    @Override public ResourceLocation getTextureLocation(EntityCultistLeader entity){return ResourceLocation.parse("thaumcraft:textures/entity/cultist.png");}@Override protected void scale(EntityCultistLeader entity,PoseStack poses,float partial){poses.scale(1.15F,1.15F,1.15F);}
}
