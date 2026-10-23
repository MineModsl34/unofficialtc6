package thaumcraft.api.casters;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
public final class CasterTriggerRegistry {
    private record Trigger(ICasterTriggerManager manager,int event){}
    private static final Map<String,LinkedHashMap<BlockState,List<Trigger>>> triggers=new HashMap<>();private CasterTriggerRegistry(){}
    public static void registerWandBlockTrigger(ICasterTriggerManager manager,int event,BlockState state,String modid){triggers.computeIfAbsent(modid,key->new LinkedHashMap<>()).computeIfAbsent(state,key->new ArrayList<>()).add(new Trigger(manager,event));}
    public static void registerCasterBlockTrigger(ICasterTriggerManager manager,int event,BlockState state){registerWandBlockTrigger(manager,event,state,"default");}
    public static boolean hasTrigger(BlockState state){return triggers.values().stream().anyMatch(map->map.containsKey(state));}public static boolean hasTrigger(BlockState state,String modid){return triggers.containsKey(modid)&&triggers.get(modid).containsKey(state);}
    public static boolean performTrigger(Level level,ItemStack caster,Player player,BlockPos pos,Direction side,BlockState state){for(var modid:triggers.keySet())if(performTrigger(level,caster,player,pos,side,state,modid))return true;return false;}
    public static boolean performTrigger(Level level,ItemStack caster,Player player,BlockPos pos,Direction side,BlockState state,String modid){var registry=triggers.get(modid);if(registry==null)return false;for(var trigger:registry.getOrDefault(state,List.of()))if(trigger.manager.performTrigger(level,caster,player,pos,side,trigger.event))return true;return false;}
}
