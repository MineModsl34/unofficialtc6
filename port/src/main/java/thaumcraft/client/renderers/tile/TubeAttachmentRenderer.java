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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import thaumcraft.common.tiles.essentia.*;

/** Exact ModelTubeValve geometry and source attachment transforms in native PoseStack. */
public final class TubeAttachmentRenderer<T extends TileTube> implements BlockEntityRenderer<T> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("thaumcraft:textures/models/valve.png");
    private final ModelPart rod, ring;
    public TubeAttachmentRenderer(BlockEntityRendererProvider.Context context) {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("rod", CubeListBuilder.create().texOffs(0, 10).mirror().addBox(-1, 2, -1, 2, 2, 2), PartPose.ZERO);
        mesh.getRoot().addOrReplaceChild("ring", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(-2, 4, -2, 4, 1, 4), PartPose.ZERO);
        var root = LayerDefinition.create(mesh, 64, 32).bakeRoot(); rod = root.getChild("rod"); ring = root.getChild("ring");
    }
    private static void orient(PoseStack poses, Direction direction) {
        if (direction.getStepY() == 0) poses.mulPose(Axis.YP.rotationDegrees(90));
        else { poses.mulPose(Axis.XN.rotationDegrees(90)); poses.mulPose(Axis.YP.rotationDegrees(90 * direction.getStepY())); }
        poses.mulPose(new Quaternionf().rotationAxis((float)Math.PI / 2, direction.getStepX(), direction.getStepY(), direction.getStepZ()));
    }
    @Override public void render(T tube, float partial, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        var vertices = buffers.getBuffer(RenderType.entityCutout(TEXTURE));
        if (tube instanceof TileTubeValve valve) {
            poses.pushPose(); poses.translate(.5, .5, .5); orient(poses, valve.facing);
            poses.mulPose(Axis.YP.rotationDegrees(-valve.rotation * 1.5F)); poses.translate(0, -.03F - valve.rotation / 360 * .09F, 0);
            ring.render(poses, vertices, light, overlay); poses.scale(.75F, 1, .75F); rod.render(poses, vertices, light, overlay); poses.popPose();
        } else if (tube instanceof TileTubeOneway && (tube.getLevel() == null || TransportConnections.connectable(tube.getLevel(), tube.getBlockPos(), tube.facing.getOpposite()) != null)) {
            poses.pushPose(); poses.translate(.5, .5, .5); orient(poses, tube.facing); poses.scale(2, 2, 2); poses.translate(0, -.32F, 0); rod.render(poses, vertices, light, overlay, 0xff7380ff); poses.popPose();
        } else if (tube instanceof TileTubeBuffer buffer && tube.getLevel() != null) {
            for (Direction side : Direction.values()) if (buffer.chokedSides[side.get3DDataValue()] != 0 && buffer.openSides[side.get3DDataValue()] && TransportConnections.connectable(tube.getLevel(), tube.getBlockPos(), side) != null) {
                poses.pushPose(); poses.translate(.5, .5, .5); orient(poses, side.getOpposite()); poses.scale(2, 1, 2); poses.translate(0, -.5, 0);
                rod.render(poses, vertices, light, overlay, buffer.chokedSides[side.get3DDataValue()] == 2 ? 0xffff4d4d : 0xff4d4dff); poses.popPose();
            }
        }
    }
}
