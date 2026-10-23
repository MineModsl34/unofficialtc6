package thaumcraft.common.lib.crafting;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.crafting.Part;
import thaumcraft.common.blocks.basic.BlockPillar;
import thaumcraft.common.blocks.devices.BlockInlay;

/** The three exact ConfigRecipes.initializeCompoundRecipes infusion blueprints. No future
 * infernal/thaumatorium/clockwork structure is registered before its actual runtime exists.
 * Original Ancient/Eldritch central pedestal meta2/meta1 means CHARGE2/CHARGE1, not a variant;
 * that source gate is retained explicitly, including its inconvenient charged-pedestal quirk.
 */
public final class InfusionDustTriggers {
    private static boolean initialized;
    private static List<Blueprint> blueprints = List.of();
    private InfusionDustTriggers() {}

    /** Invoke after DustTriggers.initialize and block registration, including lazy fallback only
     * after that method sets its own initialized flag. Builtins remain ahead of addon triggers.
     */
    public static synchronized void initializeInfusion() {
        if (initialized) return;
        require(BlocksTC.infusionMatrix, "infusionMatrix");
        var normal = definition("infusionaltar", "INFUSION", require(BlocksTC.stoneArcane, "stoneArcane"),
            require(BlocksTC.pedestalArcane, "pedestalArcane"), require(BlocksTC.pillarArcane, "pillarArcane"), 0);
        var ancient = definition("infusionaltarancient", "INFUSIONANCIENT", require(BlocksTC.stoneAncient, "stoneAncient"),
            require(BlocksTC.pedestalAncient, "pedestalAncient"), require(BlocksTC.pillarAncient, "pillarAncient"), 2);
        var eldritch = definition("infusionaltareldritch", "INFUSIONELDRITCH", require(BlocksTC.stoneEldritchTile, "stoneEldritchTile"),
            require(BlocksTC.pedestalEldritch, "pedestalEldritch"), require(BlocksTC.pillarEldritch, "pillarEldritch"), 1);
        blueprints = List.of(normal, ancient, eldritch);
        List<IDustTrigger> triggers = blueprints.stream().<IDustTrigger>map(blueprint -> new DustTriggerMultiblock(blueprint.research(),
            blueprint.parts(), () -> new ItemStack(BlocksTC.infusionMatrix))).toList();
        IDustTrigger.triggers.addAll(Math.min(5, IDustTrigger.triggers.size()), triggers);
        initialized = true;
    }
    /** Catalog adapter seam: ten material instances (8 stone + pedestal + matrix), exact source
     * Part descriptors retained. Call once from the common source catalog bootstrap, not per GUI.
     */
    public static List<Blueprint> blueprints() { return blueprints; }
    public record Blueprint(ResourceLocation id, String research, Part[][][] parts, List<ItemStack> ingredients) {}
    private static Block require(Block block, String name) {
        if (block == null) throw new IllegalStateException("Infusion dust initialized before " + name + " registration"); return block;
    }
    private static Blueprint definition(String id, String research, Block stone, Block pedestal, Block pillar, int charge) {
        Object[] pillars = {pillar(pillar, Direction.EAST), pillar(pillar, Direction.NORTH), pillar(pillar, Direction.SOUTH), pillar(pillar, Direction.WEST)};
        var center = pedestal.defaultBlockState().setValue(BlockInlay.CHARGE, charge);
        return new Blueprint(ResourceLocation.fromNamespaceAndPath("thaumcraft", id), research,
            sourceBlueprint(stone, BlocksTC.infusionMatrix, center, pillars), List.of(new ItemStack(stone, 8), new ItemStack(pedestal), new ItemStack(BlocksTC.infusionMatrix)));
    }
    private static ItemStack pillar(Block block, Direction direction) {
        ItemStack stack = new ItemStack(block);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(BlockPillar.FACING, direction)); return stack;
    }
    /** Engine-independent geometry seam; outputs are absolute E/N/S/W source ItemStack states. */
    static Part[][][] sourceBlueprint(Object stone, Object matrix, Object centralPedestal, Object[] pillars) {
        if (pillars.length != 4) throw new IllegalArgumentException("Infusion needs exactly four pillar targets");
        Part im = new Part(matrix, null), top = new Part(stone, "AIR"), center = new Part(centralPedestal, null);
        Part a = new Part(stone, pillars[0]), b = new Part(stone, pillars[1]), c = new Part(stone, pillars[2]), d = new Part(stone, pillars[3]);
        return new Part[][][]{
            {{null, null, null}, {null, im, null}, {null, null, null}},
            {{top, null, top}, {null, null, null}, {top, null, top}},
            {{a, null, b}, {null, center, null}, {c, null, d}}
        };
    }
}
