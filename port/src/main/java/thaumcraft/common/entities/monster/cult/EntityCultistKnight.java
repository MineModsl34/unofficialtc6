package thaumcraft.common.entities.monster.cult;

import java.util.Optional;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.ItemsTC;

public final class EntityCultistKnight extends EntityCultist {
    public EntityCultistKnight(EntityType<? extends EntityCultistKnight> type, Level level) { super(type, level); }
    public static AttributeSupplier.Builder createAttributes() { return baseAttributes().add(Attributes.MAX_HEALTH, 30); }
    @Override protected void registerGoals() { registerCultistGoals(); }
    @Override protected void setLoot(DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.HEAD, equipment(ItemsTC.crimsonPlateHelm)); setItemSlot(EquipmentSlot.CHEST, equipment(ItemsTC.crimsonPlateChest));
        setItemSlot(EquipmentSlot.LEGS, equipment(ItemsTC.crimsonPlateLegs)); setItemSlot(EquipmentSlot.FEET, equipment(ItemsTC.crimsonBoots));
        if (random.nextFloat() < (level().getDifficulty() == Difficulty.HARD ? .05F : .01F)) {
            if (random.nextInt(5) == 0) { setItemSlot(EquipmentSlot.MAINHAND, equipment(ItemsTC.voidSword)); setItemSlot(EquipmentSlot.HEAD, equipment(ItemsTC.crimsonRobeHelm)); }
            else { setItemSlot(EquipmentSlot.MAINHAND, equipment(ItemsTC.thaumiumSword)); if (random.nextBoolean()) setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY); }
        } else setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
    }
    @Override protected void enchantLoot(DifficultyInstance difficulty) {
        float strength = difficulty.getSpecialMultiplier();
        if (!getMainHandItem().isEmpty() && random.nextFloat() < .25F * strength)
            setItemSlot(EquipmentSlot.MAINHAND, EnchantmentHelper.enchantItem(random, getMainHandItem(), (int)(5 + strength * random.nextInt(18)), registryAccess(), Optional.empty()));
    }
}
