package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.common.container.ContainerArcaneWorkbench;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchCosts;
import thaumcraft.common.lib.crafting.ArcaneWorkbenchRecipes;
import thaumcraft.common.lib.crafting.RecipeArcaneWorkbench;
import thaumcraft.common.lib.research.ResearchItemResolver;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.common.world.aura.AuraHandler;

/** Real registered recipe manager/codec, BE, menu clicks, aura and crafting events.
 * Explicit fixture research is NOT a claim of normal survival acquisition or client/visual/TCP QA.
 */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class ArcaneWorkbenchGameTests {
    private static final ResourceLocation THAUMOMETER = ResourceLocation.fromNamespaceAndPath("thaumcraft", "thaumometer");
    private static ServerPlayer owner(GameTestHelper helper, String name) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3)); player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        return player;
    }
    private static TileArcaneWorkbench table(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
        helper.getLevel().setBlock(pos, BlocksTC.arcaneWorkbench.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof TileArcaneWorkbench, "registered block creates actual 15-slot entity");
        return (TileArcaneWorkbench) helper.getLevel().getBlockEntity(pos);
    }
    private static ContainerArcaneWorkbench menu(ServerPlayer owner, TileArcaneWorkbench table) {
        ContainerArcaneWorkbench menu = new ContainerArcaneWorkbench(17, owner.getInventory(), table); owner.containerMenu = menu; return menu;
    }
    private static void research(ServerPlayer player, int stage) {
        var knowledge = ThaumcraftCapabilities.getKnowledge(player); knowledge.addResearch("FIRSTSTEPS"); knowledge.setResearchStage("FIRSTSTEPS", stage);
    }
    private static void ingredients(TileArcaneWorkbench table, int count) {
        table.clearContent();
        for (int index : new int[]{1, 3, 5, 7}) table.setItem(index, new ItemStack(Items.GOLD_INGOT, count));
        table.setItem(4, new ItemStack(Items.GLASS_PANE, count));
        for (int index = 0; index < 6; index++) table.setItem(9 + index, ThaumcraftApiHelper.makeCrystal(ArcaneWorkbenchCosts.PRIMALS[index], count));
    }
    private static CraftingInput input(TileArcaneWorkbench table) {
        return CraftingInput.of(3, 3, java.util.stream.IntStream.range(0, 9).mapToObj(index -> table.getItem(index).copy()).toList());
    }
    private static RecipeArcaneWorkbench recipe(GameTestHelper helper) {
        var found = helper.getLevel().getRecipeManager().byKey(THAUMOMETER).orElseThrow();
        helper.assertTrue(found.value() instanceof RecipeArcaneWorkbench, "real source Thaumometer datapack recipe uses separate executable arcane type");
        return (RecipeArcaneWorkbench) found.value();
    }
    private static void cleanup(ServerPlayer... players) { for (ServerPlayer player : players) { player.containerMenu = player.inventoryMenu; PlayerDataSync.forget(player); } }
    private static final class Aura implements AutoCloseable {
        final AuraChunk chunk; final float before;
        Aura(GameTestHelper helper, TileArcaneWorkbench table, float vis) {
            var pos = table.getBlockPos(); var loaded = helper.getLevel().getChunkAt(pos);
            chunk = AuraHandler.getAuraChunk(helper.getLevel(), loaded.getPos().x, loaded.getPos().z);
            helper.assertTrue(chunk != null, "actual loaded chunk aura cache, no injected dummy aura"); before = chunk.getVis(); chunk.setVis(vis);
        }
        @Override public void close() { chunk.setVis(before); }
    }

    @GameTest(template = "scan_test")
    public static void sourceThaumometerShapeCostsResearchAndRealRegistryCodecRoundTrip(GameTestHelper helper) {
        TileArcaneWorkbench table = table(helper); ingredients(table, 1); RecipeArcaneWorkbench recipe = recipe(helper);
        helper.assertTrue(recipe.research().equals("FIRSTSTEPS@2") && recipe.vis() == 20 && recipe.crystals().equals(List.of(1, 1, 1, 1, 1, 1)), "source exact gate20vis/six primals");
        helper.assertTrue(recipe.getType() == ArcaneWorkbenchRecipes.TYPE && recipe.getSerializer() == ArcaneWorkbenchRecipes.SERIALIZER
            && recipe.matches(input(table), helper.getLevel()), "native shape + actual separate type/serializer registration");
        helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input(table), helper.getLevel()).isEmpty(), "vanilla table cannot make a free Thaumometer");
        table.setItem(0, new ItemStack(Items.GOLD_INGOT)); helper.assertFalse(recipe.matches(input(table), helper.getLevel()), "extra occupied source pattern corner rejected"); table.setItem(0, ItemStack.EMPTY);
        var encoded = Recipe.CODEC.encodeStart(JsonOps.INSTANCE, recipe).getOrThrow();
        var parsed = (RecipeArcaneWorkbench) Recipe.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(parsed.matches(input(table), helper.getLevel()) && parsed.crystals().equals(recipe.crystals()) && parsed.vis() == 20 && parsed.research().equals(recipe.research()), "codec retains shape/research/costs");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            Recipe.STREAM_CODEC.encode(buffer, recipe); var wire = (RecipeArcaneWorkbench) Recipe.STREAM_CODEC.decode(buffer);
            helper.assertTrue(buffer.readableBytes() == 0 && wire.matches(input(table), helper.getLevel()) && wire.crystals().equals(recipe.crystals())
                && wire.getResultItem(helper.getLevel().registryAccess()).is(ItemsTC.thaumometer), "synced actual recipe descriptor fully round-trips registry-aware wire");
        } finally { buffer.release(); }
        ItemStack preview = recipe.getResultItem(helper.getLevel().registryAccess()); preview.set(DataComponents.CUSTOM_NAME, Component.literal("External mutation"));
        helper.assertFalse(recipe.getResultItem(helper.getLevel().registryAccess()).has(DataComponents.CUSTOM_NAME), "output descriptor copy cannot corrupt next output");
        helper.succeed();
    }

    @GameTest(template = "scan_test")
    public static void realPaidTakeRejectsStaleAuraResearchAndBystanderAndBlocksCraftCallbackReentry(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, "TC_Arcane_Paid"), other = owner(helper, "TC_Arcane_Other");
        TileArcaneWorkbench table = table(helper); ingredients(table, 2); research(owner, 2);
        try (Aura aura = new Aura(helper, table, 65.5F)) {
            ContainerArcaneWorkbench menu = menu(owner, table);
            helper.assertTrue(menu.getSlot(0).getItem().is(ItemsTC.thaumometer), "genuine paid-ready preview");
            aura.chunk.setVis(0); menu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(menu.getCarried().isEmpty() && table.getItem(1).getCount() == 2 && table.getItem(9).getCount() == 2, "stale preview cannot bypass current aura");
            aura.chunk.setVis(65.5F); research(owner, 1); menu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(menu.getCarried().isEmpty() && aura.chunk.getVis() == 65.5F, "stage1 cannot use source @2 gate");
            research(owner, 2); other.containerMenu = menu; menu.clicked(0, 0, ClickType.PICKUP, other);
            helper.assertTrue(menu.getCarried().isEmpty() && table.getItem(1).getCount() == 2, "different owner cannot take another player's menu result");
            try (PaidProbe probe = new PaidProbe(helper, owner, menu, table, aura)) {
                menu.clicked(0, 0, ClickType.PICKUP, owner);
                helper.assertTrue(probe.calls == 1, "exactly one real crafting callback including denied reentrant take");
            }
            helper.assertTrue(menu.getCarried().is(ItemsTC.thaumometer) && menu.getCarried().getCount() == 1
                && table.getItem(4).getCount() == 1 && table.getItem(1).getCount() == 1 && aura.chunk.getVis() == 45.5F, "one source output costs four gold+pane and20real vis");
            for (int index = 9; index < 15; index++) helper.assertTrue(table.getItem(index).getCount() == 1, "one of each primal consumed");
            Integer hash = ResearchItemResolver.craftReference(new ItemStack(ItemsTC.thaumometer));
            helper.assertTrue(hash != null && ThaumcraftCapabilities.getKnowledge(owner).isResearchKnown("[#]" + hash)
                && !ThaumcraftCapabilities.getKnowledge(other).isResearchKnown("[#]" + hash), "real source crafting receipt belongs only to actual crafter");
        } finally { cleanup(owner, other); }
        helper.succeed();
    }
    public static final class PaidProbe implements AutoCloseable {
        final GameTestHelper helper; final ServerPlayer owner; final ContainerArcaneWorkbench menu; final TileArcaneWorkbench table; final Aura aura; int calls;
        PaidProbe(GameTestHelper helper, ServerPlayer owner, ContainerArcaneWorkbench menu, TileArcaneWorkbench table, Aura aura) {
            this.helper = helper; this.owner = owner; this.menu = menu; this.table = table; this.aura = aura; NeoForge.EVENT_BUS.register(this);
        }
        @SubscribeEvent public void crafted(PlayerEvent.ItemCraftedEvent event) {
            if (event.getEntity() != owner || !event.getCrafting().is(ItemsTC.thaumometer)) return;
            calls++; helper.assertTrue(event.getInventory().getContainerSize() == 15 && event.getInventory().getItem(1).getCount() == 2
                && table.getItem(1).getCount() == 1 && table.getItem(9).getCount() == 1 && aura.chunk.getVis() == 45.5F,
                "modern safety adaptation: commit costs before callbacks, pass captured source 15-slot matrix");
            menu.clicked(0, 0, ClickType.PICKUP, owner); helper.assertTrue(table.getItem(1).getCount() == 1, "callback cannot reenter a second craft");
        }
        @Override public void close() { NeoForge.EVENT_BUS.unregister(this); }
    }

    @GameTest(template = "scan_test")
    public static void strictCrystalComponentsAndMalformedDataCannotCreateUnpaidOutput(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, "TC_Arcane_Crystal"); TileArcaneWorkbench table = table(helper); ingredients(table, 1); research(owner, 2);
        try (Aura aura = new Aura(helper, table, 40)) {
            ContainerArcaneWorkbench menu = menu(owner, table);
            ItemStack valid = table.getItem(9).copy(); ItemStack named = valid.copy(); named.set(DataComponents.CUSTOM_NAME, Component.literal("Not source-equal"));
            for (ItemStack invalid : List.of(ItemStack.EMPTY, named, ThaumcraftApiHelper.makeCrystal(Aspect.FIRE))) {
                table.setItem(9, invalid); menu.clicked(0, 0, ClickType.PICKUP, owner);
                helper.assertTrue(menu.getCarried().isEmpty() && aura.chunk.getVis() == 40 && table.getItem(1).getCount() == 1, "missing/wrong/full-component crystal cannot bypass cost");
            }
            ItemStack malformed = new ItemStack(ItemsTC.crystalEssence); AspectList invalid = new AspectList(); invalid.aspects = null; malformed.set(TCDataComponents.ASPECTS, invalid);
            helper.assertFalse(ArcaneWorkbenchCosts.crystal(malformed, 0), "malformed in-memory map rejects instead of crashing modern component equality");
            table.setItem(9, valid); menu.getSlot(0).set(new ItemStack(Items.DIAMOND, 64));
            menu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(menu.getCarried().is(ItemsTC.thaumometer) && menu.getCarried().getCount() == 1 && aura.chunk.getVis() == 20,
                "injected/stale result slot is replaced by fresh server recipe; never trusts forged diamond promise");
        } finally { cleanup(owner); }
        helper.succeed();
    }

    @GameTest(template = "scan_test")
    public static void actualVanillaFallbackCostsNoAuraAndKeepsReusableSourceMagicDustRemainders(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, "TC_Arcane_Vanilla"); TileArcaneWorkbench table = table(helper);
        try (Aura aura = new Aura(helper, table, 0)) {
            table.setItem(0, new ItemStack(Items.OAK_PLANKS, 2)); table.setItem(3, new ItemStack(Items.OAK_PLANKS, 2));
            ContainerArcaneWorkbench menu = menu(owner, table); menu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(menu.getCarried().is(Items.STICK) && menu.getCarried().getCount() == 4 && table.getItem(0).getCount() == 1
                && table.getItem(3).getCount() == 1 && aura.chunk.getVis() == 0, "real vanilla stick recipe works without arcane research/aura/crystal payment");
            menu.setCarried(ItemStack.EMPTY); table.clearContent();
            ItemStack bowl = new ItemStack(Items.BOWL); bowl.set(DataComponents.CUSTOM_NAME, Component.literal("Reusable source bowl"));
            ItemStack flint = new ItemStack(Items.FLINT); flint.set(DataComponents.CUSTOM_NAME, Component.literal("Reusable source flint"));
            table.setItem(0, bowl); table.setItem(1, flint); table.setItem(2, new ItemStack(Items.REDSTONE));
            table.setItem(3, ThaumcraftApiHelper.makeCrystal(Aspect.AIR)); table.setItem(4, ThaumcraftApiHelper.makeCrystal(Aspect.FIRE)); table.setItem(5, ThaumcraftApiHelper.makeCrystal(Aspect.EARTH));
            table.setItem(9, ThaumcraftApiHelper.makeCrystal(Aspect.AIR, 7));
            menu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(menu.getCarried().is(ItemsTC.salisMundus) && ItemStack.matches(table.getItem(0), bowl) && ItemStack.matches(table.getItem(1), flint)
                && table.getItem(2).isEmpty() && table.getItem(3).isEmpty() && table.getItem(4).isEmpty() && table.getItem(5).isEmpty()
                && table.getItem(9).getCount() == 7 && aura.chunk.getVis() == 0,
                "actual native magic dust fallback refunds full bowl/flint components, consumes grid crystals but NOT arcane crystal-slot reserve");
        } finally { cleanup(owner); }
        helper.succeed();
    }

    @GameTest(template = "scan_test")
    public static void crystalQuickMovePersistenceCloseAndOutputCapacityRetainAllPaidResources(GameTestHelper helper) {
        ServerPlayer owner = owner(helper, "TC_Arcane_Storage"); TileArcaneWorkbench table = table(helper); ContainerArcaneWorkbench menu = menu(owner, table);
        owner.getInventory().setItem(0, ThaumcraftApiHelper.makeCrystal(Aspect.WATER, 3));
        helper.assertFalse(menu.quickMoveStack(owner, 43).isEmpty(), "actual hotbar shift-click routes primal crystal");
        helper.assertTrue(table.getItem(11).getCount() == 3 && owner.getInventory().getItem(0).isEmpty(), "source WATER metadata2 routes menu12/table11");
        menu.setCarried(ThaumcraftApiHelper.makeCrystal(Aspect.AIR)); menu.clicked(12, 0, ClickType.PICKUP, owner);
        helper.assertTrue(menu.getCarried().is(ItemsTC.crystalEssence) && table.getItem(11).getCount() == 3, "manual wrong-aspect insertion rejected");
        menu.setCarried(ItemStack.EMPTY); table.setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
        var saved = table.saveWithFullMetadata(helper.getLevel().registryAccess());
        var restored = BlockEntity.loadStatic(table.getBlockPos(), table.getBlockState(), saved, helper.getLevel().registryAccess());
        helper.assertTrue(restored instanceof TileArcaneWorkbench && ItemStack.matches(((TileArcaneWorkbench) restored).getItem(11), table.getItem(11))
            && ((TileArcaneWorkbench) restored).getItem(0).getCount() == 3, "real BE NBT restores stored crafting/crystal contents");
        menu.removed(owner); helper.assertTrue(table.getItem(11).getCount() == 3 && table.getItem(0).getCount() == 3, "closing never returns/drops persistent workbench inputs");
        ingredients(table, 2); research(owner, 2);
        try (Aura aura = new Aura(helper, table, 65)) {
            menu = menu(owner, table);
            for (int index = 0; index < 36; index++) owner.getInventory().setItem(index, new ItemStack(Items.COBBLESTONE, 64));
            helper.assertTrue(menu.quickMoveStack(owner, 0).isEmpty() && aura.chunk.getVis() == 65 && table.getItem(1).getCount() == 2,
                "full output destination preflight preserves all materials/aura");
            owner.getInventory().setItem(8, ItemStack.EMPTY); owner.getInventory().setItem(7, ItemStack.EMPTY);
            menu.clicked(0, 0, ClickType.QUICK_MOVE, owner);
            helper.assertTrue(owner.getInventory().countItem(ItemsTC.thaumometer) == 2 && table.getItem(1).isEmpty() && table.getItem(4).isEmpty()
                && aura.chunk.getVis() == 25, "native shift action repeats paid batches into reverse source player-slot order");
        } finally { cleanup(owner); }
        helper.succeed();
    }
}
