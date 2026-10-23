package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.items.consumables.ItemPhial;
import thaumcraft.common.lib.capabilities.PlayerDataSync;

/** Actual registered source recipe and native ResultSlot crafting callback. Inputs and
 * unspawned FakePlayers are explicit test fixtures, not survival/resource acquisition. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class PhialCraftingGameTests {
    @GameTest(template = "empty")
    public static void nativeResultTakesKeepEightEmptyPhialsEmptyAndPayEverySourceCell(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "TC_Phial_Craft");
        BlockPos table = new BlockPos(1, 1, 1); helper.setBlock(table, Blocks.CRAFTING_TABLE);
        CraftingMenu menu = new CraftingMenu(49, owner.getInventory(),
            ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(table)));
        try {
            menu.getSlot(2).set(new ItemStack(Items.CLAY_BALL, 2));
            for (int slot : new int[]{4, 6, 8}) menu.getSlot(slot).set(new ItemStack(Items.GLASS, 2));
            helper.assertTrue(menu.getSlot(0).container instanceof RecipeCraftingHolder,
                "actual crafting result container owns the selected native recipe");
            var holder = (RecipeCraftingHolder) menu.getSlot(0).container;
            helper.assertTrue(holder.getRecipeUsed() != null && holder.getRecipeUsed().id().equals(
                ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "phial")), "real manager selected the registered source phial recipe");
            for (int take = 1; take <= 2; take++) {
                ItemStack output = menu.getSlot(0).safeTake(8, Integer.MAX_VALUE, owner);
                helper.assertTrue(output.is(ItemPhial.emptyVariant) && output.getCount() == 8
                    && !output.has(TCDataComponents.ASPECTS), "actual ResultSlot callback keeps all eight source empty phials empty");
                output.getItem().inventoryTick(output, helper.getLevel(), owner, 0, false);
                helper.assertTrue(!output.has(TCDataComponents.ASPECTS), "later empty-phial inventory callback does not manufacture random essentia");
                for (int slot = 1; slot <= 9; slot++) {
                    int remaining = slot == 2 || slot == 4 || slot == 6 || slot == 8 ? 2 - take : 0;
                    helper.assertTrue(menu.getSlot(slot).getItem().getCount() == remaining,
                        "actual native take pays exactly one clay and three glass cells, take " + take + ", slot " + slot);
                }
            }
            helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "paid source inputs cannot create a third result");
        } finally { menu.clearCraftingContent(); PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftedFilledPhialRetainsActualTenUnitComponentsAndEmptyVariantNeverFills(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "TC_Phial_Filled");
        try {
            ItemStack filled = ItemPhial.makeFilledPhial(Aspect.AIR); filled.setCount(3);
            filled.set(DataComponents.CUSTOM_NAME, Component.literal("Source ten-unit phial survives callback"));
            ItemStack before = filled.copy(); filled.onCraftedBy(helper.getLevel(), owner, 3);
            filled.getItem().inventoryTick(filled, helper.getLevel(), owner, 0, false);
            helper.assertTrue(filled.is(ItemPhial.filledVariant) && filled.getCount() == 3
                && ItemStack.isSameItemSameComponents(filled, before)
                && ((ItemPhial) filled.getItem()).getAspects(filled).getAmount(Aspect.AIR) == 10,
                "native crafted/inventory callbacks preserve exact valid filled identity, components and count");
            ItemStack empty = new ItemStack(ItemsTC.phial, 8); empty.onCraftedBy(helper.getLevel(), owner, 8);
            helper.assertTrue(empty.is(ItemPhial.emptyVariant) && empty.getCount() == 8 && !empty.has(TCDataComponents.ASPECTS),
                "empty variant overrides the generic onCraftedBy random-fill path");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }
}
