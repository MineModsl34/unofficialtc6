package thaumcraft.common.lib.enchantment;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.blocks.BlocksTC;

/** Source Utils.specialMiningResult/Chance plus native-tag ModConfig compatibility.
 * Raw-metal drops replace 1.12's self-dropping ore; no new chance/balance is introduced. */
public final class SpecialMiningResults {
    private record Key(Item item, int damage) {}
    private record Result(ItemStack stack, float chance) {}
    private static final Map<Key, Result> ADDONS = new LinkedHashMap<>();
    private SpecialMiningResults() {}
    public static void add(ItemStack input, ItemStack output, float chance) {
        if (input.isEmpty() || output.isEmpty() || !Float.isFinite(chance) || chance < 0) throw new IllegalArgumentException("Invalid special mining result");
        ADDONS.put(new Key(input.getItem(), input.getDamageValue()), new Result(output.copy(), chance));
    }
    public static ItemStack find(ItemStack input, float chance, RandomSource random) {
        float roll = random.nextFloat(); // Source consumes this even for an unmapped drop.
        Result result = ADDONS.get(new Key(input.getItem(), input.getDamageValue()));
        if (result == null) {
            Item output = null;
            if (input.is(Items.IRON_ORE) || input.is(Items.DEEPSLATE_IRON_ORE) || input.is(Items.RAW_IRON) || tagged(input, "ores/iron") || tagged(input, "raw_materials/iron")) output = ItemsTC.clusterIron;
            else if (input.is(Items.GOLD_ORE) || input.is(Items.DEEPSLATE_GOLD_ORE) || input.is(Items.RAW_GOLD) || tagged(input, "ores/gold") || tagged(input, "raw_materials/gold")) output = ItemsTC.clusterGold;
            else if (input.is(Items.COPPER_ORE) || input.is(Items.DEEPSLATE_COPPER_ORE) || input.is(Items.RAW_COPPER) || tagged(input, "ores/copper") || tagged(input, "raw_materials/copper")) output = ItemsTC.clusterCopper;
            else if (tagged(input, "ores/tin")) output = ItemsTC.clusterTin;
            else if (tagged(input, "ores/silver")) output = ItemsTC.clusterSilver;
            else if (tagged(input, "ores/lead")) output = ItemsTC.clusterLead;
            else if (BlocksTC.oreCinnabar != null && input.is(BlocksTC.oreCinnabar.asItem())) output = ItemsTC.clusterCinnabar;
            else if (input.is(Items.QUARTZ)) output = ItemsTC.clusterQuartz;
            if (output != null) result = new Result(new ItemStack(output), 1F);
        }
        if (result != null && roll <= chance * result.chance()) {
            ItemStack output = result.stack().copy(); output.setCount(output.getCount() * input.getCount()); return output;
        }
        return input.copy();
    }
    private static boolean tagged(ItemStack stack, String path) { return stack.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path))); }
}
