package thaumcraft.common.lib.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.EntityFollowingItem;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import thaumcraft.common.lib.enchantment.SpecialMiningResults;
import thaumcraft.common.lib.enchantment.ToolMiningRules;
import thaumcraft.common.lib.network.fx.PacketFXScanSource;
import thaumcraft.common.lib.network.fx.PacketFXSlash;

/** All eight source infusion-enchantment gameplay hooks. No effects are invented for
 * the five source enum constants that had no gameplay implementation. Server-owned
 * native block events preserve protections; recursion state is per player, not a
 * shared cross-world source static cursor. All extra harvesting pays source damage
 * exactly once, not an additional native mineBlock() durability charge. */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class ToolEvents {
    private static final Map<Player, Direction> LAST_FACE = new WeakHashMap<>();
    public static final Map<ResourceKey<Level>, Set<BlockPos>> blockedBlocks = new HashMap<>();
    private static final Set<UUID> BURROWING = new HashSet<>(), DESTRUCTIVE = new HashSet<>();
    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", "ores"));
    private ToolEvents() {}
    private static int level(ItemStack stack, EnumInfusionEnchantment type) { return EnumInfusionEnchantment.getInfusionEnchantmentLevel(stack, type); }
    private static boolean effective(ItemStack tool, BlockState state) { return tool.isCorrectToolForDrops(state) || tool.getDestroySpeed(state) > 1F; }
    public static boolean isOre(BlockState state) { return !state.isAir() && !state.is(Blocks.BEDROCK) && (state.is(ORES)
        || state.getTags().anyMatch(tag -> tag.location().getPath().startsWith("ores/"))); }
    public static void addBlockedBlock(Level world, BlockPos pos) { blockedBlocks.computeIfAbsent(world.dimension(), key -> new HashSet<>()).add(pos.immutable()); }
    public static void clearBlockedBlock(Level world, BlockPos pos) { var positions = blockedBlocks.get(world.dimension()); if (positions != null) positions.remove(pos); }

    @SubscribeEvent public static void playerAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) return;
        int rank = level(player.getMainHandItem(), EnumInfusionEnchantment.ARCING);
        if (rank <= 0 || !event.getTarget().isAlive()) return;
        var targets = player.level().getEntities(player, event.getTarget().getBoundingBox().inflate(1.5 + rank, 1F + rank / 2F, 1.5 + rank));
        int count = 0;
        if (targets.size() > 1) for (var target : targets) {
            // Original EntityLiving excludes players, despite its redundant player-name check.
            if (target instanceof Mob mob && target != event.getTarget() && target.isAlive()
                && !player.isPassengerOfSameVehicle(target) && !player.isAlliedTo(target)
                && !(target instanceof OwnableEntity owned && owned.getOwner() == player)) {
                player.setLastHurtMob(target); // Source attackEntityAsMob only does this, and returns false.
                var damage = player.damageSources().playerAttack(player);
                if (target.hurt(damage, (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * .5F)) {
                    // Null item source limits native post-attack processing to the victim (source thorns).
                    EnchantmentHelper.doPostAttackEffectsWithItemSource(player.serverLevel(), target, damage, null);
                    target.push(-Mth.sin(player.getYRot() * (float)Math.PI / 180F) * .5F, .1, Mth.cos(player.getYRot() * (float)Math.PI / 180F) * .5F);
                    count++;
                    PacketDistributor.sendToPlayersNear(player.serverLevel(), null, event.getTarget().getX(), event.getTarget().getY(), event.getTarget().getZ(), 64,
                        new PacketFXSlash(event.getTarget().getId(), target.getId()));
                }
                if (count >= rank) break;
            }
        }
        if (count > 0) {
            player.playSound(SoundsTC.wind, 1F, .9F + player.level().random.nextFloat() * .2F);
            PacketDistributor.sendToPlayersNear(player.serverLevel(), null, player.getX(), player.getY(), player.getZ(), 64, new PacketFXSlash(player.getId(), event.getTarget().getId()));
        }
    }
    @SubscribeEvent public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled() || !player.isShiftKeyDown()) return;
        ItemStack tool = event.getItemStack(); int rank = level(tool, EnumInfusionEnchantment.SOUNDING);
        if (rank <= 0) return;
        tool.hurtAndBreak(5, player, event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        BlockPos pos = event.getPos();
        player.level().playSound(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, SoundsTC.wandfail, SoundSource.BLOCKS, .2F, .2F + player.level().random.nextFloat() * .2F);
        // Source queries after paying durability: a broken last-use tool sends rank zero.
        PacketDistributor.sendToPlayer(player, new PacketFXScanSource(pos, level(tool, EnumInfusionEnchantment.SOUNDING)));
    }
    @SubscribeEvent public static void leftClick(PlayerInteractEvent.LeftClickBlock event) { if (event.getFace() != null) LAST_FACE.put(event.getEntity(), event.getFace()); }

    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (blockedBlocks.getOrDefault(((Level)event.getLevel()).dimension(), Set.of()).contains(event.getPos())) event.setCanceled(true);
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || BURROWING.contains(player.getUUID()) || player.isShiftKeyDown()) return;
        ItemStack tool = player.getMainHandItem(); BlockState state = event.getState(); ServerLevel world = player.serverLevel();
        if (level(tool, EnumInfusionEnchantment.BURROWING) <= 0 || !tool.isCorrectToolForDrops(state) || !(state.is(BlockTags.LOGS) || isOre(state))) return;
        event.setCanceled(true); BURROWING.add(player.getUUID());
        try {
            damage(tool, player);
            BlockPos furthest = ToolMiningRules.furthest(event.getPos(), state.is(BlockTags.LOGS) ? 2 : 1, pos -> world.hasChunkAt(pos)
                && world.getBlockState(pos).getBlock() == state.getBlock() && world.getBlockState(pos).getDestroySpeed(world, pos) >= 0);
            boolean worked = harvest(world, player, furthest);
            world.sendBlockUpdated(event.getPos(), state, state, 3);
            if (worked && state.is(BlockTags.LOGS)) for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++) {
                BlockPos pos = furthest.offset(x, y, z);
                if (world.hasChunkAt(pos)) world.scheduleTick(pos, world.getBlockState(pos).getBlock(), 50 + world.random.nextInt(75));
            }
        } finally { BURROWING.remove(player.getUUID()); }
    }
    private static void damage(ItemStack tool, ServerPlayer player) {
        if (!player.getGameProfile().getName().equals("FakeThaumcraftBore")) tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        else if (DESTRUCTIVE.contains(player.getUUID())) player.takeXpDelay++;
    }
    private static boolean harvest(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (!world.hasChunkAt(pos) || player.blockActionRestricted(world, pos, player.gameMode.getGameModeForPlayer())) return false;
        BlockState state = world.getBlockState(pos); if (state.isAir() || state.getDestroySpeed(world, pos) < 0) return false;
        if (CommonHooks.fireBlockBreak(world, player.gameMode.getGameModeForPlayer(), player, pos, state).isCanceled()) return false;
        if (state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks()) return false;
        var entity = world.getBlockEntity(pos); Block block = state.getBlock(); ItemStack tool = player.getMainHandItem().copy();
        state = block.playerWillDestroy(world, pos, state, player);
        boolean canHarvest = !player.isCreative() && state.canHarvestBlock(world, pos, player);
        boolean removed = state.onDestroyedByPlayer(world, pos, player, canHarvest, world.getFluidState(pos));
        if (removed) {
            block.destroy(world, pos, state);
            if (canHarvest) block.playerDestroy(world, player, pos, state, entity, tool);
        }
        return removed;
    }
    @SubscribeEvent public static void harvestDrops(BlockDropsEvent event) {
        ServerLevel world = event.getLevel(); ItemStack dropTool = event.getTool(); BlockState state = event.getState();
        boolean silk = EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), dropTool) > 0;
        float rareChance = rareEarthChance(state);
        if (!silk && rareChance > 0 && world.random.nextFloat() < rareChance) event.getDrops().add(new ItemEntity(world, event.getPos().getX() + .5, event.getPos().getY() + .5, event.getPos().getZ() + .5, new ItemStack(ItemsTC.nuggetRareEarth)));
        if (event.isCanceled() || !(event.getBreaker() instanceof ServerPlayer player)) return;
        ItemStack tool = dropTool;
        if (tool.isEmpty() || !(silk || effective(tool, state))) return;
        int refining = level(tool, EnumInfusionEnchantment.REFINING);
        if (refining > 0) {
            boolean changed = false;
            for (ItemEntity drop : event.getDrops()) {
                ItemStack refined = SpecialMiningResults.find(drop.getItem(), ToolMiningRules.refiningChance(refining), world.random);
                if (!refined.is(drop.getItem().getItem())) { drop.setItem(refined); changed = true; }
            }
            if (changed) world.playSound(null, event.getPos(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, .2F, .7F + world.random.nextFloat() * .2F);
        }
        if (level(tool, EnumInfusionEnchantment.DESTRUCTIVE) > 0 && !player.isShiftKeyDown() && DESTRUCTIVE.add(player.getUUID())) {
            try {
                // Drop enchantments use the event snapshot; extra mining follows the mutable live tool.
                ItemStack miningTool = player.getMainHandItem();
                Direction face = LAST_FACE.getOrDefault(player, ToolMiningRules.fallbackFace(event.getPos(), player.getX(), player.getEyeY(), player.getZ(), player.getDirection()));
                for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) if (a != 0 || b != 0) {
                    BlockPos pos = ToolMiningRules.destructiveOffset(event.getPos(), face, a, b); if (!world.hasChunkAt(pos)) continue;
                    BlockState neighbor = world.getBlockState(pos);
                    if (!miningTool.isEmpty() && neighbor.getDestroySpeed(world, pos) >= 0 && effective(miningTool, neighbor)) { damage(miningTool, player); harvest(world, player, pos); }
                }
            } finally { DESTRUCTIVE.remove(player.getUUID()); }
        }
        if (level(tool, EnumInfusionEnchantment.COLLECTOR) > 0 && !player.isShiftKeyDown()) {
            var collected = new ArrayList<ItemEntity>();
            for (ItemEntity drop : event.getDrops()) {
                double x = event.getPos().getX() + world.random.nextFloat() * .5F + .25;
                double y = event.getPos().getY() + world.random.nextFloat() * .5F + .25;
                double z = event.getPos().getZ() + world.random.nextFloat() * .5F + .25;
                EntityFollowingItem following = new EntityFollowingItem(world, x, y, z, drop.getItem(), player, 10); following.setDefaultPickUpDelay(); collected.add(following); drop.discard();
            }
            event.getDrops().clear(); event.getDrops().addAll(collected);
        }
        if (level(tool, EnumInfusionEnchantment.LAMPLIGHT) > 0 && !player.isShiftKeyDown()) world.getServer().execute(() -> {
            if (world.isEmptyBlock(event.getPos()) && !world.getBlockState(event.getPos()).is(BlocksTC.effectGlimmer) && world.getMaxLocalRawBrightness(event.getPos()) < 10)
                world.setBlock(event.getPos(), BlocksTC.effectGlimmer.defaultBlockState(), 3);
        });
    }
    private static float rareEarthChance(BlockState state) {
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE) || state.is(BlocksTC.oreAmber) || state.is(BlocksTC.oreQuartz)) return .05F;
        if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) return .075F;
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) return .001F;
        if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE) || state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE) || state.is(Blocks.NETHER_QUARTZ_ORE)) return .01F;
        return 0;
    }
    @SubscribeEvent public static void livingDrops(LivingDropsEvent event) {
        if (event.isCanceled() || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        ItemStack tool = player.getMainHandItem(); boolean collector = level(tool, EnumInfusionEnchantment.COLLECTOR) > 0;
        if (collector) {
            var replacements = new ArrayList<ItemEntity>();
            for (ItemEntity drop : event.getDrops()) {
                EntityFollowingItem item = new EntityFollowingItem(player.level(), drop.getX(), drop.getY(), drop.getZ(), drop.getItem(), player, 10);
                item.setDeltaMovement(drop.getDeltaMovement()); item.setDefaultPickUpDelay(); replacements.add(item); drop.discard();
            }
            event.getDrops().clear(); event.getDrops().addAll(replacements);
        }
        int rank = level(tool, EnumInfusionEnchantment.ESSENCE); if (rank <= 0) return;
        var source = AspectHelper.getEntityAspects(event.getEntity()); if (source == null || source.size() == 0) return;
        var aspects = source.copy(); var types = aspects.getAspects(); var random = player.level().random;
        int count = random.nextInt(5) < rank ? 0 : 99;
        while (count < rank && types.length > 0) {
            var aspect = types[random.nextInt(types.length)];
            if (aspects.getAmount(aspect) > 0) {
                aspects.remove(aspect, 1); ItemStack crystal = ThaumcraftApiHelper.makeCrystal(aspect);
                var entity = event.getEntity();
                ItemEntity drop = collector ? new EntityFollowingItem(player.level(), entity.getX(), entity.getEyeY(), entity.getZ(), crystal, player, 10)
                    : new ItemEntity(player.level(), entity.getX(), entity.getEyeY(), entity.getZ(), crystal);
                event.getDrops().add(drop); count++;
            }
            types = aspects.getAspects(); if (random.nextInt(rank) == 0) count += 1 + random.nextInt(2);
        }
    }
}
