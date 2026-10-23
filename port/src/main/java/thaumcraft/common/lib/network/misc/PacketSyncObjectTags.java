package thaumcraft.common.lib.network.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.internal.CommonInternals;

/**
 * Not in TC6: in 1.12.2 the client computed aspects from recipes itself. In 1.21 only the server has recipes,
 * so the server sends the client the finished item aspect table.
 */
public record PacketSyncObjectTags(List<Entry> entries) implements CustomPacketPayload {
    public record Entry(ItemStack stack, AspectList aspects) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, Entry::stack,
            AspectList.STREAM_CODEC, Entry::aspects,
            Entry::new
        );
    }

    public static final Type<PacketSyncObjectTags> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "sync_object_tags"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSyncObjectTags> STREAM_CODEC = Entry.STREAM_CODEC
        .apply(ByteBufCodecs.list())
        .map(PacketSyncObjectTags::new, PacketSyncObjectTags::entries);

    public static PacketSyncObjectTags fromServerTable() {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> e : CommonInternals.objectTagStacks.entrySet()) {
            AspectList aspects = CommonInternals.objectTags.get(e.getKey());
            if (aspects != null && aspects.size() > 0) {
                entries.add(new Entry(e.getValue(), aspects));
            }
        }
        return new PacketSyncObjectTags(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
