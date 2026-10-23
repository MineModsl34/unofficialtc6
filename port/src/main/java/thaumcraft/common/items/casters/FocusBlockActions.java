package thaumcraft.common.items.casters;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import thaumcraft.api.aura.AuraHelper;
/** ServerEvents breaker/swapper/runnable slice needed by actual focus effects. */
@EventBusSubscriber(modid="thaumcraft")
public final class FocusBlockActions {
    public static final List<String> portableHoleBlackList=new ArrayList<>();
    private record Scheduled(long tick,Runnable action){}
    private record Break(BlockPos pos,BlockState source,Player player,boolean fx,boolean silk,int fortune,float strength,float current,float maximum,float vis){}
    private static final Map<ServerLevel,List<Scheduled>> tasks=new WeakHashMap<>();private static final Map<ServerLevel,List<Break>> breaks=new WeakHashMap<>();
    private FocusBlockActions(){}
    public static boolean exposed(Level level,BlockPos pos){for(var side:Direction.values()){var p=pos.relative(side);if(!level.getBlockState(p).isSolidRender(level,p))return true;}return false;}
    public static boolean holeBlacklisted(BlockState state){String identity=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();for(String entry:portableHoleBlackList){String[] parts=entry.split(";");if(!identity.equals(parts[0]))continue;boolean match=true;for(int i=1;i<parts.length;i++)match&=state.toString().contains(parts[i]);if(match)return true;}return false;}
    public static void schedule(Level level,Runnable task,int delay){if(level instanceof ServerLevel server)tasks.computeIfAbsent(server,k->new ArrayList<>()).add(new Scheduled(server.getGameTime()+Math.max(0,delay),task));}
    public static void breaker(ServerLevel level,BlockPos pos,Player player,boolean fx,boolean silk,int fortune,float strength,float current,float maximum,int delay,float vis){var source=level.getBlockState(pos);Runnable task=()->breaks.computeIfAbsent(level,k->new ArrayList<>()).add(new Break(pos.immutable(),source,player,fx,silk,fortune,strength,current,maximum,vis));if(delay>0)schedule(level,task,delay);else task.run();}
    private static ItemStack tool(ServerLevel level,Player player,boolean silk,int fortune){var tool=player.getMainHandItem().copy();var registry=level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);var silkHolder=registry.getHolderOrThrow(Enchantments.SILK_TOUCH);var fortuneHolder=registry.getHolderOrThrow(Enchantments.FORTUNE);if(silk||fortune>EnchantmentHelper.getItemEnchantmentLevel(fortuneHolder,tool)){if(tool.isEmpty())tool=new ItemStack(Items.DIAMOND_PICKAXE);if(silk)tool.enchant(silkHolder,1);int rank=Math.max(fortune,EnchantmentHelper.getItemEnchantmentLevel(fortuneHolder,player.getMainHandItem()));if(rank>0)tool.enchant(fortuneHolder,rank);}return tool;}
    private static boolean harvest(ServerLevel level,BlockPos pos,Player player,boolean silk,int fortune){if(!(player instanceof ServerPlayer server)||NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,level.getBlockState(pos),player)).isCanceled())return false;var state=level.getBlockState(pos);if((state.getBlock() instanceof GameMasterBlock)&&!player.canUseGameMasterBlocks())return false;var entity=level.getBlockEntity(pos);level.levelEvent(player,2001,pos,Block.getId(state));if(!state.onDestroyedByPlayer(level,pos,player,!player.isCreative(),state.getFluidState()))return false;state.getBlock().destroy(level,pos,state);if(!player.isCreative()){var held=tool(level,player,silk,fortune);state.getBlock().playerDestroy(level,player,pos,state,entity,held);}return true;}
    /** Source preflight placement event: intended state explicit, world and snapshot unchanged until approval. */
    private static boolean deniedPlace(ServerLevel level,BlockPos pos,Player player,BlockState placed){
        var snapshot=BlockSnapshot.create(level.dimension(),level,pos);
        var event=new BlockEvent.EntityPlaceEvent(snapshot,level.getBlockState(pos.below()),player){
            @Override public BlockState getPlacedBlock(){return placed;}
            @Override public BlockState getState(){return placed;}
        };
        return NeoForge.EVENT_BUS.post(event).isCanceled();
    }
    public static void swapper(ServerLevel level,BlockPos pos,Player player,ItemStack target,boolean silk,int fortune,float vis){
        var source=level.getBlockState(pos);var replacement=target.copyWithCount(1);
        schedule(level,()->{
            if(!level.hasChunkAt(pos)||player.level()!=level||!player.isAlive()||level.getBlockState(pos)!=source||source.getDestroySpeed(level,pos)<0||AuraHelper.getVis(level,pos)<vis||!level.mayInteract(player,pos)||!player.getAbilities().mayBuild||source.getBlock().asItem()==replacement.getItem())return;
            int slot=-1;for(int i=0;i<36;i++)if(ItemStack.isSameItemSameComponents(player.getInventory().getItem(i),replacement)){slot=i;break;}
            if(!player.isCreative()&&slot<0)return;
            var placed=replacement.getItem() instanceof BlockItem item?item.getBlock().defaultBlockState():Blocks.AIR.defaultBlockState();
            if(replacement.get(net.minecraft.core.component.DataComponents.BLOCK_STATE)!=null)placed=replacement.get(net.minecraft.core.component.DataComponents.BLOCK_STATE).apply(placed);
            if(deniedPlace(level,pos,player,placed)||player.level()!=level||!player.isAlive()||level.getBlockState(pos)!=source||!player.getAbilities().mayBuild||!level.mayInteract(player,pos)||!player.isCreative()&&(slot<0||!ItemStack.isSameItemSameComponents(player.getInventory().getItem(slot),replacement)||AuraHelper.getVis(level,pos)<vis))return;
            var loot=Block.getDrops(source,level,pos,level.getBlockEntity(pos),player,tool(level,player,silk,fortune));
            boolean captureBefore=level.captureBlockSnapshots;level.captureBlockSnapshots=false;boolean changed;
            try{changed=level.setBlock(pos,placed,3);}finally{level.captureBlockSnapshots=captureBefore;}
            if(!changed)return;
            if(!player.isCreative()){player.getInventory().getItem(slot).shrink(1);AuraHelper.drainVis(level,pos,vis,false);for(var drop:loot)if(!player.getInventory().add(drop))Block.popResource(level,pos,drop);}
            if(!(replacement.getItem() instanceof BlockItem)){var drop=new thaumcraft.common.entities.EntitySpecialItem(level,pos.getX()+.5,pos.getY()+.1,pos.getZ()+.5,replacement.copy());drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);level.addFreshEntity(drop);}
            thaumcraft.common.lib.fx.SourceFX.bamf(level,net.minecraft.world.phys.Vec3.atCenterOf(pos),8038177,true,false,null);player.getInventory().setChanged();
        },0);
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel level))return;var pending=tasks.remove(level);if(pending!=null)for(var task:pending)if(task.tick<=level.getGameTime())task.action.run();else tasks.computeIfAbsent(level,k->new ArrayList<>()).add(task);var active=breaks.remove(level);if(active==null)return;for(var b:active){if(!level.hasChunkAt(b.pos)||b.player.level()!=level||!b.player.isAlive()||level.getBlockState(b.pos)!=b.source){if(b.fx)level.destroyBlockProgress(b.pos.hashCode(),b.pos,-1);continue;}if(AuraHelper.getVis(level,b.pos)<b.vis||!level.mayInteract(b.player,b.pos)||!b.player.getAbilities().mayBuild||b.source.getDestroySpeed(level,b.pos)<0)continue;if(b.fx)level.destroyBlockProgress(b.pos.hashCode(),b.pos,b.maximum==0?10:(int)((1-b.current/b.maximum)*10));float remaining=b.current-b.strength;if(remaining<=0){harvest(level,b.pos,b.player,b.silk,b.fortune);if(b.fx)level.destroyBlockProgress(b.pos.hashCode(),b.pos,-1);if(b.vis>0)AuraHelper.drainVis(level,b.pos,b.vis,false);}else breaks.computeIfAbsent(level,k->new ArrayList<>()).add(new Break(b.pos,b.source,b.player,b.fx,b.silk,b.fortune,b.strength,remaining,b.maximum,b.vis));}}
}
