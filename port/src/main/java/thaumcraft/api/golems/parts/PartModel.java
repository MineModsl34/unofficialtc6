package thaumcraft.api.golems.parts;

import net.minecraft.resources.ResourceLocation;

/** Server-safe source OBJ binding. Actual pose/mesh callbacks live in the sided client renderer,
 * never in the properties catalog loaded by dedicated servers. Add-ons may provide their own
 * client adapter keyed by this descriptor without shipping GL classes to common code. */
public class PartModel {
    public enum EnumAttachPoint { ARMS, LEGS, BODY, HEAD }
    public enum EnumLimbSide { LEFT, RIGHT, MIDDLE }
    public enum Animation { NONE, CLAWS, BREAKERS, DARTS, HAULER, WHEEL }
    private final ResourceLocation objModel, texture;
    private final EnumAttachPoint attachPoint;
    private final Animation animation;
    public PartModel(ResourceLocation objModel,ResourceLocation texture,EnumAttachPoint attachPoint) { this(objModel,texture,attachPoint,Animation.NONE); }
    public PartModel(ResourceLocation objModel,ResourceLocation texture,EnumAttachPoint attachPoint,Animation animation) {
        this.objModel = java.util.Objects.requireNonNull(objModel); this.texture = texture;
        this.attachPoint = java.util.Objects.requireNonNull(attachPoint); this.animation = java.util.Objects.requireNonNull(animation);
    }
    public ResourceLocation getObjModel() { return objModel; }
    public ResourceLocation getTexture() { return texture; }
    public EnumAttachPoint getAttachPoint() { return attachPoint; }
    public Animation animation() { return animation; }
    public boolean useMaterialTextureForObjectPart(String partName) { return partName.startsWith("bm"); }
    public void preRenderObjectPart(String partName, thaumcraft.api.golems.IGolemAPI golem, float partialTicks, EnumLimbSide side) {}
    public void postRenderObjectPart(String partName, thaumcraft.api.golems.IGolemAPI golem, float partialTicks, EnumLimbSide side) {}
    public float preRenderArmRotationX(thaumcraft.api.golems.IGolemAPI golem, float partialTicks, EnumLimbSide side, float inputRot) { return inputRot; }
    public float preRenderArmRotationY(thaumcraft.api.golems.IGolemAPI golem, float partialTicks, EnumLimbSide side, float inputRot) { return inputRot; }
    public float preRenderArmRotationZ(thaumcraft.api.golems.IGolemAPI golem, float partialTicks, EnumLimbSide side, float inputRot) { return inputRot; }
}
