package thaumcraft.common.lib;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.RegisterEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.world.aura.AuraChunk;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.lib.capabilities.PlayerKnowledge;
import thaumcraft.common.lib.capabilities.PlayerWarp;
import thaumcraft.common.lib.capabilities.PlayerDataAttachmentPersistence;

/** The mod's Data Attachments (replacing 1.12 chunk NBT data and entity Capabilities). */
public final class TCAttachments {
    /** Chunk aura; an empty (not generated) value is not saved. */
    public static AttachmentType<AuraChunk> AURA;
    /** Personal, persistent data. No generic tracking/broadcast sync is registered. */
    public static AttachmentType<PlayerKnowledge> KNOWLEDGE;
    public static AttachmentType<PlayerWarp> WARP;

    private TCAttachments() {
    }

    public static void register(RegisterEvent.RegisterHelper<AttachmentType<?>> registry) {
        AURA = AttachmentType.builder(() -> new AuraChunk()).serialize(AuraChunk.CODEC, AuraChunk::isGenerated).build();
        registry.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "aura"), AURA);
        // Always serialize empty data: deserialize(empty) has source AUTOUNLOCK semantics.
        KNOWLEDGE = AttachmentType.builder(PlayerKnowledge::new)
            .serialize(PlayerDataAttachmentPersistence.knowledgeSerializer()).copyOnDeath().build();
        WARP = AttachmentType.builder(PlayerWarp::new)
            .serialize(PlayerDataAttachmentPersistence.warpSerializer()).copyOnDeath().build();
        registry.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "knowledge"), KNOWLEDGE);
        registry.register(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "warp"), WARP);
        ThaumcraftCapabilities.KNOWLEDGE = KNOWLEDGE;
        ThaumcraftCapabilities.WARP = WARP;
    }
}
