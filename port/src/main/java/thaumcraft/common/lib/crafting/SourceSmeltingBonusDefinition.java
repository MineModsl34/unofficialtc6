package thaumcraft.common.lib.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;

/** Native server-data definition, not a crafting recipe or an infernal furnace implementation. */
public record SourceSmeltingBonusDefinition(int sourceOrder,Input input,ItemStack output,float chance) {
    public record Input(Optional<String> legacyOre,Optional<ResourceLocation> tag,List<ResourceLocation> items) {
        private static final Codec<Input> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("legacy_ore").forGetter(Input::legacyOre),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(Input::tag),
            ResourceLocation.CODEC.listOf(0,32).optionalFieldOf("items",List.of()).forGetter(Input::items)
        ).apply(instance,Input::new));
        public static final Codec<Input> CODEC = RAW_CODEC.validate(input -> {
            boolean ore = input.legacyOre().isPresent() && input.tag().isPresent() && input.items().isEmpty();
            boolean item = input.legacyOre().isEmpty() && input.tag().isEmpty() && !input.items().isEmpty();
            if (!ore && !item) return DataResult.error(() -> "Bonus input must be one bounded explicit item family OR an OreDictionary/tag binding");
            if (ore) {
                String legacy = input.legacyOre().orElseThrow();
                if (legacy.length() > 256 || !input.tag().orElseThrow().equals(SourceSmeltingBonusCatalog.tagForOre(legacy)))
                    return DataResult.error(() -> "Unreviewed legacy ore tag binding");
            }
            if (input.items().stream().distinct().count() != input.items().size()) return DataResult.error(() -> "Duplicate bonus input alternative");
            if (input.items().stream().anyMatch(id -> !BuiltInRegistries.ITEM.containsKey(id)))
                return DataResult.error(() -> "Unknown bonus input item (missing item_exists pre-decode gate)");
            return DataResult.success(input);
        });
        public Input { legacyOre = java.util.Objects.requireNonNull(legacyOre); tag = java.util.Objects.requireNonNull(tag); items = List.copyOf(items); }
    }
    private static final Codec<Float> CHANCE = Codec.FLOAT.validate(value -> Float.isFinite(value) && value >= 0 && value <= 1
        ? DataResult.success(value) : DataResult.error(() -> "Native smelting chance must be finite and in [0,1]"));
    public static final Codec<SourceSmeltingBonusDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(0,4095).fieldOf("source_order").forGetter(SourceSmeltingBonusDefinition::sourceOrder),
        Input.CODEC.fieldOf("input").forGetter(SourceSmeltingBonusDefinition::input),
        ItemStack.STRICT_CODEC.fieldOf("output").forGetter(SourceSmeltingBonusDefinition::output),
        CHANCE.optionalFieldOf("chance",0.33F).forGetter(SourceSmeltingBonusDefinition::chance)
    ).apply(instance,SourceSmeltingBonusDefinition::new));
    public SourceSmeltingBonusDefinition { output = output.copy(); }
    @Override public ItemStack output() { return output.copy(); }
    public ThaumcraftApi.SmeltBonus toSourceBonus() {
        Object sourceInput;
        if (input.legacyOre().isPresent()) sourceInput = input.legacyOre().orElseThrow();
        else {
            List<ItemStack> stacks = input.items().stream().map(id -> {
                if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Missing guarded bonus input item " + id);
                return new ItemStack(BuiltInRegistries.ITEM.get(id));
            }).toList();
            sourceInput = stacks.size() == 1 ? stacks.get(0) : new SourceSmeltingBonusCatalog.Alternatives(stacks);
        }
        return new ThaumcraftApi.SmeltBonus(sourceInput,output(),chance);
    }
}
