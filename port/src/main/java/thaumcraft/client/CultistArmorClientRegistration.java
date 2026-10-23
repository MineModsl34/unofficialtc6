package thaumcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.client.renderers.models.gear.CustomArmorHelper;
import thaumcraft.client.renderers.models.gear.ModelCultistArmor;
import thaumcraft.client.renderers.models.gear.ModelKnightArmor;
import thaumcraft.client.renderers.models.gear.ModelLeaderArmor;
import thaumcraft.client.renderers.models.gear.ModelRobe;
import thaumcraft.common.items.armor.SourceCultistArmorGeometry.Kind;

@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CultistArmorClientRegistration {
    private CultistArmorClientRegistration() {}
    public static ModelLayerLocation layer(Kind kind, boolean inner) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "cultist_" + kind.name().toLowerCase(java.util.Locale.ROOT)), inner ? "inner" : "outer");
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (Kind kind : Kind.values()) for (boolean inner : new boolean[]{false, true}) event.registerLayerDefinition(layer(kind, inner), () -> ModelCultistArmor.createLayer(kind, inner));
    }
    @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new Extension(Kind.KNIGHT), ItemsTC.crimsonPlateHelm, ItemsTC.crimsonPlateChest, ItemsTC.crimsonPlateLegs);
        event.registerItem(new Extension(Kind.ROBE), ItemsTC.crimsonRobeHelm, ItemsTC.crimsonRobeChest, ItemsTC.crimsonRobeLegs);
        // Source crimson boots use the standard iron-boot mesh and Item.getArmorTexture, no custom model.
    }
    private static final class Extension implements IClientItemExtensions {
        final Kind kind; Object bakedOwner; ModelCultistArmor inner, outer;
        Extension(Kind kind) { this.kind = kind; }
        private ModelCultistArmor bake(boolean inner) {
            var root = Minecraft.getInstance().getEntityModels().bakeLayer(layer(kind, inner));
            return switch (kind) { case KNIGHT -> new ModelKnightArmor(root); case ROBE -> new ModelRobe(root); case LEADER -> new ModelLeaderArmor(root); };
        }
        @Override public Model getGenericArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
            Object owner = Minecraft.getInstance().getEntityModels();
            if (bakedOwner != owner) { bakedOwner = owner; inner = bake(true); outer = bake(false); }
            ModelCultistArmor model = CustomArmorHelper.inner(slot) ? inner : outer;
            // Do not use default ClientHooks copy, which overwrites source slot visibility/poses.
            CustomArmorHelper.prepare(model, entity, slot, 0); return model;
        }
        @Override public void setupModelAnimations(LivingEntity entity, ItemStack stack, EquipmentSlot slot, Model model,
            float swing, float amount, float partialTick, float age, float yaw, float pitch) {
            if (model instanceof ModelCultistArmor armor) { CustomArmorHelper.prepare(armor, entity, slot, partialTick); armor.setupAnim(entity, swing, amount, age, yaw, pitch); }
        }
    }
}
