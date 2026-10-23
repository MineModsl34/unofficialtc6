package thaumcraft.common.lib.crafting;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.crafting.Part;

/** Complete source ConfigRecipes compound definitions for later genuine machines.
 * Common setup must run AFTER native block, ItemBlock and BE registrations. Missing bindings
 * skip the whole definition BEFORE constructing Parts/stacks/triggers/catalog entries.
 * No future block, BE, proxy, surrogate Item or machine runtime is instantiated here.
 * Source BlockPlaceholder is NOT a BE: its real proxy blocks/items use exact source ids and
 * INVISIBLE render shape. Actual furnace/thaumatorium/press require EntityBlock + registered
 * BlockEntityType.isValid + native horizontal FACING; plain Block carriers cannot activate.
 */
public final class FutureDustTriggers {
    private static final Map<Machine, Installed> INSTALLED = new EnumMap<>(Machine.class);
    private FutureDustTriggers() {}

    private enum Binding {
        INFERNAL("infernalFurnace", "infernal_furnace", () -> BlocksTC.infernalFurnace, true, false),
        BRICK_PROXY("placeholderNetherbrick", "placeholder_brick", () -> BlocksTC.placeholderNetherbrick, false, true),
        OBSIDIAN_PROXY("placeholderObsidian", "placeholder_obsidian", () -> BlocksTC.placeholderObsidian, false, true),
        THAUMATORIUM("thaumatorium", "thaumatorium", () -> BlocksTC.thaumatorium, true, false),
        THAUMATORIUM_TOP("thaumatoriumTop", "thaumatorium_top", () -> BlocksTC.thaumatoriumTop, true, false),
        ALCHEMICAL_METAL("metalAlchemical", "metal_alchemical", () -> BlocksTC.metalAlchemical, false, false),
        CRUCIBLE("crucible", "crucible", () -> BlocksTC.crucible, false, false),
        GOLEM_BUILDER("golemBuilder", "golem_builder", () -> BlocksTC.golemBuilder, true, false),
        BARS_PROXY("placeholderBars", "placeholder_bars", () -> BlocksTC.placeholderBars, false, true),
        CAULDRON_PROXY("placeholderCauldron", "placeholder_cauldron", () -> BlocksTC.placeholderCauldron, false, true),
        ANVIL_PROXY("placeholderAnvil", "placeholder_anvil", () -> BlocksTC.placeholderAnvil, false, true),
        TABLE_PROXY("placeholderTable", "placeholder_table", () -> BlocksTC.placeholderTable, false, true),
        STONE_TABLE("tableStone", "table_stone", () -> BlocksTC.tableStone, false, false);

        final String sourceAlias, registryPath;
        final Supplier<Block> alias;
        final boolean machine, proxy;
        Binding(String sourceAlias, String registryPath, Supplier<Block> alias, boolean machine, boolean proxy) {
            this.sourceAlias = sourceAlias; this.registryPath = registryPath; this.alias = alias; this.machine = machine; this.proxy = proxy;
        }
    }

    private enum Machine {
        INFERNAL("infernalfurnace", "INFERNALFURNACE", Binding.INFERNAL,
            List.of(Binding.INFERNAL, Binding.BRICK_PROXY, Binding.OBSIDIAN_PROXY),
            List.of(new Material("minecraft:nether_bricks", 12), new Material("minecraft:obsidian", 12), new Material("minecraft:iron_bars", 1), new Material("minecraft:lava_bucket", 1)), false),
        THAUMATORIUM("thaumatorium", "THAUMATORIUM", Binding.THAUMATORIUM,
            List.of(Binding.THAUMATORIUM, Binding.THAUMATORIUM_TOP, Binding.ALCHEMICAL_METAL, Binding.CRUCIBLE),
            List.of(new Material("thaumcraft:metal_alchemical", 2), new Material("thaumcraft:crucible", 1)), false),
        GOLEM_PRESS("golempress", "MINDCLOCKWORK", Binding.GOLEM_BUILDER,
            List.of(Binding.GOLEM_BUILDER, Binding.BARS_PROXY, Binding.CAULDRON_PROXY, Binding.ANVIL_PROXY, Binding.TABLE_PROXY, Binding.STONE_TABLE),
            List.of(new Material("minecraft:iron_bars", 1), new Material("minecraft:cauldron", 1), new Material("minecraft:piston", 1), new Material("minecraft:anvil", 1), new Material("thaumcraft:table_stone", 1)), true);

