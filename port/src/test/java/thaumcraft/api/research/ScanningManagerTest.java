package thaumcraft.api.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

class ScanningManagerTest {
    private static IScanThing handler(String name, String key, boolean matches, List<String> calls) {
        return new IScanThing() {
            @Override public boolean checkThing(Player player, Object object) { calls.add(name + ":check"); return matches; }
            @Override public String getResearchKey(Player player, Object object) { calls.add(name + ":key"); return key; }
            @Override public void onSuccess(Player player, Object object) { calls.add(name + ":success"); }
        };
    }

    @Test
    void dispatchesAllMatchesInRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        var result = ScanningManager.scanHandlers(null, new Object(), List.of(
            handler("a", "A", true, calls), handler("skip", "NO", false, calls), handler("b", "B", true, calls)),
            key -> { calls.add("progress:" + key); return true; });
        assertTrue(result.found()); assertFalse(result.suppress());
        assertEquals(List.of("a:check", "a:key", "a:key", "a:key", "progress:A", "a:key", "a:key", "a:success",
            "skip:check", "b:check", "b:key", "b:key", "b:key", "progress:B", "b:key", "b:key", "b:success"), calls);
    }

    @Test
    void nullAndEmptyKeysBypassProgressAndSuppressStatus() {
        List<String> calls = new ArrayList<>();
        var result = ScanningManager.scanHandlers(null, null,
            List.of(handler("null", null, true, calls), handler("empty", "", true, calls)),
            key -> { fail("null/empty key must not enter progress"); return false; });
        assertTrue(result.found()); assertTrue(result.suppress());
        assertEquals(List.of("null:check", "null:key", "null:key", "null:success", "empty:check", "empty:key",
            "empty:key", "empty:key", "empty:key", "empty:success"), calls);
    }

    @Test
    void duplicateKeyStillChecksAndCallsProgressButAwardsOnce() {
        List<String> calls = new ArrayList<>();
        var known = new HashSet<String>();
        var handlers = List.of(handler("first", "SAME", true, calls), handler("second", "SAME", true, calls));
        assertTrue(ScanningManager.scanHandlers(null, null, handlers, known::add).found());
        assertTrue(calls.contains("first:success")); assertFalse(calls.contains("second:success"));
        calls.clear();
        assertFalse(ScanningManager.scanHandlers(null, null, handlers, known::add).found());
        assertEquals(List.of("first:check", "first:key", "first:key", "first:key", "second:check", "second:key", "second:key", "second:key"), calls);
    }

    @Test
    void doesNotMemoizeStatefulResearchKey() {
        AtomicInteger reads = new AtomicInteger();
        var handler = new IScanThing() {
            @Override public boolean checkThing(Player player, Object object) { return true; }
            @Override public String getResearchKey(Player player, Object object) { return reads.incrementAndGet() == 4 ? null : "KEY"; }
        };
        var result = ScanningManager.scanHandlers(null, null, List.of(handler), "KEY"::equals);
        assertTrue(result.found()); assertTrue(result.suppress()); assertEquals(4, reads.get());
    }

    @Test
    void scanExceptionsAreNotSwallowedAndEarlierSuccessRemainsObservable() {
        List<String> calls = new ArrayList<>();
        RuntimeException nativeFailure = new IllegalArgumentException("addon check");
        var failing = new IScanThing() {
            @Override public boolean checkThing(Player player, Object object) { throw nativeFailure; }
            @Override public String getResearchKey(Player player, Object object) { return "FAIL"; }
        };
        assertSame(nativeFailure, assertThrows(IllegalArgumentException.class, () -> ScanningManager.scanHandlers(null, null,
            List.of(handler("ok", "OK", true, calls), failing), key -> true)));
        assertTrue(calls.contains("ok:success"));
    }

    @Test
    void stillScannableCatchesQueryExceptionAndContinues() {
        List<String> calls = new ArrayList<>();
        assertTrue(ScanningManager.stillScannable(null, null,
            List.of(handler("broken", "BROKEN", true, calls), handler("new", "NEW", true, calls)),
            key -> { if (key.equals("BROKEN")) throw new IllegalStateException("query"); return false; }));
        assertEquals(List.of("broken:check", "broken:key", "new:check", "new:key"), calls);
    }

    @Test
    void stillScannableCatchesKeyButNotCheckException() {
        var brokenKey = new IScanThing() {
            @Override public boolean checkThing(Player player, Object object) { return true; }
            @Override public String getResearchKey(Player player, Object object) { throw new IllegalStateException("key"); }
        };
        assertFalse(ScanningManager.stillScannable(null, null, List.of(brokenKey), key -> false));
        var brokenCheck = new IScanThing() {
            @Override public boolean checkThing(Player player, Object object) { throw new IllegalArgumentException("check"); }
            @Override public String getResearchKey(Player player, Object object) { return "K"; }
        };
        assertThrows(IllegalArgumentException.class, () -> ScanningManager.stillScannable(null, null, List.of(brokenCheck), key -> false));
    }

    @Test
    void entityClassMatchingRetainsExactVersusInheritedSemantics() {
        var exact = new ScanEntity("EXACT", ArrayList.class, false);
        var inherited = new ScanEntity("INHERITED", List.class, true);
        assertTrue(exact.checkThing(null, new ArrayList<>()));
        assertFalse(exact.checkThing(null, List.of()));
        assertTrue(inherited.checkThing(null, new ArrayList<>()));
        assertTrue(inherited.checkThing(null, List.of()));
        assertFalse(inherited.checkThing(null, null));
        assertEquals("EXACT", exact.getResearchKey(null, null));
    }
}
