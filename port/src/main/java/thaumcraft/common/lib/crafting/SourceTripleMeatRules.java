package thaumcraft.common.lib.crafting;

import java.util.HashSet;
import java.util.Set;

/** Original sugar multiplicity and raw damage distinctness; no six-variant range invention. */
public final class SourceTripleMeatRules {
    private boolean sugar;
    private final Set<Integer> meats = new HashSet<>();
    public boolean addSugar() { if (sugar) return false; sugar = true; return true; }
    public boolean addMeat(int sourceMetadata) { return meats.size() < 3 && meats.add(sourceMetadata); }
    public boolean complete() { return sugar && meats.size() == 3; }

    /** Actual modern flattening of source ItemChunksEdible variants/meta order0..5.
     * Exact TC ids only: a tag, another namespace or a chunk_* prefix is not source identity.
     */
    public static Integer canonicalChunkMetadata(String namespace, String path) {
        if (!"thaumcraft".equals(namespace) || path == null) return null;
        return switch (path) {
            case "chunk_beef" -> 0;
            case "chunk_chicken" -> 1;
            case "chunk_pork" -> 2;
            case "chunk_fish" -> 3;
            case "chunk_rabbit" -> 4;
            case "chunk_mutton" -> 5;
            default -> null;
        };
    }
}
