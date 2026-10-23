package thaumcraft.common.tiles.crafting;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.event.EventHooks;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.IInteractWithCaster;
import thaumcraft.api.crafting.IInfusionStabiliser;
import thaumcraft.api.crafting.IInfusionStabiliserExt;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.items.IGogglesDisplayExtended;
import thaumcraft.common.blocks.basic.BlockPillar;
import thaumcraft.common.blocks.devices.BlockPedestal;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.crafting.InfusionMatrixRules;
import thaumcraft.common.lib.crafting.InfusionRecipes;
import thaumcraft.common.lib.events.EssentiaHandler;
import thaumcraft.common.lib.potions.WarpEffects;
import thaumcraft.common.lib.warp.WarpGameplay;
import thaumcraft.common.tiles.devices.TileStabilizer;

/** Real source TC6 infusion state machine, not a timed-output approximation. Native pedestal
 * inventory, registered owner recipes, one-unit external essentia drains, source ordered component
 * consumption and all24 instability rolls drive the world. Source RNG control-flow/arithmetic,
 * zero-key essentia list, unpaid XP quirks and failed-job losses remain. Modern adaptations are
 * server-thread ownership, no implicit chunk loading, finite NBT validation and isolated public
 * sync tags; native block-event particles replace only the old GL/beam presentation.
 */
public final class TileInfusionMatrix extends BlockEntity implements IInteractWithCaster, IAspectContainer, IGogglesDisplayExtended {
    private static final int MAX_COMPONENTS = 3168; // Source288 noncentral columns *11 heights.
    private static final Random SOURCE_DROP_RANDOM = new Random(); // Old InventoryHelper's separate RNG, never level.random.
    private static final DecimalFormat FORMAT = new DecimalFormat("#######.##");
    private static final int[] CORNER_X = {-1, 1, 1, -1}, CORNER_Z = {-1, -1, 1, 1};
    private final ArrayList<BlockPos> pedestals = new ArrayList<>(), problemBlocks = new ArrayList<>();
    private final HashMap<Block, Integer> matchedPairs = new HashMap<>();
    public boolean active, crafting, checkSurroundings = true;
    public float costMult, stability, stabilityReplenish, startUp;
    public int stabilityCap = 25, count, craftCount;
    private int cycleTime = 20, countDelay = 10, itemCount, recipeInstability, recipeXP, recipeType;
    private AspectList recipeEssentia = new AspectList();
    private ArrayList<ItemStack> recipeIngredients = new ArrayList<>();
    private ItemStack recipeInput = ItemStack.EMPTY;
    private Object recipeOutput;
    private String recipePlayer, recipeOutputLabel;
    public final HashMap<String, SourceFX> sourceFX = new HashMap<>();

