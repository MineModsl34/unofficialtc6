package thaumcraft.gametest;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.essentia.*;
import thaumcraft.common.world.aura.AuraHandler;

/** Bounded native load sample. Reports operation cost; does not promise full-pack TPS. */
@GameTestHolder(Thaumcraft.MODID)
public final class LoadAcceptanceGameTests {
    @GameTestGenerator public static Collection<TestFunction> sample(){return List.of(new TestFunction("bulk_native_load","thaumcraft.bulk_native_load","thaumcraft:scan_test",350,0,true,LoadAcceptanceGameTests::run));}
    private static void run(GameTestHelper h){
        var report=new JsonObject();var pos=h.absolutePos(new BlockPos(5,2,5));var cp=h.getLevel().getChunkAt(pos).getPos();var aura=AuraHandler.getAuraChunk(h.getLevel(),cp.x,cp.z);float old=aura.getVis();long start=System.nanoTime();
        try{aura.setVis(500);for(int i=0;i<1000;i++)h.assertTrue(AuraHelper.drainVis(h.getLevel(),pos,.125F,true)==.125F,"load simulation returns requested actual aura");h.assertTrue(aura.getVis()==500,"1000 probes do not mutate actual aura");for(int i=0;i<1000;i++)h.assertTrue(AuraHelper.drainVis(h.getLevel(),pos,.125F,false)==.125F,"load payment drains real aura");h.assertTrue(aura.getVis()==375,"1000 actual payments conserve precisely 125 vis");}finally{aura.setVis(old);}report.addProperty("auraOperations",2000);report.addProperty("auraNanoseconds",System.nanoTime()-start);
        var sources=new ArrayList<TileAlembic>();var pipes=new ArrayList<TileTube>();var jars=new ArrayList<TileJarFillable>();
        for(int x:List.of(1,4,7))for(int z:List.of(1,4,7)){var source=BulkGameplayFixtures.tile(h,new BlockPos(x,3,z),BlocksTC.alembic,TileAlembic.class);source.aspect=Aspect.AIR;source.amount=32;var pipe=BulkGameplayFixtures.tile(h,new BlockPos(x+1,3,z),BlocksTC.tube,TileTube.class);var jar=BulkGameplayFixtures.tile(h,new BlockPos(x+1,2,z),BlocksTC.jarNormal,TileJarFillable.class);jar.aspectFilter=Aspect.AIR;sources.add(source);pipes.add(pipe);jars.add(jar);}
        long transferStart=System.nanoTime();long firstTick=h.getLevel().getGameTime();h.succeedWhen(()->{h.assertTrue(jars.stream().allMatch(j->j.amount==32),"nine native routes deliver all 288 paid units");h.assertTrue(sources.stream().allMatch(s->s.amount==0)&&pipes.stream().allMatch(p->p.getEssentiaAmount(Direction.DOWN)==0),"parallel routes conserve source/output/pipe escrow");report.addProperty("nativeRoutes",9);report.addProperty("nativeTiles",27);report.addProperty("essentiaUnits",288);report.addProperty("transferTicks",h.getLevel().getGameTime()-firstTick);report.addProperty("transferNanoseconds",System.nanoTime()-transferStart);report.addProperty("passed",true);report.addProperty("scope","finite native load sample on this machine; no full-pack TPS claim");String directory=System.getProperty("thaumcraft.bulkRunDir");if(directory!=null)try{Files.writeString(Path.of(directory).resolve("native-load-sample.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n",StandardOpenOption.CREATE_NEW);}catch(java.io.IOException e){throw new IllegalStateException(e);}});
    }
}
