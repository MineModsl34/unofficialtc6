package thaumcraft.client.renderers.entity.mob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.SilverfishModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.tainted.EntityTaintCrawler;
public final class RenderTaintCrawler extends MobRenderer<EntityTaintCrawler,SilverfishModel<EntityTaintCrawler>> {
    public RenderTaintCrawler(EntityRendererProvider.Context context){super(context,new SilverfishModel<>(context.bakeLayer(ModelLayers.SILVERFISH)),.2F);}
    @Override public ResourceLocation getTextureLocation(EntityTaintCrawler entity){return ResourceLocation.parse("thaumcraft:textures/entity/crawler.png");}@Override protected float getFlipDegrees(EntityTaintCrawler entity){return 180;}@Override protected void scale(EntityTaintCrawler entity,PoseStack poses,float partial){poses.scale(.7F,.7F,.7F);}
}
