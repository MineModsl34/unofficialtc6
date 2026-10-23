package thaumcraft.api;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;

/** Public TC6 helper API adapted to native components, item tags and modern world types. */
public class ThaumcraftApiHelper {
    @Deprecated public static boolean areItemsEqual(ItemStack a, ItemStack b) { return ThaumcraftInvHelper.areItemsEqual(a, b); }
    @Deprecated public static boolean containsMatch(boolean strict, ItemStack[] inputs, List<ItemStack> targets) { return ThaumcraftInvHelper.containsMatch(strict, inputs, targets); }
    @Deprecated public static boolean areItemStacksEqualForCrafting(ItemStack stack, Object input) { return ThaumcraftInvHelper.areItemStacksEqualForCrafting(stack, input); }
    @Deprecated public static boolean areItemStackTagsEqualForCrafting(ItemStack slot, ItemStack recipe) { return ThaumcraftInvHelper.areItemStackTagsEqualForCrafting(slot, recipe); }
    @Deprecated public static boolean areItemStackTagsEqualRelaxed(ItemStack prime, ItemStack other) { return ThaumcraftInvHelper.areItemStackTagsEqualRelaxed(prime, other); }
    @Deprecated public static boolean compareTagsRelaxed(CompoundTag prime, CompoundTag other) { return ThaumcraftInvHelper.compareTagsRelaxed(prime, other); }
    @Deprecated public static net.neoforged.neoforge.items.IItemHandler getItemHandlerAt(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction side) { return ThaumcraftInvHelper.getItemHandlerAt(level, pos, side); }
    @Deprecated public static net.neoforged.neoforge.items.IItemHandler wrapInventory(net.minecraft.world.Container inventory, net.minecraft.core.Direction side) { return ThaumcraftInvHelper.wrapInventory(inventory, side); }

    public static int setByteInInt(int data, byte value, int index) {
        java.util.Objects.checkIndex(index, 4);
        int shift = (3 - index) * 8;
        return (data & ~(255 << shift)) | ((value & 255) << shift);
    }
    public static byte getByteInInt(int data, int index) {
        java.util.Objects.checkIndex(index, 4);
        return (byte)(data >>> ((3 - index) * 8));
    }
    public static int setNibbleInInt(int data, int nibble, int nibbleIndex) {
        int shift = nibbleIndex * 4;
        return data & ~(15 << shift) | nibble << shift;
    }
    public static int getNibbleInInt(int data, int nibbleIndex) { return data >> (nibbleIndex << 2) & 15; }

    /** Old wildcard ore-name API; modern callers can pass namespaced tags directly. */
    public static List<ItemStack> getOresWithWildCards(String name) {
        try { return getTaggedItemsWithWildCards(oreTagName(name)); }
        catch (IllegalArgumentException unsupported) { return List.of(); }
    }

    public static String oreTagName(String name) {
        String trimmed = name.trim();
        if (trimmed.equals("*")) return trimmed;
        if (trimmed.contains(":")) return trimmed;
        String sourceAlias = switch (trimmed) {
            case "logWood" -> "minecraft:logs"; case "plankWood" -> "minecraft:planks";
            case "slabWood" -> "minecraft:wooden_slabs"; case "treeSapling" -> "minecraft:saplings";
            case "treeLeaves" -> "minecraft:leaves"; case "trapdoorWood" -> "minecraft:wooden_trapdoors";
            case "nitor" -> "c:nitor"; case "quicksilver" -> "c:quicksilver";
            default -> null;
        };
        if (sourceAlias != null) return sourceAlias;
        // Source/common material families; arbitrary legacy categories require an explicit tag.
            String[][] families = {{"ingot", "ingots"}, {"nugget", "nuggets"}, {"dust", "dusts"},
                {"gem", "gems"}, {"ore", "ores"}, {"block", "storage_blocks"}, {"plate", "plates"}, {"stick", "rods"}, {"cluster", "clusters"}};
            for (String[] family : families) if (trimmed.startsWith(family[0]) && trimmed.length() > family[0].length()) {
                String material = trimmed.substring(family[0].length()).replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
                return "c:" + family[1] + "/" + material;
            }
        throw new IllegalArgumentException("Use a modern namespaced item tag for legacy ore name: " + name);
    }

