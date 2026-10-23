package thaumcraft.client.renderers.entity.mob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.renderers.models.entity.ModelEldritchGolem;
import thaumcraft.common.entities.monster.boss.EntityEldritchGolem;
public final class RenderEldritchGolem extends MobRenderer<EntityEldritchGolem,ModelEldritchGolem> {
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/entity/eldritch_golem.png");
    public RenderEldritchGolem(EntityRendererProvider.Context context){super(context,new ModelEldritchGolem(),.5F);}
    @Override public ResourceLocation getTextureLocation(EntityEldritchGolem entity){return TEXTURE;}@Override protected void scale(EntityEldritchGolem entity,PoseStack poses,float partial){poses.scale(1.7F,1.7F,1.7F);}@Override protected RenderType getRenderType(EntityEldritchGolem entity,boolean visible,boolean translucent,boolean glowing){return thaumcraft.client.lib.warp.WarpEntityRenderTypes.body(TEXTURE,true);}
}
