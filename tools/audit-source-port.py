"""Read-only BETA26/native source and data inventory; never boots or tests Minecraft.

The report distinguishes an implementation mapping from behavioural equivalence.
Renamed classes are mapped explicitly below, not counted as complete merely by name.
"""
from pathlib import Path
from collections import Counter
import argparse
import fnmatch
import hashlib
import json
import re
import convert_lang

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "reference/tc6/src/thaumcraft"
NATIVE = ROOT / "port/src/main/java/thaumcraft"
RESOURCES = [ROOT / "port/src/main/resources", ROOT / "port/src/generated/resources"]

# Paths are relative to the native thaumcraft Java root. A rule lists actual owners;
# it does not assert that every old method is source-equivalent.
ROUTES = [
    ("api/OreDictionaryEntries.java", "native-tags", ["datagen/TCItemTagsProvider.java", "common/lib/crafting/SourceIngredientTags.java"]),
    ("api/ThaumcraftMaterials.java", "native-materials", ["common/items/tools/EquipmentRegistries.java", "common/items/armor/WearableRegistries.java"]),
    ("api/damagesource/DamageSourceIndirectThaumcraftEntity.java", "native-damage-types", ["api/damagesource/DamageSourceThaumcraft.java", "common/items/casters/foci/FocusEffectRuntime.java"]),
    ("api/package-info.java", "package-metadata", ["api/ThaumcraftApi.java"]),
    ("api/potions/*", "native-effects", ["common/lib/potions/WarpEffects.java"]),
    ("*/theorycraft/Card*.java", "consolidated-cards", ["common/lib/research/theorycraft/SourceTheorycraftCard.java", "api/research/theorycraft/TheorycraftManager.java"]),
    ("*/theorycraft/Aid*.java", "consolidated-aids", ["common/lib/research/theorycraft/TheorycraftAids.java", "api/research/theorycraft/TheorycraftManager.java"]),
    ("client/gui/GuiLogistics.java", "relocated", ["common/golems/client/gui/GuiLogistics.java"]),
    ("client/gui/GuiSpa.java", "shared-device-screen", ["client/gui/GuiWorldDevice.java", "client/WorldDeviceClientRegistration.java"]),
    ("client/gui/GuiPotionSprayer.java", "shared-device-screen", ["client/gui/GuiWorldDevice.java", "client/WorldDeviceClientRegistration.java"]),
    ("client/gui/Gui*", "shared-construct-screen", ["client/gui/GuiConstruct.java", "client/Mob8ConstructClientRegistration.java"]),
    ("client/gui/plugins/*", "native-gui-controls", ["client/gui/GuiResearchTable.java", "client/gui/GuiFocalManipulator.java", "client/gui/GuiResearchPage.java"]),
    ("client/lib/obj/*", "native-and-golem-obj", ["common/golems/client/GolemObjModel.java", "common/golems/client/GolemObjMeshes.java", "client/renderers/models/entity/ConstructObjMesh.java"]),
    ("client/lib/*", "native-render-math", ["client/lib/UtilsFX.java", "client/lib/ender/SourceRenderMath.java", "client/fx/NativeGLE.java"]),
    ("client/renderers/entity/mob/LayerTainted.java", "native-taint-layer", ["client/lib/warp/WarpTaintPresentation.java"]),
    ("client/renderers/entity/mob/*", "consolidated-natural-mob-rendering", ["client/renderers/entity/Mob8NaturalClient.java", "client/renderers/entity/Mob8NaturalClientEffects.java"]),
    ("client/renderers/entity/projectile/RenderFocusCloud.java", "native-invisible-cloud-and-fx", ["client/CasterClientRegistration.java", "client/lib/fx/SourceProjectileFX.java"]),
    ("client/renderers/entity/RenderFallingTaint.java", "native-falling-block-renderer", ["client/Mob8MiscClient.java"]),
    ("client/renderers/tile/TileBannerRenderer.java", "native-banner-renderer", ["common/blocks/basic/BannerRegistries.java", "client/Mob8MiscClient.java"]),
    ("client/renderers/tile/TileHungryChestRenderer.java", "shared-device-rendering", ["client/renderers/tile/TileWorldDeviceRenderer.java"]),
    ("client/renderers/tile/TileTube*Renderer.java", "shared-tube-rendering", ["client/renderers/tile/TubeAttachmentRenderer.java", "client/TransportClientRegistration.java"]),
    ("client/renderers/tile/*", "shared-device-rendering", ["client/renderers/tile/TileWorldDeviceRenderer.java", "client/WorldDeviceClientRegistration.java"]),
    ("client/renderers/models/entity/ModelGrappler.java", "native-grappler-mesh", ["client/renderers/entity/projectile/RenderGrapple.java"]),
    ("client/renderers/models/entity/ModelRendererTaint*", "native-taint-geometry", ["client/renderers/models/entity/SourceBossGeometry.java"]),
    ("client/renderers/models/block/ModelBanner.java", "native-banner-renderer", ["common/blocks/basic/BannerRegistries.java"]),
    ("client/renderers/models/block/ModelBore*.java", "source-construct-geometry", ["client/renderers/models/entity/SourceConstructGeometry.java"]),
    ("client/renderers/models/block/*", "native-block-meshes", ["client/renderers/tile/TileWorldDeviceRenderer.java", "client/renderers/tile/AlchemyRenderSupport.java", "client/renderers/tile/TubeAttachmentRenderer.java"]),
    ("client/renderers/models/ModelCube.java", "native-model-parts", ["client/fx/NativeGLE.java", "client/renderers/models/entity/SourceConstructGeometry.java"]),
    ("client/renderers/block/CrystalModel.java", "generated-crystal-models", ["datagen/TCBlockStateProvider.java"]),
    ("codechicken/*", "removed-embedded-library-native-math", ["client/lib/ender/SourceRenderMath.java", "client/fx/NativeGLE.java", "common/items/casters/FocusTargeting.java"]),
    ("common/blocks/basic/BlockStonePorous.java", "native-loot-table", ["datagen/TCBlockLoot.java", "common/lib/crafting/TaggedMetalRecipes.java"]),
    ("common/blocks/basic/*", "native-vanilla-block-families", ["common/config/ConfigBlocks.java", "datagen/TCBlockLoot.java"]),
    ("common/blocks/crafting/BlockResearchTable.java", "relocated", ["common/blocks/basic/BlockResearchTable.java"]),
    ("common/blocks/crafting/BlockPatternCrafter.java", "shared-world-device", ["common/blocks/devices/BlockWorldDevice.java", "common/tiles/devices/TileStorageDevice.java"]),
    ("common/blocks/devices/*", "shared-world-device", ["common/blocks/devices/BlockWorldDevice.java", "common/tiles/devices/DeviceRegistries.java"]),
    ("common/blocks/essentia/BlockJarBrainItem.java", "relocated", ["common/blocks/devices/BlockJarBrainItem.java"]),
    ("common/blocks/misc/BlockFlesh.java", "native-block-properties", ["common/config/ConfigBlocks.java", "datagen/TCBlockLoot.java"]),
    ("common/blocks/misc/BlockPlaceholder.java", "real-multiblock-proxies", ["common/blocks/misc/BlockGolemPlaceholder.java", "common/blocks/misc/BlockInfernalPlaceholder.java"]),
    ("common/blocks/world/BlockLoot.java", "relocated", ["common/blocks/world/BlockLootTC.java"]),
    ("common/blocks/world/ore/BlockOreTC.java", "native-ore-and-loot", ["common/blocks/world/ore/BlockOreAmber.java", "common/config/ConfigBlocks.java", "datagen/TCBlockLoot.java"]),
    ("common/blocks/world/plants/BlockLogsTC.java", "native-pillar-block", ["common/config/ConfigBlocks.java"]),
    ("common/blocks/world/taint/ITaintBlock.java", "native-taint-blocks-and-tags", ["common/blocks/world/taint/TaintHelper.java", "datagen/TCBlockTagsProvider.java"]),
    ("common/blocks/*", "native-block-base-and-properties", ["common/config/ConfigBlocks.java", "common/blocks/devices/BlockWorldDevice.java"]),
    ("common/config/ConfigRecipes.java", "native-recipes-and-dust", ["datagen/TCRecipeProvider.java", "common/lib/crafting/DustTriggers.java", "common/lib/crafting/CrucibleRecipes.java", "common/lib/crafting/InfusionRecipes.java"]),
    ("common/container/ContainerLogistics.java", "relocated", ["common/golems/client/gui/ContainerLogistics.java"]),
    ("common/container/InventoryLogistics.java", "relocated", ["common/golems/client/gui/InventoryLogistics.java"]),
    ("common/container/ContainerSpa.java", "shared-device-menu", ["common/container/ContainerWorldDevice.java"]),
    ("common/container/ContainerPotionSprayer.java", "shared-device-menu", ["common/container/ContainerWorldDevice.java"]),
    ("common/container/Container*", "shared-construct-menu", ["common/container/ContainerConstruct.java", "common/container/ConstructEquipmentContainer.java"]),
    ("common/container/InventoryArcaneWorkbench.java", "native-workbench-inventory", ["common/tiles/crafting/TileArcaneWorkbench.java"]),
    ("common/container/InventoryHandMirror.java", "native-mirror-container", ["common/container/ContainerHandMirror.java"]),
    ("common/container/InventoryFake.java", "native-container", ["api/crafting/ContainerDummy.java", "common/lib/crafting/ThaumcraftCraftingManager.java"]),
    ("common/container/slot/*", "native-menu-slots", ["common/container/ContainerConstruct.java", "common/container/ContainerWorldDevice.java", "common/container/ContainerArcaneWorkbench.java", "common/container/ContainerFocusPouch.java"]),
    ("common/entities/monster/mods/*", "consolidated-champion-modifiers", ["common/entities/monster/mods/ChampionEffects.java", "common/entities/monster/mods/ChampionSystem.java", "common/entities/monster/mods/ChampionDamageRules.java"]),
    ("common/golems/client/PartModel*", "native-golem-animation-adapters", ["common/golems/client/GolemPartAnimations.java", "api/golems/parts/PartModel.java"]),
    ("common/golems/client/gui/GuiGolem*Button.java", "native-seal-controls", ["common/golems/client/gui/GuiGolemStateButton.java", "common/golems/client/gui/SealBaseGUI.java"]),
    ("common/items/armor/CustomArmorHelper.java", "relocated", ["client/renderers/models/gear/CustomArmorHelper.java"]),
    ("common/items/misc/ItemCreativePlacer.java", "unregistered-in-beta26", ["common/entities/construct/ItemTurretPlacer.java", "common/golems/ItemGolemPlacer.java"]),
    ("common/items/*", "native-item-base-and-components", ["common/config/ConfigItems.java", "api/items/TCDataComponents.java"]),
    ("common/lib/BehaviorDispenseAlumetum.java", "native-dispenser-behaviour", ["common/entities/AlumentumRegistration.java"]),
    ("common/lib/CreativeTabThaumcraft.java", "native-creative-tab", ["Registrar.java"]),
    ("common/lib/RefGui.java", "native-menu-registration", ["Registrar.java", "common/tiles/devices/DeviceRegistries.java", "common/entities/construct/Mob8ConstructRegistries.java"]),
    ("common/lib/crafting/ContainerFake.java", "native-recipe-input", ["api/crafting/ContainerDummy.java", "common/lib/crafting/ThaumcraftCraftingManager.java"]),
    ("common/lib/events/KeyHandler.java", "native-key-events", ["client/CasterClientEvents.java", "client/ToolClientRegistration.java"]),
    ("common/lib/events/CraftingEvents.java", "native-crafting-events-and-fuels", ["common/lib/events/ResearchCraftingEvents.java", "common/lib/crafting/OrdinaryCustomRecipes.java", "datagen/TCDataMapProvider.java"]),
    ("common/lib/events/WarpEvents.java", "native-warp-events", ["common/lib/warp/WarpGameplay.java", "common/lib/potions/WarpEffects.java"]),
    ("common/lib/events/ServerEvents.java", "native-server-work-queues", ["common/items/casters/FocusBlockActions.java", "common/lib/crafting/DustConversion.java", "common/lib/crafting/DustMultiblockConversion.java"]),
    ("common/lib/events/EntityEvents.java", "native-entity-events", ["common/lib/events/ChampionEvents.java", "common/lib/events/CultistDropEvents.java", "common/lib/events/ResearchDamageEvents.java"]),
    ("common/lib/events/PlayerEvents.java", "native-player-events", ["common/lib/events/PlayerDataEvents.java", "common/lib/events/WearableEvents.java", "common/lib/warp/WarpGameplay.java", "common/lib/events/BookAcquisitionEvents.java"]),
    ("common/lib/events/*", "native-world-and-aura-events", ["common/world/aura/AuraEvents.java", "common/world/features/TCWorldgen.java", "common/world/WorldgenBlacklistImc.java"]),
    ("common/lib/network/fx/*", "consolidated-fx-payload", ["common/lib/network/fx/PacketSourceFX.java", "common/lib/network/fx/SourceFXNetwork.java", "common/lib/fx/SourceFX.java"]),
    ("common/lib/network/misc/PacketSeal*", "native-seal-payloads", ["common/golems/seals/Golem9SealRegistries.java"]),
    ("common/lib/network/misc/PacketLogistics*", "native-logistics-menu", ["common/golems/client/gui/ContainerLogistics.java", "common/golems/seals/Golem9SealRegistries.java"]),
    ("common/lib/network/misc/PacketStartTheory*", "native-theorycraft-actions", ["common/lib/network/research/PacketTheorycraftAction.java"]),
    ("common/lib/network/misc/PacketFocus*", "native-caster-actions", ["common/lib/network/misc/PacketCasterAction.java"]),
    ("common/lib/network/misc/PacketItemKey*", "native-item-actions", ["common/lib/network/misc/PacketCasterAction.java", "common/lib/network/ToolNetwork.java"]),
    ("common/lib/network/misc/PacketBiomeChange.java", "native-biome-update", ["common/golems/seals/SealBlockActions.java"]),
    ("common/lib/network/misc/PacketNote.java", "native-block-events", ["common/tiles/devices/TileSignalDevice.java"]),
    ("common/lib/network/misc/*", "native-menu-and-item-sync", ["common/lib/network/ProcessingNetwork.java", "common/lib/network/WearableNetwork.java"]),
    ("common/lib/network/playerdata/PacketFocus*", "native-focus-editing", ["common/lib/network/playerdata/PacketFocusEdit.java", "common/lib/network/playerdata/PacketFocalState.java"]),
    ("common/lib/network/playerdata/Packet*Flags*", "native-research-requests", ["common/lib/network/playerdata/ServerResearchRequestHandler.java"]),
    ("common/lib/network/playerdata/PacketPlayerFlag*", "native-research-requests", ["common/lib/network/playerdata/ServerResearchRequestHandler.java"]),
    ("common/lib/network/tiles/*", "native-block-entity-and-menu-sync", ["common/lib/network/ProcessingNetwork.java", "common/tiles/devices/DeviceTile.java"]),
    ("common/lib/network/FakeNetHandlerPlayServer.java", "native-fake-player", ["common/entities/construct/BoreSilkHarvest.java", "common/golems/GolemInteractionHelper.java"]),
    ("common/lib/network/EventHandlerNetwork.java", "native-payload-registration", ["common/lib/network/PacketHandler.java"]),
    ("common/lib/potions/*", "native-effects", ["common/lib/potions/WarpEffects.java"]),
    ("common/lib/utils/InventoryUtils.java", "distributed-inventory-operations", ["api/ThaumcraftInvHelper.java", "common/golems/seals/SealInventoryOps.java", "common/lib/research/theorycraft/TheorycraftInventory.java"]),
    ("common/lib/utils/Utils.java", "distributed-loot-and-mining-operations", ["common/lib/utils/TCLegacyLoot.java", "common/lib/enchantment/SpecialMiningResults.java", "common/golems/seals/SealBlockActions.java"]),
    ("common/lib/utils/BlockUtils.java", "distributed-block-operations", ["common/items/casters/FocusBlockActions.java", "common/golems/seals/SealBlockActions.java", "common/world/features/TCTreeSupport.java"]),
    ("common/lib/utils/BlockStateUtils.java", "native-block-state-properties", ["common/blocks/devices/BlockWorldDevice.java", "common/tiles/devices/DeviceRules.java"]),
    ("common/lib/utils/HexUtils.java", "native-rift-and-gui-math", ["client/lib/events/HudGeometry.java", "client/lib/ender/SourceRenderMath.java"]),
    ("common/lib/utils/PosXY.java", "native-records", ["client/gui/model/ResearchRecipeGeometry.java"]),
    ("common/lib/utils/RandomItemChooser.java", "native-weighted-loot", ["api/internal/WeightedRandomLoot.java", "common/lib/utils/LegacyLootRules.java"]),
    ("common/tiles/TileThaumcraft*", "native-block-entity-base", ["common/tiles/devices/DeviceTile.java", "common/tiles/crafting/TileArcaneWorkbench.java"]),
    ("common/tiles/crafting/FocalData.java", "native-focus-data", ["common/tiles/crafting/FocusElementNode.java", "common/tiles/crafting/TileFocalManipulator.java"]),
    ("common/tiles/crafting/TilePatternCrafter.java", "shared-device-storage", ["common/tiles/devices/TileStorageDevice.java"]),
    ("common/tiles/devices/TileLamp*.java", "shared-lamp-logic", ["common/tiles/devices/TileLampDevice.java"]),
    ("common/tiles/devices/TileVisGenerator.java", "native-energy-device", ["common/tiles/devices/TileEnergyDevice.java"]),
    ("common/tiles/devices/TileRechargePedestal.java", "native-energy-device", ["common/tiles/devices/TileEnergyDevice.java"]),
    ("common/tiles/devices/TileLevitator.java", "native-energy-device", ["common/tiles/devices/TileEnergyDevice.java"]),
    ("common/tiles/devices/TileWaterJug.java", "shared-fluid-device", ["common/tiles/devices/TileFluidDevice.java"]),
    ("common/tiles/devices/TileSpa.java", "shared-fluid-device", ["common/tiles/devices/TileFluidDevice.java"]),
    ("common/tiles/devices/TilePotionSprayer.java", "shared-fluid-device", ["common/tiles/devices/TileFluidDevice.java"]),
    ("common/tiles/devices/TileHungryChest.java", "shared-device-storage", ["common/tiles/devices/TileStorageDevice.java"]),
    ("common/tiles/devices/TileJarBrain.java", "shared-device-storage", ["common/tiles/devices/TileStorageDevice.java"]),
    ("common/tiles/devices/*", "shared-device-signals", ["common/tiles/devices/TileSignalDevice.java"]),
    ("common/tiles/essentia/TileJar.java", "native-jar-base", ["common/tiles/essentia/TileJarFillable.java", "common/tiles/essentia/TileJarFillableVoid.java"]),
    ("common/tiles/misc/TileMemory.java", "native-remembered-block-state", ["common/tiles/misc/TileHole.java"]),
    ("common/world/ThaumcraftWorldGenerator.java", "native-feature-and-biome-modifiers", ["common/world/features/TCWorldgen.java", "common/world/features/TCUndergroundFeature.java", "common/world/features/TCVegetationFeature.java"]),
    ("common/world/aura/AuraWorld.java", "native-level-aura-ownership", ["common/world/aura/AuraHandler.java", "common/world/aura/AuraEvents.java"]),
    ("common/world/biomes/*", "native-biome-data", ["common/world/biomes/TCBiomes.java", "common/world/biomes/TCMobSpawnModifier.java", "common/world/features/TCLegacyBiomeDecoration.java"]),
    ("common/world/objects/WorldGenBigMagicTree.java", "native-tree-feature", ["common/world/features/TCBigMagicTreeFeature.java"]),
    ("common/world/objects/WorldGenCustomFlowers.java", "native-flower-feature", ["common/world/features/TCFlowersFeature.java"]),
    ("common/world/objects/WorldGenGreatwoodTrees.java", "native-tree-feature", ["common/world/features/TCGreatwoodTreeFeature.java"]),
    ("common/world/objects/WorldGenSilverwoodTrees.java", "native-tree-feature", ["common/world/features/TCSilverwoodTreeFeature.java"]),
    ("common/world/objects/WorldGenMound.java", "native-structure", ["common/world/structures/TCMoundStructure.java", "common/world/structures/TCMoundPiece.java"]),
    ("proxies/*", "native-sided-registration", ["Thaumcraft.java", "ThaumcraftClient.java", "Registrar.java"]),
]


