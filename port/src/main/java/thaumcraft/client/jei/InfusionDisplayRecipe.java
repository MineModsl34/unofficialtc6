package thaumcraft.client.jei;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;

/** Client display projection only; never registered as an executable recipe or sent to a server. */
public final class InfusionDisplayRecipe extends InfusionRecipe {
    public enum Kind { ENCHANTMENT, RUNIC, TAG }

    private final ResourceLocation sourceId;
    private final InfusionRecipe source;
    private final Kind kind;
    private final int level;
    private final List<ItemStack> inputs;
    private final List<ItemStack> outputs;
    private final AspectList displayCosts;

    InfusionDisplayRecipe(ResourceLocation sourceId, InfusionRecipe source, Kind kind, int level,
            List<ItemStack> inputs, List<ItemStack> outputs, List<Ingredient> components, AspectList costs, int instability) {
        // Executable codec bounds limit base recipe amounts to 32767. A legitimate dynamic
        // runic upgrade can cost more; keep its exact display cost outside that base codec.
        super(source.getResearch(), outputs.getFirst(), instability, source.getAspects(), source.getRecipeInput(), components, source.getGroup());
        if (inputs.isEmpty() || inputs.size() != outputs.size()) throw new IllegalArgumentException("Unpaired infusion display");
        this.sourceId = sourceId;
        this.source = source;
        this.kind = kind;
        this.level = level;
        this.inputs = inputs.stream().map(ItemStack::copy).toList();
        this.outputs = outputs.stream().map(ItemStack::copy).toList();
        this.displayCosts = costs.copy();
    }

    public ResourceLocation sourceId() { return sourceId; }
    public InfusionRecipe source() { return source; }
    public Kind kind() { return kind; }
    /** Resulting enchantment/runic level; zero for a fixed tag modification. */
    public int level() { return level; }
    public ItemStack firstInput() { return inputs.getFirst().copy(); }
    public List<ItemStack> inputs() { return inputs.stream().map(ItemStack::copy).toList(); }
    public List<ItemStack> outputs() { return outputs.stream().map(ItemStack::copy).toList(); }
    @Override public AspectList getAspects() { return displayCosts.copy(); }
}
