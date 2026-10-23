package thaumcraft.common.lib.research.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.research.ResearchEntry.EnumResearchMeta;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Addendum;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ArrayField;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Diagnostic;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Entry;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.IconToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemKind;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ItemToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.KnowledgeToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.LegacyResourceId;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.MetaToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.ResearchToken;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.Stage;

/**
 * Bounded, registry-free TC6 catalog parser. Unlike the original partial append-and-log loader,
 * malformed candidates fail as a whole before touching ResearchCategories or player data.
 * Legacy token interpretation is retained, but no missing item becomes AIR or a lost requirement.
 */
public final class ResearchCatalogParser {
    public static final Limits DEFAULT_LIMITS = new Limits(64, 4_000_000, 2_000_000, 32,
        4096, 4096, 32_768, 250_000);
    private static final Set<String> ENTRY_FIELDS = Set.of("key", "name", "category", "icons", "parents",
        "siblings", "meta", "location", "reward_item", "reward_knowledge", "stages", "addenda");
    private static final Set<String> STAGE_FIELDS = Set.of("text", "recipes", "required_item", "required_craft",
        "required_knowledge", "required_research", "warp");
    private static final Set<String> ADDENDUM_FIELDS = Set.of("text", "recipes", "required_research");

    private final Limits limits;
    private final Set<String> categories;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private String source;
    private int nodes;

    private ResearchCatalogParser(Set<String> categories, Limits limits) {
        this.categories = Set.copyOf(categories);
        this.limits = limits;
    }

    /** Same bounded legacy reward/requirement grammar, without consulting any item registry. */
    public static ItemToken parseItemToken(String raw) {
        if (raw == null || raw.length() > DEFAULT_LIMITS.maxStringChars())
            throw new IllegalArgumentException("Item token exceeds source parser bounds");
        var parser = new ResearchCatalogParser(Set.of(), DEFAULT_LIMITS);
        parser.source = "item-token";
        return parser.item(raw, "$", true, true);
    }

    /** Input map iteration order is the source/entry order. No API registry is read or mutated. */
    public static ResearchCatalogSpec parse(Map<String, String> sources, Set<String> categories) {
        return parse(sources, categories, DEFAULT_LIMITS);
    }

    public static ResearchCatalogSpec parse(Map<String, String> sources, Set<String> categories, Limits limits) {
        return new ResearchCatalogParser(categories, limits).candidate(sources);
    }

    public record Limits(int maxSources, int maxTotalChars, int maxSourceChars, int maxDepth,
                         int maxEntries, int maxArrayLength, int maxStringChars, int maxNodes) {
        public Limits {
            if (maxSources < 1 || maxTotalChars < 1 || maxSourceChars < 1 || maxDepth < 1
                || maxDepth > 128 || maxEntries < 1 || maxArrayLength < 1 || maxStringChars < 1 || maxNodes < 1) {
                throw new IllegalArgumentException("All bounds must be positive; depth must not exceed128");
            }
        }
    }

    public static final class CatalogException extends IllegalArgumentException {
        private final String source;
        private final String path;
        private CatalogException(String source, String path, String message) {
            super(source + " " + path + ": " + message);
            this.source = source;
            this.path = path;
        }
        public String source() { return source; }
        public String path() { return path; }
    }

    private record Coordinate(String category, int column, int row) {}

