package thaumcraft.client.lib;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumcraft.client.lib.events.RenderEventHandler;
import thaumcraft.common.lib.SoundsTC;
import thaumcraft.common.lib.network.misc.PacketKnowledgeGain;

/** Source whole-point animation; owner/catalog checks do not mutate knowledge or research. */
public final class ClientKnowledgeGainHandlers {
    private ClientKnowledgeGainHandlers() {}
    public static void gain(PacketKnowledgeGain packet,IPayloadContext context) {
        var mc=Minecraft.getInstance();if(!mc.isSameThread())throw new IllegalStateException("Knowledge feedback requires client MAIN");
        if(mc.player==null||mc.level==null||mc.getConnection()==null||mc.getConnection().getConnection()!=context.connection())return;
        if(!ClientResearchCatalogHandlers.readyForCurrentConnection()){context.disconnect(Component.literal("Thaumcraft research catalog is missing before knowledge feedback."));return;}
        RenderEventHandler.hudHandler.knowledge(packet.knowledgeType(),packet.categoryKey());
        mc.level.playLocalSound(mc.player.getX(),mc.player.getY(),mc.player.getZ(),SoundsTC.learn,SoundSource.AMBIENT,1,1,false);
    }
}
