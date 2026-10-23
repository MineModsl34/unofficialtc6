package thaumcraft.client.lib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumcraft.common.lib.network.misc.PacketSyncObjectTags;

/** Received aspect data only, including memory connections. Never reads server/API maps. */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientAspectSnapshot {
    private static final ClientConnectionSnapshot<Data> OWNER = new ClientConnectionSnapshot<>();

    private ClientAspectSnapshot() {}

    @SubscribeEvent
    public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        requireMain();
        var listener = Minecraft.getInstance().getConnection();
        if (listener != null && listener.getConnection() == event.getConnection()) OWNER.begin(event.getConnection());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        requireMain();
        OWNER.end(event.getConnection());
    }

    public static boolean accept(PacketSyncObjectTags packet, Connection connection) {
        requireMain();
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || listener.getConnection() != connection) return false;
        // The first sync may precede LoggingIn; a later login of the same identity preserves it.
        OWNER.begin(connection);
        List<Entry> entries = new ArrayList<>();
        Map<Integer, List<RawEntry>> lookup = new LinkedHashMap<>();
        for (var received : packet.entries()) {
            if (received.stack().isEmpty()) continue;
            var stack = received.stack().copyWithCount(1);
            Map<String, Integer> raw = amounts(received.aspects());
            var registered = new RawEntry(stack, raw);
            lookup.computeIfAbsent(ItemStack.hashItemAndComponents(stack), ignored -> new ArrayList<>()).add(registered);
            // This applies item-local bonuses to received raw tags, with the actual source cap.
            // It never generates recipes or consults CommonInternals/integrated-server state.
            var costs = ThaumcraftCraftingManager.applyObjectTagBonuses(stack, received.aspects());
            Map<String, Integer> resolved = amounts(costs);
            if (!resolved.isEmpty()) entries.add(new Entry(stack, resolved));
        }
        lookup.replaceAll((hash, bucket) -> List.copyOf(bucket));
        return OWNER.apply(connection, new Data(List.copyOf(entries), Collections.unmodifiableMap(lookup)));
    }

    public static Snapshot view() {
        requireMain();
        var listener = Minecraft.getInstance().getConnection();
        var snapshot = OWNER.capture(listener == null ? null : listener.getConnection());
        return snapshot == null ? null : new Snapshot(snapshot.revision(), snapshot.value());
    }

    public static final class Snapshot {
        private final long revision;
        private final Data data;

        private Snapshot(long revision, Data data) { this.revision = revision; this.data = data; }
        public long revision() { return revision; }
        public List<Entry> entries() { return data.entries(); }

        /** Same native fallback as getObjectTags, resolved exclusively in the received table. */
        public Entry resolve(ItemStack requested) {
            if (requested.isEmpty()) return null;
            var stack = requested.copyWithCount(1);
            Map<String, Integer> raw = find(stack);
            if (raw == null) {
                var undamaged = stack.copy();
                if (undamaged.isDamageableItem()) undamaged.set(DataComponents.DAMAGE, 0);
                raw = find(undamaged);
            }
            if (raw == null) raw = find(new ItemStack(stack.getItem()));
            AspectList registered = null;
            if (raw != null) {
                registered = new AspectList();
                for (var amount : raw.entrySet()) {
                    var aspect = Aspect.getAspect(amount.getKey());
                    if (aspect != null) registered.add(aspect, amount.getValue());
                }
            }
            var resolved = amounts(ThaumcraftCraftingManager.applyObjectTagBonuses(stack, registered));
            return resolved.isEmpty() ? null : new Entry(stack, resolved);
        }

        private Map<String, Integer> find(ItemStack stack) {
            var bucket = data.lookup().get(ItemStack.hashItemAndComponents(stack));
            if (bucket != null) for (var entry : bucket)
                if (ItemStack.isSameItemSameComponents(entry.stack, stack)) return entry.aspects;
            return null;
        }
    }

    private record Data(List<Entry> entries, Map<Integer, List<RawEntry>> lookup) {}

    private static final class RawEntry {
        private final ItemStack stack;
        private final Map<String, Integer> aspects;
        private RawEntry(ItemStack stack, Map<String, Integer> aspects) {
            this.stack = stack.copy();
            this.aspects = Collections.unmodifiableMap(new LinkedHashMap<>(aspects));
        }
    }

    /** Stack access returns a copy; component-bearing variants never collapse to item IDs. */
    public static final class Entry {
        private final ItemStack stack;
        private final Map<String, Integer> aspects;

        public Entry(ItemStack stack, Map<String, Integer> aspects) {
            this.stack = stack.copyWithCount(1);
            this.aspects = Collections.unmodifiableMap(new LinkedHashMap<>(aspects));
        }

        public ItemStack stack() { return stack.copy(); }
        public Map<String, Integer> aspects() { return aspects; }
    }

    private static Map<String, Integer> amounts(AspectList costs) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (costs != null) for (var aspect : costs.getAspects())
            if (aspect != null && costs.getAmount(aspect) > 0) out.put(aspect.getTag(), costs.getAmount(aspect));
        return out;
    }

    private static void requireMain() {
        if (!Minecraft.getInstance().isSameThread())
            throw new IllegalStateException("Aspect presentation requires client MAIN");
    }
}
