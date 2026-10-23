package thaumcraft.client.renderers.entity.mob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.renderers.models.entity.ModelTaintSeed;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;
public final class RenderTaintSeed<T extends EntityTaintSeed> extends MobRenderer<T,ModelTaintSeed<T>> {
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/entity/taintseed.png");
    public RenderTaintSeed(EntityRendererProvider.Context context,float shadow){super(context,new ModelTaintSeed<>(),shadow);}
    @Override public ResourceLocation getTextureLocation(T entity){return TEXTURE;}@Override protected RenderType getRenderType(T entity,boolean visible,boolean translucent,boolean glowing){return RenderType.entityTranslucent(TEXTURE);}
    @Override protected void setupRotations(T entity,PoseStack poses,float age,float yaw,float partial,float scale){super.setupRotations(entity,poses,age,0,partial,scale);}
}