        final String path, research;
        final Binding output;
        final List<Binding> dependencies;
        final List<Material> materials;
        final boolean explicitDisplay;
        Machine(String path, String research, Binding output, List<Binding> dependencies, List<Material> materials, boolean explicitDisplay) {
            this.path = path; this.research = research; this.output = output; this.dependencies = dependencies; this.materials = materials; this.explicitDisplay = explicitDisplay;
        }
    }

    public record Material(String nativeId, int count) {}
    public record Definition(String id, String research, List<String> requiredBindings, List<Material> materials, boolean explicitDisplay) {}
    private record Installed(DustTriggerMultiblock trigger, ThaumcraftApi.BluePrint catalog) {}

    /** Read-only source definitions exist even while actual machine bindings remain absent. */
    public static List<Definition> definitions() {
        return java.util.Arrays.stream(Machine.values()).map(machine -> new Definition("thaumcraft:" + machine.path, machine.research,
            machine.dependencies.stream().map(binding -> binding.sourceAlias + "=thaumcraft:" + binding.registryPath).toList(), machine.materials, machine.explicitDisplay)).toList();
    }

    /** Root hook after initializeInfusion. May be called again after later real registrations;
     * missing definitions are retried, successful ones never duplicated or re-created.
     * Source order: first5, infernal, three infusions, thaumatorium, golem press, then add-ons.
     */
    public static synchronized void initializeFuture() {
        for (Machine machine : Machine.values()) {
            if (INSTALLED.containsKey(machine)) continue;
            Map<Binding, Block> resolved = new EnumMap<>(Binding.class);
            for (Binding binding : machine.dependencies) {
                Block block = resolve(binding);
                if (block != null) resolved.put(binding, block);
            }
            if (!allBindingsReady(machine.dependencies, resolved::containsKey)) continue;
            Part[][][] parts = build(machine, resolved);
            Block output = resolved.get(machine.output);
            ItemStack receipt = new ItemStack(output);
            // Validate all actual source inputs/materials/items before exposing either half.
            List<ItemStack> materials = machine.materials.stream().map(FutureDustTriggers::material).toList();
            if (!DustConversion.isSupportedOutput(receipt) || materials.stream().anyMatch(ItemStack::isEmpty)) continue;
            var trigger = new DustTriggerMultiblock(machine.research, parts, receipt::copy);
            var catalog = new ThaumcraftApi.BluePrint(machine.research, machine.explicitDisplay ? receipt.copy() : null, parts, materials.toArray(ItemStack[]::new));
            int sourceIndex = machine == Machine.INFERNAL ? 5 : 5 + InfusionDustTriggers.blueprints().size()
                + (INSTALLED.containsKey(Machine.INFERNAL) ? 1 : 0)
                + (machine == Machine.GOLEM_PRESS && INSTALLED.containsKey(Machine.THAUMATORIUM) ? 1 : 0);
            IDustTrigger.triggers.add(Math.min(sourceIndex, IDustTrigger.triggers.size()), trigger);
            ThaumcraftApi.addMultiblockRecipeToCatalog(ResourceLocation.fromNamespaceAndPath("thaumcraft", machine.path), catalog);
            INSTALLED.put(machine, new Installed(trigger, catalog));
        }
    }

    /** Pure readiness seam: false for ANY missing/proxy/wrong-id/unregistered dependency. */
    static <T> boolean allBindingsReady(List<T> bindings, Predicate<T> ready) { return bindings.stream().allMatch(ready); }

