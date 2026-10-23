package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.container.ContainerArcaneWorkbench;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchTransfer;
import thaumcraft.common.lib.network.misc.PacketArcaneRecipeTransfer;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;
import thaumcraft.common.world.aura.AuraHandler;

/** Actual registered source recipes/15-slot tile/server menu/inventory transfers. Fixture
 * research is explicit; these tests do not claim JEI GUI, real TCP or survival progression QA.
 */
@GameTestHolder("thaumcraft_jei")
@PrefixGameTestTemplate(false)
public final class ArcaneTransferGameTests {
    private static ServerPlayer owner(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "TC_JEI_Transfer"));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
        var pos = helper.absolutePos(new BlockPos(3, 3, 3)); player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5); return player;
    }
    private static TileArcaneWorkbench table(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(3, 3, 3)); helper.getLevel().setBlock(pos, BlocksTC.arcaneWorkbench.defaultBlockState(), Block.UPDATE_ALL);
        return (TileArcaneWorkbench) helper.getLevel().getBlockEntity(pos);
    }
    private static ContainerArcaneWorkbench menu(ServerPlayer owner, TileArcaneWorkbench table) {
        var menu = new ContainerArcaneWorkbench(27, owner.getInventory(), table); owner.containerMenu = menu; return menu;
    }
    private static void cleanup(ServerPlayer... players) { for (var player : players) { player.containerMenu = player.inventoryMenu; PlayerDataSync.forget(player); } }
    private static void research(ServerPlayer player, String key, int stage) {
        var knowledge = ThaumcraftCapabilities.getKnowledge(player); knowledge.addResearch(key); knowledge.setResearchStage(key, stage);
    }
    private static PacketArcaneRecipeTransfer request(ContainerArcaneWorkbench menu, String recipe, boolean maximum) {
        return new PacketArcaneRecipeTransfer(menu.containerId, menu.getStateId(), 1, ResourceLocation.fromNamespaceAndPath("thaumcraft", recipe), maximum);
    }
    private static void thaumometerStock(ServerPlayer player, int batches) {
        player.getInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 4 * batches));
        player.getInventory().setItem(1, new ItemStack(Items.GLASS_PANE, batches));
        for (int aspect = 0; aspect < 6; aspect++) player.getInventory().setItem(2 + aspect, ThaumcraftApiHelper.makeCrystal(ArcaneWorkbenchCosts.PRIMALS[aspect], batches));
    }
    private static List<ItemStack> stored(ContainerArcaneWorkbench menu) {
        List<ItemStack> items = new ArrayList<>(51);
        for (int slot = 0; slot < 15; slot++) items.add(menu.table().getItem(slot).copy());
        for (int slot = 16; slot < 52; slot++) items.add(menu.slots.get(slot).getItem().copy()); return items;
    }
    private static int count(List<ItemStack> items, ItemStack key) {
        return items.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, key)).mapToInt(ItemStack::getCount).sum();
    }
    private static void conserved(GameTestHelper helper, List<ItemStack> before, ContainerArcaneWorkbench menu) {
        var after = stored(menu);
        for (var key : before) if (!key.isEmpty()) helper.assertTrue(count(before, key) == count(after, key), "transfer preserves full item components and exact total counts");
        for (var stack : after) if (!stack.isEmpty()) helper.assertTrue(before.stream().anyMatch(key -> ItemStack.isSameItemSameComponents(key, stack)), "transfer never invents a new item/component variant");
    }

    @GameTest(templateNamespace = "thaumcraft_jei", template = "scan_test")
    public static void singleSourceRecipeTransfersAndThenUsesTheNormalPaidCraftPath(GameTestHelper helper) {
        var owner = owner(helper); var table = table(helper); research(owner, "FIRSTSTEPS", 2); thaumometerStock(owner, 2);
        ItemStack displaced = new ItemStack(Items.DIAMOND, 3); displaced.set(DataComponents.CUSTOM_NAME, Component.literal("Keep complete components")); table.setItem(0, displaced);
        var pos = table.getBlockPos(); var chunk = AuraHandler.getAuraChunk(helper.getLevel(), pos.getX() >> 4, pos.getZ() >> 4);
        helper.assertTrue(chunk != null, "actual loaded chunk aura"); float previous = chunk.getVis(); chunk.setVis(60);
        try {
            var menu = menu(owner, table); var before = stored(menu); var packet = request(menu, "thaumometer", false);
            helper.assertTrue(menu.transferRecipe(owner, packet), "actual native source shaped recipe transfers one set"); conserved(helper, before, menu);
            for (int slot : new int[] {1, 3, 5, 7}) helper.assertTrue(table.getItem(slot).is(Items.GOLD_INGOT) && table.getItem(slot).getCount() == 1, "source shaped cross uses correct slots");
            helper.assertTrue(table.getItem(4).is(Items.GLASS_PANE) && table.getItem(4).getCount() == 1 && table.getItem(0).isEmpty(), "pattern holes cleared and central item placed");
            for (int aspect = 0; aspect < 6; aspect++) helper.assertTrue(ArcaneWorkbenchCosts.crystal(table.getItem(9 + aspect), aspect) && table.getItem(9 + aspect).getCount() == 1, "all six true crystal slots receive source cost");
            helper.assertTrue(chunk.getVis() == 60 && menu.getCarried().isEmpty(), "transfer neither pays aura nor creates output");
            var arranged = ArcaneWorkbenchTransfer.snapshot(menu);
            helper.assertFalse(menu.transferRecipe(owner, packet), "duplicate old-state request rejected"); helper.assertTrue(arranged.matches(menu), "rejected duplicate preserves all slots");
            menu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(menu.getCarried().is(ItemsTC.thaumometer) && menu.getCarried().getCount() == 1 && chunk.getVis() == 40, "normal output click still pays exact source20vis");
            for (int slot = 0; slot < 15; slot++) helper.assertTrue(table.getItem(slot).isEmpty(), "normal craft consumes grid and six crystal quantities");
            helper.assertTrue(count(stored(menu), displaced) == 3, "displaced item's name and count survive transfer/craft");
        } finally { chunk.setVis(previous); cleanup(owner); }
        helper.succeed();
    }
    @GameTest(templateNamespace = "thaumcraft_jei", template = "scan_test")
    public static void maximumNativeChargerRecipeHonorsTwoCrystalCostAndKeepsUnusedCrystals(GameTestHelper helper) {
        var owner = owner(helper); var table = table(helper); research(owner, "WORKBENCHCHARGER", 1);
        owner.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 64)); owner.getInventory().setItem(1, new ItemStack(BlocksTC.plankGreatwood, 64));
        owner.getInventory().setItem(2, new ItemStack(ItemsTC.visResonator, 64)); owner.getInventory().setItem(3, ThaumcraftApiHelper.makeCrystal(Aspect.AIR, 64));
        owner.getInventory().setItem(4, ThaumcraftApiHelper.makeCrystal(Aspect.ORDER, 64)); table.setItem(11, ThaumcraftApiHelper.makeCrystal(Aspect.WATER, 7));
        try {
            var menu = menu(owner, table); var before = stored(menu);
            helper.assertTrue(menu.transferRecipe(owner, request(menu, "workbenchcharger", true)), "source recipe max transfer succeeds"); conserved(helper, before, menu);
            for (int slot : new int[] {3, 5}) helper.assertTrue(table.getItem(slot).getCount() == 32 && table.getItem(slot).is(BlocksTC.plankGreatwood.asItem()), "two planks per batch limits max to32");
            for (int slot : new int[] {6, 8}) helper.assertTrue(table.getItem(slot).getCount() == 32 && table.getItem(slot).is(Items.IRON_INGOT), "two ingots per batch retained in separate pattern cells");
            helper.assertTrue(table.getItem(1).getCount() == 32 && table.getItem(9).getCount() == 64 && table.getItem(13).getCount() == 64, "two air/order crystals per batch bound real slot capacity");
            helper.assertTrue(table.getItem(11).getCount() == 7 && owner.getInventory().countItem(ItemsTC.visResonator) == 32, "unused crystal reserve and leftover resonators remain owned");
        } finally { cleanup(owner); }
        helper.succeed();
    }
    @GameTest(templateNamespace = "thaumcraft_jei", template = "scan_test")
    public static void nativeShapelessRecipeAndTrimmedTwoByTwoPatternTransferCorrectly(GameTestHelper helper) {
        var owner = owner(helper); var table = table(helper);
        try {
            research(owner, "BASEARTIFICE", 1); owner.getInventory().setItem(0, new ItemStack(ItemsTC.quicksilver, 5));
            owner.getInventory().setItem(1, new ItemStack(Items.GLASS_PANE, 5)); owner.getInventory().setItem(2, ThaumcraftApiHelper.makeCrystal(Aspect.WATER, 5));
            owner.getInventory().setItem(3, ThaumcraftApiHelper.makeCrystal(Aspect.ORDER, 5)); var menu = menu(owner, table); var before = stored(menu);
            helper.assertTrue(menu.transferRecipe(owner, request(menu, "mirrorglass", false)), "actual native shapeless recipe transfers"); conserved(helper, before, menu);
            helper.assertTrue(table.getItem(0).is(ItemsTC.quicksilver) && table.getItem(1).is(Items.GLASS_PANE) && table.getItem(11).getCount() == 1 && table.getItem(13).getCount() == 1,
                "shapeless native ingredient order and exact crystal slots");
            table.clearContent(); owner.getInventory().clearContent(); research(owner, "PAVINGSTONES", 1);
            owner.getInventory().setItem(0, new ItemStack(BlocksTC.stoneArcaneBrick, 4)); owner.getInventory().setItem(1, ThaumcraftApiHelper.makeCrystal(Aspect.AIR));
            owner.getInventory().setItem(2, ThaumcraftApiHelper.makeCrystal(Aspect.EARTH)); menu = menu(owner, table); before = stored(menu);
            helper.assertTrue(menu.transferRecipe(owner, request(menu, "pavetravel", false)), "actual native trimmed2x2 recipe transfers"); conserved(helper, before, menu);
            for (int slot : new int[] {0, 1, 3, 4}) helper.assertTrue(table.getItem(slot).is(BlocksTC.stoneArcaneBrick.asItem()), "2x2 pattern preserves full3-wide table stride");
            CraftingInput input = CraftingInput.ofPositioned(3, 3, stored(menu).subList(0, 9)).input();
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse("thaumcraft:pavetravel")).orElseThrow().value() instanceof thaumcraft.api.crafting.IArcaneRecipe recipe
                && recipe.matches(input, helper.getLevel()), "the arranged2x2 matrix matches actual native recipe");
        } finally { cleanup(owner); }
        helper.succeed();
    }
    @GameTest(templateNamespace = "thaumcraft_jei", template = "scan_test")
    public static void staleStateMissingCrystalUnknownResearchAndWrongOwnerAreAtomicFailures(GameTestHelper helper) {
        var owner = owner(helper); var other = owner(helper); var table = table(helper); thaumometerStock(owner, 2);
        try {
            var menu = menu(owner, table); var snapshot = ArcaneWorkbenchTransfer.snapshot(menu);
            helper.assertFalse(menu.transferRecipe(owner, request(menu, "thaumometer", false)), "unknown research rejects transfer"); helper.assertTrue(snapshot.matches(menu), "unknown research failure atomic");
            research(owner, "FIRSTSTEPS", 2); menu = menu(owner, table); snapshot = ArcaneWorkbenchTransfer.snapshot(menu);
            var stale = new PacketArcaneRecipeTransfer(menu.containerId, (menu.getStateId() + 1) & 32767, 1, ResourceLocation.parse("thaumcraft:thaumometer"), false);
            helper.assertFalse(menu.transferRecipe(owner, stale) || !snapshot.matches(menu), "stale menu state rejected without mutations");
            other.containerMenu = menu; helper.assertFalse(menu.transferRecipe(other, request(menu, "thaumometer", false)), "different owner rejected"); helper.assertTrue(snapshot.matches(menu), "bystander cannot move owner's inventory");
            owner.getInventory().setItem(2, ItemStack.EMPTY); menu = menu(owner, table); snapshot = ArcaneWorkbenchTransfer.snapshot(menu);
            helper.assertFalse(menu.transferRecipe(owner, request(menu, "thaumometer", false)), "missing air crystal rejects complete transfer"); helper.assertTrue(snapshot.matches(menu), "missing crystal cannot partially move ingredients");
            ItemStack named = ThaumcraftApiHelper.makeCrystal(Aspect.AIR, 2); named.set(DataComponents.CUSTOM_NAME, Component.literal("Not exact primal crystal")); owner.getInventory().setItem(2, named);
            menu = menu(owner, table); snapshot = ArcaneWorkbenchTransfer.snapshot(menu);
            helper.assertFalse(menu.transferRecipe(owner, request(menu, "thaumometer", false)), "wrong crystal component variant is not the source crystal"); helper.assertTrue(snapshot.matches(menu), "component mismatch remains atomic");
            menu = menu(owner, table); snapshot = ArcaneWorkbenchTransfer.snapshot(menu); owner.containerMenu = owner.inventoryMenu;
            helper.assertFalse(menu.transferRecipe(owner, request(menu, "thaumometer", false)), "closed menu rejects delayed request"); helper.assertTrue(snapshot.matches(menu), "closed request never alters persistent table");
        } finally { cleanup(owner, other); }
        helper.succeed();
    }
    @GameTest(templateNamespace = "thaumcraft_jei", template = "scan_test")
    public static void fullInventoryFailureRetainsEveryComponentAndCount(GameTestHelper helper) {
        var owner = owner(helper); var table = table(helper); research(owner, "FIRSTSTEPS", 2);
        for (int slot = 0; slot < 36; slot++) owner.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64)); thaumometerStock(owner, 2);
        ItemStack displaced = new ItemStack(Items.DIAMOND); displaced.set(DataComponents.CUSTOM_NAME, Component.literal("No dropping to make space")); table.setItem(0, displaced);
        try {
            var menu = menu(owner, table); var snapshot = ArcaneWorkbenchTransfer.snapshot(menu);
            helper.assertFalse(menu.transferRecipe(owner, request(menu, "thaumometer", false)), "no inventory destination for displaced custom diamond");
            helper.assertTrue(snapshot.matches(menu), "full inventory failure retains every original stack atomically");
        } finally { cleanup(owner); }
        helper.succeed();
    }
}
