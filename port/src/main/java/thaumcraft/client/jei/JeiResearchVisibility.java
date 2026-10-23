package thaumcraft.client.jei;

import java.util.function.Predicate;

/** Read-only visibility uses each actual station's source research semantics. */
public final class JeiResearchVisibility {
    public enum Rule { STRICT, KNOWN, KNOWN_LITERAL }
    private JeiResearchVisibility() {}
    public static boolean visible(boolean hideUnknown, String research, Rule rule,
        Predicate<String> strict, Predicate<String> known, Predicate<String> knownLiteral) {
        if (!hideUnknown || research == null || research.isEmpty()) return true;
        if (strict == null || known == null || knownLiteral == null) return false;
        return switch (rule) {
            case STRICT -> strict.test(research);
            case KNOWN -> known.test(research);
            case KNOWN_LITERAL -> knownLiteral.test(research);
        };
    }
}
