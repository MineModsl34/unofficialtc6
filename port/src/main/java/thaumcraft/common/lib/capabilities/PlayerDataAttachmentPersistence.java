package thaumcraft.common.lib.capabilities;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.TCAttachments;

/**
 * Fail-closed entity-persistence adapter, not a change to TC6's public storage APIs.
 * NeoForge otherwise catches a serializer exception and silently drops the entire attachment.
 * A failed load instead retains an isolated copy of ALL raw NBT, refuses personal-data login sync,
 * and writes that original tag back on save/clone. Recovery requires fixing the saved data (or its
 * metadata dependency) and loading a new player; mutating a partial model does not clear quarantine.
 */
public final class PlayerDataAttachmentPersistence {
    // Both model classes are final and keep Object equality/hashCode, hence these are weak identity
    // keys. Values never reference their keys/holders; ordinary valid models are not retained here.
    private static final Map<Object, Quarantine> QUARANTINED = new WeakHashMap<>();
    private static final IAttachmentSerializer<Tag, PlayerKnowledge> KNOWLEDGE = guarded(
        PlayerKnowledge::new, PlayerKnowledge::deserializeNBT, PlayerKnowledge::serializeNBT);
    private static final IAttachmentSerializer<Tag, PlayerWarp> WARP = guarded(
        PlayerWarp::new, PlayerWarp::deserializeNBT, PlayerWarp::serializeNBT);

    private PlayerDataAttachmentPersistence() {}

    public static IAttachmentSerializer<Tag, PlayerKnowledge> knowledgeSerializer() { return KNOWLEDGE; }
    public static IAttachmentSerializer<Tag, PlayerWarp> warpSerializer() { return WARP; }

    /** Read-only predicate: does not create missing attachments, send packets or disconnect. */
    public static boolean allowsLogin(ServerPlayer player) {
        return !isQuarantined(player);
    }

    public static boolean isQuarantined(IAttachmentHolder holder) {
        Object knowledge = holder.getExistingDataOrNull(TCAttachments.KNOWLEDGE);
        Object warp = holder.getExistingDataOrNull(TCAttachments.WARP);
        synchronized (QUARANTINED) {
            return QUARANTINED.containsKey(knowledge) || QUARANTINED.containsKey(warp);
        }
    }

    private static <T> IAttachmentSerializer<Tag, T> guarded(Supplier<T> factory,
        BiConsumer<T, CompoundTag> deserialize, Function<T, CompoundTag> serialize) {
        return new IAttachmentSerializer<>() {
            @Override
            public T read(IAttachmentHolder holder, Tag raw, HolderLookup.Provider provider) {
                T model = factory.get();
                if (raw == null) return model; // No such key is emitted by Entity's actual load loop.
                try {
                    if (!(raw instanceof CompoundTag compound)) {
                        throw new IllegalArgumentException("Expected a compound personal-data attachment");
                    }
                    // Preserve source partial-state/native exceptions when this is called directly
                    // on the model; ONLY this persistence boundary captures the failed-load marker.
                    deserialize.accept(model, compound);
                } catch (RuntimeException failure) {
                    synchronized (QUARANTINED) {
                        QUARANTINED.put(model, new Quarantine(raw.copy(), failure.getClass().getName()));
                    }
                    Thaumcraft.log.warn("Quarantined unreadable {} attachment ({}). Full raw NBT is preserved; login sync is refused.",
                        model.getClass().getSimpleName(), failure.getClass().getName());
                }
                // Do not throw to AttachmentHolder.deserializeAttachments: it would discard model
                // AND quarantine. Its default copy handler passes through this same boundary.
                return model;
            }

            @Override
            public Tag write(T model, HolderLookup.Provider provider) {
                synchronized (QUARANTINED) {
                    Quarantine quarantine = QUARANTINED.get(model);
                    if (quarantine != null) return quarantine.raw.copy();
                }
                // Valid data remains source-faithful, including raw warp int-array alias behavior.
                return serialize.apply(model);
            }
        };
    }

    private record Quarantine(Tag raw, String exceptionClass) {}
}
