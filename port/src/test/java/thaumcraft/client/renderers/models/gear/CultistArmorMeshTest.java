package thaumcraft.client.renderers.models.gear;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import thaumcraft.common.items.armor.SourceCultistArmorGeometry;
import thaumcraft.common.items.armor.SourceCultistArmorGeometry.Kind;

/** Actual native CPU baking/vertices, no window, GL context or claimed live visual acceptance. */
class CultistArmorMeshTest {
    @Test void nativeBakedPartsMatchSourceBoxesPivotsRotationsAndLeaveVanillaBodyCubesEmpty() {
        for (Kind kind : Kind.values()) for (boolean inner : new boolean[]{true, false}) {
            var root = ModelCultistArmor.createLayer(kind, inner).bakeRoot();
            var geometry = SourceCultistArmorGeometry.load(kind);
            assertEquals(geometry.parts().stream().filter(p -> p.included(inner)).count() + 8, root.getAllParts().count());
            for (String name : List.of("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg")) assertTrue(root.getChild(name).isEmpty(), "no vanilla cube under custom mesh: " + name);
            for (var source : geometry.parts()) if (source.included(inner)) {
                String parent = switch (source.parent()) { case "bipedHead" -> "head"; case "bipedBody" -> "body"; case "bipedRightArm" -> "right_arm"; case "bipedLeftArm" -> "left_arm"; case "bipedRightLeg" -> "right_leg"; case "bipedLeftLeg" -> "left_leg"; default -> "hat"; };
                var part = root.getChild(parent).getChild(source.name()); var cube = part.getRandomCube(RandomSource.create(0));
                assertEquals(source.box().origin().x(), cube.minX); assertEquals(source.box().origin().y(), cube.minY); assertEquals(source.box().origin().z(), cube.minZ);
                assertEquals(source.box().origin().x() + source.box().size().x(), cube.maxX); assertEquals(source.box().origin().y() + source.box().size().y(), cube.maxY); assertEquals(source.box().origin().z() + source.box().size().z(), cube.maxZ);
                assertEquals(source.offset().x(), part.x); assertEquals(source.offset().y(), part.y); assertEquals(source.offset().z(), part.z);
                assertEquals(source.rotation().x(), part.xRot); assertEquals(source.rotation().y(), part.yRot); assertEquals(source.rotation().z(), part.zRot);
            }
        }
    }
    @Test void actualVertexRendererKeepsSourceAdultHeadScaleAndExactChildTransforms() {
        ModelKnightArmor model = new ModelKnightArmor(ModelCultistArmor.createLayer(Kind.KNIGHT, true).bakeRoot());
        model.setAllVisible(false); model.head.visible = true;
        // Native EntityModel defaults young=true. Real CustomArmorHelper.prepare assigns
        // wearer.isBaby() every frame; this no-entity CPU fixture must specify adult likewise.
        model.young = false;
        Vertices adult = new Vertices(); model.renderToBuffer(new PoseStack(), adult, 0, 0, -1);
        assertEquals(24, adult.points.size()); assertEquals(-4.5F / 16F * 1.01F, adult.min(0), 0.000001F);
        assertEquals(-9F / 16F * 1.01F, adult.min(1), 0.000001F);
        model.young = true; Vertices child = new Vertices(); model.renderToBuffer(new PoseStack(), child, 0, 0, -1);
        assertEquals(24, child.points.size()); assertEquals(-4.5F / 16F * .75F, child.min(0), 0.000001F);
        assertEquals((-9F / 16F + 1F) * .75F, child.min(1), 0.000001F);
    }
    private static final class Vertices implements VertexConsumer {
        final List<float[]> points = new ArrayList<>();
        float min(int axis) { return (float) points.stream().mapToDouble(p -> p[axis]).min().orElseThrow(); }
        @Override public VertexConsumer addVertex(float x, float y, float z) { assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)); points.add(new float[]{x, y, z}); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
