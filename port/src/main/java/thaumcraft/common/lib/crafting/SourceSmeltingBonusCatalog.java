package thaumcraft.common.lib.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.internal.CommonInternals;
import thaumcraft.common.lib.research.LegacyScanIdentity;
import org.slf4j.Logger;

/** Publishes only engine-accepted resources on the server owner thread. A failed /reload never
 * stages partial public API changes. No furnace block, RNG, heat/XP or free output is implemented.
 */
public final class SourceSmeltingBonusCatalog {
    public static final String DIRECTORY = "smelting_bonus";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final OwnedSourceRuleList<ResourceLocation,ThaumcraftApi.SmeltBonus> OWNER = new OwnedSourceRuleList<>();
    private static MinecraftServer server;
    private static final Map<String,String> SOURCE_ORES = Map.ofEntries(
        Map.entry("oreGold","gold"),Map.entry("oreIron","iron"),Map.entry("oreCinnabar","cinnabar"),
        Map.entry("oreCopper","copper"),Map.entry("oreTin","tin"),Map.entry("oreSilver","silver"),
        Map.entry("oreLead","lead"),Map.entry("oreQuartz","quartz"),Map.entry("oreDiamond","diamond"),
        Map.entry("oreRedstone","redstone"),Map.entry("oreLapis","lapis"),Map.entry("oreEmerald","emerald"));
    private SourceSmeltingBonusCatalog() {}
    /** Explicit flattened source wildcard family: 4 fish/8 clusters, matched once per source rule. */
    public record Alternatives(List<ItemStack> stacks) {
        public Alternatives {
            if (stacks.isEmpty() || stacks.size() > 32 || stacks.stream().anyMatch(ItemStack::isEmpty)) throw new IllegalArgumentException("Invalid smelting input family");
            stacks = stacks.stream().map(ItemStack::copy).toList();
        }
        @Override public List<ItemStack> stacks() { return stacks.stream().map(ItemStack::copy).toList(); }
    }
    /** Root calls after accepted tag binding/native recipes at startup and /reload, null on stop. */
    public static void setServer(MinecraftServer current) {
        if (current != null && !current.isSameThread()) throw new IllegalStateException("Smelting source catalog requires server owner thread");
        if (current == null || current != server) OWNER.clear(CommonInternals.smeltingBonus);
        server = current;
        if (current == null) return;
        // Read and validate the entire bounded table before replacing any identity-owned entries.
        Map<ResourceLocation,SourceSmeltingBonusDefinition> definitions;
        try { definitions = load(current); }
        catch (RuntimeException failure) {
            LOGGER.error("Could not refresh bounded smelting bonus data; preserving prior identity-owned API entries",failure);
            return;
        }
        Map<ResourceLocation,ThaumcraftApi.SmeltBonus> next = new LinkedHashMap<>();
        definitions.entrySet().stream().sorted(java.util.Comparator
            .<Map.Entry<ResourceLocation,SourceSmeltingBonusDefinition>>comparingInt(entry -> entry.getValue().sourceOrder())
            .thenComparing(entry -> entry.getKey().toString())).forEach(entry -> next.put(entry.getKey(),entry.getValue().toSourceBonus()));
        OWNER.rebuild(CommonInternals.smeltingBonus,next);
    }
    private static Map<ResourceLocation,SourceSmeltingBonusDefinition> load(MinecraftServer current) {
        var ops = new ConditionalOps<JsonElement>(current.registryAccess().createSerializationContext(JsonOps.INSTANCE),new AcceptedTagContext(current));
        var codec = ConditionalOps.createConditionalCodec(SourceSmeltingBonusDefinition.CODEC);
        FileToIdConverter converter = FileToIdConverter.json(DIRECTORY);
        var resources = converter.listMatchingResources(current.getResourceManager());
        if (resources.size() > 256) throw new IllegalArgumentException("Too many native smelting bonus data files");
        Map<ResourceLocation,SourceSmeltingBonusDefinition> result = new LinkedHashMap<>();
        for (var entry : resources.entrySet()) {
            ResourceLocation id = converter.fileToId(entry.getKey());
            try (var input = entry.getValue().open()) {
                byte[] bytes = input.readNBytes(16385);
                if (bytes.length > 16384) throw new IllegalArgumentException("Smelting bonus data file too large " + id);
                JsonElement json = JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8));
                if (!json.isJsonObject() || (json.getAsJsonObject().has("neoforge:conditions")
                    && json.getAsJsonObject().getAsJsonArray("neoforge:conditions").size() > 64))
                    throw new IllegalArgumentException("Unbounded smelting bonus condition payload " + id);
                // Native NeoForge condition codec checks item/tag gates BEFORE output ItemStack decoding.
                codec.parse(ops,json).getOrThrow().ifPresent(value -> result.put(id,value));
            } catch (IOException | RuntimeException failure) {
                // Like native RecipeManager: malformed overrides are logged/skipped, never a null/AIR
                // substitute or an exception that crashes the already-accepted resource owner hook.
                LOGGER.error("Ignoring invalid smelting bonus data {}",id,failure);
            }
        }
        return result;
    }
    private record AcceptedTagContext(MinecraftServer current) implements ICondition.IContext {
        @Override public <T> Map<ResourceLocation,Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> key) {
            Registry<T> registry = current.registryAccess().registryOrThrow(key);
            Map<ResourceLocation,Collection<Holder<T>>> result = new LinkedHashMap<>();
            registry.getTags().forEach(pair -> result.put(pair.getFirst().location(),pair.getSecond().stream().toList()));
            return Collections.unmodifiableMap(result);
        }
    }
    /** Matching alone: source ignores all input NBT and compares old shared item/meta or ore membership.
     * The future source furnace consumer must independently do <=chance, once per matching rule.
     */
    public static boolean matches(ItemStack actual,ThaumcraftApi.SmeltBonus bonus) {
        if (actual == null || actual.isEmpty() || bonus == null) return false;
        if (bonus.in instanceof ItemStack required) return sourceItemMatches(actual,required);
        if (bonus.in instanceof Alternatives family) return family.stacks.stream().anyMatch(required -> sourceItemMatches(actual,required));
        if (bonus.in instanceof String legacy) {
            ResourceLocation tag = tagForOre(legacy);
            return tag != null && actual.is(TagKey.create(Registries.ITEM,tag));
        }
        return false;
    }
    private static boolean sourceItemMatches(ItemStack actual,ItemStack required) {
        if (required.isEmpty()) return false;
        var actualId = LegacyScanIdentity.identify(actual); var expectedId = LegacyScanIdentity.identify(required);
        return actualId.itemId().equals(expectedId.itemId()) && (LegacyScanIdentity.rawDamage(required) == 32767 || actualId.metadata() == expectedId.metadata());
    }
    /** Only the 12 actual source ore names, plus an explicit modern namespaced addon tag string.
     * Arbitrary legacy ingot/dust/custom categories remain registerable but require the modern tag
     * bridge for matching: no guessed registry inference or pretend full OreDictionary emulation.
     */
    public static ResourceLocation tagForOre(String legacy) {
        if (legacy == null || legacy.length() > 256) return null;
        if (legacy.contains(":")) return ResourceLocation.tryParse(legacy); // Explicit modern addon tag bridge.
        String path = SOURCE_ORES.get(legacy);
        return path == null ? null : ResourceLocation.fromNamespaceAndPath("c","ores/"+path);
    }
}
