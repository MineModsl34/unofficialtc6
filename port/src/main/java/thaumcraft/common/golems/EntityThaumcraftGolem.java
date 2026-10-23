package thaumcraft.common.golems;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.sounds.SoundEvents;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.IGolemProperties;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.entities.construct.EntityOwnedConstruct;
import thaumcraft.common.golems.ai.AIFollowOwner;
import thaumcraft.common.golems.ai.AIGotoBlock;
import thaumcraft.common.golems.ai.AIGotoEntity;
import thaumcraft.common.golems.ai.AIGotoHome;
import thaumcraft.common.golems.ai.AIOwnerHurtByTarget;
import thaumcraft.common.golems.ai.AIOwnerHurtTarget;
import thaumcraft.common.golems.ai.PathNavigateGolemAir;
import thaumcraft.common.golems.ai.PathNavigateGolemGround;
import thaumcraft.common.golems.tasks.TaskHandler;
import thaumcraft.common.golems.tasks.TaskRules;
import thaumcraft.common.lib.SoundsTC;

/** Full BETA26 modular golem runtime. Equipment slots are its one/two carried inventories. */
public class EntityThaumcraftGolem extends EntityOwnedConstruct implements IGolemAPI, RangedAttackMob {
    public int rankXp;
    private static final EntityDataAccessor<Integer> PROPS1 = SynchedEntityData.defineId(EntityThaumcraftGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PROPS2 = SynchedEntityData.defineId(EntityThaumcraftGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PROPS3 = SynchedEntityData.defineId(EntityThaumcraftGolem.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> CLIMBING = SynchedEntityData.defineId(EntityThaumcraftGolem.class, EntityDataSerializers.BYTE);
    public boolean redrawParts;
    public float armSwingProgress;
    /** Filled only by the client registration; common/server code has no client dependency. */
    public static BiConsumer<EntityThaumcraftGolem, Byte> CLIENT_STATUS;
    private boolean firstRun = true;
    protected Task task;
    protected int taskID = Integer.MAX_VALUE;
    public static final int XPM = 1000;

    public EntityThaumcraftGolem(EntityType<? extends EntityThaumcraftGolem> type, Level world) {
        super(type, world);
        xpReward = 5;
        setDropChance(EquipmentSlot.OFFHAND, 0);
    }
    public EntityThaumcraftGolem(Level world) { this(Golem9EntityRegistries.GOLEM, world); }
    @Override protected void registerGoals() {
        goalSelector.addGoal(2, new AIGotoEntity(this));
        goalSelector.addGoal(3, new AIGotoBlock(this));
        goalSelector.addGoal(4, new AIGotoHome(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PROPS1, 0);
        builder.define(PROPS2, 0);
        builder.define(PROPS3, 0);
        builder.define(CLIMBING, (byte) 0);
    }
    @Override public IGolemProperties getProperties() {
        long packed = GolemCoreRules.joinProperties(entityData.get(PROPS1), entityData.get(PROPS2));
        try { return GolemProperties.fromLong(packed); }
        catch (IllegalArgumentException invalid) { return GolemProperties.fromLong(0); }
    }
    @Override public void setProperties(IGolemProperties properties) {
        long packed = properties.toLong();
        if (!GolemProperties.isValid(packed)) throw new IllegalArgumentException("Invalid golem properties");
        entityData.set(PROPS1, (int) (packed >>> 32));
        entityData.set(PROPS2, (int) packed);
        redrawParts = true;
    }
    @Override public byte getGolemColor() { return (byte) GolemCoreRules.color(entityData.get(PROPS3)); }
    public void setGolemColor(byte value) { entityData.set(PROPS3, GolemCoreRules.withColor(entityData.get(PROPS3), value)); }
    public byte getFlags() { return (byte) GolemCoreRules.flags(entityData.get(PROPS3)); }
    public void setFlags(byte value) { entityData.set(PROPS3, GolemCoreRules.withFlags(entityData.get(PROPS3), value)); }
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.MAX_HEALTH, 10)
                .add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.ATTACK_KNOCKBACK, 0).add(Attributes.FOLLOW_RANGE, 40).add(Attributes.STEP_HEIGHT, 0.6);
    }
    public void updateEntityAttributes() {
        IGolemProperties properties = getProperties();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(GolemCoreRules.health(properties.getMaterial().healthMod, properties.getRank(), properties.hasTrait(EnumGolemTrait.FRAGILE)));
        getAttribute(Attributes.STEP_HEIGHT).setBaseValue(properties.hasTrait(EnumGolemTrait.WHEELED) ? 0.5 : 0.6);
        restrictTo(getRestrictCenter().equals(BlockPos.ZERO) ? blockPosition() : getRestrictCenter(), properties.hasTrait(EnumGolemTrait.SCOUT) ? 48 : 32);
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(properties.hasTrait(EnumGolemTrait.SCOUT) ? 56 : 40);
        navigation.stop();
        navigation = getGolemNavigator();
        moveControl = properties.hasTrait(EnumGolemTrait.FLYER) ? new FlyingMoveControl() : new MoveControl(this);
        setNoGravity(properties.hasTrait(EnumGolemTrait.FLYER));
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(GolemCoreRules.damage(properties.getMaterial().damage, properties.getRank(),
                properties.hasTrait(EnumGolemTrait.FIGHTER), properties.hasTrait(EnumGolemTrait.BRUTAL)));
        createAI();
    }
    private void createAI() {
        goalSelector.removeAllGoals(goal -> true);
        targetSelector.removeAllGoals(goal -> true);
        if (isFollowingOwner()) goalSelector.addGoal(4, new AIFollowOwner(this, 1, 10, 2));
        else {
            goalSelector.addGoal(3, new AIGotoEntity(this));
            goalSelector.addGoal(4, new AIGotoBlock(this));
            goalSelector.addGoal(5, new AIGotoHome(this));
        }
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        if (getProperties().hasTrait(EnumGolemTrait.FIGHTER)) {
            if (navigation instanceof GroundPathNavigation) goalSelector.addGoal(0, new FloatGoal(this));
            if (getProperties().hasTrait(EnumGolemTrait.RANGED) && getProperties().getArms().function != null) {
                var ranged = getProperties().getArms().function.getRangedAttackAI(this);
                if (ranged != null) goalSelector.addGoal(1, ranged);
            }
            goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
            if (isFollowingOwner()) {
                targetSelector.addGoal(1, new AIOwnerHurtByTarget(this));
                targetSelector.addGoal(2, new AIOwnerHurtTarget(this));
            }
            targetSelector.addGoal(3, new HurtByTargetGoal(this));
        }
    }
    @Override public boolean onClimbable() { return isBesideClimbableBlock(); }
    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
        restrictTo(blockPosition(), 32);
        updateEntityAttributes();
        return data;
    }
    @Override public int getArmorValue() {
        return GolemCoreRules.armor(getProperties().getMaterial().armor, getProperties().hasTrait(EnumGolemTrait.ARMORED), getProperties().hasTrait(EnumGolemTrait.FRAGILE));
    }
    @Override public void tick() {
        super.tick();
        armSwingProgress = attackAnim;
        if (isRemoved()) return;
        IGolemProperties properties = getProperties();
        if (properties.hasTrait(EnumGolemTrait.FLYER)) setNoGravity(true);
        if (!level().isClientSide) {
            if (firstRun) {
                firstRun = false;
                if (hasRestriction() && !blockPosition().equals(getRestrictCenter())) goHome();
            }
            if (task != null && task.isSuspended()) task = null;
            if (getTarget() != null && (!getTarget().isAlive() || properties.hasTrait(EnumGolemTrait.RANGED) && distanceToSqr(getTarget()) > 1024
                    || getTarget() instanceof Player && level().getServer() != null && !level().getServer().isPvpAllowed())) setTarget(null);
            if (tickCount % (properties.hasTrait(EnumGolemTrait.REPAIR) ? 40 : 100) == 0) heal(1);
            if (properties.hasTrait(EnumGolemTrait.CLIMBER)) setBesideClimbableBlock(horizontalCollision);
        } else if (tickCount < 20 || tickCount % 20 == 0) redrawParts = true;
        if (properties.getHead().function != null) properties.getHead().function.onUpdateTick(this);
        if (properties.getArms().function != null) properties.getArms().function.onUpdateTick(this);
        if (properties.getLegs().function != null) properties.getLegs().function.onUpdateTick(this);
        if (properties.getAddon().function != null) properties.getAddon().function.onUpdateTick(this);
    }
    @Override public void handleEntityEvent(byte event) {
        if (event >= 5 && event <= 9 && CLIENT_STATUS != null) CLIENT_STATUS.accept(this, event);
        else super.handleEntityEvent(event);
    }
    public float getGolemMoveSpeed() {
        IGolemProperties properties = getProperties();
        return GolemCoreRules.moveSpeed(properties.getRank(), properties.hasTrait(EnumGolemTrait.LIGHT), properties.hasTrait(EnumGolemTrait.HEAVY),
                properties.hasTrait(EnumGolemTrait.FLYER), properties.hasTrait(EnumGolemTrait.WHEELED));
    }
    public PathNavigation getGolemNavigator() {
        return getProperties().hasTrait(EnumGolemTrait.FLYER) ? new PathNavigateGolemAir(this, level())
                : getProperties().hasTrait(EnumGolemTrait.CLIMBER) ? new WallClimberNavigation(this, level()) : new PathNavigateGolemGround(this, level());
    }
    @Override protected MovementEmission getMovementEmission() {
        return getProperties().hasTrait(EnumGolemTrait.HEAVY) && !getProperties().hasTrait(EnumGolemTrait.FLYER) ? MovementEmission.ALL : MovementEmission.NONE;
    }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !getProperties().hasTrait(EnumGolemTrait.FLYER) && !getProperties().hasTrait(EnumGolemTrait.CLIMBER) && super.causeFallDamage(distance, multiplier, source);
    }
    /** Original ceilingward home search, not an invented groundward teleport. */
    private void goHome() {
        BlockPos pos = getRestrictCenter();
        if (!level().hasChunkAt(pos)) return;
        boolean ceiling = false;
        while (pos.getY() < level().getMaxBuildHeight() - 1) {
            if (level().getBlockState(pos.above()).blocksMotion()) { ceiling = true; break; }
            pos = pos.above();
        }
        if (!ceiling) return;
        double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
        var moved = getBoundingBox().move(x - getX(), y - getY(), z - getZ());
        if (!level().noCollision(this, moved)) return;
        teleportTo(x, y, z);
        navigation.stop();
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        long packed = tag.getLong("props");
        if (!GolemProperties.isValid(packed)) { packed = 0; rankXp = 0; }
        else rankXp = Math.max(0, tag.getInt("rankXP"));
        setProperties(GolemProperties.fromLong(packed));
        restrictTo(BlockPos.of(tag.getLong("homepos")), 32);
        setFlags(tag.getByte("gflags"));
        setGolemColor(tag.getByte("color"));
        updateEntityAttributes();
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("props", getProperties().toLong());
        tag.putLong("homepos", getRestrictCenter().asLong());
        tag.putByte("gflags", getFlags());
        tag.putInt("rankXP", rankXp);
        tag.putByte("color", getGolemColor());
    }
    @Override protected void actuallyHurt(DamageSource source, float damage) {
        if (source.is(DamageTypeTags.IS_FIRE) && getProperties().hasTrait(EnumGolemTrait.FIREPROOF) || source.is(DamageTypes.CACTUS)) return;
        if (source.is(DamageTypeTags.IS_EXPLOSION) && getProperties().hasTrait(EnumGolemTrait.BLASTPROOF)) damage = GolemCoreRules.blastDamage(getMaxHealth(), damage);
        if (hasRestriction() && (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FELL_OUT_OF_WORLD))) goHome();
        super.actuallyHurt(source, damage);
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || player.getItemInHand(hand).is(Items.NAME_TAG)) return InteractionResult.PASS;
        if (level().isClientSide || !isOwner(player)) return super.mobInteract(player, hand);
        if (player.isShiftKeyDown()) {
            playSound(SoundsTC.zap, 1, 1);
            if (task != null) task.setReserved(false);
            dropCarried();
            ItemStack placer = new ItemStack(ItemsTC.golemPlacer);
            CompoundTag data = new CompoundTag();
            data.putLong("props", getProperties().toLong());
            data.putInt("xp", rankXp);
            placer.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            spawnAtLocation(placer, 0.5F);
            discard();
            player.swing(hand);
        } else if (player.getItemInHand(hand).is(ItemsTC.golemBell) && ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("GOLEMDIRECT")) {
            if (task != null) task.setReserved(false);
            playSound(SoundsTC.scan, 1, 1);
            setFollowingOwner(!isFollowingOwner());
            if (isFollowingOwner()) {
                player.displayClientMessage(Component.translatable("golem.follow", ""), true);
                if (ModConfig.CONFIG_GRAPHICS.showGolemEmotes) level().broadcastEntityEvent(this, (byte) 5);
                clearRestriction();
            } else {
                player.displayClientMessage(Component.translatable("golem.stay", ""), true);
                if (ModConfig.CONFIG_GRAPHICS.showGolemEmotes) level().broadcastEntityEvent(this, (byte) 8);
                restrictTo(blockPosition(), getProperties().hasTrait(EnumGolemTrait.SCOUT) ? 48 : 32);
            }
            updateEntityAttributes();
            player.swing(hand);
        } else {
            DyeColor dye = dye(player.getItemInHand(hand));
            if (dye != null) {
                playSound(SoundsTC.zap, 1, 1);
                setGolemColor((byte) (dye.getId() + 1));
                player.getItemInHand(hand).shrink(1); // Source consumes even in creative mode.
                player.swing(hand);
            }
        }
        return InteractionResult.SUCCESS;
    }
    private static DyeColor dye(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof DyeItem dye) return dye.getDyeColor();
        for (DyeColor color : DyeColor.values()) if (stack.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "dyes/" + color.getName())))) return color;
        return null;
    }
    @Override public void die(DamageSource source) {
        if (task != null) task.setReserved(false);
        super.die(source);
        if (!level().isClientSide) dropCarried();
    }
    protected void dropCarried() {
        // Clear BOTH native hands; Mob equipment death drops must never duplicate the carried stacks.
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack held = getItemBySlot(hand);
            if (held.isEmpty()) continue;
            setItemSlot(hand, ItemStack.EMPTY);
            spawnAtLocation(held, 0.25F);
        }
    }
    @Override protected void dropCustomDeathLoot(ServerLevel world, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(world, source, recentlyHit);
        int looting = source.getEntity() instanceof LivingEntity attacker
                ? EnchantmentHelper.getEnchantmentLevel(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), attacker) : 0;
        for (ItemStack component : getProperties().generateComponents()) {
            ItemStack stack = component.copy();
            if (random.nextFloat() < 0.3F + looting * 0.15F) {
                if (stack.getCount() > 0) stack.shrink(random.nextInt(stack.getCount()));
                spawnAtLocation(stack, 0.25F);
            }
        }
    }
    public boolean isBesideClimbableBlock() { return (entityData.get(CLIMBING) & 1) != 0; }
    public void setBesideClimbableBlock(boolean value) { entityData.set(CLIMBING, (byte) (value ? entityData.get(CLIMBING) | 1 : entityData.get(CLIMBING) & ~1)); }
    public boolean isFollowingOwner() { return (getFlags() & 2) != 0; }
    public void setFollowingOwner(boolean value) { setFlags((byte) (value ? getFlags() | 2 : getFlags() & ~2)); }
    @Override public void setTarget(LivingEntity target) { super.setTarget(target); setInCombat(getTarget() != null); }
    @Override public boolean isInCombat() { return (getFlags() & 8) != 0; }
    public void setInCombat(boolean value) { setFlags((byte) (value ? getFlags() | 8 : getFlags() & ~8)); }
    @Override public boolean doHurtTarget(Entity target) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource source = damageSources().mobAttack(this);
        if (level() instanceof ServerLevel server) damage = EnchantmentHelper.modifyDamage(server, getMainHandItem(), target, source, damage);
        boolean hit = target.hurt(source, damage);
        if (!hit) return false;
        if (target instanceof LivingEntity living && getProperties().hasTrait(EnumGolemTrait.DEFT)) living.lastHurtByPlayerTime = 100;
        float knockback = getKnockback(target, source);
        if (knockback > 0) {
            double yaw = getYRot() * Math.PI / 180;
            target.setDeltaMovement(target.getDeltaMovement().add(-Math.sin(yaw) * knockback * 0.5, 0.1, Math.cos(yaw) * knockback * 0.5));
            setDeltaMovement(getDeltaMovement().multiply(0.6, 1, 0.6));
        }
        if (level() instanceof ServerLevel server) EnchantmentHelper.doPostAttackEffects(server, target, source);
        if (getProperties().getArms().function != null) getProperties().getArms().function.onMeleeAttack(this, target);
        if (target instanceof Mob && !target.isAlive()) addRankXp(8);
        return true;
    }
    public Task getTask() {
        if (task == null && taskID != Integer.MAX_VALUE) { task = TaskHandler.getTask(level().dimension(), taskID); taskID = Integer.MAX_VALUE; }
        return task;
    }
    public void setTask(Task task) { this.task = task; }
    @Override public void addRankXp(int amount) {
        if (level().isClientSide || !getProperties().hasTrait(EnumGolemTrait.SMART)) return;
        int rank = getProperties().getRank();
        if (rank >= 10) return;
        rankXp += amount;
        int threshold = TaskRules.rankThreshold(rank);
        if (rankXp < threshold) return;
        rankXp -= threshold;
        IGolemProperties properties = getProperties();
        properties.setRank(rank + 1);
        setProperties(properties); // Source deliberately grants at most one rank per call.
        if (ModConfig.CONFIG_GRAPHICS.showGolemEmotes) {
            level().broadcastEntityEvent(this, (byte) 9);
            playSound(SoundEvents.PLAYER_LEVELUP, 0.25F, 1);
        }
    }
    private int carryingSlots() { return getProperties().hasTrait(EnumGolemTrait.HAULER) ? 2 : 1; }
    private static EquipmentSlot slot(int index) { return index == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND; }
    @Override public ItemStack holdItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        for (int index = 0; index < carryingSlots(); index++) {
            EquipmentSlot hand = slot(index);
            ItemStack held = getItemBySlot(hand);
            if (held.isEmpty()) {
                // Native stacks must not exceed their component-defined stack size.
                int count = Math.min(stack.getCount(), stack.getMaxStackSize());
                setItemSlot(hand, stack.split(count));
            } else if (ItemStack.isSameItemSameComponents(held, stack)) {
                int count = Math.min(stack.getCount(), held.getMaxStackSize() - held.getCount());
                if (count > 0) { held.grow(count); stack.shrink(count); }
            }
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        return stack;
    }
    @Override public ItemStack dropItem(ItemStack requested) {
        ItemStack output = ItemStack.EMPTY;
        for (int index = 0; index < carryingSlots(); index++) {
            EquipmentSlot hand = slot(index);
            ItemStack held = getItemBySlot(hand);
            if (held.isEmpty()) continue;
            if (requested == null || requested.isEmpty()) { output = held; setItemSlot(hand, ItemStack.EMPTY); }
            else if (ItemStack.isSameItemSameComponents(held, requested)) {
                output = held.split(Math.min(requested.getCount(), held.getCount()));
                if (held.isEmpty()) setItemSlot(hand, ItemStack.EMPTY);
            }
            if (!output.isEmpty()) break;
        }
        if (carryingSlots() == 2 && getMainHandItem().isEmpty() && !getOffhandItem().isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, getOffhandItem());
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        return output;
    }
    @Override public int canCarryAmount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        int capacity = 0;
        for (int index = 0; index < carryingSlots(); index++) {
            ItemStack held = getItemBySlot(slot(index));
            if (held.isEmpty()) capacity += stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(held, stack)) capacity += Math.max(0, held.getMaxStackSize() - held.getCount());
        }
        return capacity;
    }
    @Override public boolean canCarry(ItemStack stack, boolean partial) {
        int capacity = canCarryAmount(stack);
        return capacity > 0 && (partial || capacity >= stack.getCount());
    }
    @Override public boolean isCarrying(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (ItemStack held : getCarrying()) if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack)) return true;
        return false;
    }
    @Override public NonNullList<ItemStack> getCarrying() {
        NonNullList<ItemStack> carried = NonNullList.withSize(carryingSlots(), ItemStack.EMPTY);
        for (int index = 0; index < carried.size(); index++) carried.set(index, getItemBySlot(slot(index)));
        return carried;
    }
    @Override public LivingEntity getGolemEntity() { return this; }
    @Override public Level getGolemWorld() { return level(); }
    @Override public void swingArm() {
        if (!swinging || swingTime >= 3 || swingTime < 0) {
            swingTime = -1;
            swinging = true;
            swingingArm = InteractionHand.MAIN_HAND;
            if (level() instanceof ServerLevel server) server.getChunkSource().broadcast(this, new ClientboundAnimatePacket(this, 0));
        }
    }
    public float getArmSwingProgress(float partialTick) { return getAttackAnim(partialTick); }
    @Override public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (getProperties().getArms().function != null) getProperties().getArms().function.onRangedAttack(this, target, distanceFactor);
    }
    public void setSwingingArms(boolean ignored) { /* Actual BETA26 ranged interface method was empty. */ }

    private class FlyingMoveControl extends MoveControl {
        FlyingMoveControl() { super(EntityThaumcraftGolem.this); }
        @Override public void tick() {
            if (operation != Operation.MOVE_TO) return;
            double x = wantedX - getX(), y = wantedY - getY(), z = wantedZ - getZ();
            double distance = Math.sqrt(x * x + y * y + z * z);
            if (distance < getBoundingBox().getSize()) { operation = Operation.WAIT; setDeltaMovement(getDeltaMovement().scale(0.5)); return; }
            setDeltaMovement(getDeltaMovement().add(x / distance * 0.033 * speedModifier, y / distance * 0.0125 * speedModifier, z / distance * 0.033 * speedModifier));
            if (getTarget() == null) setYRot((float) (-Math.atan2(getDeltaMovement().x, getDeltaMovement().z) * 180 / Math.PI));
            else setYRot((float) (-Math.atan2(getTarget().getX() - getX(), getTarget().getZ() - getZ()) * 180 / Math.PI));
            yBodyRot = getYRot();
        }
    }
}
