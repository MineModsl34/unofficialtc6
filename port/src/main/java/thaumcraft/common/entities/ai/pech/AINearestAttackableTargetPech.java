package thaumcraft.common.entities.ai.pech;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import thaumcraft.common.entities.monster.EntityPech;
public final class AINearestAttackableTargetPech extends NearestAttackableTargetGoal<Player> {
    private final EntityPech pech;
    public AINearestAttackableTargetPech(EntityPech pech){super(pech,Player.class,true);this.pech=pech;}
    @Override public boolean canUse(){return pech.getAnger()!=0&&super.canUse();}
}
