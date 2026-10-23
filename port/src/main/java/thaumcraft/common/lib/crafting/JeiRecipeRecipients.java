package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/** Login's explicit player can precede insertion into the native online-player list. */
public final class JeiRecipeRecipients {
    private JeiRecipeRecipients() {}
    public static <T> List<T> select(boolean changed, List<T> online, List<T> requested) {
        if (!changed) return requested == null ? List.of() : List.copyOf(requested);
        var seen = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
        List<T> recipients = new ArrayList<>();
        for (T player : online) if (seen.add(player)) recipients.add(player);
        if (requested != null) for (T player : requested) if (seen.add(player)) recipients.add(player);
        return List.copyOf(recipients);
    }
}