    private ResearchCatalogSpec candidate(Map<String, String> input) {
        if (input.size() > limits.maxSources()) throw fail("$", "Too many source files");
        Map<String, String> rawSources = new LinkedHashMap<>();
        List<Entry> entries = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<Coordinate> coordinates = new HashSet<>();
        long totalChars = 0;
        for (var file : input.entrySet()) {
            source = file.getKey();
            if (source == null || source.isEmpty()) throw fail("$", "Missing source name");
            if (source.length() > limits.maxStringChars()) throw fail("$", "Source name too long");
            String text = file.getValue();
            if (text == null || text.length() > limits.maxSourceChars()
                || (totalChars += text.length()) > limits.maxTotalChars()) throw fail("$", "Source text exceeds bounds");
            JsonObject root = object(readJson(text), "$");
            unknown(root, Set.of("entries"), "$");
            JsonArray array = requiredArray(root, "entries", "$");
            for (int i = 0; i < array.size(); i++) {
                String path = "$.entries[" + i + "]";
                Entry entry = entry(object(array.get(i), path), path);
                if (entries.size() >= limits.maxEntries()) throw fail(path, "Too many research entries");
                if (!keys.add(entry.key())) throw fail(path + ".key", "Duplicate opaque research key " + entry.key());
                if (!coordinates.add(new Coordinate(entry.category(), entry.displayColumn(), entry.displayRow()))) {
                    throw fail(path + ".location", "Duplicate display coordinate in " + entry.category());
                }
                entries.add(entry);
            }
            rawSources.put(source, text);
        }
        return new ResearchCatalogSpec(rawSources, entries, diagnostics);
    }

    private Entry entry(JsonObject obj, String path) {
        String key = opaque(requiredString(obj, "key", path), path + ".key");
        String category = opaque(requiredString(obj, "category", path), path + ".category");
        if (!categories.contains(category)) throw fail(path + ".category", "Unknown opaque category " + category);
        ArrayField<Integer> location = field(obj, "location", path, this::integer);
        if (location.present() && location.values().size() != 2) throw fail(path + ".location", "Expected exactly two integer coordinates");
        ArrayField<Stage> stages = field(obj, "stages", path, (value, at) -> stage(object(value, at), at));
        if (!stages.present()) throw fail(path + ".stages", "Required stages array is absent");
        return new Entry(source, path, key, requiredString(obj, "name", path), category,
            field(obj, "icons", path, (value, at) -> icon(string(value, at), at)),
            strings(obj, "parents", path), strings(obj, "siblings", path),
            field(obj, "meta", path, (value, at) -> meta(string(value, at), at)), location,
            items(obj, "reward_item", path, false), knowledge(obj, "reward_knowledge", path), stages,
            field(obj, "addenda", path, (value, at) -> addendum(object(value, at), at)), unknown(obj, ENTRY_FIELDS, path));
    }

    private Stage stage(JsonObject obj, String path) {
        return new Stage(requiredString(obj, "text", path), recipes(obj, path),
            items(obj, "required_item", path, true), items(obj, "required_craft", path, true),
            knowledge(obj, "required_knowledge", path),
            field(obj, "required_research", path, (value, at) -> research(string(value, at), at)),
            obj.has("warp"), obj.has("warp") ? integer(obj.get("warp"), path + ".warp") : 0,
            unknown(obj, STAGE_FIELDS, path));
    }

    private Addendum addendum(JsonObject obj, String path) {
        return new Addendum(requiredString(obj, "text", path), recipes(obj, path),
            strings(obj, "required_research", path), unknown(obj, ADDENDUM_FIELDS, path));
    }

    private ArrayField<String> strings(JsonObject obj, String name, String path) {
        return field(obj, name, path, this::string);
    }

    private ArrayField<LegacyResourceId> recipes(JsonObject obj, String path) {
        return field(obj, "recipes", path, (value, at) -> resource(string(value, at), at));
    }

    private ArrayField<KnowledgeToken> knowledge(JsonObject obj, String name, String path) {
        return field(obj, name, path, (value, at) -> knowledge(string(value, at), at));
    }

    private ArrayField<ItemToken> items(JsonObject obj, String name, String path, boolean ores) {
        return field(obj, name, path, (value, at) -> item(string(value, at), at, true, ores));
    }

