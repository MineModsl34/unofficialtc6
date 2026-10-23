package thaumcraft.common.world.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Fail-closed, explicit import of externally supplied TC6 mound templates. This does not register
 * a feature or bundle an original asset. Vanilla's normal template loader silently substitutes AIR
 * for unknown IDs and ignores invalid properties; both old and fixed palettes are checked first.
 */
public final class LegacyMoundImporter {
    public static final int LEGACY_VERSION = 1343;
    private static final int MAX_BLOCKS = 19 * 17 * 19;
    private static final Set<String> STONE_BRICKS = Set.of("stonebrick", "mossy_stonebrick",
        "cracked_stonebrick", "chiseled_stonebrick");

    private LegacyMoundImporter() {
    }

    public static ImportResult read(Path externalTemplate) throws IOException {
        return importLegacy(NbtIo.readCompressed(externalTemplate, NbtAccounter.create(16L * 1024 * 1024)));
    }

    public static ImportResult importLegacy(CompoundTag source) {
        require(NbtUtils.getDataVersion(source, -1) == LEGACY_VERSION,
            "Expected explicit original DataVersion " + LEGACY_VERSION);
        requireMoundShape(source);
        List<ListTag> legacyPalettes = palettes(source);
        for (ListTag palette : legacyPalettes) {
            for (int index = 0; index < palette.size(); index++) {
                validateLegacyState(palette.getCompound(index), "legacy palette entry " + index);
            }
        }
        validateEntries(source, legacyPalettes);
        validateLegacyBlockEntityIds(source);
        CompoundTag fixed = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(),
            source.copy(), LEGACY_VERSION);
        fixed.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        requireMoundShape(fixed);
        restoreLegacyBarConnections(legacyPalettes, fixed);
        validateModern(fixed);
        require(fixed.getList("blocks", Tag.TAG_COMPOUND).size() == source.getList("blocks", Tag.TAG_COMPOUND).size(),
            "DataFixer changed the coordinate-mask size");
        require(positions(source).equals(positions(fixed)), "DataFixer changed the coordinate mask");
        validateRetainedSpecialData(source, fixed);
        int normalizedTrappedChests = normalizeAndValidateBlockEntities(fixed);
        StructureTemplate template = new StructureTemplate();
        // The first call to load occurs only after every palette entry and property has passed.
        template.load(BuiltInRegistries.BLOCK.asLookup(), fixed.copy());
        Map<String, Integer> counts = blockCounts(fixed);
        CompoundTag roundTrip = template.save(new CompoundTag());
        validateModern(roundTrip);
        require(blockCounts(roundTrip).equals(counts), "Template loading lost or substituted blocks");
        require(positions(roundTrip).equals(positions(fixed)), "Template loading changed the coordinate mask");
        return new ImportResult(template, fixed, Map.copyOf(counts), normalizedTrappedChests);
    }

    private static void restoreLegacyBarConnections(List<ListTag> original, CompoundTag fixed) {
        List<ListTag> modern = palettes(fixed);
        require(modern.size() == original.size(), "DataFixer changed the number of palettes");
        for (int paletteIndex = 0; paletteIndex < original.size(); paletteIndex++) {
            ListTag before = original.get(paletteIndex);
            ListTag after = modern.get(paletteIndex);
            require(before.size() == after.size(), "DataFixer changed palette indices");
            for (int index = 0; index < before.size(); index++) {
                CompoundTag legacyState = before.getCompound(index);
                if (!legacyState.getString("Name").equals("minecraft:iron_bars")) continue;
                CompoundTag state = after.getCompound(index);
                require(state.getString("Name").equals("minecraft:iron_bars"), "DataFixer changed bar identity/index");
                // BlockStateData maps all 16 old getActualState connection combinations to the
                // disconnected canonical state. Retain the explicitly captured and already validated
                // old connection properties; modern Template loading otherwise loses the real cage.
                CompoundTag properties = state.getCompound("Properties").copy();
                CompoundTag legacyProperties = legacyState.getCompound("Properties");
                for (String direction : List.of("north", "east", "south", "west")) {
                    if (legacyProperties.contains(direction, Tag.TAG_STRING)) {
                        properties.putString(direction, legacyProperties.getString(direction));
                    }
                }
                state.put("Properties", properties);
            }
        }
    }

    /** Public guard is also tested with synthetic, non-original fixtures. No template is loaded. */
    public static void validateModern(CompoundTag tag) {
        List<ListTag> allPalettes = palettes(tag);
        for (int paletteIndex = 0; paletteIndex < allPalettes.size(); paletteIndex++) {
            ListTag palette = allPalettes.get(paletteIndex);
            for (int index = 0; index < palette.size(); index++) {
                CompoundTag state = palette.getCompound(index);
                String context = "palette " + paletteIndex + " entry " + index;
                Block block = registeredBlock(state, context);
                validateProperties(state, block, context);
            }
        }
        validateEntries(tag, allPalettes);
    }

    public static Map<String, Integer> blockCounts(CompoundTag tag) {
        ListTag palette = palettes(tag).getFirst();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Tag raw : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            counts.merge(palette.getCompound(entry.getInt("state")).getString("Name"), 1, Integer::sum);
        }
        return counts;
    }

    private static void validateLegacyState(CompoundTag state, String context) {
        ResourceLocation name = stateName(state, context);
        String id = name.toString();
        CompoundTag properties = properties(state, context);
        switch (id) {
            case "minecraft:grass" -> legacyProperties(properties, context, Map.of("snowy", Set.of("true", "false")));
            case "minecraft:dirt" -> legacyProperties(properties, context, Map.of("snowy", Set.of("true", "false"),
                "variant", Set.of("dirt", "coarse_dirt", "podzol")));
            case "minecraft:tallgrass" -> legacyProperties(properties, context,
                Map.of("type", Set.of("dead_bush", "tall_grass", "fern")));
            case "minecraft:stonebrick" -> legacyProperties(properties, context, Map.of("variant", STONE_BRICKS));
            // The capture palette also contains untouched pad stone. Unused entries still require validation.
            case "minecraft:stone" -> legacyProperties(properties, context, Map.of("variant", Set.of("stone", "granite",
                "smooth_granite", "diorite", "smooth_diorite", "andesite", "smooth_andesite")));
            case "minecraft:tnt" -> legacyProperties(properties, context, Map.of("explode", Set.of("true", "false")));
            case "minecraft:mob_spawner" -> legacyProperties(properties, context, Map.of());
            case "minecraft:stone_stairs" -> validateProperties(state, Blocks.COBBLESTONE_STAIRS, context);
            default -> validateProperties(state, registeredBlock(state, context), context);
        }
    }

    private static void legacyProperties(CompoundTag properties, String context, Map<String, Set<String>> allowed) {
        for (String property : properties.getAllKeys()) {
            require(properties.contains(property, Tag.TAG_STRING), context + ": non-string property " + property);
            require(allowed.containsKey(property), context + ": unknown legacy property " + property);
            require(allowed.get(property).contains(properties.getString(property)),
                context + ": invalid legacy value for " + property + " = " + properties.getString(property));
        }
    }

    private static void validateProperties(CompoundTag state, Block block, String context) {
        CompoundTag properties = properties(state, context);
        for (String name : properties.getAllKeys()) {
            require(properties.contains(name, Tag.TAG_STRING), context + ": non-string property " + name);
            Property<?> property = block.getStateDefinition().getProperty(name);
            require(property != null, context + ": unknown property " + name + " for " + state.getString("Name"));
            require(property.getValue(properties.getString(name)).isPresent(), context + ": invalid value for " + name
                + " = " + properties.getString(name));
        }
    }

    private static CompoundTag properties(CompoundTag state, String context) {
        require(!state.contains("Properties") || state.contains("Properties", Tag.TAG_COMPOUND),
            context + ": Properties must be a compound");
        return state.getCompound("Properties");
    }

    private static Block registeredBlock(CompoundTag state, String context) {
        ResourceLocation id = stateName(state, context);
        require(BuiltInRegistries.BLOCK.containsKey(id), context + ": unregistered block " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static ResourceLocation stateName(CompoundTag state, String context) {
        require(state.contains("Name", Tag.TAG_STRING), context + ": missing block Name");
        ResourceLocation id = ResourceLocation.tryParse(state.getString("Name"));
        require(id != null, context + ": malformed block ID " + state.getString("Name"));
        return id;
    }

    private static List<ListTag> palettes(CompoundTag tag) {
        List<ListTag> result = new ArrayList<>();
        boolean single = tag.contains("palette", Tag.TAG_LIST);
        boolean multiple = tag.contains("palettes", Tag.TAG_LIST);
        require(single != multiple, "Expected exactly one of palette/palettes");
        if (single) {
            result.add(compoundList(tag, "palette"));
        } else {
            ListTag variants = tag.getList("palettes", Tag.TAG_LIST);
            require(!variants.isEmpty(), "Empty palettes");
            for (Tag raw : variants) {
                require(raw instanceof ListTag, "Palette variant must be a list");
                ListTag palette = (ListTag) raw;
                require(palette.getElementType() == Tag.TAG_COMPOUND, "Palette entries must be compounds");
                result.add(palette);
            }
        }
        for (ListTag palette : result) {
            require(!palette.isEmpty() && palette.size() <= MAX_BLOCKS, "Invalid palette size");
        }
        return result;
    }

    private static ListTag compoundList(CompoundTag tag, String key) {
        require(tag.contains(key, Tag.TAG_LIST), "Missing list " + key);
        ListTag list = (ListTag) tag.get(key);
        require(list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND, key + " entries must be compounds");
        return list;
    }

    private static void validateEntries(CompoundTag tag, List<ListTag> allPalettes) {
        ListTag size = tag.getList("size", Tag.TAG_INT);
        require(size.size() == 3 && size.getInt(0) > 0 && size.getInt(1) > 0 && size.getInt(2) > 0, "Invalid structure size");
        ListTag entries = compoundList(tag, "blocks");
        require(entries.size() <= MAX_BLOCKS, "Structure exceeds mound-volume bound");
        Set<BlockPos> seen = new HashSet<>();
        for (Tag raw : entries) {
            CompoundTag entry = (CompoundTag) raw;
            require(entry.contains("state", Tag.TAG_INT), "Missing integer state index");
            int state = entry.getInt("state");
            for (ListTag palette : allPalettes) {
                require(state >= 0 && state < palette.size(), "Out-of-range state index " + state);
            }
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            require(pos.size() == 3, "Invalid block position");
            for (int axis = 0; axis < 3; axis++) {
                require(pos.getInt(axis) >= 0 && pos.getInt(axis) < size.getInt(axis), "Block outside structure bounds");
            }
            require(seen.add(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2))), "Duplicate block position");
            require(!entry.contains("nbt") || entry.contains("nbt", Tag.TAG_COMPOUND), "Block entity NBT must be a compound");
        }
    }

    private static Set<BlockPos> positions(CompoundTag tag) {
        Set<BlockPos> result = new HashSet<>();
        for (Tag raw : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            ListTag pos = ((CompoundTag) raw).getList("pos", Tag.TAG_INT);
            result.add(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)));
        }
        return result;
    }

    private static void requireMoundShape(CompoundTag tag) {
        ListTag size = tag.getList("size", Tag.TAG_INT);
        require(size.size() == 3 && size.getInt(0) == 19 && size.getInt(1) == 17 && size.getInt(2) == 19,
            "Mound capture must be 19 x 17 x 19 with origin offset (0,-1,0)");
        require(tag.contains("palette", Tag.TAG_LIST) && !tag.contains("palettes"), "Original mound has one palette");
        require(!tag.contains("entities") || (tag.contains("entities", Tag.TAG_LIST)
            && compoundList(tag, "entities").isEmpty()), "Mound entities unsupported");
    }

    private static void validateLegacyBlockEntityIds(CompoundTag source) {
        for (Tag raw : source.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) raw;
            if (!block.contains("nbt", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag entity = block.getCompound("nbt");
            require(entity.contains("id", Tag.TAG_STRING), "Missing legacy block entity ID");
            String oldId = entity.getString("id");
            // The block flattened to minecraft:spawner, but the modern BE registry still uses mob_spawner.
            ResourceLocation id = ResourceLocation.tryParse(oldId);
            require(id != null && BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id), "Unregistered legacy block entity " + oldId);
        }
    }

    private static void validateRetainedSpecialData(CompoundTag source, CompoundTag fixed) {
        ListTag oldPalette = palettes(source).getFirst();
        ListTag fixedPalette = palettes(fixed).getFirst();
        Map<BlockPos, CompoundTag> fixedBlocks = new LinkedHashMap<>();
        for (Tag raw : fixed.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) raw;
            fixedBlocks.put(position(block), block);
        }
        for (Tag raw : source.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag old = (CompoundTag) raw;
            CompoundTag modern = fixedBlocks.get(position(old));
            String oldId = oldPalette.getCompound(old.getInt("state")).getString("Name");
            String fixedId = fixedPalette.getCompound(modern.getInt("state")).getString("Name");
            if (oldId.equals("minecraft:air") || !oldId.startsWith("minecraft:")) {
                require(oldId.equals(fixedId), "DataFixer substituted explicit AIR or a custom block at " + position(old));
            }
            require(old.contains("nbt", Tag.TAG_COMPOUND) == modern.contains("nbt", Tag.TAG_COMPOUND),
                "DataFixer removed or introduced block entity NBT at " + position(old));
            if (old.contains("nbt", Tag.TAG_COMPOUND)) {
                CompoundTag oldEntity = old.getCompound("nbt");
                CompoundTag fixedEntity = modern.getCompound("nbt");
                for (String field : List.of("LootTable", "LootTableSeed")) {
                    require(java.util.Objects.equals(oldEntity.get(field), fixedEntity.get(field)),
                        "DataFixer changed " + field + " at " + position(old));
                }
            }
        }
    }

    private static BlockPos position(CompoundTag block) {
        ListTag pos = block.getList("pos", Tag.TAG_INT);
        return new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
    }

    private static int normalizeAndValidateBlockEntities(CompoundTag fixed) {
        int adapted = 0;
        ListTag palette = palettes(fixed).getFirst();
        for (Tag raw : fixed.getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.contains("nbt", Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag nbt = entry.getCompound("nbt");
            String block = palette.getCompound(entry.getInt("state")).getString("Name");
            if (block.equals("minecraft:trapped_chest") && nbt.getString("id").equals("minecraft:chest")) {
                nbt.putString("id", "minecraft:trapped_chest");
                adapted++;
            }
            ResourceLocation type = ResourceLocation.tryParse(nbt.getString("id"));
            require(type != null && BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(type), "Unregistered block entity " + nbt.getString("id"));
            Block stateBlock = registeredBlock(palette.getCompound(entry.getInt("state")), "block entity state");
            require(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(type).isValid(stateBlock.defaultBlockState()),
                "Block entity " + type + " is incompatible with " + block);
        }
        return adapted;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("Rejected legacy mound: " + message);
        }
    }

    public record ImportResult(StructureTemplate template, CompoundTag fixedTag, Map<String, Integer> blockCounts,
        int normalizedTrappedChestIds) {
    }
}
