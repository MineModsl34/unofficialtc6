package thaumcraft.common.lib.network.misc;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;

/** Owner-only S2C feedback for ONE whole point, not raw knowledge/NBT or a client-side award. */
public record PacketKnowledgeGain(byte typeOrdinal, String categoryKey) implements CustomPacketPayload {
    public static final int MAX_CATEGORY_CHARACTERS = 1024;
    public static final Type<PacketKnowledgeGain> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "knowledge_gain"));
    public static final StreamCodec<ByteBuf, PacketKnowledgeGain> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BYTE, PacketKnowledgeGain::typeOrdinal,
        ByteBufCodecs.stringUtf8(MAX_CATEGORY_CHARACTERS), PacketKnowledgeGain::categoryKey,
        PacketKnowledgeGain::new);

    public PacketKnowledgeGain {
        if (typeOrdinal < 0 || typeOrdinal >= EnumKnowledgeType.values().length) {
            throw new IllegalArgumentException("Unknown knowledge-gain type ordinal");
        }
        categoryKey = categoryKey == null ? "" : categoryKey;
        if (categoryKey.length() > MAX_CATEGORY_CHARACTERS) {
            throw new IllegalArgumentException("Knowledge-gain category exceeds wire bound");
        }
    }

    public EnumKnowledgeType knowledgeType() { return EnumKnowledgeType.values()[typeOrdinal]; }

    @Override
    public Type<PacketKnowledgeGain> type() { return TYPE; }
}
