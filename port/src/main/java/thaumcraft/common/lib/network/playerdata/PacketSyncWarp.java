package thaumcraft.common.lib.network.playerdata;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;

/** Detached owner-only snapshot; never retain the storage's live IntArrayTag array. */
public record PacketSyncWarp(CompoundTag data) implements CustomPacketPayload {
    public static final Type<PacketSyncWarp> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "sync_warp"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSyncWarp> STREAM_CODEC =
        ByteBufCodecs.COMPOUND_TAG.map(PacketSyncWarp::new, PacketSyncWarp::data).cast();

    public PacketSyncWarp {
        data = Objects.requireNonNull(data, "warp snapshot").copy();
    }

    @Override
    public CompoundTag data() {
        return data.copy();
    }

    public static PacketSyncWarp fromPlayer(ServerPlayer player) {
        return new PacketSyncWarp(ThaumcraftCapabilities.getWarp(player).serializeNBT());
    }

    @Override
    public Type<PacketSyncWarp> type() {
        return TYPE;
    }
}
