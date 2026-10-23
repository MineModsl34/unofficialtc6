package thaumcraft.client.lib.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.common.lib.network.fx.PacketSourceFX;

/** Native consumers of the two real source cloud/projectile presentation branches. */
public final class SourceProjectileFX {
    private SourceProjectileFX() {}
    public static boolean render(ClientLevel world,PacketSourceFX packet){if(packet.kind()!=PacketSourceFX.Kind.LOCAL)return false;var fx=FXDispatcher.INSTANCE;
        switch(packet.key()){
            case "focus_cloud_base"->fx.drawFocusCloudParticle(packet.x(),packet.y(),packet.z(),packet.tx(),packet.ty(),packet.tz(),packet.color());
            case "focus_projectile_mote"->fx.drawFireMote((float)packet.x(),(float)packet.y(),(float)packet.z(),(float)packet.tx(),(float)packet.ty(),(float)packet.tz(),(packet.color()>>16&255)/255F,(packet.color()>>8&255)/255F,(packet.color()&255)/255F,.5F,7);
            default->{return false;}
        }return true;
    }
}