    public static net.minecraft.world.item.crafting.Ingredient getIngredient(Object object) {
        if (object instanceof net.minecraft.world.item.crafting.Ingredient ingredient) return ingredient;
        if (object instanceof thaumcraft.api.crafting.IngredientNBTTC custom) return custom.toVanilla();
        if (object instanceof ItemStack stack) return stack.isEmpty()
            ? net.minecraft.world.item.crafting.Ingredient.EMPTY : thaumcraft.api.crafting.CrucibleRecipe.ingredient(stack);
        if (object instanceof net.minecraft.world.level.ItemLike item) return net.minecraft.world.item.crafting.Ingredient.of(item);
        if (object instanceof String tag) return net.minecraft.world.item.crafting.Ingredient.of(
            TagKey.create(Registries.ITEM, ResourceLocation.parse(oreTagName(tag))));
        if (object instanceof TagKey<?> tag && tag.registry().equals(Registries.ITEM)) {
            return net.minecraft.world.item.crafting.Ingredient.of(TagKey.create(Registries.ITEM, tag.location()));
        }
        if (object instanceof com.google.gson.JsonElement json) {
            return net.minecraft.world.item.crafting.Ingredient.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
        }
        if (object instanceof Object[] alternatives) return net.neoforged.neoforge.common.crafting.CompoundIngredient.of(
            java.util.Arrays.stream(alternatives).map(ThaumcraftApiHelper::getIngredient)
                .toArray(net.minecraft.world.item.crafting.Ingredient[]::new));
        if (object == null) return net.minecraft.world.item.crafting.Ingredient.EMPTY;
        throw new IllegalArgumentException("Unsupported ingredient: " + object);
    }

    /** A metadata wildcard covers every existing flattened item in the same reviewed family. */
    public static net.minecraft.world.item.crafting.Ingredient wildcardIngredient(ItemStack stack) {
        var identity = thaumcraft.common.lib.research.LegacyScanIdentity.identify(stack);
        var choices = new java.util.ArrayList<ItemStack>();
        for (var item : BuiltInRegistries.ITEM) {
            if (item == net.minecraft.world.item.Items.AIR) continue;
            ItemStack candidate = new ItemStack(item);
            if (thaumcraft.common.lib.research.LegacyScanIdentity.identify(candidate).itemId().equals(identity.itemId())) choices.add(candidate);
        }
        return net.minecraft.world.item.crafting.Ingredient.of(choices.stream());
    }

    /** Source 201-step DDA, deliberately skipping the block containing the ray origin. */
    public static net.minecraft.world.phys.BlockHitResult rayTraceIgnoringSource(net.minecraft.world.level.Level world,
            net.minecraft.world.phys.Vec3 start, net.minecraft.world.phys.Vec3 end,
            boolean stopOnLiquid, boolean ignoreWithoutCollisionBox, boolean returnLastMiss) {
        if (!finite(start) || !finite(end)) return null;
        int targetX = net.minecraft.util.Mth.floor(end.x), targetY = net.minecraft.util.Mth.floor(end.y), targetZ = net.minecraft.util.Mth.floor(end.z);
        int x = net.minecraft.util.Mth.floor(start.x), y = net.minecraft.util.Mth.floor(start.y), z = net.minecraft.util.Mth.floor(start.z);
        net.minecraft.world.phys.BlockHitResult lastMiss = null;
        for (int remaining = 200; remaining >= 0; remaining--) {
            if (!finite(start)) return null;
            if (x == targetX && y == targetY && z == targetZ) break;
            double edgeX = targetX > x ? x + 1.0 : x, edgeY = targetY > y ? y + 1.0 : y, edgeZ = targetZ > z ? z + 1.0 : z;
            double dx = end.x - start.x, dy = end.y - start.y, dz = end.z - start.z;
            double tx = targetX != x ? (edgeX - start.x) / dx : 999.0;
            double ty = targetY != y ? (edgeY - start.y) / dy : 999.0;
            double tz = targetZ != z ? (edgeZ - start.z) / dz : 999.0;
            if (tx == 0.0) tx = -1.0E-4;
            if (ty == 0.0) ty = -1.0E-4;
            if (tz == 0.0) tz = -1.0E-4;
            net.minecraft.core.Direction face;
            if (tx < ty && tx < tz) {
                face = targetX > x ? net.minecraft.core.Direction.WEST : net.minecraft.core.Direction.EAST;
                start = new net.minecraft.world.phys.Vec3(edgeX, start.y + dy * tx, start.z + dz * tx);
            } else if (ty < tz) {
                face = targetY > y ? net.minecraft.core.Direction.DOWN : net.minecraft.core.Direction.UP;
                start = new net.minecraft.world.phys.Vec3(start.x + dx * ty, edgeY, start.z + dz * ty);
            } else {
                face = targetZ > z ? net.minecraft.core.Direction.NORTH : net.minecraft.core.Direction.SOUTH;
                start = new net.minecraft.world.phys.Vec3(start.x + dx * tz, start.y + dy * tz, edgeZ);
            }
            x = net.minecraft.util.Mth.floor(start.x) - (face == net.minecraft.core.Direction.EAST ? 1 : 0);
            y = net.minecraft.util.Mth.floor(start.y) - (face == net.minecraft.core.Direction.UP ? 1 : 0);
            z = net.minecraft.util.Mth.floor(start.z) - (face == net.minecraft.core.Direction.SOUTH ? 1 : 0);
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
            var state = world.getBlockState(pos);
            boolean liquidBlock = state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock;
            if (ignoreWithoutCollisionBox && (liquidBlock || state.getCollisionShape(world, pos).isEmpty())) continue;
            boolean collidable = liquidBlock ? stopOnLiquid && state.getFluidState().isSource() : !state.isAir();
            if (collidable) {
                var shape = liquidBlock ? net.minecraft.world.phys.shapes.Shapes.block() : state.getShape(world, pos);
                var hit = shape.clip(start, end, pos);
                if (hit != null) return hit;
            } else lastMiss = net.minecraft.world.phys.BlockHitResult.miss(start, face, pos);
        }
        return returnLastMiss ? lastMiss : null;
    }

