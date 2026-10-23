package thaumcraft.client.renderers.tile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.tiles.essentia.TileCentrifuge;

/** Source six mirrored 64x32 boxes; two static and four rotating parts. */
public final class TileCentrifugeRenderer implements BlockEntityRenderer<TileCentrifuge> {
    private final ModelPart root;
    public TileCentrifugeRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition mesh = new MeshDefinition(); var definition = mesh.getRoot();
        definition.addOrReplaceChild("crossbar", CubeListBuilder.create().texOffs(16, 0).mirror().addBox(-4, -1, -1, 8, 2, 2), PartPose.ZERO);
        definition.addOrReplaceChild("dingus1", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(4, -3, -2, 4, 6, 4), PartPose.ZERO);
        definition.addOrReplaceChild("dingus2", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-8, -3, -2, 4, 6, 4), PartPose.ZERO);
        definition.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(-1.5F, -4, -1.5F, 3, 8, 3), PartPose.ZERO);
        definition.addOrReplaceChild("top", CubeListBuilder.create().texOffs(20, 16).mirror().addBox(-4, -8, -4, 8, 4, 8), PartPose.ZERO);
        definition.addOrReplaceChild("bottom", CubeListBuilder.create().texOffs(20, 16).mirror().addBox(-4, 4, -4, 8, 4, 8), PartPose.ZERO);
        root = LayerDefinition.create(mesh, 64, 32).bakeRoot();
    }
    @Override public void render(TileCentrifuge tile, float partial, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var vertices = buffers.getBuffer(RenderType.entityCutout(ResourceLocation.parse("thaumcraft:textures/models/centrifuge.png")));
        poses.pushPose(); poses.translate(.5, .5, .5); root.getChild("top").render(poses, vertices, light, overlay); root.getChild("bottom").render(poses, vertices, light, overlay);
        poses.mulPose(Axis.YP.rotationDegrees(tile.rotation));
        for (String name : new String[]{"crossbar", "dingus1", "dingus2", "core"}) root.getChild(name).render(poses, vertices, light, overlay);
        poses.popPose();
    }
}
