package thaumcraft.common.items.consumables;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.common.lib.warp.WarpGameplay;

/** Source edible cure dependency, including its OWN warp award independent of hunger cure. */
public final class ItemZombieBrain extends Item {
    public ItemZombieBrain() {
        // TC6's constructor assigned an 80% Hunger effect, but its onFoodEaten override never
        // calls ItemFood.onFoodEaten. That configured effect never actually runs; do not "fix" it.
        super(new Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.2F).build()));
    }
    @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player player) {
            if (level.getRandom().nextFloat() < 0.1F) WarpGameplay.addWarpToPlayer(player, 1, EnumWarpType.NORMAL);
            else WarpGameplay.addWarpToPlayer(player, 1 + level.getRandom().nextInt(3), EnumWarpType.TEMPORARY);
        }
        return result;
    }
}