    public TileInfusionMatrix(BlockPos pos, BlockState state) { super(InfusionRegistries.INFUSION_MATRIX, pos, state); }
    public static void tick(Level level, BlockPos pos, BlockState state, TileInfusionMatrix tile) { tile.update(); }
    public void update() {
        count++;
        if (checkSurroundings) { checkSurroundings = false; getSurroundings(); }
        if (level == null) return;
        if (level.isClientSide) { doEffects(); return; }
        requireServer();
        if (count % (crafting ? 20 : 100) == 0 && !validLocation()) { active = false; changedAndSync(); return; }
        if (active && !crafting && stability < stabilityCap && count % Math.max(5, countDelay) == 0) {
            stability = InfusionMatrixRules.idleStability(stability, stabilityCap, stabilityReplenish); changedAndSync();
        }
        if (active && crafting && count % Math.max(1, countDelay) == 0) { craftCycle(); setChanged(); }
    }
    public boolean validLocation() {
        if (level == null || !loaded(worldPosition.below(2)) || !(level.getBlockState(worldPosition.below(2)).getBlock() instanceof BlockPedestal)) return false;
        for (int i = 0; i < 4; i++) {
            BlockPos pos = worldPosition.offset(CORNER_X[i], -2, CORNER_Z[i]);
            if (!loaded(pos) || !(level.getBlockState(pos).getBlock() instanceof BlockPillar)) return false;
        }
        return true;
    }
    private TilePedestal pedestal(BlockPos pos) {
        return loaded(pos) && level.getBlockEntity(pos) instanceof TilePedestal pedestal ? pedestal : null;
    }
    private boolean loaded(BlockPos pos) { return level != null && level.hasChunkAt(pos); }
    private ServerLevel requireServer() {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) throw new IllegalStateException("Infusion requires the owning server thread");
        return server;
    }
    public void markDirty() { setChanged(); }
    public void syncTile(boolean force) { changedAndSync(); }
    public void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public void craftingStart(Player player) {
        ServerLevel server = requireServer();
        if (!(player instanceof ServerPlayer) || player.level() != server || player.isRemoved() || !player.isAlive()) return;
        if (!validLocation()) { active = false; changedAndSync(); return; }
        getSurroundings();
        TilePedestal center = pedestal(worldPosition.below(2));
        recipeInput = center == null ? ItemStack.EMPTY : center.getStackInSlot(0).copy();
        if (recipeInput.isEmpty()) return;
        ArrayList<ItemStack> components = new ArrayList<>();
        for (BlockPos pos : pedestals) {
            TilePedestal pedestal = pedestal(pos);
            if (pedestal != null && !pedestal.getStackInSlot(0).isEmpty()) components.add(pedestal.getStackInSlot(0).copy());
        }
        if (components.isEmpty()) return;
        InfusionRecipe recipe = InfusionRecipes.findMatching(components, recipeInput, player);
        if (costMult < .5F) costMult = .5F;
        if (recipe == null) return;
        // Source invokes its output getter twice. Preserve that callback order.
        Object probe = recipe.getRecipeOutput(player, recipeInput, components);
        if (probe instanceof Object[]) {
            Object output = recipe.getRecipeOutput(player, recipeInput, components);
            if (!(output instanceof Object[] values) || values.length != 2 || !(values[0] instanceof String label) || !(values[1] instanceof Tag tag)) return;
            recipeOutputLabel = label; recipeOutput = tag.copy();
        } else recipeOutput = copyOutput(recipe.getRecipeOutput(player, recipeInput, components));
        if (!supportedOutput(recipeOutput, recipeOutputLabel)) { recipeOutput = null; return; }
        recipeType = 0; // Source enchanting subclass also starts type0; type1 XP is only an old loaded-job path.
        recipeIngredients = components;
        recipeInstability = recipe.getInstability(player, recipeInput, components);
        if (recipeInstability < 0 || recipeInstability > 1_000_000) { recipeOutput = null; return; }
        AspectList original = recipe.getAspects(player, recipeInput, components);
        if (!validAspects(original)) { recipeOutput = null; return; }
        recipeEssentia = new AspectList();
        for (Aspect aspect : original.getAspects()) {
            int cost = InfusionMatrixRules.cost(original.getAmount(aspect), costMult);
            if (cost > 0) recipeEssentia.add(aspect, cost);
        }
        if (!validAspects(recipeEssentia)) { recipeOutput = null; return; }
        recipePlayer = player.getGameProfile().getName(); crafting = true;
        level.playSound(null, worldPosition, SoundsTC.craftstart, SoundSource.BLOCKS, .5F, 1F); changedAndSync();
    }
    private static Object copyOutput(Object output) {
        return output instanceof ItemStack stack ? stack.copy() : output instanceof Tag tag ? tag.copy() : output;
    }
    private static boolean supportedOutput(Object output, String label) {
        return output instanceof ItemStack stack && !stack.isEmpty() && stack.getCount() <= stack.getMaxStackSize()
            || output instanceof Tag tag && label != null && !label.isEmpty() && label.length() <= 256 && tag.toString().length() <= 65536
            || output instanceof Holder<?> holder && holder.value() instanceof Enchantment && holder.unwrapKey().isPresent();
    }

    public void craftCycle() {
        ServerLevel server = requireServer();
        if (recipeInput.isEmpty() || recipeIngredients == null || !supportedOutput(recipeOutput, recipeOutputLabel)) { abortCraft(); return; }
        stability = InfusionMatrixRules.craftStability(stability, stabilityCap, recipeInstability, stabilityReplenish, level.random.nextFloat());
        TilePedestal center = pedestal(worldPosition.below(2));
        boolean valid = center != null && !center.getStackInSlot(0).isEmpty() && InfusionRecipes.itemsMatch(center.getStackInSlot(0), recipeInput);
        // Keep the original RNG short-circuit: invalid input never rolls nextInt(1500).
        if (!valid || stability < 0 && InfusionMatrixRules.mishap(true, stability, level.random.nextInt(1500))) {
            instabilityEvent(level.random.nextInt(24));
            stability += 5F + level.random.nextFloat() * 5F;
            grantInstabilityResearch();
            if (valid) return;
        }
        if (!valid) { abortCraft(); return; }
        if (recipeType == 1 && recipeXP > 0) {
            List<Player> targets = level.getEntitiesOfClass(Player.class, nearby());
            if (!targets.isEmpty()) {
                for (Player target : targets) if (target.getAbilities().instabuild || target.experienceLevel > 0) {
                    if (!target.getAbilities().instabuild) target.giveExperienceLevels(-1);
                    recipeXP--; target.hurt(level.damageSources().magic(), level.random.nextInt(2));
                    level.blockEvent(worldPosition, getBlockState().getBlock(), 101, target.getId());
                    target.playSound(SoundEvents.LAVA_EXTINGUISH, 1F, 2F + level.random.nextFloat() * .4F);
                    countDelay = cycleTime; return;
                }
                Aspect[] keys = recipeEssentia.getAspects();
                if (keys.length > 0 && level.random.nextInt(3) == 0) {
                    recipeEssentia.add(keys[level.random.nextInt(keys.length)], 1); stability -= .25F; changedAndSync();
                }
            }
            return;
        }
        if (recipeType == 1 && recipeXP == 0) countDelay = cycleTime / 2;
        if (countDelay < 1) countDelay = 1;
        if (recipeEssentia.visSize() > 0) {
            for (Aspect aspect : recipeEssentia.getAspects()) {
                int amount = recipeEssentia.getAmount(aspect);
                if (amount > 0) {
                    if (EssentiaHandler.drainEssentia(this, aspect, null, 12, amount > 1 ? countDelay : 0)) {
                        recipeEssentia.reduce(aspect, 1); changedAndSync(); return;
                    }
                    stability -= .25F; changedAndSync();
                }
            }
            checkSurroundings = true;
        } else if (recipeIngredients.isEmpty()) {
            crafting = false; craftingFinish(recipeOutput, recipeOutputLabel); recipeOutput = null; changedAndSync();
        } else {
            for (int index = 0; index < recipeIngredients.size(); index++) {
                for (BlockPos pos : pedestals) {
                    TilePedestal pedestal = pedestal(pos);
                    if (pedestal != null && !pedestal.getStackInSlot(0).isEmpty() && InfusionRecipes.itemsMatch(pedestal.getStackInSlot(0), recipeIngredients.get(index))) {
                        var step = InfusionMatrixRules.component(itemCount); itemCount = step.remaining();
                        if (step.begin()) {
                            level.blockEvent(worldPosition, getBlockState().getBlock(), 100, sourceParameter(pos));
                        } else if (step.consume()) {
                            ItemStack stack = pedestal.getStackInSlot(0);
                            ItemStack remainder = stack.getCraftingRemainingItem();
                            pedestal.setInventorySlotContents(0, remainder.isEmpty() ? ItemStack.EMPTY : remainder.copy());
                            pedestal.changedAndSync(); recipeIngredients.remove(index); setChanged();
                        }
                        return;
                    }
                }
                // reduce(), not remove(), retains zero amount keys, so missing components can
                // reintroduce already-paid essentia exactly as in the source.
                Aspect[] keys = recipeEssentia.getAspects();
                if (keys.length > 0 && level.random.nextInt(1 + index) == 0) {
                    recipeEssentia.add(keys[level.random.nextInt(keys.length)], 1); stability -= .25F; changedAndSync();
                }
            }
        }
    }
    private void abortCraft() {
        crafting = false; recipeEssentia = new AspectList(); recipeInstability = 0;
        changedAndSync(); level.playSound(null, worldPosition, SoundsTC.craftfail, SoundSource.BLOCKS, 1F, .6F);
    }
    private AABB nearby() { return new AABB(worldPosition).inflate(10); }
    private void instabilityEvent(int roll) {
        switch (InfusionMatrixRules.mishap(roll)) {
            case EJECT -> ejectIngredient(0);
            case WARP -> warp();
            case ZAP_ONE -> zap(false);
            case ZAP_ALL -> zap(true);
            case EJECT_GOO -> ejectIngredient(1);
            case EJECT_FLUX -> ejectIngredient(2);
            case EJECT_GOO_3 -> ejectIngredient(3);
            case DESTROY_FLUX -> ejectIngredient(4);
            case HARM_ONE -> harm(false);
            case EJECT_EXPLOSION -> ejectIngredient(5);
            case HARM_ALL -> harm(true);
            case EXPLOSION -> level.explode(null, worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5,
                1.5F + level.random.nextFloat(), Level.ExplosionInteraction.NONE);
        }
    }
    private void zap(boolean all) {
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, nearby())) {
            arc(worldPosition, target.position().add(0, target.getBbHeight() / 2F, 0));
            target.hurt(level.damageSources().magic(), 4 + level.random.nextInt(4));
            if (!all) break;
        }
    }
    private void harm(boolean all) {
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, nearby())) {
            if (level.random.nextBoolean()) target.addEffect(new MobEffectInstance(WarpEffects.FLUX_TAINT, 120, 0, false, true));
            else {
                MobEffectInstance effect = new MobEffectInstance(WarpEffects.VIS_EXHAUST, 2400, 0, true, true);
                effect.getCures().clear(); target.addEffect(effect);
            }
            if (!all) break;
        }
    }
    private void warp() {
        List<Player> targets = level.getEntitiesOfClass(Player.class, nearby());
        if (targets.isEmpty()) return;
        Player target = targets.get(level.random.nextInt(targets.size()));
        if (level.random.nextFloat() < .25F) WarpGameplay.addWarpToPlayer(target, 1, EnumWarpType.NORMAL);
        else WarpGameplay.addWarpToPlayer(target, 2 + level.random.nextInt(4), EnumWarpType.TEMPORARY);
    }
    private void grantInstabilityResearch() {
        for (Player player : level.getEntitiesOfClass(Player.class, nearby())) if (player instanceof ServerPlayer owner) {
            var knowledge = ThaumcraftCapabilities.getKnowledge(owner);
            if (!knowledge.isResearchKnown("!INSTABILITY")) {
                knowledge.addResearch("!INSTABILITY"); knowledge.sync(owner);
                owner.displayClientMessage(Component.translatable("got.instability").withStyle(ChatFormatting.DARK_PURPLE), true);
            }
        }
    }
    private void ejectIngredient(int type) {
        for (int retry = 0; retry < 25 && !pedestals.isEmpty(); retry++) {
            BlockPos pos = pedestals.get(level.random.nextInt(pedestals.size()));
            TilePedestal pedestal = pedestal(pos);
            if (pedestal == null || pedestal.getStackInSlot(0).isEmpty()) continue;
            BlockPos mitigator = pedestal.findInstabilityMitigator();
            if (mitigator != null && loaded(mitigator) && level.getBlockEntity(mitigator) instanceof TileStabilizer stabilizer
                && stabilizer.mitigate(5 + level.random.nextInt(6))) {
                level.blockEvent(pos, level.getBlockState(pos).getBlock(), 5, 0);
                arc(worldPosition, Vec3.atCenterOf(pos.above())); arc(pos.above(), Vec3.atCenterOf(mitigator)); return;
            }
            if (type > 3 && type != 5) pedestal.setInventorySlotContents(0, ItemStack.EMPTY);
            else {
                dropSourceInventory(pos, pedestal.getStackInSlot(0));
                pedestal.setInventorySlotContents(0, ItemStack.EMPTY);
            }
            pedestal.changedAndSync();
            if (type == 1 || type == 3) {
                level.setBlockAndUpdate(pos.above(), BlocksTC.fluxGoo.defaultBlockState());
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, .3F, 1F);
            } else if (type == 2 || type == 4) AuraHelper.polluteAura(level, pos, 5 + level.random.nextInt(5), true);
            else if (type == 5) level.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 1F, Level.ExplosionInteraction.NONE);
            level.blockEvent(pos, level.getBlockState(pos).getBlock(), 11, 0);
            arc(worldPosition, Vec3.atCenterOf(pos.above())); return;
        }
    }
    private void dropSourceInventory(BlockPos pos, ItemStack stack) {
        float x = SOURCE_DROP_RANDOM.nextFloat() * .8F + .1F, y = SOURCE_DROP_RANDOM.nextFloat() * .8F + .1F, z = SOURCE_DROP_RANDOM.nextFloat() * .8F + .1F;
        while (!stack.isEmpty()) {
            ItemEntity drop = new ItemEntity(level, pos.getX() + x, pos.getY() + y, pos.getZ() + z, stack.split(SOURCE_DROP_RANDOM.nextInt(21) + 10));
            drop.setDeltaMovement(SOURCE_DROP_RANDOM.nextGaussian() * .05000000074505806,
                SOURCE_DROP_RANDOM.nextGaussian() * .05000000074505806 + .20000000298023224,
                SOURCE_DROP_RANDOM.nextGaussian() * .05000000074505806);
            level.addFreshEntity(drop);
        }
    }
    private void arc(BlockPos from, Vec3 to) {
        float red = .3F - level.random.nextFloat() * .1F, blue = .3F - level.random.nextFloat() * .1F;
        // Preserve both source RNG draws and their FLOAT precision, not an 8-bit colour rounding.
        thaumcraft.common.lib.fx.SourceFX.emit(level,thaumcraft.common.lib.fx.SourceFX.event(
            thaumcraft.common.lib.network.fx.PacketSourceFX.Kind.ARC,Vec3.atCenterOf(from),new Vec3((float)to.x,(float)to.y,(float)to.z),
            Float.floatToIntBits(red),Float.floatToIntBits(blue),1,-1,""));
    }

    public void craftingFinish(Object output, String label) {
        requireServer();
        TilePedestal center = pedestal(worldPosition.below(2));
        if (center == null) return;
        ItemStack input = center.getStackInSlot(0);
        if (output instanceof ItemStack stack) {
            ItemStack result = stack.copy();
            if (input.isDamageableItem() && input.isDamaged() && result.isDamageableItem() && !result.isDamaged())
                result.setDamageValue(InfusionMatrixRules.damage(input.getDamageValue(), input.getMaxDamage(), result.getMaxDamage()));
            center.setInventorySlotContentsFromInfusion(0, result);
        } else if (output instanceof Tag tag) {
            if (!InfusionRecipes.applyOutputTag(input, label, tag.copy(), level.registryAccess())) { abortCraft(); return; }
            center.changedAndSync();
        } else if (output instanceof Holder<?> holder && holder.value() instanceof Enchantment) {
            @SuppressWarnings("unchecked") Holder<Enchantment> enchantment = (Holder<Enchantment>) holder;
            EnchantmentHelper.updateEnchantments(input, values -> values.set(enchantment, values.getLevel(enchantment) + 1));
            center.changedAndSync();
        } else { abortCraft(); return; }
        if (recipePlayer != null) {
            Player owner = level.players().stream().filter(player -> player.getGameProfile().getName().equals(recipePlayer)).findFirst().orElse(null);
            if (owner != null) EventHooks.firePlayerCraftingEvent(owner, center.getStackInSlot(0), new SimpleContainer(recipeIngredients.toArray(ItemStack[]::new)));
        }
        recipeEssentia = new AspectList(); recipeInstability = 0; changedAndSync();
        level.blockEvent(worldPosition.below(2), level.getBlockState(worldPosition.below(2)).getBlock(), 12, 0);
        level.playSound(null, worldPosition, SoundsTC.wand, SoundSource.BLOCKS, .5F, 1F);
    }

    private static boolean sourceSkull(Block block) {
        return block == Blocks.SKELETON_SKULL || block == Blocks.SKELETON_WALL_SKULL || block == Blocks.WITHER_SKELETON_SKULL
            || block == Blocks.WITHER_SKELETON_WALL_SKULL || block == Blocks.ZOMBIE_HEAD || block == Blocks.ZOMBIE_WALL_HEAD
            || block == Blocks.PLAYER_HEAD || block == Blocks.PLAYER_WALL_HEAD || block == Blocks.CREEPER_HEAD || block == Blocks.CREEPER_WALL_HEAD
            || block == Blocks.DRAGON_HEAD || block == Blocks.DRAGON_WALL_HEAD;
    }
    private static Block sourceBlockIdentity(Block block) { return sourceSkull(block) ? Blocks.SKELETON_SKULL : block; }
    public void getSurroundings() {
        if (level == null) return;
        Set<Long> stabilizers = new HashSet<>();
        pedestals.clear(); matchedPairs.clear(); problemBlocks.clear();
        cycleTime = 10; stabilityReplenish = 0; costMult = 1;
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) for (int yy = -3; yy <= 7; yy++) {
            if (x == 0 && z == 0) continue;
            BlockPos pos = worldPosition.offset(x, -yy, z);
            if (!loaded(pos)) continue;
            Block block = level.getBlockState(pos).getBlock();
            if (block instanceof BlockPedestal) pedestals.add(pos);
            try {
                if (sourceSkull(block) || block instanceof IInfusionStabiliser stabilizer && stabilizer.canStabaliseInfusion(level, pos)) stabilizers.add(sourcePosition(pos));
            } catch (RuntimeException unsupportedAddon) { /* Source ignores failed stabilizer callbacks, not invented stability. */ }
        }
        while (!stabilizers.isEmpty()) {
            long first = stabilizers.iterator().next();
            BlockPos one = new BlockPos(InfusionMatrixRules.sourceX(first), InfusionMatrixRules.sourceY(first), InfusionMatrixRules.sourceZ(first));
            BlockPos two = new BlockPos(2 * worldPosition.getX() - one.getX(), one.getY(), 2 * worldPosition.getZ() - one.getZ());
            try {
                if (!loaded(two)) { stabilizers.remove(first); continue; }
                Block firstBlock = level.getBlockState(one).getBlock(), secondBlock = level.getBlockState(two).getBlock();
                float a = firstBlock instanceof IInfusionStabiliserExt ext ? ext.getStabilizationAmount(level, one) : .1F;
                float b = secondBlock instanceof IInfusionStabiliserExt ext ? ext.getStabilizationAmount(level, two) : .1F;
                if (!Float.isFinite(a) || !Float.isFinite(b)) { stabilizers.remove(first); continue; }
                if (sourceBlockIdentity(firstBlock) == sourceBlockIdentity(secondBlock) && a == b) {
                    if (firstBlock instanceof IInfusionStabiliserExt ext && ext.hasSymmetryPenalty(level, one, two)) {
                        float penalty = ext.getSymmetryPenalty(level, one);
                        if (Float.isFinite(penalty)) { stabilityReplenish -= penalty; problemBlocks.add(one); }
                    } else {
                        Block identity = sourceBlockIdentity(firstBlock);
                        int matched = matchedPairs.getOrDefault(identity, 0);
                        stabilityReplenish += InfusionMatrixRules.diminishing(a, matched); matchedPairs.put(identity, matched + 1);
                    }
                } else { stabilityReplenish -= Math.max(a, b); problemBlocks.add(one); }
                stabilizers.remove(sourcePosition(two));
            } catch (RuntimeException unsupportedAddon) { /* Same isolated callback failure semantics as TC6. */ }
            stabilizers.remove(first);
        }
        InfusionMatrixRules.Foundation foundation = InfusionMatrixRules.Foundation.ARCANE_OR_MIXED;
        boolean allAncient = true, allEldritch = true, allPillars = true;
        InfusionMatrixRules.Upgrade[] upgrades = new InfusionMatrixRules.Upgrade[4];
        for (int i = 0; i < 4; i++) {
            BlockPos pillarPos = worldPosition.offset(CORNER_X[i], -2, CORNER_Z[i]);
            Block pillar = loaded(pillarPos) ? level.getBlockState(pillarPos).getBlock() : Blocks.AIR;
            allPillars &= pillar instanceof BlockPillar; allAncient &= pillar == BlocksTC.pillarAncient; allEldritch &= pillar == BlocksTC.pillarEldritch;
            BlockPos upgradePos = pillarPos.below(); Block upgrade = loaded(upgradePos) ? level.getBlockState(upgradePos).getBlock() : Blocks.AIR;
            upgrades[i] = upgrade == BlocksTC.matrixSpeed ? InfusionMatrixRules.Upgrade.SPEED
                : upgrade == BlocksTC.matrixCost ? InfusionMatrixRules.Upgrade.COST : InfusionMatrixRules.Upgrade.NONE;
        }
        if (allPillars && allAncient) foundation = InfusionMatrixRules.Foundation.ANCIENT;
        if (allPillars && allEldritch) foundation = InfusionMatrixRules.Foundation.ELDRITCH;
        InfusionMatrixRules.Pedestal[] variants = new InfusionMatrixRules.Pedestal[pedestals.size()];
        for (int i = 0; i < variants.length; i++) {
            Block block = level.getBlockState(pedestals.get(i)).getBlock();
            variants[i] = block == BlocksTC.pedestalAncient ? InfusionMatrixRules.Pedestal.ANCIENT
                : block == BlocksTC.pedestalEldritch ? InfusionMatrixRules.Pedestal.ELDRITCH : InfusionMatrixRules.Pedestal.ARCANE;
        }
        var modifiers = InfusionMatrixRules.modifiers(foundation, upgrades, variants, stabilityReplenish);
        cycleTime = modifiers.cycleTime(); countDelay = modifiers.countDelay(); costMult = modifiers.cost(); stabilityReplenish = modifiers.replenish();
    }
    private static long sourcePosition(BlockPos pos) { return InfusionMatrixRules.sourcePosition(pos.getX(), pos.getY(), pos.getZ()); }
    @Override public boolean onCasterRightClick(Level world, ItemStack caster, Player player, BlockPos pos, Direction side, InteractionHand hand) {
        if (world != level || !pos.equals(worldPosition)) return false;
        if (world.isClientSide && active && !crafting) checkSurroundings = true;
        if (!world.isClientSide && active && !crafting) craftingStart(player);
        else if (!world.isClientSide && !active && validLocation()) {
            requireServer(); level.playSound(null, worldPosition, SoundsTC.craftstart, SoundSource.BLOCKS, .5F, 1F); active = true; changedAndSync();
        }
        return false; // Source alwaysfalse, including successful activation/start.
    }

    public void readSyncNBT(CompoundTag tag) {
        active = tag.getBoolean("active"); crafting = tag.getBoolean("crafting");
        stability = Float.isFinite(tag.getFloat("stability")) ? tag.getFloat("stability") : 0;
        recipeInstability = Math.max(0, Math.min(1_000_000, tag.getInt("recipeinst")));
        recipeEssentia.readFromNBT(tag);
        // AspectList intentionally ignores removed addon aspects globally. A paid infusion job
        // must not turn their outstanding costs into free completion when loading that save.
        if (!validEncodedAspects(tag) || !validAspects(recipeEssentia)) { recipeEssentia = new AspectList(); crafting = false; }
    }
    public CompoundTag writeSyncNBT(CompoundTag tag) {
        tag.putBoolean("active", active); tag.putBoolean("crafting", crafting); tag.putFloat("stability", stability);
        tag.putInt("recipeinst", recipeInstability); recipeEssentia.writeToNBT(tag); return tag;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); writeSyncNBT(tag);
        if (recipeIngredients != null && !recipeIngredients.isEmpty()) {
            ListTag ingredients = new ListTag();
            for (ItemStack stack : recipeIngredients) if (!stack.isEmpty()) {
                CompoundTag row = (CompoundTag) stack.save(registries); row.putByte("item", (byte) count); ingredients.add(row); count++;
            }
            tag.put("recipein", ingredients); // Preserve source SAVE-only count++ quirk; public sync never calls saveAdditional.
        }
        if (recipeOutput instanceof ItemStack stack) { tag.putString("rotype", "@"); tag.put("recipeout", stack.save(registries)); }
        else if (recipeOutput instanceof Tag output) { tag.putString("rotype", recipeOutputLabel); tag.put("recipeout", output.copy()); }
        else if (recipeOutput instanceof Holder<?> holder && holder.value() instanceof Enchantment && holder.unwrapKey().isPresent()) {
            // Source did not persist Enchantment outputs at all. An explicit new field avoids
            // stealing any formerly valid addon output label (for example "#").
            tag.putString("recipeenchantment", holder.unwrapKey().get().location().toString());
        }
        if (!recipeInput.isEmpty()) tag.put("recipeinput", recipeInput.save(registries));
        tag.putInt("recipetype", recipeType); tag.putInt("recipexp", recipeXP); tag.putString("recipeplayer", recipePlayer == null ? "" : recipePlayer);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); readSyncNBT(tag);
        // A malformed reload must not retain a previous in-memory output/actor/job.
        recipeOutput = null; recipeOutputLabel = null; recipeInput = ItemStack.EMPTY; recipePlayer = null;
        recipeType = 0; recipeXP = 0; checkSurroundings = true;
        recipeIngredients = new ArrayList<>(); ListTag ingredients = tag.getList("recipein", Tag.TAG_COMPOUND);
        if (ingredients.size() > MAX_COMPONENTS) { crafting = false; return; }
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, ingredients.getCompound(i));
            if (stack.isEmpty()) { crafting = false; return; }
            recipeIngredients.add(stack);
        }
        String type = tag.getString("rotype");
        if (tag.contains("recipeenchantment", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("recipeenchantment"));
            if (id != null) recipeOutput = registries.lookupOrThrow(Registries.ENCHANTMENT).get(ResourceKey.create(Registries.ENCHANTMENT, id)).orElse(null);
        } else if (type.equals("@")) recipeOutput = ItemStack.parseOptional(registries, tag.getCompound("recipeout"));
        else if (!type.isEmpty() && tag.contains("recipeout")) { recipeOutputLabel = type; recipeOutput = tag.get("recipeout").copy(); }
        recipeInput = ItemStack.parseOptional(registries, tag.getCompound("recipeinput"));
        recipeType = tag.getInt("recipetype"); recipeXP = Math.max(0, Math.min(1_000_000, tag.getInt("recipexp")));
        recipePlayer = tag.getString("recipeplayer"); if (recipePlayer.isEmpty()) recipePlayer = null;
        if (recipeType < 0 || recipeType > 1 || recipeInput.isEmpty() || !supportedOutput(recipeOutput, recipeOutputLabel)
            || recipePlayer != null && recipePlayer.length() > 256) crafting = false;
        checkSurroundings = true;
    }
    private static boolean validAspects(AspectList aspects) {
        if (aspects == null || aspects.size() > 512) return false;
        long total = 0;
        for (Aspect aspect : aspects.getAspects()) {
            if (aspect == null || aspects.getAmount(aspect) < 0 || aspects.getAmount(aspect) > 1_000_000) return false;
            total += aspects.getAmount(aspect);
        }
        return total <= 1_000_000;
    }
    private static boolean validEncodedAspects(CompoundTag tag) {
        if (!tag.contains("Aspects")) return true;
        if (!tag.contains("Aspects", Tag.TAG_LIST)) return false;
        ListTag entries = (ListTag) tag.get("Aspects");
        if (entries.size() > 512 || !entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) return false;
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.contains("key", Tag.TAG_STRING) || Aspect.getAspect(entry.getString("key")) == null
                || !entry.contains("amount", Tag.TAG_ANY_NUMERIC) || entry.getInt("amount") < 0) return false;
        }
        return true;
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return writeSyncNBT(new CompoundTag()); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { readSyncNBT(tag); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        readSyncNBT(packet.getTag());
    }
    private int sourceParameter(BlockPos pos) {
        int x = pos.getX() - worldPosition.getX() + 8, z = pos.getZ() - worldPosition.getZ() + 8, y = pos.getY() - worldPosition.getY() + 7;
        return x | z << 5 | y << 10;
    }
    @Override public boolean triggerEvent(int id, int parameter) {
        if (id != 100 && id != 101) return super.triggerEvent(id, parameter);
        if (level != null && level.isClientSide) {
            BlockPos source = id == 101 ? worldPosition : worldPosition.offset((parameter & 31) - 8, (parameter >> 10 & 15) - 7, (parameter >> 5 & 31) - 8);
            if (id == 100 && (Math.abs(source.getX() - worldPosition.getX()) > 8 || Math.abs(source.getZ() - worldPosition.getZ()) > 8
                || source.getY() - worldPosition.getY() < -7 || source.getY() - worldPosition.getY() > 3)) return false;
            String key = source + ":" + (id == 101 ? parameter : 0);
            if (sourceFX.size() < MAX_COMPONENTS || sourceFX.containsKey(key)) sourceFX.put(key, new SourceFX(source, id == 100 ? 60 : 15, id == 101 ? parameter : 0));
        }
        return true;
    }
    private void doEffects() {
        if (crafting) {
            if (craftCount == 0) level.playLocalSound(worldPosition, SoundsTC.infuserstart, SoundSource.BLOCKS, .5F, 1F, false);
            else if (craftCount % 65 == 0) level.playLocalSound(worldPosition, SoundsTC.infuser, SoundSource.BLOCKS, .5F, 1F, false);
            craftCount++;
        } else if (craftCount > 0) craftCount = Math.max(0, Math.min(50, craftCount - 2));
        if (active && startUp != 1F) { startUp += Math.max(startUp / 10F, .001F); if (startUp > .999F) startUp = 1F; }
        if (!active && startUp > 0) { startUp -= startUp / 10F; if (startUp < .001F) startUp = 0; }
        thaumcraft.common.lib.fx.SourceFX.local(level,"matrix",Vec3.atLowerCornerOf(worldPosition),Vec3.ZERO,0,0);
    }
    public final class SourceFX {
        public final BlockPos loc; public int ticks, color;
        public SourceFX(BlockPos loc, int ticks, int color) { this.loc = loc; this.ticks = ticks; this.color = color; }
    }
    public int cycleTime() { return cycleTime; }
    public int countDelay() { return countDelay; }
    public int pendingComponents() { return recipeIngredients.size(); }
    public List<BlockPos> pedestalPositions() { return List.copyOf(pedestals); }
    public List<BlockPos> problemPositions() { return List.copyOf(problemBlocks); }
    @Override public String[] getIGogglesText() {
        Language language = Language.getInstance();
        String status = ChatFormatting.BOLD + language.getOrDefault("stability." + InfusionMatrixRules.stability(stability, stabilityCap).name());
        String gain = ChatFormatting.GOLD + "" + ChatFormatting.ITALIC + FORMAT.format(stabilityReplenish) + " " + language.getOrDefault("stability.gain");
        float loss = InfusionMatrixRules.lossPerCycle(recipeInstability, stability, stabilityCap);
        return loss != 0 ? new String[]{status, gain, ChatFormatting.RED + language.getOrDefault("stability.range") + ChatFormatting.ITALIC + FORMAT.format(loss) + " " + language.getOrDefault("stability.loss")} : new String[]{status, gain};
    }
    @Override public AspectList getAspects() { return recipeEssentia; }
    @Override public void setAspects(AspectList values) {}
    @Override public boolean doesContainerAccept(Aspect aspect) { return true; }
    @Override public int addToContainer(Aspect aspect, int amount) { return 0; }
    @Override public boolean takeFromContainer(Aspect aspect, int amount) { return false; }
    @Override public boolean takeFromContainer(AspectList values) { return false; }
    @Override public boolean doesContainerContainAmount(Aspect aspect, int amount) { return false; }
    @Override public boolean doesContainerContain(AspectList values) { return false; }
    @Override public int containerContains(Aspect aspect) { return 0; }
}
