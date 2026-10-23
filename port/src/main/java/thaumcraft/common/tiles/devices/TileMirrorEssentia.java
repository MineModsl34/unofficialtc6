package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.common.lib.events.EssentiaHandler;
import thaumcraft.common.lib.crafting.MirrorLinkRules;

/** Source remote IAspectSource, not a local jar or a second free store. Transfers one unit;
 * ignoreMirror=true prevents recursion through the far-side source search.
 */
public final class TileMirrorEssentia extends TileMirrorBase implements IAspectSource {
    public TileMirrorEssentia(BlockPos pos,BlockState state){super(MirrorRegistries.ESSENTIA,pos,state);}
    @Override protected boolean essentia(){return true;}
    public void update(){updateLink();}
    @Override public AspectList getAspects(){return null;}
    @Override public void setAspects(AspectList aspects){}
    @Override public boolean doesContainerAccept(Aspect aspect){
        TileMirrorBase other=destinationTile();
        return other==null || EssentiaHandler.canAcceptEssentia(other,aspect,other.facing(),8,true);
    }
    @Override public int addToContainer(Aspect aspect,int amount){
        if(!MirrorLinkRules.acceptsEssentiaTransfer(amount)||!isLinkValid())return amount;
        TileMirrorBase other=destinationTile();
        if(other!=null && EssentiaHandler.addEssentia(other,aspect,other.facing(),8,true,5)){addInstability(amount);return 0;}
        return amount;
    }
    @Override public boolean takeFromContainer(Aspect aspect,int amount){
        if(!MirrorLinkRules.acceptsEssentiaTransfer(amount)||!isLinkValid())return false;
        TileMirrorBase other=destinationTile();
        boolean paid=other!=null && EssentiaHandler.drainEssentia(other,aspect,other.facing(),8,true,5);
        if(paid)addInstability(amount);return paid;
    }
    @Override public boolean doesContainerContainAmount(Aspect aspect,int amount){
        if(!MirrorLinkRules.acceptsEssentiaTransfer(amount)||!isLinkValid())return false;
        TileMirrorBase other=destinationTile();return other!=null && EssentiaHandler.findEssentia(other,aspect,other.facing(),8,true);
    }
    @Override public boolean takeFromContainer(AspectList aspects){return false;}
    @Override public boolean doesContainerContain(AspectList aspects){return false;}
    @Override public int containerContains(Aspect aspect){return 0;}
    @Override public boolean isBlocked(){return false;}
}
