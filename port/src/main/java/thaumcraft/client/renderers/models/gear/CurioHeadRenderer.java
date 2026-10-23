package thaumcraft.client.renderers.models.gear;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;
/** Source head-worn atlas rendered as genuine textured item geometry, following the wearer head. */
public final class CurioHeadRenderer implements ICurioRenderer {
    private final ModelResourceLocation model;
    public CurioHeadRenderer(String item){model=ModelResourceLocation.standalone(ResourceLocation.parse("thaumcraft:item/"+item+"_worn"));}
    @Override public <T extends LivingEntity,M extends EntityModel<T>> void render(ItemStack stack,SlotContext context,PoseStack pose,RenderLayerParent<T,M> parent,MultiBufferSource buffers,int light,float swing,float amount,float partial,float age,float yaw,float pitch){
        if(!(parent.getModel() instanceof HumanoidModel<?> humanoid))return;pose.pushPose();humanoid.head.translateAndRotate(pose);boolean armor=!context.entity().getItemBySlot(EquipmentSlot.HEAD).isEmpty();pose.translate(0,-.25,armor?-.42:-.30);pose.scale(1,-1,-1);
        var mc=Minecraft.getInstance();mc.getItemRenderer().render(stack,ItemDisplayContext.NONE,false,pose,buffers,light,OverlayTexture.NO_OVERLAY,mc.getModelManager().getModel(model));pose.popPose();
    }
}
