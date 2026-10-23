package thaumcraft.common.lib.crafting;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.InfusionRecipe;

/** Authoritative detached metadata for supported native recipes; no JEI or client dependencies.
 * Dynamic native serializers only encode their source defaults, so preserve add-on base fields
 * separately instead of widening a constrained server recipe when reconstructing the display.
 */
public final class JeiRecipeSnapshotCodec {
    public static final int MAX_BASE_JSON = 262144;
    private JeiRecipeSnapshotCodec() {}

    private record InfusionBase(String research, int instability, AspectList aspects, Ingredient central,
            List<Ingredient> components, String group) {
        private static final Codec<InfusionBase> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            InfusionRecipe.STRING.fieldOf("research").forGetter(InfusionBase::research),
            Codec.intRange(0, 512).fieldOf("instability").forGetter(InfusionBase::instability),
            CrucibleAspectCosts.CODEC.fieldOf("aspects").forGetter(InfusionBase::aspects),
            Ingredient.CODEC.fieldOf("central").forGetter(InfusionBase::central),
            Ingredient.CODEC_NONEMPTY.listOf(0, InfusionRecipe.MAX_COMPONENTS).fieldOf("components").forGetter(InfusionBase::components),
            InfusionRecipe.STRING.fieldOf("group").forGetter(InfusionBase::group)
        ).apply(instance, InfusionBase::new));
        private static InfusionBase capture(InfusionRecipe recipe) {
            return new InfusionBase(recipe.getResearch(), recipe.instability, recipe.getAspects(), recipe.getRecipeInput(),
                List.copyOf(recipe.getComponents()), recipe.getGroup());
        }
    }

    public static boolean isSupported(RecipeHolder<?> holder) {
        Class<?> kind = holder.value().getClass();
        return kind == CrucibleRecipe.class || kind == InfusionRecipe.class
            || kind == InfusionEnchantmentRecipe.class || kind == InfusionRunicAugmentRecipe.class;
    }

    public static void encode(RegistryFriendlyByteBuf buffer, RecipeHolder<?> holder) {
        if (!isSupported(holder)) throw new IllegalArgumentException("Unsupported JEI recipe implementation: " + holder.id());
        RecipeHolder<?> canonical = holder;
        InfusionBase base = null;
        if (holder.value() instanceof InfusionRecipe infusion) {
            base = InfusionBase.capture(infusion);
            if (infusion instanceof InfusionEnchantmentRecipe enchantment) {
                canonical = new RecipeHolder<>(holder.id(), new InfusionEnchantmentRecipe(enchantment.enchantment,
                    infusion.getAspects(), infusion.getComponents().toArray()));
            } else if (infusion instanceof InfusionRunicAugmentRecipe) {
                canonical = new RecipeHolder<>(holder.id(), new InfusionRunicAugmentRecipe());
            }
        }
        RecipeHolder.STREAM_CODEC.encode(buffer, canonical);
        buffer.writeBoolean(base != null);
        if (base != null) {
            var ops = buffer.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            String json = InfusionBase.CODEC.encodeStart(ops, base).getOrThrow().toString();
            buffer.writeUtf(json, MAX_BASE_JSON);
        }
    }

    public static RecipeHolder<?> decode(RegistryFriendlyByteBuf buffer) {
        RecipeHolder<?> holder = RecipeHolder.STREAM_CODEC.decode(buffer);
        if (!isSupported(holder)) throw new IllegalArgumentException("Unsupported JEI recipe implementation: " + holder.id());
        boolean hasBase = buffer.readBoolean();
        if (hasBase != (holder.value() instanceof InfusionRecipe)) throw new IllegalArgumentException("Mismatched infusion snapshot metadata");
        if (hasBase) {
            var ops = buffer.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            InfusionBase base = InfusionBase.CODEC.parse(ops, JsonParser.parseString(buffer.readUtf(MAX_BASE_JSON))).getOrThrow();
            ((InfusionRecipe) holder.value()).restoreJeiSnapshotBase(base.research(), base.instability(), base.aspects(),
                base.central(), base.components(), base.group());
        }
        return holder;
    }
}
