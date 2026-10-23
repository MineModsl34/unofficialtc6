package thaumcraft.common.entities.monster;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.*;
import thaumcraft.api.items.ItemsTC;

/** Real source zombie subclass: sunlight/baby/equipment AI remains native zombie behavior. */
public class EntityBrainyZombie extends Zombie {
    public EntityBrainyZombie(EntityType<? extends EntityBrainyZombie> type,Level level){super(type,level);}
    public static AttributeSupplier.Builder attributes(){return Zombie.createAttributes().add(Attributes.MAX_HEALTH,25).add(Attributes.ATTACK_DAMAGE,5).add(Attributes.SPAWN_REINFORCEMENTS_CHANCE,0);}
    @Override protected void registerGoals(){thaumcraft.common.entities.SourceZombieGoals.install(this,goalSelector,targetSelector);targetSelector.addGoal(1,new HurtByTargetGoal(this));}
    @Override public int getArmorValue(){return super.getArmorValue()+1;}
    @Override protected ResourceKey<LootTable> getDefaultLootTable(){return ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,net.minecraft.resources.ResourceLocation.parse("minecraft:entities/zombie"));}
    @Override protected void dropFromLootTable(DamageSource source,boolean recent){if(random.nextInt(10)-looting(source)<=4)spawnAtLocation(new ItemStack(ItemsTC.brain),1.5F);super.dropFromLootTable(source,recent);}
    static int looting(DamageSource source){return source.getEntity() instanceof LivingEntity attacker?EnchantmentHelper.getEnchantmentLevel(attacker.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING),attacker):0;}
    @Override protected boolean convertsInWater(){return false;}
}
