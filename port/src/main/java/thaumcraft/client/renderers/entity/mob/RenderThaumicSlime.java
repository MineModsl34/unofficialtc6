package thaumcraft.client.renderers.entity.mob;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SlimeRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Slime;

/** Source uses vanilla slime inner/outer models with exactly this texture. */
public final class RenderThaumicSlime extends SlimeRenderer {
    private static final ResourceLocation TEXTURE=ResourceLocation.parse("thaumcraft:textures/entity/tslime.png");
    public RenderThaumicSlime(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(Slime slime) { return TEXTURE; }
}
