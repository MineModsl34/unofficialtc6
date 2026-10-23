package thaumcraft.client.lib;

import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.common.entities.construct.EntityOwnedConstruct;
import thaumcraft.common.entities.monster.EntityWisp;
import thaumcraft.common.golems.EntityThaumcraftGolem;

/** All registered living entity representatives, received over native S2C and rendered normally. */
final class ClientEntityRenderReview {
    private record Row(String key,int id){}
    private static final List<Row> ROWS=new ArrayList<>();
    private static final JsonObject REPORT=new JsonObject();
    private static CompletableFuture<Void> operation;
    private static int stage,ticks,frames;
    private static boolean capturePending;
    private static Throwable captureFailure;
    private static net.minecraft.world.Difficulty previousDifficulty;
    static JsonObject evidence(){return REPORT.deepCopy();}
    static boolean tick(Minecraft mc) throws Exception {
        require(ClientJeiReviewScenario.owns(mc.getSingleplayerServer()),"Entity render server remains owned");require(++ticks<1200,"Entity render timeout at "+stage);if(captureFailure!=null)throw new IllegalStateException(captureFailure);
        if(stage==0){var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();operation=new CompletableFuture<>();var pending=operation;server.execute(()->{try{var p=server.getPlayerList().getPlayer(owner);require(p!=null,"actual visual owner");p.closeContainer();var level=p.serverLevel();previousDifficulty=level.getDifficulty();server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);REPORT.addProperty("privateVisualDifficulty","normal");var anchor=p.blockPosition().offset(-5,0,4);
            for(var id:BuiltInRegistries.ENTITY_TYPE.keySet())if(id.getNamespace().equals("thaumcraft")){var e=BuiltInRegistries.ENTITY_TYPE.get(id).create(level);if(!(e instanceof LivingEntity living)){if(e!=null)e.discard();continue;}var pos=anchor.offset(ROWS.size()%6*2,0,ROWS.size()/6*2);level.setBlockAndUpdate(pos.below(),Blocks.STONE.defaultBlockState());living.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);living.setNoGravity(true);living.setInvulnerable(true);if(living instanceof Mob mob){mob.setNoAi(true);mob.setPersistenceRequired();}if(living instanceof EntityOwnedConstruct c){c.setOwnerId(owner);c.setOwned(true);c.setValidSpawn();}if(living instanceof EntityThaumcraftGolem g){g.setOwnerId(owner);g.setOwned(true);g.setValidSpawn();g.updateEntityAttributes();g.setHealth(g.getMaxHealth());}if(living instanceof EntityWisp w)w.setWispType("aer");if(living instanceof thaumcraft.common.entities.monster.EntitySpellBat bat){bat.setOwner(p);bat.setIsFriendly(true);}require(level.addFreshEntity(living),"native visual spawn "+id);ROWS.add(new Row(id.getPath(),living.getId()));}
            require(ROWS.size()>=25&&ROWS.size()<=36,"finite complete native living catalog: "+ROWS.size());pending.complete(null);
        }catch(Throwable t){pending.completeExceptionally(t);}});stage=1;return false;}
        if(stage==1){if(!operation.isDone())return false;operation.join();for(var row:ROWS)if(!(mc.level.getEntity(row.id)instanceof LivingEntity))return false;
            var entries=new JsonArray();var textures=new HashSet<ResourceLocation>();for(var row:ROWS){var e=(LivingEntity)mc.level.getEntity(row.id);var renderer=mc.getEntityRenderDispatcher().getRenderer(e);require(renderer!=null,"actual renderer bound "+row.key);var texture=renderer.getTextureLocation(e);if(texture.getNamespace().equals("thaumcraft"))require(mc.getResourceManager().getResource(texture).isPresent(),"source entity texture exists "+texture);textures.add(texture);var entry=new JsonObject();entry.addProperty("entity",row.key);entry.addProperty("renderer",renderer.getClass().getName());entry.addProperty("texture",texture.toString());entries.add(entry);}REPORT.add("views",entries);REPORT.addProperty("actualS2CEntities",ROWS.size());REPORT.addProperty("distinctTextureBindings",textures.size());mc.setScreen(new Catalog());stage=2;return false;}
        if(stage==2){if(frames<20)return false;var name="gameplay-entity-catalog.png";var path=ClientJeiReviewScenario.directory().resolve("screenshots").resolve(name);require(!Files.exists(path),"fresh entity framebuffer");capturePending=true;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->mc.execute(()->{try{require(Files.isRegularFile(path)&&Files.size(path)>1024,"native entity framebuffer completed");REPORT.addProperty("screenshot",path.toString());REPORT.addProperty("nativeRenderCalls",frames*ROWS.size());}catch(Throwable t){captureFailure=t;}finally{capturePending=false;}}));stage=3;return false;}
        if(stage==3){if(capturePending)return false;mc.setScreen(null);var server=mc.getSingleplayerServer();operation=new CompletableFuture<>();var pending=operation;server.execute(()->{try{require(ClientJeiReviewScenario.owns(server),"owned entity cleanup");for(var row:ROWS){var e=server.overworld().getEntity(row.id);if(e!=null)e.discard();}server.setDifficulty(previousDifficulty,true);REPORT.addProperty("privateDifficultyRestored",true);pending.complete(null);}catch(Throwable t){pending.completeExceptionally(t);}});stage=4;return false;}
        if(!operation.isDone())return false;operation.join();REPORT.addProperty("passed",true);REPORT.addProperty("scope","native default living representatives; animation, effects and every variant are separate visual scope");return true;
    }
    private static final class Catalog extends Screen {
        Catalog(){super(Component.literal("Thaumcraft native living entity catalog"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff10141b);g.drawCenteredString(font,title,width/2,4,0xffffff);int cols=6,rows=(ROWS.size()+5)/6,w=width/cols,h=(height-20)/rows;
            for(int i=0;i<ROWS.size();i++){var row=ROWS.get(i);var e=(LivingEntity)minecraft.level.getEntity(row.id);require(e!=null,"native viewport entity remains present "+row.key);int x=i%cols*w,y=18+i/cols*h;g.fill(x+2,y+2,x+w-2,y+h-2,0xff1d2633);int scale=Math.max(7,(int)((h-16)/Math.max(1,e.getBbHeight())));InventoryScreen.renderEntityInInventoryFollowsMouse(g,x+3,y+3,x+w-3,y+h-12,scale,.0625F,x+w/2F+10,y+h/2F,e);g.drawCenteredString(font,font.plainSubstrByWidth(row.key,w-4),x+w/2,y+h-10,0xffffff);}g.flush();frames++;}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
