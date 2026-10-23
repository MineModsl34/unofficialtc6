package thaumcraft.common.lib.crafting;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.lib.research.ResearchItemResolver;

/** Finite explicit OreDictionary -> current item-tag bridge. No inferred arbitrary old names,
 * no cached membership, no pick-block/bucket fallback. Addons can supply an explicit item tag. */
public class DustTriggerOre extends DustTriggerSimple {
    private final TagKey<Item> tag;

    public DustTriggerOre(String research, String target, ItemStack result) {
        this(research, legacyTag(target), () -> result.copy());
        Objects.requireNonNull(result, "result");
    }
    public DustTriggerOre(String research, TagKey<Item> target, ItemStack result) {
        this(research, target, () -> result.copy());
        Objects.requireNonNull(result, "result");
    }
    protected DustTriggerOre(String research, TagKey<Item> tag, Supplier<ItemStack> result) {
        super(research, Blocks.AIR, result);
        this.tag = Objects.requireNonNull(tag, "tag");
        if (!tag.registry().equals(Registries.ITEM)) throw new IllegalArgumentException("Dust ore trigger requires an item tag");
    }
    public static TagKey<Item> legacyTag(String name) {
        TagKey<Item> tag = name == null ? null : ResearchItemResolver.oreTag(name);
        if (tag == null) throw new IllegalArgumentException("Unsupported legacy dust OreDictionary name: " + name);
        return tag;
    }
    @Override protected boolean matches(BlockState state) {
        Item item = state.getBlock().asItem();
        return item instanceof BlockItem && new ItemStack(item).is(tag);
    }
}