    private static boolean finite(net.minecraft.world.phys.Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
    /** Exact source ByteBuffer BIG_ENDIAN indexing: index0 is bits56..63, not the low byte. */
    public static long setByteInLong(long data, byte value, int index) {
        java.util.Objects.checkIndex(index, 8);
        int shift = (7 - index) * 8;
        return (data & ~(255L << shift)) | (((long)value & 255L) << shift);
    }
    public static byte getByteInLong(long data, int index) {
        java.util.Objects.checkIndex(index, 8);
        return (byte)(data >>> ((7 - index) * 8));
    }
    /** Stage 8 native holder; -2 unprocessed, -1 ordinary, 0..13 champion. */
    public static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> CHAMPION_MOD;
    /** Original transport-TE neighbor helper; native unloaded chunks are not brought in. */
    public static net.minecraft.world.level.block.entity.BlockEntity getConnectableTile(net.minecraft.world.level.BlockGetter world,
        net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face) {
        return thaumcraft.common.tiles.essentia.TransportConnections.getConnectableTile(world, pos, face);
    }
    public static net.minecraft.world.level.block.entity.BlockEntity getConnectableTile(net.minecraft.world.level.Level world,
        net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face) {
        return getConnectableTile((net.minecraft.world.level.BlockGetter)world, pos, face);
    }
    public static Object getNBTDataFromId(CompoundTag nbt, byte id, String key) {
        switch (id) {
            case Tag.TAG_BYTE:
                return nbt.getByte(key);
            case Tag.TAG_SHORT:
                return nbt.getShort(key);
            case Tag.TAG_INT:
                return nbt.getInt(key);
            case Tag.TAG_LONG:
                return nbt.getLong(key);
            case Tag.TAG_FLOAT:
                return nbt.getFloat(key);
            case Tag.TAG_DOUBLE:
                return nbt.getDouble(key);
            case Tag.TAG_BYTE_ARRAY:
                return nbt.getByteArray(key);
            case Tag.TAG_STRING:
                return nbt.getString(key);
            case Tag.TAG_LIST:
                return nbt.getList(key, Tag.TAG_COMPOUND);
            case Tag.TAG_COMPOUND:
                return nbt.get(key);
            case Tag.TAG_INT_ARRAY:
                return nbt.getIntArray(key);
            default:
                return null;
        }
    }

    /**
     * Replacement for getOresWithWildCards: item tags instead of the Ore Dictionary.
     * "c:ingots/iron": all items of the tag; "c:ingots/*": all items of all tags with this prefix.
     * Before datapacks load, tags are empty and the method returns an empty list.
     */
    public static List<ItemStack> getTaggedItemsWithWildCards(String itemTag) {
        String t = itemTag.trim();
        List<ItemStack> out = new ArrayList<>();
        if (t.endsWith("*")) {
            String prefix = t.replace("*", "");
            BuiltInRegistries.ITEM.getTags()
                .filter(pair -> pair.getFirst().location().toString().startsWith(prefix))
                .forEach(pair -> pair.getSecond().forEach(holder -> out.add(new ItemStack(holder))));
        } else {
            ResourceLocation id = ResourceLocation.tryParse(t);
            if (id != null) {
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id))
                    .ifPresent(set -> set.forEach(holder -> out.add(new ItemStack(holder))));
            }
        }

        return out;
    }

    public static ItemStack makeCrystal(Aspect aspect, int stackSize) {
        if (aspect == null) {
            return ItemStack.EMPTY;
        }

        ItemStack is = new ItemStack(ItemsTC.crystalEssence, stackSize);
        ((ItemGenericEssentiaContainer) ItemsTC.crystalEssence).setAspects(is, new AspectList().add(aspect, 1));
        return is;
    }

    public static ItemStack makeCrystal(Aspect aspect) {
        return makeCrystal(aspect, 1);
    }
}
