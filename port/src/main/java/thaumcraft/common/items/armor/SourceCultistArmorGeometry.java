package thaumcraft.common.items.armor;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Finite, mechanically imported source constructor geometry. No reflection, expression eval,
 * arbitrary file names or client classes. Unattached source boxes are retained as metadata,
 * and mirror is captured at addBox time (late source mirror=true did not mirror old ModelBox).
 */
public record SourceCultistArmorGeometry(String source, String sha256, List<Part> parts) {
    public enum Kind { KNIGHT, ROBE, LEADER }
    public record Vector(float x, float y, float z) {}
    public record Box(Vector origin, Vector size) {}
    public record Part(String name, int u, int v, Box box, boolean mirror, Vector offset, Vector rotation, String parent, String variant) {
        public boolean included(boolean inner) { return parent != null && (variant.equals("both") || variant.equals(inner ? "inner" : "outer")); }
    }
    public SourceCultistArmorGeometry { parts = List.copyOf(parts); }
    public static SourceCultistArmorGeometry load(Kind kind) {
        String path = "/assets/thaumcraft/armor_geometry/" + kind.name().toLowerCase(java.util.Locale.ROOT) + ".json";
        return loadPath(path);
    }
    /** Finite additional source mesh; no arbitrary resource/path access. */
    public static SourceCultistArmorGeometry loadPath(String path) {
        if (!List.of("/assets/thaumcraft/armor_geometry/knight.json", "/assets/thaumcraft/armor_geometry/robe.json", "/assets/thaumcraft/armor_geometry/leader.json", "/assets/thaumcraft/armor_geometry/fortress.json").contains(path)) throw new IllegalArgumentException("Unknown source armor geometry");
        try (var input = SourceCultistArmorGeometry.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing source armor mesh " + path);
            var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("textureWidth").getAsInt() != 128 || root.get("textureHeight").getAsInt() != 64) throw new IllegalArgumentException("Bad source atlas dimensions");
            var raw = root.getAsJsonArray("parts"); if (raw.size() > 128) throw new IllegalArgumentException("Oversized source model");
            List<Part> parts = new ArrayList<>();
            for (var element : raw) {
                var part = element.getAsJsonObject(); var box = part.getAsJsonArray("box");
                if (box.size() != 6) throw new IllegalArgumentException("Invalid source cube");
                String parent = part.get("parent").isJsonNull() ? null : part.get("parent").getAsString();
                String variant = part.get("variant").isJsonNull() ? null : part.get("variant").getAsString();
                if (parent != null && (!List.of("bipedHead", "bipedHeadwear", "bipedBody", "bipedRightArm", "bipedLeftArm", "bipedRightLeg", "bipedLeftLeg").contains(parent)
                    || !List.of("inner", "outer", "both").contains(variant))) throw new IllegalArgumentException("Invalid source attachment");
                parts.add(new Part(part.get("name").getAsString(), part.get("u").getAsInt(), part.get("v").getAsInt(),
                    new Box(vector(box, 0), vector(box, 3)), part.get("mirror").getAsBoolean(), vector(part.getAsJsonArray("offset"), 0),
                    vector(part.getAsJsonArray("rotation"), 0), parent, variant));
            }
            return new SourceCultistArmorGeometry(root.get("source").getAsString(), root.get("sha256").getAsString(), parts);
        } catch (IOException failure) { throw new IllegalStateException("Reading source armor mesh", failure); }
    }
    private static Vector vector(JsonArray array, int offset) {
        float x = array.get(offset).getAsFloat(), y = array.get(offset + 1).getAsFloat(), z = array.get(offset + 2).getAsFloat();
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || Math.abs(x) > 256 || Math.abs(y) > 256 || Math.abs(z) > 256) throw new IllegalArgumentException("Invalid source mesh coordinate");
        return new Vector(x, y, z);
    }
}
