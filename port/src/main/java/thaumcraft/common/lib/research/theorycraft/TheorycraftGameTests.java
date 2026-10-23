package thaumcraft.common.lib.research.theorycraft;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.TCDataComponents;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftManager;
import thaumcraft.common.container.ContainerResearchTable;
import thaumcraft.common.blocks.world.ore.BlockOreAmber;
import thaumcraft.common.items.consumables.ItemPhial;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.network.research.PacketTheorycraftAction;
import thaumcraft.common.lib.network.research.PacketTheorycraftState;
import thaumcraft.common.lib.research.theorycraft.SourceTheorycraftCard.Kind;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/** Real registered table/menu/card/attachment tests. Knowledge/content setup below is an
 * explicit GameTest fixture, never ordinary acquisition or actual TCP/GUI acceptance. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class TheorycraftGameTests {
    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer result = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3)); result.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5); return result;
    }
    private static TileResearchTable table(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(3, 2, 3)); helper.getLevel().setBlock(position, BlocksTC.researchTable.defaultBlockState(), Block.UPDATE_ALL);
        TileResearchTable result = (TileResearchTable)helper.getLevel().getBlockEntity(position);
        result.setItem(0, new ItemStack(ItemsTC.scribingTools)); result.setItem(1, new ItemStack(Items.PAPER, 64)); return result;
    }
    private static void knowledgeFixture(ServerPlayer owner) {
        var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
        knowledge.addResearch("CELESTIALSCANNING"); knowledge.setResearchStage("CELESTIALSCANNING", 99);
        for (var category : ResearchCategories.researchCategories.values()) {
            if (category.researchKey != null) { knowledge.addResearch(category.researchKey); knowledge.setResearchStage(category.researchKey, 99); }
            knowledge.addKnowledge(EnumKnowledgeType.OBSERVATION, category, 160);
        }
        // Source Analyze mistakenly queries OBSERVATION with its still-null category.
        knowledge.addKnowledge(EnumKnowledgeType.OBSERVATION, null, 160);
        owner.getInventory().setItem(9, new ItemStack(ItemsTC.curio)); owner.experienceLevel = 20;
    }
    private static ResearchTableData dataFixture(ServerPlayer owner, TileResearchTable table) {
        ResearchTableData data = new ResearchTableData(owner, table); data.inspirationStart = 15; data.inspiration = 10;
        int value = 20; for (String category : ResearchCategories.researchCategories.keySet()) { data.addTotal(category, value); value += 20; } return data;
    }
    private static SourceTheorycraftCard initialized(Kind kind, ServerPlayer player, ResearchTableData data) {
        for (long seed = 1; seed < 4096; seed++) {
            var card = new SourceTheorycraftCard(kind); card.bindRegistries(player.registryAccess()); card.setSeed(seed);
            if (card.initialize(player, data)) return card;
        }
        throw new IllegalStateException("No valid fixture card " + kind);
    }
    private static PacketTheorycraftAction request(ContainerResearchTable menu, PacketTheorycraftState state, long sequence, int button, Set<String> aids) {
        return new PacketTheorycraftAction(menu.containerId, state.position(), state.session(), state.revision(), sequence, button, aids);
    }
    @GameTest(template = "scan_test", timeoutTicks = 2400)
    public static void realMenuNaturallyRunsCreateDrawPaySelectArchiveAndCompleteWithoutInjectedNoteTotals(GameTestHelper helper) {
        ServerPlayer owner = player(helper, "TC_Theory_Loop"); TileResearchTable table = table(helper); knowledgeFixture(owner);
        ContainerResearchTable menu = new ContainerResearchTable(61, owner.getInventory(), table); owner.containerMenu = menu;
        PacketTheorycraftState[] latest = {null}; long[] sequence = {0}; int[] draws = {0}, chosen = {0}; boolean[] started = {false};
        var before = ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().copy();
        helper.onEachTick(() -> {
            if (helper.getTick() % 8 != 0) return;
            try (var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> {
                helper.assertTrue(recipient == owner, "source menu result goes only to actual owner");
                if (payload instanceof PacketTheorycraftState state) latest[0] = state;
            })) {
                menu.broadcastChanges(); helper.assertTrue(latest[0] != null, "actual owner menu publishes authoritative initial state");
                if (!started[0]) {
                    helper.assertTrue(menu.handleTheoryAction(owner, request(menu, latest[0], ++sequence[0], 0, Set.of())), "genuine create without locally fabricated note");
                    started[0] = true; return;
                }
                ResearchTableData data = table.data;
                helper.assertTrue(data != null, "theory remains server-owned until real finish");
                if (data.cardChoices.stream().anyMatch(choice -> choice.selected)) {
                    helper.assertTrue(menu.handleTheoryAction(owner, request(menu, latest[0], ++sequence[0], 1, Set.of())), "real selected-card archive clears offered cards once"); return;
                }
                if (data.isComplete()) {
                    helper.assertTrue(chosen[0] > 0 && draws[0] > 0 && !data.categoryTotals.isEmpty(), "theory completion naturally follows real activated cards, no totals/inspiration fixture writes");
                    helper.assertTrue(menu.handleTheoryAction(owner, request(menu, latest[0], ++sequence[0], 7, Set.of())), "real checked finish action");
                    helper.assertTrue(table.data == null && !ThaumcraftCapabilities.getKnowledge(owner).serializeNBT().equals(before), "actual theory knowledge awarded and persisted note removed");
                    helper.assertTrue(table.getItem(0).getDamageValue() >= chosen[0] && table.getItem(1).getCount() <= 64 - draws[0], "ink and source paper costs paid, not fake client completion");
                    owner.containerMenu = owner.inventoryMenu; PlayerDataSync.forget(owner); helper.succeed(); return;
                }
                if (data.cardChoices.isEmpty()) {
                    helper.assertTrue(menu.handleTheoryAction(owner, request(menu, latest[0], ++sequence[0], data.bonusDraws > 0 ? 3 : 2, Set.of())), "real paper draw"); draws[0]++; return;
                }
                int index = 0;
                for (int candidate = 0; candidate < data.cardChoices.size(); candidate++) if (data.cardChoices.get(candidate).card.getInspirationCost() > 0) { index = candidate; break; }
                var card = data.cardChoices.get(index).card;
                // Explicit material and XP fixtures: these are genuine inventory stacks paid by
                // production action; this test does not claim survival acquisition of them.
                owner.experienceLevel = 20;
                if (card.getRequiredItems() != null) {
                    int slot = 10; for (ItemStack stack : card.getRequiredItems()) owner.getInventory().setItem(slot++, stack.copy());
                }
                helper.assertTrue(menu.handleTheoryAction(owner, request(menu, latest[0], ++sequence[0], 4 + index, Set.of())), "real card cost/activation/selection"); chosen[0]++;
                helper.assertTrue(chosen[0] < 150, "bounded natural finish demonstration, not infinite fixture loop");
            } catch (Exception exception) { PlayerDataSync.forget(owner); throw new RuntimeException(exception); }
        });
    }
    @GameTest(template = "scan_test")
    public static void menuRejectsWrongSessionReplayRevisionOwnerDistanceAbsentAidAndUnpaidCard(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Theory_Guards"), other = player(helper, "TC_Theory_Other"); TileResearchTable table = table(helper);
        var menu = new ContainerResearchTable(62, owner.getInventory(), table); owner.containerMenu = menu;
        PacketTheorycraftState[] latest = {null};
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> { if (payload instanceof PacketTheorycraftState state) latest[0] = state; })) {
            menu.broadcastChanges(); var state = latest[0];
            CompoundTag before = table.saveWithoutMetadata(owner.registryAccess()).copy();
            helper.assertFalse(menu.handleTheoryAction(owner, new PacketTheorycraftAction(62, state.position(), state.session() + 1, state.revision(), 1, 0, Set.of())), "fresh live menu nonce rejects old-session action");
            helper.assertFalse(menu.handleTheoryAction(other, request(menu, state, 1, 0, Set.of())), "contextual viewer identity, no supplied owner spoofing");
            owner.setPos(owner.getX() + 20, owner.getY(), owner.getZ()); helper.assertFalse(menu.handleTheoryAction(owner, request(menu, state, 1, 0, Set.of())), "real table distance checked");
            owner.setPos(table.getBlockPos().getX() + .5, table.getBlockPos().getY() + .5, table.getBlockPos().getZ() + .5);
            helper.assertFalse(menu.handleTheoryAction(owner, request(menu, state, 1, 0, Set.of("Unknown.Aid"))), "missing nearby aid cannot be bought through arbitrary key");
            helper.assertTrue(table.saveWithoutMetadata(owner.registryAccess()).equals(before), "all rejected attempts mutate no ink/paper/note");
            helper.assertFalse(menu.handleTheoryAction(owner, request(menu, state, 1, 0, Set.of())), "same sequence replay rejected even after failed validation");
            var fresh = new ContainerResearchTable(63, owner.getInventory(), table); owner.containerMenu = fresh; fresh.broadcastChanges(); state = latest[0];
            var stale = new PacketTheorycraftAction(63, state.position(), state.session(), state.revision() + 1, 1, 0, Set.of());
            helper.assertFalse(fresh.handleTheoryAction(owner, stale), "fresh limiter proves current table revision required");
        } finally { owner.containerMenu = owner.inventoryMenu; PlayerDataSync.forget(owner); PlayerDataSync.forget(other); }
        helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void menuRequiresActualConsumedItemAndPublishesLockedStateForAnotherOwner(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Theory_Cost"), other = player(helper, "TC_Theory_Lock"); TileResearchTable table = table(helper);
        table.data = dataFixture(owner, table); var card = initialized(Kind.SCULPTING, owner, table.data);
        table.data.cardChoices.add(new ResearchTableData.CardChoice(Kind.SCULPTING.sourceKey(), card, false, false)); table.setChanged();
        var menu = new ContainerResearchTable(64, owner.getInventory(), table); owner.containerMenu = menu; PacketTheorycraftState[] latest = {null};
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> { if (payload instanceof PacketTheorycraftState state) latest[0] = state; })) {
            menu.broadcastChanges(); var before = table.saveWithoutMetadata(owner.registryAccess()).copy();
            helper.assertFalse(menu.handleTheoryAction(owner, request(menu, latest[0], 1, 4, Set.of())), "missing clay cannot activate source Sculpting");
            helper.assertTrue(table.saveWithoutMetadata(owner.registryAccess()).equals(before) && !table.data.cardChoices.get(0).selected, "no fake selection, ink or theory without real consumed item");
            var outsider = new ContainerResearchTable(65, other.getInventory(), table); other.containerMenu = outsider; outsider.broadcastChanges();
            helper.assertTrue(!latest[0].owned() && latest[0].note().isEmpty(), "other player's menu receives only locked view, not private note");
            helper.assertFalse(outsider.handleTheoryAction(other, request(outsider, latest[0], 1, 9, Set.of())), "other contextual owner cannot scrap a live researcher's note");
            helper.assertTrue(table.saveWithoutMetadata(owner.registryAccess()).equals(before), "owner's note and physical contents preserved");
        } finally { owner.containerMenu = owner.inventoryMenu; other.containerMenu = other.inventoryMenu; PlayerDataSync.forget(owner); PlayerDataSync.forget(other); }
        helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void all33RegisteredCardsInitializeSerializeAndExecuteGenuineSourceEffects(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Theory_Cards"); TileResearchTable table = table(helper);
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> {})) {
            helper.assertTrue(TheorycraftManager.cards.size() == 33 && TheorycraftManager.aids.size() == 14, "exact original registered 33 cards and14 aids, not stale plan14 cards");
            helper.assertFalse(TheorycraftManager.cards.containsKey(Kind.TRUTH.sourceKey()) || TheorycraftManager.cards.containsKey(Kind.DRAGON_EGG.sourceKey()), "source omission is not silently repaired");
            int executed = 0;
            for (Kind kind : Kind.values()) {
                if (kind == Kind.TRUTH || kind == Kind.DRAGON_EGG) continue;
                owner.getInventory().clearContent(); knowledgeFixture(owner); table.setItem(0, new ItemStack(ItemsTC.scribingTools)); table.setItem(1, new ItemStack(Items.PAPER, 64));
                var data = dataFixture(owner, table); var card = initialized(kind, owner, data);
                CompoundTag nbt = card.serialize(); var loaded = new SourceTheorycraftCard(kind); loaded.bindRegistries(owner.registryAccess()); loaded.deserialize(nbt);
                helper.assertTrue(loaded.serialize().equals(nbt) && loaded.getSeed() == card.getSeed() && loaded.getInspirationCost() == card.getInspirationCost(), "actual card NBT/category/aspect/stack/seed/cost round-trip " + kind);
                if (card.getRequiredItems() != null) for (ItemStack required : card.getRequiredItems()) helper.assertFalse(required.isEmpty(), "genuine registered nonAIR item dependency " + kind);
                owner.getRandom().setSeed(5147L + executed); helper.assertTrue(card.activate(owner, data), "actual original activation " + kind);
                helper.assertTrue(!data.categoryTotals.isEmpty() || data.bonusDraws > 0 || data.penaltyStart > 0, "source card performs an actual state effect " + kind); executed++;
            }
            helper.assertTrue(executed == 33, "every registered base/category/aid card actually executed");
        } finally { PlayerDataSync.forget(owner); }
        helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void originalAwardRoundingPenaltyAndRealKnowledgeWithoutDuplicateFinish(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Theory_Award"); TileResearchTable table = table(helper);
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> {})) {
            var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
            var basic = ResearchCategories.getResearchCategory("BASICS"); var alchemy = ResearchCategories.getResearchCategory("ALCHEMY");
            int beforeBasic = knowledge.getKnowledgeRaw(EnumKnowledgeType.THEORY, basic), beforeAlchemy = knowledge.getKnowledgeRaw(EnumKnowledgeType.THEORY, alchemy);
            table.data = new ResearchTableData(owner, table); table.data.inspiration = 0; table.data.addTotal("BASICS", 100); table.data.addTotal("ALCHEMY", 50);
            table.finishTheory(owner); helper.assertTrue(table.data == null, "completion clears the real persisted note");
            helper.assertTrue(knowledge.getKnowledgeRaw(EnumKnowledgeType.THEORY, basic) - beforeBasic == 32
                && knowledge.getKnowledgeRaw(EnumKnowledgeType.THEORY, alchemy) - beforeAlchemy == 10, "source32progression then int floor two-thirds penalty only index>penaltyStart");
            CompoundTag awarded = knowledge.serializeNBT().copy(); table.finishTheory(owner); helper.assertTrue(knowledge.serializeNBT().equals(awarded), "empty second finish grants nothing");
            for (int amount = 0; amount <= 2000; amount++) for (int index = 0; index < 8; index++) {
                int total = Math.round(amount / 100.0F * 32); if (index > 2) total = (int)Math.max(1.0, total * .666666667);
                helper.assertTrue(TileResearchTable.theoryAward(amount, index, 2) == total, "literal source finish formula " + amount + "/" + index);
            }
        } finally { PlayerDataSync.forget(owner); } helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void sourceAidVolumePoolsAndExactTenUnitPhialComponents(GameTestHelper helper) {
        TileResearchTable table = table(helper); BlockPos pos = table.getBlockPos();
        String bookshelf = "thaumcraft.api.research.theorycraft.AidBookshelf";
        helper.getLevel().setBlock(pos.offset(4, 1, 4), Blocks.BOOKSHELF.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(table.checkSurroundingAids().contains(bookshelf), "inclusive source xz+-4 y+-1 search");
        helper.getLevel().setBlock(pos.offset(4, 1, 4), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        helper.getLevel().setBlock(pos.offset(5, 1, 4), Blocks.BOOKSHELF.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertFalse(table.checkSurroundingAids().contains(bookshelf), "outside source volume excluded");
        String[] pool = TheorycraftManager.aids.get(bookshelf).getCardKeys();
        helper.assertTrue(List.of(pool).equals(List.of(Kind.BALANCE.sourceKey(), Kind.NOTATION.sourceKey(), Kind.NOTATION.sourceKey(), Kind.STUDY.sourceKey(), Kind.STUDY.sourceKey(), Kind.STUDY.sourceKey())), "weighted duplicate source bookshelf pool retained");
        var phial = ItemPhial.makeFilledPhial(thaumcraft.api.aspects.Aspect.MAGIC);
        helper.assertTrue(phial.getItem() == ItemPhial.filledVariant && phial.get(TCDataComponents.ASPECTS).getAmount(thaumcraft.api.aspects.Aspect.MAGIC) == 10, "genuine source phial ten-unit aspect component");
        helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void mainInventoryPaymentPreservesComponentsAndDoesNotDoubleReserveConsumables(GameTestHelper helper) {
        var first = thaumcraft.api.ThaumcraftApiHelper.makeCrystal(thaumcraft.api.aspects.Aspect.MAGIC);
        var second = thaumcraft.api.ThaumcraftApiHelper.makeCrystal(thaumcraft.api.aspects.Aspect.FIRE);
        helper.assertTrue(TheorycraftInventory.matches(first.copy(), first) && !TheorycraftInventory.matches(second, first), "actual contained-aspect identity, not item-only fake crystal payment");
        List<ItemStack> inventory = new ArrayList<>(List.of(new ItemStack(Items.CLAY_BALL)));
        helper.assertTrue(TheorycraftInventory.payment(inventory, new ItemStack[]{new ItemStack(Items.CLAY_BALL), new ItemStack(Items.CLAY_BALL)}, new boolean[]{true, true}) == null, "same clay cannot satisfy two consumed costs");
        helper.assertTrue(inventory.get(0).getCount() == 1, "failed planning mutates no actual inventory");
        int[] proof = TheorycraftInventory.payment(inventory, new ItemStack[]{new ItemStack(Items.CLAY_BALL)}, new boolean[]{false}); helper.assertTrue(proof != null && proof[0] == 0, "source tools/material proofs are not consumed unless the card says so");
        helper.assertTrue(TheorycraftInventory.payment(inventory, new ItemStack[]{ItemStack.EMPTY}, new boolean[]{true}) == null, "unresolved later-stage object is not a paid AIR requirement");
        helper.assertTrue(TheorycraftInventory.matches(new ItemStack(Items.RED_WOOL), new ItemStack(Items.WHITE_WOOL)), "source ore wool wildcard accepts another real color");
        helper.assertTrue(TheorycraftInventory.matches(new ItemStack(Items.CHEST), new ItemStack(Items.ENDER_CHEST))
            && TheorycraftInventory.matches(new ItemStack(Items.TRAPPED_CHEST), new ItemStack(Items.ENDER_CHEST)), "source broad chest ore equivalence includes all three registered chest types");
        helper.assertFalse(TheorycraftInventory.matches(new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND)), "generic modern gems tag does not invent source equivalence");
        var markedAmber = new ItemStack(ItemsTC.amber); var custom = new CompoundTag(); custom.putString("mark", "real custom data");
        markedAmber.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(custom));
        helper.assertTrue(TheorycraftInventory.matches(markedAmber, new ItemStack(ItemsTC.amber)), "source gemAmber ore early-success bypasses NBT");
        helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void preservedCurioComesFromRealAmberBlockLootAndSourceWholeStackReplacement(GameTestHelper helper) {
        helper.assertTrue(BlocksTC.oreAmber instanceof BlockOreAmber, "actual registered amber block invokes source curio drop, not an isolated fixture helper");
        var random = net.minecraft.util.RandomSource.create(51475391L); var expected = new java.util.Random(51475391L); int count = 0;
        for (int index = 0; index < 4096; index++) {
            var amber = new ItemStack(ItemsTC.amber, 12); boolean replaced = expected.nextFloat() < .066D;
            var result = BlockOreAmber.replaceAmber(List.of(amber), random);
            helper.assertTrue(result.size() == 1 && (replaced ? SourceTheorycraftCard.curioType(result.getFirst()).equals("preserved") && result.getFirst().getCount() == 1
                : result.getFirst().is(ItemsTC.amber) && result.getFirst().getCount() == 12), "literal seeded source chance replaces ENTIRE Fortune-like stack with ONE preserved curio");
            helper.assertTrue(amber.getCount() == 12, "input loot stack not mutated"); if (replaced) count++;
        }
        helper.assertTrue(count > 0, "actual registered preserved curio variant produced");
        random.setSeed(51L); var baseline = net.minecraft.util.RandomSource.create(51L);
        var silkInput = BlockOreAmber.replaceAmber(List.of(new ItemStack(BlocksTC.oreAmber)), random);
        helper.assertTrue(silkInput.getFirst().is(BlocksTC.oreAmber.asItem()) && random.nextLong() == baseline.nextLong(), "ore-block Silk Touch result consumes no source curio RNG roll");
        var tool = new ItemStack(Items.DIAMOND_PICKAXE); tool.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var silkDrops = Block.getDrops(BlocksTC.oreAmber.defaultBlockState(), helper.getLevel(), pos, null, null, tool);
        helper.assertTrue(silkDrops.size() == 1 && silkDrops.getFirst().is(BlocksTC.oreAmber.asItem()), "genuine registered loot table honors actual Silk Touch enchantment");
        int curios = 0, normalAmber = 0;
        for (int index = 0; index < 1024; index++) for (ItemStack drop : Block.getDrops(BlocksTC.oreAmber.defaultBlockState(), helper.getLevel(), pos, null, null, new ItemStack(Items.DIAMOND_PICKAXE))) {
            if ("preserved".equals(SourceTheorycraftCard.curioType(drop))) { helper.assertTrue(drop.getCount() == 1, "real block curio count is one"); curios++; }
            else { helper.assertTrue(drop.is(ItemsTC.amber) && drop.getCount() >= 1 && drop.getCount() <= 2, "ordinary genuine source amber loot retained"); normalAmber++; }
        }
        helper.assertTrue(curios > 0 && normalAmber > 0, "ordinary mining loot genuinely contains amber and preserved curios"); helper.succeed();
    }
    @GameTest(template = "scan_test")
    public static void noteSurvivesActualRegisteredBeDiskNbtRoundTripIncludingSelectedAndLastCards(GameTestHelper helper) throws Exception {
        ServerPlayer owner = player(helper, "TC_Theory_Nbt"); TileResearchTable table = table(helper);
        try (var capture = PlayerDataSync.captureGameTestPackets((recipient, payload) -> {})) {
            knowledgeFixture(owner); table.data = dataFixture(owner, table); table.data.aidCards.add(Kind.STUDY.sourceKey()); table.data.savedCards.add(42L);
            var card = initialized(Kind.CELESTIAL, owner, table.data);
            var choice = new ResearchTableData.CardChoice(Kind.CELESTIAL.sourceKey(), card, true, true); table.data.cardChoices.add(choice); table.data.lastDraw = choice;
            table.setChanged(); CompoundTag saved = table.saveWithFullMetadata(owner.registryAccess());
            helper.assertTrue(saved.contains("note", 10), "disk save keeps the complete private note");
            CompoundTag visible = table.getUpdateTag(owner.registryAccess());
            var updatePacket = table.getUpdatePacket();
            helper.assertTrue(!visible.contains("note") && updatePacket != null && !updatePacket.getTag().contains("note"), "actual chunk and BE packet updates never broadcast the private owner's note");
            helper.assertTrue(visible.getList("Items", 10).equals(saved.getList("Items", 10))
                && visible.getLong("theoryRevision") == saved.getLong("theoryRevision")
                && visible.contains("theoryRevision", 4), "public chunk update retains physical inventory and exact theory revision");
            helper.assertTrue(updatePacket.getTag().getList("Items", 10).equals(saved.getList("Items", 10))
                && updatePacket.getTag().getLong("theoryRevision") == saved.getLong("theoryRevision")
                && updatePacket.getTag().contains("theoryRevision", 4), "public BE packet retains physical inventory and exact theory revision");
            var loaded = (TileResearchTable)BlockEntity.loadStatic(table.getBlockPos(), table.getBlockState(), saved, owner.registryAccess());
            helper.assertTrue(loaded != null && loaded.data != null && loaded.data.ownedBy(owner) && loaded.saveWithFullMetadata(owner.registryAccess()).equals(saved), "actual registered loader preserves complete note, owner, choices, selected, lastDraw and revision");
            table.data.addTotal("BASICS", 99); helper.assertTrue(loaded.data.getTotal("BASICS") != table.data.getTotal("BASICS"), "deserialized note is detached");
        } finally { PlayerDataSync.forget(owner); } helper.succeed();
    }
}
