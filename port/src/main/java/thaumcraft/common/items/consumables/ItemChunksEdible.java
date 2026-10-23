package thaumcraft.common.items.consumables;

import java.util.List;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

/** Actual flattened source chunk food: 1 hunger, .3 modifier, ten-tick eating. */
public final class ItemChunksEdible extends Item {
    public static final List<String> VARIANTS=List.of("beef","chicken","pork","fish","rabbit","mutton");
    public ItemChunksEdible(){super(new Properties().food(new FoodProperties(1,.6F,false,.5F,java.util.Optional.empty(),List.of())));}
}
