package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.entities.EntitySpecialItem;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.research.ResearchBrowserAvailability;
import thaumcraft.common.lib.research.ServerResearchProgression;

/** Controlled world/material fixtures, NOT knowledge/stage/book-marker fixtures. Actual
 * pickup -> wake -> ResultSlot crafting -> Item use -> delayed entity -> pickup -> checked
 * FIRSTSTEPS start. This is a common GameTest, not a GUI/TCP/survival-playthrough claim.
 */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class NormalBookPathGameTests {
    private NormalBookPathGameTests() {}

    @GameTest(template = "empty", batch = "normal-book-path", timeoutTicks = 160)
    public static void actualBookAcquisitionStartsFirstStepsWithoutResearchOrKnowledgeGrants(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TC_Normal_Book"));
        owner.setGameMode(GameType.SURVIVAL);
        owner.getInventory().clearContent();
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        knowledge.clear();
        PlayerDataSync.forget(owner);
        var initialWarp = ThaumcraftCapabilities.getWarp(owner).serializeNBT().copy();
        owner.totalExperience = 0;
        owner.experienceLevel = 0;
        owner.experienceProgress = 0;
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        owner.setPos(Vec3.atCenterOf(position));
        helper.assertFalse(ResearchBrowserAvailability.evaluate(knowledge, "FIRSTSTEPS").mayRequestProgress(),
            "book prerequisite is absent, not a test-only unlock");

        // Only normal tangible ingredients are supplied by this controlled test world.
        List<ItemStack> crystals = List.of(ThaumcraftApiHelper.makeCrystal(Aspect.AIR),
            ThaumcraftApiHelper.makeCrystal(Aspect.FIRE), ThaumcraftApiHelper.makeCrystal(Aspect.WATER));
        boolean previousNoSleep = ModConfig.CONFIG_MISC.noSleep;
        try {
            ModConfig.CONFIG_MISC.noSleep = false;
            for (ItemStack crystal : crystals) {
                ItemEntity entity = new ItemEntity(level, owner.getX(), owner.getY(), owner.getZ(), crystal.copy());
                entity.setNoPickUpDelay();
                level.addFreshEntity(entity);
                entity.playerTouch(owner);
                helper.assertTrue(entity.isRemoved(), "actual crystal inventory pickup succeeded");
            }
            helper.assertTrue(knowledge.getResearchStage("!gotcrystals") == 0
                && !knowledge.isResearchKnown("!gotdream"), "source crystal PRE grants only raw known0 marker");
            owner.stopSleepInBed(true, true); // Actual vanilla/NeoForge wake hook, no dawn requirement.
        } finally {
            ModConfig.CONFIG_MISC.noSleep = previousNoSleep;
        }
        helper.assertTrue(knowledge.getResearchStage("!gotdream") == 0
            && owner.getInventory().items.stream().anyMatch(stack -> stack.is(Items.WRITTEN_BOOK)
                && stack.has(DataComponents.WRITTEN_BOOK_CONTENT)), "actual wake produced the source journal");
        helper.assertTrue(!PlayerDataSync.isKnowledgeDirty(owner) && owner.totalExperience == 0,
            "raw bootstrap does not use progression events/XP or the dirty20 route");

        // Move the three actually acquired crystals into the real crafting table grid.
        level.setBlock(position, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        var menu = new CraftingMenu(0, owner.getInventory(), ContainerLevelAccess.create(level, position));
        menu.getSlot(1).set(new ItemStack(Items.BOWL));
        menu.getSlot(2).set(new ItemStack(Items.FLINT));
        menu.getSlot(3).set(new ItemStack(Items.REDSTONE));
        for (int index = 0; index < crystals.size(); index++) {
            ItemStack acquired = ItemStack.EMPTY;
            for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
                ItemStack candidate = owner.getInventory().getItem(slot);
                if (ItemStack.isSameItemSameComponents(candidate, crystals.get(index))) {
                    acquired = owner.getInventory().removeItem(slot, 1);
                    break;
                }
            }
            helper.assertTrue(!acquired.isEmpty(), "crafting consumes the actually acquired component-bearing crystal");
            menu.getSlot(index + 4).set(acquired);
        }
        var result = menu.getSlot(0);
        helper.assertTrue(result.getItem().is(ItemsTC.salisMundus), "actual RecipeManager selects source salismundus");
        ItemStack dust = result.remove(1);
        result.onTake(owner, dust);
        helper.assertTrue(dust.is(ItemsTC.salisMundus) && dust.getCount() == 1
            && menu.getSlot(1).getItem().is(Items.BOWL) && menu.getSlot(2).getItem().is(Items.FLINT),
            "real ResultSlot TAKE produces dust and returns the source reusable ingredients");
        level.setBlock(position, Blocks.BOOKSHELF.defaultBlockState(), 3);
        owner.setItemInHand(InteractionHand.MAIN_HAND, dust);
        var hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
        var use = ItemsTC.salisMundus.onItemUseFirst(owner.getItemInHand(InteractionHand.MAIN_HAND),
            new UseOnContext(owner, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(use.consumesAction() && owner.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
            "actual source dust use consumes one before delayed conversion");
        helper.assertTrue(level.getBlockState(position).is(Blocks.BOOKSHELF)
            && !knowledge.isResearchKnown("!gotthaumonomicon"), "no immediate block replacement or marker grant");

        helper.startSequence().thenExecuteAfter(55, () -> {
            try {
                helper.assertTrue(level.getBlockState(position).isAir(), "actual world END ticks converted the bookshelf");
                var drops = level.getEntitiesOfClass(EntitySpecialItem.class,
                    new net.minecraft.world.phys.AABB(position).inflate(1.0), entity -> entity.getItem().is(ItemsTC.thaumonomicon));
                helper.assertTrue(drops.size() == 1 && !knowledge.isResearchKnown("!gotthaumonomicon"),
                    "one real special book entity, not an inventory/knowledge shortcut");
                drops.getFirst().playerTouch(owner);
                helper.assertTrue(knowledge.getResearchStage("!gotthaumonomicon") == 0
                    && owner.getInventory().items.stream().anyMatch(stack -> stack.is(ItemsTC.thaumonomicon)),
                    "actual book pickup supplies normal item and raw known0 marker");
                helper.assertTrue(ItemsTC.thaumonomicon.getDescriptionId().equals("item.thaumcraft.thaumonomicon_normal")
                    && !net.minecraft.locale.Language.getInstance().getOrDefault(ItemsTC.thaumonomicon.getDescriptionId())
                        .equals(ItemsTC.thaumonomicon.getDescriptionId()), "normal source variant name uses an existing imported translation");
                var expectedAspects = new thaumcraft.api.aspects.AspectList(new ItemStack(Items.BOOKSHELF))
                    .merge(Aspect.MAGIC, 10);
                var actualAspects = thaumcraft.api.aspects.AspectHelper.getObjectAspects(new ItemStack(ItemsTC.thaumonomicon));
                helper.assertTrue(actualAspects != null && actualAspects.aspects.equals(expectedAspects.aspects),
                    "registered book has the original bookshelf-base plus MAGIC10 aspect tag");
                helper.assertTrue(owner.totalExperience == 0 && !PlayerDataSync.isKnowledgeDirty(owner)
                    && ThaumcraftCapabilities.getWarp(owner).serializeNBT().equals(initialWarp),
                    "acquisition preserved source no-XP/no-warp/no-dirty semantics");
                helper.assertTrue(ResearchBrowserAvailability.evaluate(knowledge, "FIRSTSTEPS").mayRequestProgress(),
                    "actual acquisition satisfies the original browser parent");
                var progress = ServerResearchProgression.tryProgress(owner, "FIRSTSTEPS", -1);
                helper.assertTrue(progress.applied() && knowledge.getResearchStage("FIRSTSTEPS") == 1
                    && !knowledge.isResearchComplete("FIRSTSTEPS"),
                    "checked common start works; absent arcane workbench is not bypassed");
            } finally {
                PlayerDataSync.forget(owner);
            }
        }).thenSucceed();
    }
}
