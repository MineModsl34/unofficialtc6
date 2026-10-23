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
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;

/** Owner-only complete snapshot. Source order: take snapshot, then remove server POPUP. */
public record PacketSyncKnowledge(CompoundTag data) implements CustomPacketPayload {
    public static final Type<PacketSyncKnowledge> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "sync_knowledge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSyncKnowledge> STREAM_CODEC =
        ByteBufCodecs.COMPOUND_TAG.map(PacketSyncKnowledge::new, PacketSyncKnowledge::data).cast();

    public PacketSyncKnowledge {
        data = Objects.requireNonNull(data, "knowledge snapshot").copy();
    }

    @Override
    public CompoundTag data() {
        return data.copy();
    }

    public static PacketSyncKnowledge fromPlayer(ServerPlayer player) {
        var knowledge = ThaumcraftCapabilities.getKnowledge(player);
        var snapshot = new PacketSyncKnowledge(knowledge.serializeNBT());
        for (String key : knowledge.getResearchList()) {
            knowledge.clearResearchFlag(key, EnumResearchFlag.POPUP);
        }
        return snapshot;
    }

    @Override
    public Type<PacketSyncKnowledge> type() {
        return TYPE;
    }
}
