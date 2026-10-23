package thaumcraft.common.entities.monster.cult;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootTable;
import thaumcraft.common.entities.ai.combat.AICultistHurtByTarget;
import thaumcraft.common.entities.ai.combat.SourceMeleeAttackGoal;
import thaumcraft.common.entities.ai.misc.AIRestrictCultistDoor;
import thaumcraft.common.entities.monster.EntityEldritchGuardian;

/** Shared original cultist equipment, allegiance, AI, home NBT, loot and spawn particles. */
public abstract class EntityCultist extends Monster {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse("thaumcraft:entities/cultist"));
    protected EntityCultist(EntityType<? extends EntityCultist> type, Level level) {
        super(type, level); xpReward = 10;
        ((GroundPathNavigation)getNavigation()).setCanOpenDoors(true);
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) setDropChance(slot, .05F);
    }
    protected static AttributeSupplier.Builder baseAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.FOLLOW_RANGE, 32).add(Attributes.MOVEMENT_SPEED, .3).add(Attributes.ATTACK_DAMAGE, 4);
    }
    protected final void registerCultistGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new SourceMeleeAttackGoal(this, 1, false));
        goalSelector.addGoal(4, new AIRestrictCultistDoor(this));
        goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, .8));
        goalSelector.addGoal(7, new RandomStrollGoal(this, .8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new AICultistHurtByTarget(this, true));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, EntityEldritchGuardian.class, true));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractIllager.class, true));
    }
    @Override public boolean canPickUpLoot() { return false; }
    @Override protected ResourceKey<LootTable> getDefaultLootTable() { return LOOT; }
    @Override public boolean isAlliedTo(Entity entity) {
        return entity instanceof EntityCultist || EntityType.getKey(entity.getType()).equals(ResourceLocation.parse("thaumcraft:cultist_leader"));
    }
    @Override public boolean canAttackType(EntityType<?> type) {
        return type != thaumcraft.common.entities.CultEntities.CLERIC && type != thaumcraft.common.entities.CultEntities.KNIGHT
            && !EntityType.getKey(type).equals(ResourceLocation.parse("thaumcraft:cultist_leader")) && super.canAttackType(type);
    }
    /** Genuine dependency must be registered. A missing crimson item is never dressed up as iron. */
    protected static ItemStack equipment(Item item) {
        if (item == null) throw new IllegalStateException("Missing source crimson cultist equipment");
        return new ItemStack(item);
    }
    protected void setLoot(DifficultyInstance difficulty) {}
    protected void enchantLoot(DifficultyInstance difficulty) {}
    @Nullable @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData group) {
        setLoot(difficulty); enchantLoot(difficulty);
        return super.finalizeSpawn(level, difficulty, reason, group);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (hasRestriction() && getRestrictRadius() > 0) {
            tag.putInt("HomeD", (int)getRestrictRadius()); tag.putInt("HomeX", getRestrictCenter().getX());
            tag.putInt("HomeY", getRestrictCenter().getY()); tag.putInt("HomeZ", getRestrictCenter().getZ());
        }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeD")) restrictTo(new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ")), tag.getInt("HomeD"));
    }
    public void spawnExplosionParticle() { if (!level().isClientSide) level().broadcastEntityEvent(this, (byte)20); else spawnParticles(); }
    private void spawnParticles() {
        for (int index = 0; index < 20; index++) {
            double x = random.nextGaussian() * .05, y = random.nextGaussian() * .05, z = random.nextGaussian() * .05;
            thaumcraft.common.entities.CultistPresentationHooks.spawn(level(), getX() + random.nextFloat() * getBbWidth() * 2 - getBbWidth() + x * 2,
                getY() + random.nextFloat() * getBbHeight() + y * 2, getZ() + random.nextFloat() * getBbWidth() * 2 - getBbWidth() + z * 2, x, y, z);
        }
    }
    @Override public void handleEntityEvent(byte event) { if (event == 20) spawnParticles(); else super.handleEntityEvent(event); }
}
