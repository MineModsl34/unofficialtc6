package thaumcraft.common.entities.construct;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.scores.PlayerTeam;
import thaumcraft.common.lib.SoundsTC;

/** Original placed-only, player-owned construct. /summon is not a valid source spawn. */
public abstract class EntityOwnedConstruct extends PathfinderMob implements OwnableEntity {
    private static final EntityDataAccessor<Byte> TAMED = SynchedEntityData.defineId(EntityOwnedConstruct.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(EntityOwnedConstruct.class, EntityDataSerializers.OPTIONAL_UUID);
    protected boolean validSpawn;
    protected EntityOwnedConstruct(EntityType<? extends EntityOwnedConstruct> type, Level level) { super(type, level); setPersistenceRequired(); setDropChance(EquipmentSlot.MAINHAND, 0); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(TAMED, (byte)0); builder.define(OWNER, Optional.empty()); }
    public boolean isOwned() { return (entityData.get(TAMED) & 4) != 0; }
    public void setOwned(boolean value) { byte flags=entityData.get(TAMED); entityData.set(TAMED,(byte)(value ? flags | 4 : flags & -5)); }
    @Override public UUID getOwnerUUID() { return entityData.get(OWNER).orElse(null); }
    public UUID getOwnerId() { return getOwnerUUID(); }
    public void setOwnerId(UUID owner) { entityData.set(OWNER, Optional.ofNullable(owner)); }
    public LivingEntity getOwnerEntity() { return getOwner(); }
    public boolean isOwner(LivingEntity entity) { return entity == getOwnerEntity(); }
    public void setValidSpawn() { validSpawn=true; }
    @Override protected int decreaseAirSupply(int air) { return air; }
    @Override protected SoundEvent getAmbientSound() { return SoundsTC.clack; }
    @Override protected SoundEvent getHurtSound(DamageSource damage) { return SoundsTC.clack; }
    @Override protected SoundEvent getDeathSound() { return SoundsTC.tool; }
    @Override public int getAmbientSoundInterval() { return 240; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected ResourceKey<LootTable> getDefaultLootTable() { return BuiltInLootTables.EMPTY; }
    @Override public void tick() { super.tick(); if (getTarget()!=null && isAlliedTo(getTarget())) setTarget(null); if (!level().isClientSide && !validSpawn) discard(); }
    @Override public PlayerTeam getTeam() { var owner=getOwnerEntity(); return isOwned() && owner!=null ? owner.getTeam() : super.getTeam(); }
    @Override public boolean isAlliedTo(Entity entity) { var owner=getOwnerEntity(); return isOwned() && owner!=null ? entity==owner || owner.isAlliedTo(entity) : super.isAlliedTo(entity); }
    @Override public void die(DamageSource source) { if (!level().isClientSide && level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES) && hasCustomName() && getOwnerEntity() instanceof ServerPlayer player) player.sendSystemMessage(getCombatTracker().getDeathMessage()); super.die(source); }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive()) return InteractionResult.PASS;
        if (!player.isShiftKeyDown() && !player.getMainHandItem().is(Items.NAME_TAG) && !level().isClientSide && !isOwner(player)) { player.displayClientMessage(Component.translatable("tc.notowned").withStyle(ChatFormatting.DARK_PURPLE,ChatFormatting.ITALIC),true); return InteractionResult.SUCCESS; }
        return super.mobInteract(player,hand);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putBoolean("v",validSpawn); tag.putString("OwnerUUID",getOwnerUUID()==null ? "" : getOwnerUUID().toString()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); validSpawn=tag.getBoolean("v"); try { UUID owner=tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : tag.getString("OwnerUUID").isEmpty() ? null : UUID.fromString(tag.getString("OwnerUUID")); if(owner==null && !tag.contains("OwnerUUID") && level().getServer()!=null)owner=net.minecraft.server.players.OldUsersConverter.convertMobOwnerIfNecessary(level().getServer(),tag.getString("Owner"));setOwnerId(owner); setOwned(owner!=null); } catch (IllegalArgumentException invalid) { setOwnerId(null); setOwned(false); } }
}
