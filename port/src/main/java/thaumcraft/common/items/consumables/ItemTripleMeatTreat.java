package thaumcraft.common.items.consumables;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

public final class ItemTripleMeatTreat extends Item {
    public ItemTripleMeatTreat(){super(new Properties().food(new FoodProperties.Builder().nutrition(6).saturationModifier(.8F).alwaysEdible().effect(()->new MobEffectInstance(MobEffects.REGENERATION,100,0),.66F).build()));}
}
