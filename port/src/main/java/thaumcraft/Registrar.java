package thaumcraft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.network.codec.ByteBufCodecs;
import thaumcraft.common.items.TCGearMaterials;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.CasterStoredStack;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.config.ConfigBlocks;
import thaumcraft.common.config.ConfigItems;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.TCAttachments;
import thaumcraft.common.world.features.TCWorldgen;
import thaumcraft.common.world.structures.TCMoundStructure;
import thaumcraft.common.world.structures.TCMoundPiece;
import thaumcraft.common.world.structures.TCCultistPortalStructure;
import thaumcraft.common.world.structures.TCCultistPortalPiece;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Analogue of TC6's Registrar: registration through RegisterEvent, which calls the Config* classes
 * and fills the static fields of BlocksTC/ItemsTC, so ported code accesses them as before.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class Registrar {
    public static CreativeModeTab TAB;

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.DATA_COMPONENT_TYPE, helper -> {
            TCDataComponents.ASPECTS = DataComponentType.<AspectList>builder()
                .persistent(AspectList.CODEC)
                .networkSynchronized(AspectList.STREAM_CODEC)
                .build();
            helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "aspects"), TCDataComponents.ASPECTS);
            TCDataComponents.CHARGE = DataComponentType.<Integer>builder()
                .persistent(ExtraCodecs.NON_NEGATIVE_INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT.map(TCDataComponents::validCharge, TCDataComponents::validCharge))
                .build();
            helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "charge"), TCDataComponents.CHARGE);
            TCDataComponents.INFUSION_ENCHANTMENTS = DataComponentType.<thaumcraft.common.lib.enchantment.InfusionEnchantments>builder()
                .persistent(thaumcraft.common.lib.enchantment.InfusionEnchantments.CODEC)
                .networkSynchronized(thaumcraft.common.lib.enchantment.InfusionEnchantments.STREAM_CODEC).build();
            helper.register(ResourceLocation.parse("thaumcraft:infusion_enchantments"), TCDataComponents.INFUSION_ENCHANTMENTS);
            TCDataComponents.CASTER_FOCUS = DataComponentType.<CasterStoredStack>builder()
                .persistent(CasterStoredStack.CODEC).networkSynchronized(CasterStoredStack.STREAM_CODEC).build();
            helper.register(ResourceLocation.parse("thaumcraft:caster_focus"), TCDataComponents.CASTER_FOCUS);
            TCDataComponents.CASTER_PICKED = DataComponentType.<CasterStoredStack>builder()
                .persistent(CasterStoredStack.CODEC).networkSynchronized(CasterStoredStack.STREAM_CODEC).build();
            helper.register(ResourceLocation.parse("thaumcraft:caster_picked"), TCDataComponents.CASTER_PICKED);
            TCDataComponents.ASPECT_FILTER = DataComponentType.<String>builder()
                .persistent(com.mojang.serialization.Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build();
            helper.register(ResourceLocation.parse("thaumcraft:aspect_filter"), TCDataComponents.ASPECT_FILTER);
            TCDataComponents.MIRROR_LINK = DataComponentType.<net.minecraft.core.GlobalPos>builder()
                .persistent(net.minecraft.core.GlobalPos.CODEC).networkSynchronized(net.minecraft.core.GlobalPos.STREAM_CODEC).build();
            helper.register(ResourceLocation.parse("thaumcraft:mirror_link"), TCDataComponents.MIRROR_LINK);
        });
        event.register(Registries.SOUND_EVENT, SoundsTC::registerSounds);
        event.register(Registries.ATTRIBUTE, thaumcraft.common.entities.monster.mods.ChampionSystem::register);
        event.register(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, helper -> helper.register(
            ResourceLocation.parse("thaumcraft:source_mob_spawns"), thaumcraft.common.world.biomes.TCMobSpawnModifier.CODEC));
        event.register(Registries.MOB_EFFECT, thaumcraft.common.lib.potions.WarpEffects::register);
        event.register(Registries.ENTITY_TYPE, thaumcraft.common.entities.WarpEntities::register);
        event.register(Registries.ENTITY_TYPE, thaumcraft.common.entities.CultEntities::register);
        event.register(Registries.ENTITY_TYPE, helper -> thaumcraft.common.entities.Mob8NaturalRegistries.createEntityTypes()
            .forEach((id,type) -> helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,id),type)));
        event.register(Registries.ENTITY_TYPE, helper -> thaumcraft.common.entities.Mob8BossRegistries.createEntityTypes()
            .forEach((id,type) -> helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,id),type)));
        event.register(Registries.ENTITY_TYPE, helper -> thaumcraft.common.entities.construct.Mob8ConstructRegistries.createEntityTypes()
            .forEach((id,type) -> helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,id),type)));
        event.register(Registries.ENTITY_TYPE, helper -> thaumcraft.common.entities.Mob8MiscRegistries.createEntityTypes()
            .forEach((id,type) -> helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,id),type)));
        event.register(Registries.ENTITY_TYPE, helper -> thaumcraft.common.golems.Golem9EntityRegistries.createEntityTypes()
            .forEach((id,type) -> helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,id),type)));
        event.register(Registries.ENTITY_TYPE, helper -> thaumcraft.common.tiles.crafting.Phase10RiftRegistries.createEntityTypes()
            .forEach((id,type) -> helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,id),type)));
        event.register(Registries.MENU, helper -> thaumcraft.common.golems.seals.Golem9SealRegistries.createMenus()
            .forEach((id,type) -> helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,id),type)));
        event.register(Registries.MENU, helper -> thaumcraft.common.entities.construct.Mob8ConstructRegistries.createMenus()
            .forEach((id,type) -> helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,id),type)));
        event.register(Registries.ENTITY_TYPE, helper -> thaumcraft.common.items.tools.EquipmentRegistries.createEntityTypes()
            .forEach((id,type) -> helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,id),type)));
        event.register(Registries.ENTITY_TYPE, helper -> thaumcraft.common.items.casters.CasterRegistries.createEntityTypes()
            .forEach((id,type) -> helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,id),type)));
        event.register(Registries.MENU, helper -> thaumcraft.common.items.casters.CasterRegistries.createMenus()
            .forEach((id,type) -> helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID,id),type)));
        event.register(NeoForgeRegistries.Keys.FLUID_TYPES, thaumcraft.common.blocks.world.taint.WarpTaintRegistries::registerFluidTypes);
        event.register(Registries.FLUID, thaumcraft.common.blocks.world.taint.WarpTaintRegistries::registerFluids);
        event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, TCAttachments::register);
        event.register(Registries.BLOCK, ConfigBlocks::initBlocks);
        event.register(Registries.BLOCK, helper -> {
            thaumcraft.common.blocks.world.taint.WarpTaintRegistries.registerBlocks(helper);
            // TC6 registers an ItemBlock only for fibre, NOT for finite flux goo.
            ConfigBlocks.BLOCKS_WITH_ITEMS.add(thaumcraft.api.blocks.BlocksTC.taintFibre);
        });
        event.register(Registries.FEATURE, TCWorldgen::register);
        event.register(Registries.ARMOR_MATERIAL, TCGearMaterials::register);
        event.register(Registries.ARMOR_MATERIAL, thaumcraft.common.items.armor.WearableRegistries::registerArmorMaterials);
        event.register(Registries.STRUCTURE_TYPE, helper -> helper.register(
            ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "mound"), TCMoundStructure.TYPE));
        event.register(Registries.STRUCTURE_PIECE, helper -> helper.register(
            ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "mound"), TCMoundPiece.TYPE));
        event.register(Registries.STRUCTURE_TYPE, helper -> helper.register(
            ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "cultist_portal"), TCCultistPortalStructure.TYPE));
        event.register(Registries.STRUCTURE_PIECE, helper -> helper.register(
            ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "cultist_portal"), TCCultistPortalPiece.TYPE));
        event.register(Registries.ITEM, helper -> {
            ConfigBlocks.initBlockItems(helper);
            ConfigItems.initItems(helper);
        });
        event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            TAB = CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.thaumcraft"))
                .icon(() -> new ItemStack(thaumcraft.api.items.ItemsTC.goggles))
                .displayItems((params, output) -> BuiltInRegistries.ITEM.entrySet().stream()
                    .filter(e -> e.getKey().location().getNamespace().equals(Thaumcraft.MODID))
                    // Source multiblock internals have no creative tab; obtain the assembled device through dust/caster.
                    .filter(e -> !java.util.Set.of("infernal_furnace","thaumatorium","thaumatorium_top","placeholder_brick","placeholder_obsidian",
                        "golem_builder","placeholder_bars","placeholder_cauldron","placeholder_anvil","placeholder_table").contains(e.getKey().location().getPath()))
                    .sorted(java.util.Comparator.comparingInt(e -> BuiltInRegistries.ITEM.getId(e.getValue())))
                    .forEach(e -> {
                        if (e.getValue() instanceof ItemGenericEssentiaContainer container) {
                            container.getTabVariants().forEach(output::accept);
                        } else if (e.getValue() == thaumcraft.api.items.ItemsTC.charmVerdant) {
                            thaumcraft.common.items.armor.WearableRegistries.creativeStacks(e.getValue()).forEach(output::accept);
                        } else if (e.getValue() == thaumcraft.api.items.ItemsTC.golemPlacer) {
                            thaumcraft.common.tiles.crafting.Golem9BuilderRegistries.creativeStacks(e.getValue()).forEach(output::accept);
                        } else {
                            output.accept(thaumcraft.common.items.tools.EquipmentRegistries.defaultStack(e.getValue()));
                        }
                    }))
                .build();
            helper.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "thaumcraft"), TAB);
        });
        // Golem builder BE/menu/capabilities are owned by Golem9BuilderRegistries.
        // Void Siphon BE/menu/capabilities are owned by Phase10RiftRegistries.
        // Death fluids/blocks are owned by DeathFluidRegistries, with no fluid ItemBlock.
        // Exact legacy FX remain stage 11.
    }
}
