package thaumcraft.client.lib;

import java.util.Objects;

/** Identity-owned presentation data. A delayed logout never clears a newer connection. */
public final class ClientConnectionSnapshot<T> {
    private Object connection;
    private long revision;
    private T value;

    public boolean begin(Object owner) {
        Objects.requireNonNull(owner, "connection");
        if (connection == owner) return false;
        connection = owner;
        value = null;
        revision++;
        return true;
    }

    public boolean apply(Object owner, T snapshot) {
        if (owner == null || connection != owner) return false;
        value = Objects.requireNonNull(snapshot, "snapshot");
        revision++;
        return true;
    }

    public boolean end(Object owner) {
        if (owner == null || connection != owner) return false;
        connection = null;
        value = null;
        revision++;
        return true;
    }

    public Snapshot<T> capture(Object owner) {
        return owner != null && connection == owner && value != null
            ? new Snapshot<>(revision, value) : null;
    }

    public record Snapshot<T>(long revision, T value) {}
}