    private <T> ArrayField<T> field(JsonObject obj, String name, String path, BiFunction<JsonElement, String, T> decoder) {
        if (!obj.has(name)) return ArrayField.absent();
        JsonArray array = requiredArray(obj, name, path);
        List<T> values = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) values.add(decoder.apply(array.get(i), path + "." + name + "[" + i + "]"));
        return new ArrayField<>(true, values);
    }

    private IconToken icon(String raw, String path) {
        ItemToken candidate = item(raw, path, false, false);
        return new IconToken(raw, candidate, raw.startsWith("focus") ? raw : null,
            raw.startsWith("focus") ? null : resource(raw, path));
    }

    private MetaToken meta(String raw, String path) {
        try {
            // Original enum helper uses the current default locale. Raw token is retained.
            return new MetaToken(raw, EnumResearchMeta.valueOf(raw.toUpperCase()));
        } catch (IllegalArgumentException invalid) {
            throw fail(path, "Invalid research metadata enum " + raw);
        }
    }

    private KnowledgeToken knowledge(String raw, String path) {
        String[] split = raw.split(";");
        if (split.length != 2 && split.length != 3) throw fail(path, "Invalid knowledge token shape");
        EnumKnowledgeType type;
        try { type = EnumKnowledgeType.valueOf(split[0].toUpperCase()); }
        catch (IllegalArgumentException invalid) { throw fail(path, "Invalid knowledge enum " + split[0]); }
        int amount = tokenInteger(split[split.length - 1], path);
        if (amount <= 0) throw fail(path, "Knowledge amount must be positive");
        String category = split.length == 3 ? split[1].toUpperCase() : null;
        if ((category == null && type.hasFields()) || (category != null && !categories.contains(category))) {
            throw fail(path, "Missing or unknown knowledge category");
        }
        return new KnowledgeToken(raw, type, category, amount);
    }

    private ResearchToken research(String raw, String path) {
        String[] split = raw.split(";");
        if (split.length == 0) throw fail(path, "Research/icon token lacks an opaque key");
        return new ResearchToken(raw, opaque(split[0], path), split.length > 1 ? split[1] : null);
    }

    private ItemToken item(String raw, String path, boolean replaceQuotes, boolean allowOre) {
        String effective = replaceQuotes ? raw.replace("'", "\"") : raw;
        if (allowOre && effective.startsWith("oredict:")) {
            String[] split = effective.split(":");
            String name = split.length > 1 ? split[1] : "";
            List<String> ignored = new ArrayList<>();
            for (int i = 2; i < split.length; i++) ignored.add(split[i]);
            return new ItemToken(raw, effective, ItemKind.OREDICT, name, 1, 0, null, null, ignored, null,
                name.isEmpty() ? "Empty original ore token; unresolved, not discarded" : "Ore dictionary/tag resolution deferred");
        }
        String[] split = effective.split(";");
        String name = split.length == 0 ? "" : split[0];
        int count = -1;
        int damage = -1;
        String rawSnbt = null;
        String snbt = null;
        List<String> ignored = new ArrayList<>();
        int rawOffset = name.length() + 1;
        for (int i = 1; i < split.length; i++) {
            String segment = split[i];
            if (segment.startsWith("{")) {
                snbt = segment;
                rawSnbt = raw.substring(Math.min(rawOffset, raw.length()));
                for (int j = i + 1; j < split.length; j++) ignored.add(split[j]);
                break;
            }
            if (legacyIntegerShape(segment)) {
                int number = tokenInteger(segment, path);
                if (number >= 0 && count < 0) count = number;
                else if (number >= 0 && damage < 0) damage = number;
                else ignored.add(segment);
            } else ignored.add(segment);
            rawOffset += segment.length() + 1;
        }
        count = count < 0 ? 1 : count;
        damage = damage < 0 ? 0 : damage;
        LegacyResourceId id = resource(name, path);
        String issue = !id.modernSyntax() ? "Invalid modern item identifier; unresolved, not AIR or discarded"
            : count == 0 ? "Original zero-count stack would be empty; token retained unresolved"
            : "Item registry/metadata/SNBT resolution deferred";
        if (!id.modernSyntax()) diagnostics.add(new Diagnostic(source, path, "UNRESOLVED_ITEM_ID", issue + ": " + name));
        return new ItemToken(raw, effective, ItemKind.ITEM, name, count, damage, rawSnbt, snbt, ignored, id, issue);
    }

    private LegacyResourceId resource(String raw, String path) {
        String namespace = "minecraft";
        String value = raw;
        int colon = raw.indexOf(':');
        if (colon >= 0) {
            value = raw.substring(colon + 1);
            if (colon > 1) namespace = raw.substring(0, colon);
        }
        namespace = namespace.toLowerCase(Locale.ROOT);
        value = value.toLowerCase(Locale.ROOT);
        boolean modern = namespace.matches("[a-z0-9_.-]+") && value.matches("[a-z0-9/._-]+");
        return new LegacyResourceId(raw, namespace, value, modern);
    }

    private Map<String, String> unknown(JsonObject obj, Set<String> known, String path) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var field : obj.entrySet()) {
            if (known.contains(field.getKey())) continue;
            result.put(field.getKey(), field.getValue().toString());
            diagnostics.add(new Diagnostic(source, path + "." + field.getKey(), "UNKNOWN_FIELD",
                "Ignored by the original loader; retained but not activated"));
        }
        return result;
    }

    private String opaque(String value, String path) {
        if (value.isEmpty() || value.chars().allMatch(Character::isWhitespace)
            || value.chars().anyMatch(Character::isISOControl)) throw fail(path, "Invalid empty/control opaque key");
        return value;
    }

    private String requiredString(JsonObject obj, String name, String path) {
        if (!obj.has(name)) throw fail(path + "." + name, "Required string is absent");
        return string(obj.get(name), path + "." + name);
    }

    private String string(JsonElement value, String path) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw fail(path, "Expected JSON string");
        return value.getAsString();
    }

    private int integer(JsonElement value, String path) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw fail(path, "Expected JSON integer");
        try { return value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException invalid) { throw fail(path, "Integer overflow or fractional value"); }
    }

    private int tokenInteger(String value, String path) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) { throw fail(path, "Invalid/overflowing integer token " + value); }
    }

    private static boolean legacyIntegerShape(String value) {
        // Integer.parseInt uses Character.digit(char, 10), not an ASCII-only regex. Retain the
        // original acceptance of BMP decimal digits while rejecting overflow as a whole candidate.
        int start = !value.isEmpty() && (value.charAt(0) == '+' || value.charAt(0) == '-') ? 1 : 0;
        if (start == value.length()) return false;
        for (int i = start; i < value.length(); i++) if (Character.digit(value.charAt(i), 10) < 0) return false;
        return true;
    }

    private JsonArray requiredArray(JsonObject obj, String name, String path) {
        if (!obj.has(name) || !obj.get(name).isJsonArray()) throw fail(path + "." + name, "Expected JSON array");
        return obj.getAsJsonArray(name);
    }

    private JsonObject object(JsonElement value, String path) {
        if (!value.isJsonObject()) throw fail(path, "Expected JSON object");
        return value.getAsJsonObject();
    }

    private JsonElement readJson(String text) {
        checkJsonLexical(text);
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement result = readValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) throw fail("$", "Trailing JSON content");
            return result;
        } catch (IOException | NumberFormatException malformed) {
            throw fail("$", "Malformed strict JSON: " + malformed.getMessage());
        }
    }

    /**
     * Gson2.10.1 still accepts case-insensitive keywords, raw string controls and some non-JSON
     * escapes when lenient=false. This iterative bounded preflight checks RFC JSON lexical tokens;
     * the existing reader then checks structure, duplicate members, depth, nodes and field shapes.
     */
    private void checkJsonLexical(String text) {
        for (int i = 0; i < text.length();) {
            char value = text.charAt(i);
            if (jsonWhitespace(value) || value == '{' || value == '}' || value == '[' || value == ']'
                || value == ':' || value == ',') {
                i++;
            } else if (value == '"') {
                i = jsonStringEnd(text, i);
            } else if (value == 't' || value == 'f' || value == 'n') {
                String word = value == 't' ? "true" : value == 'f' ? "false" : "null";
                if (!text.startsWith(word, i)) throw fail("$@" + i, "Invalid JSON literal");
                i += word.length();
                if (i < text.length() && !jsonDelimiter(text.charAt(i))) throw fail("$@" + i, "Invalid JSON literal suffix");
            } else if (value == '-' || asciiDigit(value)) {
                i = jsonNumberEnd(text, i);
            } else {
                throw fail("$@" + i, "Invalid strict JSON token");
            }
        }
    }

    private int jsonStringEnd(String text, int start) {
        int i = start + 1;
        while (i < text.length()) {
            char value = text.charAt(i++);
            if (value == '"') return i;
            if (value < 0x20) throw fail("$@" + (i - 1), "Unescaped JSON string control character");
            if (value != '\\') continue;
            if (i >= text.length()) throw fail("$@" + i, "Incomplete JSON string escape");
            char escape = text.charAt(i++);
            if (escape == '"' || escape == '\\' || escape == '/' || escape == 'b' || escape == 'f'
                || escape == 'n' || escape == 'r' || escape == 't') continue;
            if (escape != 'u') throw fail("$@" + (i - 1), "Invalid JSON string escape");
            for (int hex = 0; hex < 4; hex++) {
                if (i >= text.length() || !asciiHex(text.charAt(i))) throw fail("$@" + i, "Invalid JSON Unicode escape");
                i++;
            }
        }
        throw fail("$@" + start, "Unterminated JSON string");
    }

    private int jsonNumberEnd(String text, int start) {
        int i = start;
        if (text.charAt(i) == '-') i++;
        if (i >= text.length()) throw fail("$@" + start, "Incomplete JSON number");
        char first = text.charAt(i);
        if (first == '0') i++;
        else if (first >= '1' && first <= '9') while (i < text.length() && asciiDigit(text.charAt(i))) i++;
        else throw fail("$@" + i, "Invalid JSON number");
        if (i < text.length() && text.charAt(i) == '.') {
            i++;
            int fractionStart = i;
            while (i < text.length() && asciiDigit(text.charAt(i))) i++;
            if (i == fractionStart) throw fail("$@" + i, "Incomplete JSON number fraction");
        }
        if (i < text.length() && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
            i++;
            if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) i++;
            int exponentStart = i;
            while (i < text.length() && asciiDigit(text.charAt(i))) i++;
            if (i == exponentStart) throw fail("$@" + i, "Incomplete JSON number exponent");
        }
        if (i - start > 64) throw fail("$@" + start, "Numeric literal bound exceeded");
        if (i < text.length() && !jsonDelimiter(text.charAt(i))) throw fail("$@" + i, "Invalid JSON number suffix");
        return i;
    }

    private static boolean asciiDigit(char value) { return value >= '0' && value <= '9'; }
    private static boolean asciiHex(char value) {
        return asciiDigit(value) || (value >= 'a' && value <= 'f') || (value >= 'A' && value <= 'F');
    }
    private static boolean jsonWhitespace(char value) { return value == ' ' || value == '\t' || value == '\r' || value == '\n'; }
    private static boolean jsonDelimiter(char value) {
        return jsonWhitespace(value) || value == '{' || value == '}' || value == '[' || value == ']'
            || value == ',' || value == ':';
    }

    private JsonElement readValue(JsonReader reader, int depth) throws IOException {
        String path = reader.getPath();
        if (depth > limits.maxDepth() || ++nodes > limits.maxNodes()) throw fail(path, "JSON depth/node bound exceeded");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                JsonObject object = new JsonObject();
                while (reader.hasNext()) {
                    if (object.size() >= limits.maxArrayLength()) throw fail(path, "Object member bound exceeded");
                    String name = bounded(reader.nextName(), path);
                    if (object.has(name)) throw fail(reader.getPath(), "Duplicate JSON member " + name);
                    object.add(name, readValue(reader, depth + 1));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                JsonArray array = new JsonArray();
                while (reader.hasNext()) {
                    if (array.size() >= limits.maxArrayLength()) throw fail(path, "Array length bound exceeded");
                    array.add(readValue(reader, depth + 1));
                }
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(bounded(reader.nextString(), path));
            case NUMBER -> {
                String number = reader.nextString();
                if (number.length() > 64) throw fail(path, "Numeric literal bound exceeded");
                yield new JsonPrimitive(new BigDecimal(number));
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> { reader.nextNull(); yield JsonNull.INSTANCE; }
            default -> throw fail(path, "Expected JSON value");
        };
    }

    private String bounded(String value, String path) {
        if (value.length() > limits.maxStringChars()) throw fail(path, "String length bound exceeded");
        return value;
    }

    private CatalogException fail(String path, String message) { return new CatalogException(source, path, message); }
}
