package thaumcraft.common.items.casters.foci;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.*;
public class FocusMediumBolt extends FocusMediumTouch {
    public String getResearch(){return "FOCUSBOLT";}public String getKey(){return "thaumcraft.BOLT";}public int getComplexity(){return 5;}public Aspect getAspect(){return Aspect.ENERGY;}
    public boolean execute(Trajectory t){var hit=hit(t);var end=hit==null?t.source.add(t.direction.normalize().scale(16)):hit.getLocation();var effects=getPackage().getFocusEffects();
        if(effects.length>0){int r=0,g=0,b=0;for(var effect:effects){int color=FocusEngine.getElementColor(effect.getKey());r+=color>>16&255;g+=color>>8&255;b+=color&255;}
            int color=0xff000000|(r/effects.length)<<16|(g/effects.length)<<8|b/effects.length;
            thaumcraft.common.lib.fx.SourceFX.emit(getPackage().world,thaumcraft.common.lib.fx.SourceFX.event(thaumcraft.common.lib.network.fx.PacketSourceFX.Kind.ZAP,t.source,end,color,Float.floatToIntBits(getPackage().getPower()*.66F),0,-1,""),64);
        }return true;
    }
}