def read(path):
    return path.read_text(encoding="utf-8-sig")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def code(text):
    # Retain strings, so URL/regex literals cannot accidentally consume later Java code.
    token = r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\r\n]*|/\*[\s\S]*?\*/'
    return re.sub(token, lambda m: " " if m[0].startswith(("//", "/*")) else m[0], text)


def public_names(text):
    text = re.sub(r"@\w+(?:\([^()]*\))?\s*", "", code(text))
    result = set(re.findall(r"\bpublic\s+(?:(?:static|final|abstract|synchronized|default)\s+)*(?:[\w<>?\[\].,]+\s+)+(\w+)\s*\(", text))
    if re.search(r"\binterface\s+\w+", text):
        result.update(re.findall(r"(?:^|(?<=[;{}]))\s*(?:default\s+)?[\w<>?\[\].]+\s+(\w+)\s*\([^{};]*\)\s*(?=[;{])", text, re.M))
    return sorted(result)


def resource_index():
    result = {}
    for root in RESOURCES:
        for path in root.rglob("*"):
            if path.is_file():
                result[path.relative_to(root).as_posix()] = path
    return result


def model_links(resources):
    edges, missing = [], []
    for name, path in sorted(resources.items()):
        if not name.startswith("assets/thaumcraft/models/") or path.suffix != ".json": continue
        model = json.loads(read(path))
        for kind, values in (("model", [model.get("parent")]), ("texture", model.get("textures", {}).values())):
            for value in values:
                if not isinstance(value, str) or not value.startswith("thaumcraft:"): continue
                target = "assets/thaumcraft/" + ("models/" if kind == "model" else "textures/") + value.split(":",1)[1] + (".json" if kind == "model" else ".png")
                edges.append({"from": name, "to": target, "kind": kind})
                if target not in resources: missing.append(edges[-1])
    return {"edges": edges, "missing": missing,
            "bounds": "Literal TC model parents/textures only; inherited #variables, renderer geometry and optional external namespaces need their own contracts."}


