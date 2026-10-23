package thaumcraft.common.entities.ai.pech;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import thaumcraft.common.entities.monster.EntityPech;
public final class AIPechTradePlayer extends Goal {
    private final EntityPech pech;
    public AIPechTradePlayer(EntityPech pech){this.pech=pech;setFlags(EnumSet.of(Flag.MOVE,Flag.JUMP));}
    @Override public boolean canUse(){return pech.isAlive()&&!pech.isInWater()&&pech.isTamed()&&pech.onGround()&&!pech.hurtMarked&&pech.trading;}
    @Override public void start(){pech.getNavigation().stop();}
    @Override public void stop(){pech.trading=false;}
}
