package thaumcraft.client.renderers.entity.mob;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.renderers.models.entity.ModelTaintacle;
import thaumcraft.common.entities.monster.tainted.EntityTaintacle;
public final class RenderTaintacle<T extends EntityTaintacle> extends MobRenderer<T,ModelTaintacle<T>> {
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/entity/taintacle.png");
    public RenderTaintacle(EntityRendererProvider.Context context,float shadow,int length){super(context,new ModelTaintacle<>(length),shadow);}
    @Override public ResourceLocation getTextureLocation(T entity){return TEXTURE;}@Override protected RenderType getRenderType(T entity,boolean visible,boolean translucent,boolean glowing){return RenderType.entityTranslucent(TEXTURE);}
}