def language_inventory():
    remap = json.loads(read(ROOT / "tools/lang_remap.json"))
    additions = json.loads(read(ROOT / "tools/lang_port_additions.json"))
    result = []
    for path in sorted((ROOT / "reference/tc6/src/assets/thaumcraft/lang").glob("*.lang")):
        expected = {remap.get(key,convert_lang.auto_key(key)): value for key,value in convert_lang.parse_lang(path).items()
                    if remap.get(key,convert_lang.auto_key(key)) is not None}
        expected.update(additions.get(path.stem.lower(),{}))
        target = ROOT / "port/src/main/resources/assets/thaumcraft/lang" / (path.stem.lower() + ".json")
        actual = json.loads(read(target)) if target.exists() else {}
        result.append({"source": path.relative_to(ROOT).as_posix(), "sourceSha256": digest(path),
                       "native": target.relative_to(ROOT).as_posix(), "nativeSha256": digest(target) if target.exists() else None,
                       "expectedKeys": len(expected), "nativeKeys": len(actual), "missingKeys": sorted(expected.keys()-actual.keys()),
                       "differentValues": sorted(key for key in expected.keys() & actual.keys() if expected[key] != actual[key])})
    return result


def inventory(recipe_report):
    old = {p.relative_to(SOURCE).as_posix(): p for p in SOURCE.rglob("*.java")}
    new = {p.relative_to(NATIVE).as_posix(): p for p in NATIVE.rglob("*.java")}
    native_code = {name: code(read(path)) for name,path in new.items()}
    rows = []
    for name, path in sorted(old.items()):
        if name in new:
            mapping, targets = "same-path-implementation", [name]
        else:
            route = next((r for r in ROUTES if fnmatch.fnmatchcase(name, r[0])), None)
            mapping, targets = (route[1], route[2]) if route else ("unmapped", [])
        missing = [target for target in targets if target not in new]
        rows.append({"source": "reference/tc6/src/thaumcraft/" + name, "sourceSha256": digest(path),
                     "sourceLines": len(read(path).splitlines()), "mapping": mapping,
                     "implementations": [{"path": "port/src/main/java/thaumcraft/" + target,
                                          "sha256": digest(new[target])} for target in targets if target in new],
                     "missingMappingTargets": missing,
                     "publicMethodNameCandidates": sorted(set(public_names(read(path))) -
                         {method for target in targets if target in new for method in public_names(read(new[target]))})})
    assets = []
    original_assets = ROOT / "reference/tc6/src/assets/thaumcraft"
    resources = resource_index()
    for folder in ("textures", "sounds", "research"):
        for path in sorted((original_assets / folder).rglob("*")):
            if not path.is_file(): continue
            relative = path.relative_to(original_assets).as_posix()
            if folder == "research" and path.suffix != ".json":
                assets.append({"source": "reference/tc6/src/assets/thaumcraft/" + relative,
                               "sourceSha256": digest(path), "status": "documentation-only"})
                continue
            if folder == "research":
                native = "data/thaumcraft/research/" + path.name
            else:
                native = "assets/thaumcraft/" + relative.replace("textures/items/", "textures/item/").replace("textures/blocks/", "textures/block/")
            target = resources.get(native)
            assets.append({"source": "reference/tc6/src/assets/thaumcraft/" + relative,
                           "sourceSha256": digest(path), "native": native,
                           "nativeSha256": digest(target) if target else None,
                           "status": "byte-identical" if target and digest(path) == digest(target)
                               else "changed" if target else "missing"})
    sound_source = original_assets / "sounds.json"
    sound_native = resources.get("assets/thaumcraft/sounds.json")
    source_sound_events = json.loads(read(sound_source))
    native_sound_events = json.loads(read(sound_native)) if sound_native else {}
    normalized_sound_events = {("runicshieldcharge" if key == "runicshieldecharge" else key): value
                               for key,value in source_sound_events.items()}
    assets.append({"source": sound_source.relative_to(ROOT).as_posix(), "sourceSha256": digest(sound_source),
                   "native": "assets/thaumcraft/sounds.json", "nativeSha256": digest(sound_native) if sound_native else None,
                   "status": "byte-identical" if sound_native and digest(sound_native) == digest(sound_source)
                       else "source-typo-renamed" if normalized_sound_events == native_sound_events else "missing-or-changed",
                   "eventRename": {"runicshieldecharge": "runicshieldcharge"}})
    declarations = {}
    joined_source = "\n".join(code(read(p)) for p in old.values())
    joined_native = "\n".join(text for name,text in native_code.items() if "Test" not in Path(name).name)
    for owner, field_type in (("BlocksTC", "Block"), ("ItemsTC", "Item")):
        source_file = f"api/{'blocks' if owner == 'BlocksTC' else 'items'}/{owner}.java"
        fields = [field.strip() for declaration in re.findall(r"public static " + field_type + r" ([\w\s,]+)\s*;", read(old[source_file]))
                  for field in declaration.split(",")]
        assigned_old = set(re.findall(r"\b" + owner + r"\.(\w+)\s*=", joined_source))
        assigned_new = set(re.findall(r"\b" + owner + r"\.(\w+)\s*=", joined_native))
        declarations[owner] = [{"field": name, "assignedInBeta26": name in assigned_old,
                                "assignedInNative": name in assigned_new} for name in fields]
    recipes = json.loads(read(recipe_report))
    links, languages = model_links(resources), language_inventory()
    historical_entities = json.loads(read(ROOT / "reference/validation/phase8-entity-coverage-current.json"))["sourceRows"]
    entities = []
    for row in historical_entities:
        literal = re.compile(r"\.build\(\s*\"(?:thaumcraft:)?" + re.escape(row["native"]) + r"\"\s*\)")
        owners = ["port/src/main/java/thaumcraft/" + name for name,text in native_code.items()
                  if "Test" not in Path(name).name and literal.search(text)]
        helper_call = re.compile(r'\bmob\(\s*"' + re.escape(row['native']) + r'"\s*,\s*' + re.escape(row['class']) + r'::new')
        if not owners:
            owners = ["port/src/main/java/thaumcraft/" + name for name,text in native_code.items()
                      if helper_call.search(text) and 'builder.build("thaumcraft:"+name)' in text]
        if not owners and row['native'] == 'special_item':
            owner = 'common/entities/SpecialItemRegistries.java'
            if '"special_item"' in native_code[owner] and '.build(id.toString())' in native_code[owner] and row['class'] + '::new' in native_code[owner]:
                owners = ['port/src/main/java/thaumcraft/' + owner]
        entities.append({"sourceName": row["source"], "sourceClass": row["class"], "nativeId": "thaumcraft:" + row["native"],
                         "nativeBuilderOwners": owners,
                         "bounds": "Builder identity only; source class/metadata inventory is historical, this owner lookup reads current code."})
    return {"format": 1, "date": "2026-10-12", "scope": "Complete file/data presence inventory plus reviewed contract fixes; not a gameplay or universal semantic parity verdict",
            "testsRun": False, "inventoryMethod": "Static file hashes, explicit implementation routes, public method-name candidates, source assignment expressions and current recipe coverage data",
            "bounds": ["A same-path file or mapped owner is evidence of an implementation, not proof of every method or gameplay branch.",
                       "Public method candidates include inherited methods, native API renames and consolidation; they are not automatically missing features.",
                       "Registry assignment expressions exclude dynamic/reflective bindings; flattened source fields are reviewed in the human report.",
                       "Compiler output and historical game reports are separate from this inventory."],
            "summary": {"sourceJavaFiles": len(old), "nativeJavaFilesIncludingGameTestCode": len(new),
                        "samePathFiles": sum(row['mapping'] == 'same-path-implementation' for row in rows),
                        "mappingCounts": dict(sorted(Counter(row['mapping'] for row in rows).items())),
                        "unmapped": [r['source'] for r in rows if r['mapping'] == 'unmapped'],
                        "brokenMappingTargets": sorted({t for r in rows for t in r['missingMappingTargets']}),
                        "assetCounts": dict(Counter(row['status'] for row in assets)),
                        "sourceTextures": sum(1 for p in (original_assets/'textures').rglob('*') if p.is_file()),
                        "sourceSounds": sum(1 for p in (original_assets/'sounds').rglob('*') if p.is_file()),
                        "sourceModels": sum(1 for p in (original_assets/'models').rglob('*') if p.is_file()),
                        "sourceBlockstates": sum(1 for p in (original_assets/'blockstates').rglob('*') if p.is_file()),
                        "languages": len(languages), "languageDeltaCount": sum(len(x['missingKeys'])+len(x['differentValues']) for x in languages),
                        "literalModelLinks": len(links['edges']), "missingLiteralModelLinks": len(links['missing']),
                        "sourceEntityRegistrations": len(entities), "entityBuilderGaps": [x['nativeId'] for x in entities if not x['nativeBuilderOwners']],
                        "recipeCounts": dict(Counter(row['status'] for row in recipes['entries'])),
                        "nonCraftingCounts": dict(Counter(row['status'] for row in recipes['nonCraftingEntries']))},
            "java": rows, "originalAssetBytes": assets, "publicRegistryFields": declarations,
            "recipes": recipes, "modelLinks": links, "languages": languages, "entityRegistrations": entities}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recipes", type=Path, required=True, help="Read-only tools/audit-phase5-recipes.ps1 -View Json output")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = inventory(args.recipes)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
