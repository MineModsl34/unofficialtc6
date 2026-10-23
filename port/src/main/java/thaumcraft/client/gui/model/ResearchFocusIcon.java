package thaumcraft.client.gui.model;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Finite source ConfigItems.initFocus registry metadata; no FocusEngine/gameplay invention. */
public record ResearchFocusIcon(ResourceLocation icon, ResourceLocation backing, int color) {
    private static final Map<String, ResearchFocusIcon> BUILTINS = Map.ofEntries(
        entry("ROOT", "root", 10066329, null), entry("TOUCH", "touch", 11371909, "medium"),
        entry("BOLT", "bolt", 11377029, "medium"), entry("PROJECTILE", "projectile", 11382149, "medium"),
        entry("CLOUD", "cloud", 10071429, "medium"), entry("MINE", "mine", 8760709, "medium"),
        entry("PLAN", "plan", 8760728, "medium"), entry("SPELLBAT", "spellbat", 8760748, "medium"),
        entry("FIRE", "fire", 16734721, "effect"), entry("FROST", "frost", 14811135, "effect"),
        entry("AIR", "air", 16777086, "effect"), entry("EARTH", "earth", 5685248, "effect"),
        entry("FLUX", "flux", 8388736, "effect"), entry("BREAK", "break", 9063176, "effect"),
        entry("RIFT", "rift", 3084645, "effect"), entry("EXCHANGE", "exchange", 5735255, "effect"),
        entry("CURSE", "curse", 6946821, "effect"), entry("HEAL", "heal", 14548997, "effect"),
        entry("SCATTER", "scatter", 10066329, null), entry("SPLITTARGET", "split_target", 10066329, null),
        entry("SPLITTRAJECTORY", "split_trajectory", 10066329, null));

    public static ResearchFocusIcon parse(String raw) {
        return raw != null && raw.startsWith("focus:") ? BUILTINS.get(raw.replace("focus:", "").trim()) : null;
    }
    private static Map.Entry<String, ResearchFocusIcon> entry(String key, String icon, int color, String backing) {
        return Map.entry("thaumcraft." + key, new ResearchFocusIcon(location(icon), backing == null ? null : location("_" + backing), color));
    }
    private static ResourceLocation location(String path) { return ResourceLocation.parse("thaumcraft:textures/foci/" + path + ".png"); }
}
