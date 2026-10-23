package thaumcraft.common.lib.warp;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import thaumcraft.common.entities.monster.EntityThaumicSlime;

/** Typed client-installed presentation hook: common/server classes never load client classes. */
public final class WarpTaintPresentationHooks {
    public interface ClientPresentation {
        void slimeJump(EntityThaumicSlime slime,int size,int count);
        void fluxBubble(Level level,BlockPos pos,int meta,RandomSource random);
    }
    private static ClientPresentation client;
    private WarpTaintPresentationHooks() {}
    public static void install(ClientPresentation presentation) { client=presentation; }
    public static void slimeJump(EntityThaumicSlime slime,int size,int count) { if(client!=null) client.slimeJump(slime,size,count); }
    public static void fluxBubble(Level level,BlockPos pos,int meta,RandomSource random) { if(client!=null) client.fluxBubble(level,pos,meta,random); }
}
