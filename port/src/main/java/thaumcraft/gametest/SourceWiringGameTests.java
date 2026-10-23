package thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.ThaumcraftInvHelper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.damagesource.DamageSourceThaumcraft;
import thaumcraft.api.internal.CommonInternals;
import thaumcraft.api.internal.WorldCoordinates;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.misc.BlockBarrier;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.capabilities.PlayerDataSync;
import thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;
import thaumcraft.common.lib.crafting.SourceFakeRecipeCatalog;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumcraft.common.lib.events.ResearchCraftingEvents;
import thaumcraft.common.tiles.misc.TileBarrierStone;

/** Broad regressions for the final source-wiring block, sharing the ordinary GameTest engine. */
@GameTestHolder(Thaumcraft.MODID)
@PrefixGameTestTemplate(false)
public final class SourceWiringGameTests {
    private SourceWiringGameTests() {}
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("thaumcraft", name); }
    private static ServerPlayer owner(GameTestHelper h, String name) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)), name));
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void barrierTickerPowerPassengersAndRemoval(GameTestHelper h) {
        BlockPos paving = new BlockPos(1, 1, 1), absolute = h.absolutePos(paving);
        h.setBlock(paving, BlocksTC.pavingStoneBarrier);
        h.assertTrue(h.getLevel().getBlockEntity(absolute) instanceof TileBarrierStone, "barrier paving has its ticking BE");
        h.setBlock(new BlockPos(2, 1, 2), BlocksTC.pavingStoneTravel);
        h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 1, 2))) == null, "travel paving has no barrier BE");
        Cow cow = EntityType.COW.create(h.getLevel());
        // NeoForge FakePlayer deliberately refuses startRiding; the engine mock uses real riding.
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        h.runAtTickTime(120, () -> {
            for (int height = 1; height <= 2; height++) {
                BlockPos pos = absolute.above(height);
                var state = h.getLevel().getBlockState(pos);
                h.assertTrue(state.is(BlocksTC.barrier), "native ticking created barrier cell " + height);
                h.assertTrue(!state.getCollisionShape(h.getLevel(), pos, CollisionContext.of(cow)).isEmpty(), "unpowered barrier blocks mobs");
                h.assertTrue(state.getCollisionShape(h.getLevel(), pos, CollisionContext.of(player)).isEmpty(), "players cross barrier");
                h.assertTrue(state.getShape(h.getLevel(), pos).isEmpty(), "barrier has no selection outline");
                h.assertTrue(state.getBlock().getBlockPathType(state, h.getLevel(), pos, cow) == PathType.BLOCKED, "native mob path is blocked");
                h.assertTrue(state.getBlock().asItem() == Items.AIR, "auxiliary barrier is not an inventory item");
            }
            h.assertTrue(player.startRiding(cow, true), "player mounts test mob");
            h.assertTrue(h.getLevel().getBlockState(absolute.above()).getCollisionShape(h.getLevel(), absolute.above(), CollisionContext.of(cow)).isEmpty(), "player-ridden mob crosses");
            player.stopRiding();
            h.setBlock(paving.east(), Blocks.REDSTONE_BLOCK);
            h.assertTrue(h.getLevel().getBlockState(absolute.above()).getCollisionShape(h.getLevel(), absolute.above(), CollisionContext.of(cow)).isEmpty(), "live power disables collision immediately");
        });
        h.runAtTickTime(130, () -> {
            for (int height = 1; height <= 2; height++) {
                var state = h.getLevel().getBlockState(absolute.above(height));
                h.assertTrue(state.getValue(BlockBarrier.POWERED), "power state updates for path caching");
                h.assertTrue(state.getBlock().getBlockPathType(state, h.getLevel(), absolute.above(height), cow) == PathType.OPEN, "powered native path is open");
            }
            h.setBlock(paving, Blocks.AIR);
            h.assertBlockPresent(Blocks.AIR, paving.above());
            h.assertBlockPresent(Blocks.AIR, paving.above(2));
            h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void shockAndSapApplyDifferentDeferredEffects(GameTestHelper h) {
        // Keep native travel/collision active. NoAI suppresses that path, unlike empty goal selectors.
        var shock = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(1, 2, 1));
        var sap = h.spawnWithNoFreeWill(EntityType.COW, new BlockPos(2, 2, 2));
        shock.setNoGravity(true); sap.setNoGravity(true);
        h.setBlock(new BlockPos(1, 2, 1), BlocksTC.effectShock);
        h.setBlock(new BlockPos(2, 2, 2), BlocksTC.effectSap);
        h.runAtTickTime(4, () -> {
            h.assertTrue(shock.getHealth() < shock.getMaxHealth(), "shock collision inflicts magic damage; ticks=" + shock.tickCount + ", block=" + h.getLevel().getBlockState(shock.blockPosition()));
            h.assertTrue(shock.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && !shock.hasEffect(MobEffects.WITHER), "shock slows without sap curse");
            h.assertTrue(sap.hasEffect(MobEffects.WITHER) && sap.hasEffect(MobEffects.HUNGER), "sap retains its separate curse effects");
            h.assertTrue(sap.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 1, "sap slowing remains stronger");
            h.assertTrue(BlocksTC.effectShock.asItem() == Items.AIR && BlocksTC.effectSap.asItem() == Items.AIR, "temporary effects have no item forms");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void loadedTagsFlattenedWildcardsAndExplicitAspects(GameTestHelper h) {
        String[] metals = {"iron", "gold", "copper", "tin", "silver", "lead", "cinnabar", "quartz"};
        for (String metal : metals) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id("cluster_" + metal)));
            h.assertTrue(stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse("c:clusters/" + metal))), "loaded cluster tag " + metal);
            h.assertTrue(ThaumcraftApiHelper.getIngredient("cluster" + Character.toUpperCase(metal.charAt(0)) + metal.substring(1)).test(stack), "legacy ore ingredient " + metal);
        }
        for (String meat : thaumcraft.common.items.consumables.ItemChunksEdible.VARIANTS) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id("chunk_" + meat)));
            h.assertTrue(stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse("c:nuggets/meat"))), "all flattened meat chunks join source tag");
            var aspects = ThaumcraftCraftingManager.getObjectTags(stack);
            h.assertTrue(aspects.getAmount(Aspect.LIFE) == 5 && aspects.getAmount(Aspect.ENTROPY) == 1, "explicit chunk aspects " + meat);
        }
        String[] names = {"air", "earth", "water", "fire", "order", "entropy", "taint"};
        var crystals = List.of(BlocksTC.crystalAir, BlocksTC.crystalEarth, BlocksTC.crystalWater, BlocksTC.crystalFire, BlocksTC.crystalOrder, BlocksTC.crystalEntropy, BlocksTC.crystalTaint);
        for (int i = 0; i < names.length; i++) h.assertTrue(new ItemStack(crystals.get(i)).is(TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ores/crystal_" + names[i]))), "crystal ore tag " + names[i]);
        h.assertTrue(new ItemStack(ItemsTC.quicksilver).is(TagKey.create(Registries.ITEM, ResourceLocation.parse("c:quicksilver"))), "source quicksilver tag");
        var treat = ThaumcraftCraftingManager.getObjectTags(new ItemStack(ItemsTC.tripleMeatTreat));
        h.assertTrue(treat.getAmount(Aspect.LIFE) == 10 && treat.getAmount(Aspect.DESIRE) == 10, "explicit triple-meat aspects");
        ItemStack wildcard = new ItemStack(ItemsTC.ingots); wildcard.set(DataComponents.DAMAGE, 32767);
        var ingredient = ThaumcraftApiHelper.getIngredient(wildcard);
        for (var ingot : List.of(ItemsTC.ingotThaumium, ItemsTC.ingotVoid, ItemsTC.ingotBrass)) h.assertTrue(ingredient.test(new ItemStack(ingot)), "wildcard expands flattened ingot family");
        h.assertFalse(ingredient.test(new ItemStack(Items.IRON_INGOT)), "family wildcard does not match unrelated vanilla ingot");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fakeCatalogLoadsThirteenDisplaysAndPreservesAddonOverride(GameTestHelper h) {
        var server = h.getLevel().getServer();
        for (int charge = 0; charge < 3; charge++) {
            var key = id("runicarmorfake" + charge);
            var recipe = (InfusionRunicAugmentRecipe) CommonInternals.getCatalogRecipeFake(key);
            ItemStack input = recipe.getRecipeInput().getItems()[0];
            h.assertTrue(InfusionRunicAugmentRecipe.getRunicCharge(input) == charge, "distinct fake runic input charge");
            ItemStack result = (ItemStack) recipe.getRecipeOutput(null, input, List.of());
            h.assertTrue(InfusionRunicAugmentRecipe.getRunicCharge(result) == charge + 1 && InfusionRunicAugmentRecipe.getRunicCharge(input) == charge, "display result is detached");
            h.assertTrue(server.getRecipeManager().byKey(key).isEmpty(), "fake is never executable");
        }
        for (String name : List.of("burrowing", "collector", "destructive", "refining", "sounding", "arcing", "essence", "lamplight")) {
            var key = id("ie" + name + "fake");
            h.assertTrue(CommonInternals.getCatalogRecipeFake(key) instanceof InfusionEnchantmentRecipe, "loaded enchantment fake " + name);
            h.assertTrue(server.getRecipeManager().byKey(key).isEmpty(), "enchantment fake is display-only");
        }
        var meat = (ShapelessRecipe) CommonInternals.getCatalogRecipeFake(id("triplemeattreatfake"));
        var salis = (ShapelessRecipe) CommonInternals.getCatalogRecipeFake(id("salismundusfake"));
        h.assertTrue(meat.getIngredients().size() == 4 && salis.getIngredients().size() == 6, "source fake ingredient counts");
        for (String name : List.of("triplemeattreatfake", "salismundusfake")) h.assertTrue(server.getRecipeManager().byKey(id(name)).isEmpty(), "shapeless fake is display-only");
        var key = id("runicarmorfake0"); Object previous = CommonInternals.craftingRecipeCatalogFake.get(key), replacement = new Object();
        try {
            ThaumcraftApi.addFakeCraftingRecipe(key, replacement);
            SourceFakeRecipeCatalog.setServer(server);
            h.assertTrue(CommonInternals.getCatalogRecipeFake(key) == replacement, "refresh preserves addon replacement identity");
        } finally { CommonInternals.craftingRecipeCatalogFake.put(key, previous); SourceFakeRecipeCatalog.setServer(server); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void inventoryCapabilitySimulationCommitAndComponentFilters(GameTestHelper h) {
        BlockPos chest = h.absolutePos(new BlockPos(1, 1, 1)); h.setBlock(new BlockPos(1, 1, 1), Blocks.CHEST);
        var handler = ThaumcraftInvHelper.getItemHandlerAt(h.getLevel(), chest, Direction.UP);
        h.assertTrue(handler != null, "native chest exposes public inventory helper");
        ItemStack input = new ItemStack(ItemsTC.amber, 32);
        h.assertTrue(ThaumcraftInvHelper.hasRoomForAll(h.getLevel(), chest, Direction.UP, input), "simulation accepts complete stack");
        h.assertTrue(handler.getStackInSlot(0).isEmpty(), "simulation preserves inventory");
        h.assertTrue(ThaumcraftInvHelper.insertStackAt(h.getLevel(), chest, Direction.UP, input, false).isEmpty(), "real insertion consumes stack");
        h.assertTrue(ThaumcraftInvHelper.countTotalItemsIn(handler, new ItemStack(ItemsTC.amber), new ThaumcraftInvHelper.InvFilter(false, false, false, false)) == 32, "committed inventory count");
        ItemStack prime = new ItemStack(ItemsTC.amber), actual = prime.copy();
        CompoundTag required = new CompoundTag(); required.putInt("owner", 7);
        CompoundTag extended = required.copy(); extended.putString("extra", "kept");
        prime.set(DataComponents.CUSTOM_DATA, CustomData.of(required)); actual.set(DataComponents.CUSTOM_DATA, CustomData.of(extended));
        h.assertFalse(ThaumcraftInvHelper.areItemStacksEqual(prime, actual, new ThaumcraftInvHelper.InvFilter(false, false, false, false)), "strict NBT rejects extra field");
        h.assertTrue(ThaumcraftInvHelper.areItemStacksEqual(prime, actual, new ThaumcraftInvHelper.InvFilter(false, false, false, false).setRelaxedNBT()), "relaxed NBT accepts superset");
        extended.putInt("owner", 8); actual.set(DataComponents.CUSTOM_DATA, CustomData.of(extended));
        h.assertFalse(ThaumcraftInvHelper.areItemStackTagsEqualRelaxed(prime, actual), "relaxed NBT still rejects changed required field");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void craftedWarpUsesPublicRegistrationAndInternalBridge(GameTestHelper h) {
        var player = owner(h, "TC_Source_Warp"); var warp = ThaumcraftCapabilities.getWarp(player);
        CompoundTag before = warp.serializeNBT(h.getLevel().registryAccess());
        var key = java.util.Arrays.asList(ItemsTC.amber, 0); Integer old = CommonInternals.warpMap.get(key);
        boolean oldWuss = ModConfig.CONFIG_MISC.wussMode;
        try {
            ModConfig.CONFIG_MISC.wussMode = false; warp.clear();
            ThaumcraftApi.addWarpToItem(new ItemStack(ItemsTC.amber), 5);
            ResearchCraftingEvents.onCrafting(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(ItemsTC.amber), new SimpleContainer(9)));
            h.assertTrue(warp.get(EnumWarpType.NORMAL) == 5, "registered crafted item adds normal warp");
            int beforeGear = warp.get(EnumWarpType.NORMAL);
            ResearchCraftingEvents.onCrafting(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(ItemsTC.voidRobeChest), new SimpleContainer(9)));
            h.assertTrue(warp.get(EnumWarpType.NORMAL) == beforeGear + ThaumcraftApi.getWarp(new ItemStack(ItemsTC.voidRobeChest)), "gear warp is not implicitly added on crafting");
            ThaumcraftApi.internalMethods.addWarpToPlayer(player, 3, EnumWarpType.PERMANENT);
            ThaumcraftApi.internalMethods.addWarpToPlayer(player, 8, EnumWarpType.TEMPORARY);
            h.assertTrue(ThaumcraftApi.internalMethods.getActualWarp(player) == warp.get(EnumWarpType.NORMAL) + 3, "actual warp excludes temporary warp");
            h.assertTrue(ThaumcraftApi.internalMethods.completeResearch(player, "!TC_SOURCE_PUBLIC_BRIDGE"), "internal completion reaches actual research storage");
            h.assertTrue(ThaumcraftCapabilities.getKnowledge(player).isResearchComplete("!TC_SOURCE_PUBLIC_BRIDGE"), "completed opaque marker is present");
        } finally {
            ModConfig.CONFIG_MISC.wussMode = oldWuss;
            if (old == null) CommonInternals.warpMap.remove(key); else CommonInternals.warpMap.put(key, old);
            warp.deserializeNBT(h.getLevel().registryAccess(), before); PlayerDataSync.forget(player);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void coordinatesNamedAndLegacyDimensionsAndDamageFactories(GameTestHelper h) {
        var dimension = ResourceKey.create(Registries.DIMENSION, id("outer_lands"));
        var coordinate = new WorldCoordinates(new BlockPos(-123, 68, 4000000), dimension);
        CompoundTag tag = new CompoundTag(); coordinate.writeNBT(tag);
        var restored = new WorldCoordinates(); restored.readNBT(tag);
        h.assertTrue(coordinate.equals(restored) && coordinate.hashCode() == restored.hashCode(), "named dimension NBT survives roundtrip");
        var overworld = new WorldCoordinates(coordinate.pos, Level.OVERWORLD);
        h.assertTrue(Integer.signum(coordinate.compareTo(overworld)) == -Integer.signum(overworld.compareTo(coordinate)), "dimension comparator is antisymmetric");
        for (int legacy : new int[]{-1, 0, 1}) { tag.putInt("w_d", legacy); restored.readNBT(tag); h.assertTrue(restored.dim.equals(legacy == -1 ? Level.NETHER : legacy == 1 ? Level.END : Level.OVERWORLD), "built-in old dimension mapping"); }
        tag.putInt("w_d", 73); boolean rejected = false;
        try { restored.readNBT(tag); } catch (IllegalArgumentException expected) { rejected = true; }
        h.assertTrue(rejected, "unknown old dimension requires explicit mapping"); restored.readNBT(tag, ignored -> dimension);
        h.assertTrue(restored.equals(coordinate), "custom old dimension mapper preserves coordinate");
        Cow cow = EntityType.COW.create(h.getLevel());
        h.assertTrue(DamageSourceThaumcraft.causeSwarmDamage(cow).getEntity() == cow && DamageSourceThaumcraft.swarm(h.getLevel()).is(DamageSourceThaumcraft.SWARM), "swarm dynamic type and attacker");
        h.assertTrue(DamageSourceThaumcraft.causeTentacleDamage(cow).getEntity() == cow && DamageSourceThaumcraft.tentacle(h.getLevel()).is(DamageSourceThaumcraft.TENTACLE), "tentacle dynamic type and attacker");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyRegisteredEntityConstructsAndReloadsNativeNbt(GameTestHelper h) {
        int count = 0;
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            var key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!key.getNamespace().equals(Thaumcraft.MODID)) continue;
            try {
                var entity = type.create(h.getLevel()); h.assertTrue(entity != null, "entity factory " + key);
                CompoundTag tag = new CompoundTag(); entity.saveWithoutId(tag);
                var restored = type.create(h.getLevel()); restored.load(tag);
                h.assertTrue(restored.getType() == type && restored.getUUID().equals(entity.getUUID()), "entity NBT reload " + key);
            } catch (RuntimeException failure) { throw new IllegalStateException("Native entity construction/NBT failed: " + key, failure); }
            count++;
        }
        h.assertTrue(count >= 43, "all 43 source entity registrations are covered, got " + count);
        h.succeed();
    }
}