    private static Block resolve(Binding binding) {
        Block block = binding.alias.get();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("thaumcraft", binding.registryPath);
        if (block == null || block.defaultBlockState().isAir() || !BuiltInRegistries.BLOCK.containsKey(id) || BuiltInRegistries.BLOCK.get(id) != block) return null;
        if (!(block.asItem() instanceof BlockItem item) || item.getBlock() != block || !BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) != item) return null;
        if (binding.proxy && block.defaultBlockState().getRenderShape() != RenderShape.INVISIBLE) return null;
        if (binding.machine && (!(block instanceof EntityBlock) || !block.defaultBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
            || BuiltInRegistries.BLOCK_ENTITY_TYPE.stream().noneMatch(type -> type.isValid(block.defaultBlockState())))) return null;
        return block;
    }

    private static ItemStack material(Material material) {
        ResourceLocation id = ResourceLocation.parse(material.nativeId);
        if (!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id) == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(id), material.count);
    }

    private static Part[][][] build(Machine machine, Map<Binding, Block> blocks) {
        return switch (machine) {
            case INFERNAL -> infernalBlueprint(Blocks.NETHER_BRICKS, new ItemStack(blocks.get(Binding.BRICK_PROXY)),
                Blocks.OBSIDIAN, new ItemStack(blocks.get(Binding.OBSIDIAN_PROXY)), Blocks.IRON_BARS, DustMultiblockPattern.MaterialKind.LAVA, blocks.get(Binding.INFERNAL));
            case THAUMATORIUM -> thaumatoriumBlueprint(blocks.get(Binding.ALCHEMICAL_METAL).defaultBlockState(), blocks.get(Binding.THAUMATORIUM_TOP),
                blocks.get(Binding.THAUMATORIUM), blocks.get(Binding.CRUCIBLE));
            case GOLEM_PRESS -> golemBlueprint(Blocks.IRON_BARS, new ItemStack(blocks.get(Binding.BARS_PROXY)),
                new DustMultiblockPattern.BlockFamily(List.of(Blocks.CAULDRON, Blocks.WATER_CAULDRON)), new ItemStack(blocks.get(Binding.CAULDRON_PROXY)),
                Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), blocks.get(Binding.GOLEM_BUILDER),
                new DustMultiblockPattern.BlockFamily(List.of(Blocks.ANVIL, Blocks.CHIPPED_ANVIL, Blocks.DAMAGED_ANVIL)), new ItemStack(blocks.get(Binding.ANVIL_PROXY)),
                blocks.get(Binding.STONE_TABLE), new ItemStack(blocks.get(Binding.TABLE_PROXY)));
        };
    }

    /** Exact source three3x3 layers: 12NB,12OB,1bars->AIR,1lava->opposite-facing furnace. */
    static Part[][][] infernalBlueprint(Object netherBrick, Object brickProxy, Object obsidian, Object obsidianProxy, Object bars, Object lava, Object furnace) {
        Part nb = new Part(netherBrick, brickProxy), ob = new Part(obsidian, obsidianProxy);
        Part ib = new Part(bars, "AIR"), la = new Part(lava, furnace, true);
        return new Part[][][]{
            {{nb, ob, nb}, {ob, null, ob}, {nb, ob, nb}},
            {{nb, ob, nb}, {ob, la, ob}, {nb, ib, nb}},
            {{nb, ob, nb}, {ob, ob, ob}, {nb, ob, nb}}
        };
    }

    /** Source's exact default metal STATES, target BLOCKS and player-facing flags; crucible retained. */
    static Part[][][] thaumatoriumBlueprint(Object metalDefaultState, Object top, Object bottom, Object crucible) {
        return new Part[][][]{
            {{new Part(metalDefaultState, top).setApplyPlayerFacing(true)}},
            {{new Part(metalDefaultState, bottom).setApplyPlayerFacing(true)}},
            {{new Part(crucible, null)}}
        };
    }

    /** Source two2x2 layers and upward piston EXACT state, not any piston/facing. */
    static Part[][][] golemBlueprint(Object bars, Object barsProxy, Object cauldron, Object cauldronProxy, Object pistonUpState, Object builder,
                                    Object anvil, Object anvilProxy, Object table, Object tableProxy) {
        return new Part[][][]{
            {{null, null}, {new Part(bars, barsProxy), null}},
            {{new Part(cauldron, cauldronProxy), new Part(anvil, anvilProxy)}, {new Part(pistonUpState, builder), new Part(table, tableProxy)}}
        };
    }
}
