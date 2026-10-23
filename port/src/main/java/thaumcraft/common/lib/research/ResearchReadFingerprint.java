package thaumcraft.common.lib.research;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Canonical owner snapshot token for read acknowledgements, NOT authentication.
 * POPUP is excluded: both ends clear it after sending/applying its toast snapshot.
 * No attachment deserialization or ambient AUTOUNLOCK grant takes place here. */
public final class ResearchReadFingerprint {
    private ResearchReadFingerprint() {}

    public static String of(CompoundTag snapshot) {
        var research = new ArrayList<String>();
        var entries = snapshot.getList("research", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            String flags = Arrays.stream(entry.getString("flags").split(","))
                .filter(flag -> !flag.isEmpty() && !flag.equals("POPUP"))
                .distinct().sorted().reduce((a, b) -> a + "," + b).orElse("");
            research.add(field(entry.getString("key")) + entry.getInt("stage") + ":" + field(flags));
        }
        research.sort(String::compareTo);
        var pools = new ArrayList<String>();
        var knowledge = snapshot.getList("knowledge", Tag.TAG_COMPOUND);
        for (int i = 0; i < knowledge.size(); i++) {
            var entry = knowledge.getCompound(i);
            pools.add(field(entry.getString("key")) + entry.getInt("amount"));
        }
        pools.sort(String::compareTo);
        var canonical = new StringBuilder("research:").append(research.size()).append(':');
        for (String row : research) canonical.append(field(row));
        canonical.append("knowledge:").append(pools.size()).append(':');
        for (String row : pools) canonical.append(field(row));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 unavailable", impossible);
        }
    }

    private static String field(String value) { return value.length() + ":" + value; }
}
