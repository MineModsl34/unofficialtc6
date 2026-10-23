package thaumcraft.common.lib.crafting;

import java.util.function.Consumer;

/** One in-flight transfer receipt, bound by identity to a connection and menu lifetime.
 * Unknown/stale acknowledgements cannot complete a newer request. Completion clears state
 * before invoking the callback, so timeout/logout/duplicate acknowledgements complete once.
 */
public final class ArcaneTransferReceiptSession {
    private record Pending(Object connection, Object menu, int requestId, int menuId, int stateId,
                           long expiresAt, Consumer<Boolean> complete) {}
    private Pending pending;
    public boolean busy() { return pending != null; }
    public boolean begin(Object connection, Object menu, int requestId, int menuId, int stateId, long expiresAt, Consumer<Boolean> complete) {
        if (pending != null) return false;
        if (connection == null || menu == null || requestId < 1 || menuId < 0 || menuId > 255 || stateId < 0 || stateId > 32767 || complete == null)
            throw new IllegalArgumentException("Transfer receipt identity");
        pending = new Pending(connection, menu, requestId, menuId, stateId, expiresAt, complete); return true;
    }
    public boolean accept(Object connection, Object currentMenu, int requestId, int menuId, int stateId, boolean accepted) {
        Pending current = pending;
        if (current == null || connection != current.connection || requestId != current.requestId || menuId != current.menuId || stateId != current.stateId) return false;
        finish(accepted && currentMenu == current.menu); return true;
    }
    public void tick(Object currentConnection, Object currentMenu, long now) {
        if (pending != null && (now >= pending.expiresAt || currentConnection != pending.connection || currentMenu != pending.menu)) finish(false);
    }
    public void cancel(int requestId) { if (pending != null && pending.requestId == requestId) finish(false); }
    public void end() { finish(false); }
    private void finish(boolean accepted) {
        Pending current = pending; pending = null;
        if (current != null) current.complete.accept(accepted);
    }
}
