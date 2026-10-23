package thaumcraft.client.lib.events;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.client.lib.ClientAuraHandlers;
import thaumcraft.client.lib.UtilsFX;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.items.tools.EquipmentRules;
import thaumcraft.common.items.tools.ItemSanityChecker;
import thaumcraft.common.items.tools.ItemThaumometer;

/** Source hand-ordered HUD, aura/warp meters and animated whole-point knowledge notification. */
public final class HudHandler {
    private static final ResourceLocation HUD=ResourceLocation.parse("thaumcraft:textures/gui/hud.png");
    private static final ResourceLocation BOOK=ResourceLocation.parse("thaumcraft:textures/item/thaumonomicon.png");
    private static final ResourceLocation PARTICLES=ResourceLocation.parse("thaumcraft:textures/misc/particles.png");
    private static final ResourceLocation[] KNOW_TYPE={ResourceLocation.parse("thaumcraft:textures/research/knowledge_theory.png"),ResourceLocation.parse("thaumcraft:textures/research/knowledge_observation.png")};
    private static final DecimalFormat NUMBER=new DecimalFormat("#######.#");
    private static final int TRACKER_LIMIT=256;
    private final ArrayDeque<KnowledgeGainTracker> knowledge=new ArrayDeque<>();
    private float kgFade;
    public void knowledge(EnumKnowledgeType type,String category) {
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        // A packet burst may exceed a frame's useful pixels; cap presentation only, never knowledge.
        if(knowledge.size()>=TRACKER_LIMIT)knowledge.removeFirst();
        int progress=40+mc.level.random.nextInt(20)+(type==EnumKnowledgeType.THEORY?10:0);
        knowledge.addLast(new KnowledgeGainTracker(type,category,progress,mc.level.random.nextLong()));
    }
    public void tick() {
        if(knowledge.isEmpty()){if(kgFade>0)kgFade--;return;}
        kgFade=Math.min(40,kgFade+10);
        var iterator=knowledge.iterator();while(iterator.hasNext()){var tracker=iterator.next();if(tracker.progress>0)tracker.progress--;else iterator.remove();}
    }
    public void clear(){knowledge.clear();kgFade=0;}
    public boolean hasKnowledgeFeedback(){return kgFade>0;}
    public void renderHuds(GuiGraphics graphics,DeltaTracker delta) {
        var mc=Minecraft.getInstance();var player=mc.player;if(player==null||mc.level==null)return;
        float partial=delta.getGameTimeDeltaPartialTick(false);UtilsFX.sysPartialTicks=partial;
        if(mc.screen!=null||mc.options.hideGui)return;
        boolean cast=false,meter=false,sanity=false;int start=0;
        for(ItemStack hand:new ItemStack[]{player.getMainHandItem(),player.getOffhandItem()}){
            if(!cast&&hand.getItem() instanceof ICaster){renderCastingWandHud(graphics,hand,start);cast=true;if(!ModConfig.CONFIG_GRAPHICS.dialBottom)start+=33;}
            else if(!meter&&hand.getItem() instanceof ItemThaumometer){renderThaumometerHud(graphics,partial,start);meter=true;start+=80;}
            else if(!sanity&&hand.getItem() instanceof ItemSanityChecker){renderSanityHud(graphics,start);sanity=true;start+=75;}
        }
    }
    public void renderKnowledgeFeedback(GuiGraphics graphics,float partial){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.level!=null&&kgFade>0)renderKnowledgeGains(graphics,partial);}
    private void renderThaumometerHud(GuiGraphics graphics,float partial,int shift) {
        var mc=Minecraft.getInstance();var aura=ClientAuraHandlers.currentAura();
        var bars=HudGeometry.aura(aura==null?0:aura.base(),aura==null?0:aura.vis(),aura==null?0:aura.flux());
        float ticks=mc.player.tickCount+partial,ticks2=mc.player.tickCount/3F+partial;
        graphics.pose().pushPose();graphics.pose().translate(2,shift,0);
        float visY=10+(1-bars.vis())*64,fluxY=10+(1-bars.flux()-bars.vis())*64;
        coloredBar(graphics,visY,bars.vis(),.7F,.4F,.9F);
        glowBar(graphics,visY,bars.vis(),96,56+ticks%64,1,1,1);
        coloredBar(graphics,fluxY,bars.flux(),.25F,.1F,.3F);
        glowBar(graphics,fluxY,bars.flux(),104,120-ticks2%64,.7F,.4F,1);
        if(mc.player.isShiftKeyDown()&&aura!=null){graphics.pose().pushPose();graphics.pose().scale(.5F,.5F,1);
            if(bars.vis()>0)graphics.drawString(mc.font,NUMBER.format(aura.vis()),32,(int)(visY*2),15641343,false);
            if(bars.flux()>0)graphics.drawString(mc.font,NUMBER.format(aura.flux()),32,(int)((fluxY-4)*2),11145659,false);
            graphics.pose().popPose();}
        UtilsFX.drawTexturedQuad(graphics,HUD,1,1,72,48,16,80);
        UtilsFX.drawTexturedQuad(graphics,HUD,2,8+(1-bars.base())*64,117,61,14,5);
        graphics.pose().popPose();
    }
    private void coloredBar(GuiGraphics g,float y,float amount,float r,float green,float b){if(amount<=0)return;g.setColor(r,green,b,1);g.pose().pushPose();g.pose().translate(5,y,0);g.pose().scale(1,amount,1);UtilsFX.drawTexturedQuad(g,HUD,0,0,88,56,8,64);g.pose().popPose();g.setColor(1,1,1,1);}
    private void glowBar(GuiGraphics g,float y,float amount,float u,float v,float r,float green,float b){if(amount<=0)return;g.flush();RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);g.setColor(r,green,b,.5F);UtilsFX.drawTexturedQuad(g,HUD,5,y,u,v,8,amount*64);g.flush();g.setColor(1,1,1,1);RenderSystem.defaultBlendFunc();}
    private void renderSanityHud(GuiGraphics graphics,int shift) {
        var mc=Minecraft.getInstance();var warp=ThaumcraftCapabilities.getWarp(mc.player);
        int p=warp.get(EnumWarpType.PERMANENT),s=warp.get(EnumWarpType.NORMAL),t=warp.get(EnumWarpType.TEMPORARY);
        int[] parts=EquipmentRules.sanitySegments(p,s,t);int gap=parts[0],wt=parts[1],ws=parts[2];
        graphics.pose().pushPose();graphics.pose().translate(0,shift,0);graphics.blit(HUD,1,1,152,0,20,76,256,256);
        // Keep source cumulative quad heights, not normalised disjoint rectangles.
        if(t>0){graphics.setColor(1,.5F,1,1);graphics.blit(HUD,7,21+gap,200,gap,8,wt+gap,256,256);}
        if(s>0){graphics.setColor(.75F,0,.75F,1);graphics.blit(HUD,7,21+gap+wt,200,gap+wt,8,wt+ws+gap,256,256);}
        if(p>0){graphics.setColor(.5F,0,.5F,1);graphics.blit(HUD,7,21+gap+wt+ws,200,gap+wt+ws,8,48,256,256);}
        graphics.setColor(1,1,1,1);graphics.blit(HUD,1,1,176,0,20,76,256,256);
        if(p+s+t>=100){graphics.pose().pushPose();graphics.pose().scale(.75F,.75F,1);graphics.pose().translate(mc.player.getRandom().nextInt(2),mc.player.getRandom().nextInt(2),0);graphics.blit(HUD,3,3,216,0,20,16,256,256);graphics.pose().popPose();}graphics.pose().popPose();
    }
    private void renderCastingWandHud(GuiGraphics graphics,ItemStack stack,int shift) {
        var mc=Minecraft.getInstance();var player=mc.player;var caster=(ICaster)stack.getItem();
        int y=ModConfig.CONFIG_GRAPHICS.dialBottom?graphics.guiHeight()-32:shift;
        var aura=ClientAuraHandlers.currentAura();int max=aura==null?0:aura.base(),amount=aura==null?0:(int)aura.vis();
        graphics.pose().pushPose();graphics.pose().translate(0,y,0);graphics.pose().pushPose();graphics.pose().scale(.5F,.5F,1);graphics.blit(HUD,0,0,0,0,64,64,256,256);graphics.pose().popPose();
        graphics.pose().pushPose();graphics.pose().translate(32,6,0);graphics.pose().scale(.5F,.5F,1);
        // Java float->int source infinity can yield MAX_VALUE at absent zero base; clip native quad only.
        int length=max==0?0:Math.max(0,(int)(30F*amount/max));int color=Aspect.ENERGY.getColor();
        graphics.setColor((color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F,.8F);graphics.blit(HUD,-4,35-length,104,0,8,length,256,256);
        graphics.setColor(1,1,1,1);graphics.blit(HUD,-8,-3,72,0,16,42,256,256);
        var focus=caster.getFocusStack(stack);
        if(player.isShiftKeyDown()){graphics.pose().pushPose();graphics.pose().mulPose(Axis.ZP.rotationDegrees(-90));graphics.drawString(mc.font,Integer.toString(amount),-32,-4,-1,false);graphics.pose().popPose();float cost=ItemFocus.getVisCost(focus)*caster.getConsumptionModifier(stack,player,false);if(cost>0){String text=NUMBER.format(cost);graphics.drawString(mc.font,text,-32-mc.font.width(text)/2,32,-1,false);}}
        graphics.pose().popPose();
        if(focus!=null&&!focus.isEmpty()){var picked=caster.getPickedBlock(stack);if(picked!=null&&!picked.isEmpty()){graphics.renderItem(picked,8,8);int count=0;for(var item:player.getInventory().items)if(ItemStack.isSameItem(item,picked))count+=item.getCount();graphics.pose().pushPose();graphics.pose().translate(16,7,200);graphics.pose().scale(.5F,.5F,1);String text=Integer.toString(count);int tx=16-mc.font.width(text);for(int[] offset:new int[][]{{-1,0},{1,0},{0,-1},{0,1}})graphics.drawString(mc.font,text,tx+offset[0],24+offset[1],0,false);graphics.drawString(mc.font,text,tx,24,-1,false);graphics.pose().popPose();}else graphics.renderItem(focus,8,8);}
        graphics.pose().popPose();
    }
    private void renderKnowledgeGains(GuiGraphics graphics,float partial) {
        var mc=Minecraft.getInstance();int width=graphics.guiWidth(),height=graphics.guiHeight();graphics.setColor(1,1,1,kgFade/40F);graphics.blit(BOOK,width-17,height-17,0,0,16,16,16,16);graphics.setColor(1,1,1,1);
        int index=0;for(var tracker:knowledge){Random random=new Random(tracker.seed);float x=width/4+random.nextInt(32),y=height/3+random.nextInt(32);var motion=HudGeometry.knowledge(tracker.progress,tracker.max,partial,x,y);float xx=width-12+random.nextInt(8)-motion.xDistance(),yy=height-12+random.nextInt(8)-motion.yDistance();
            if(tracker.sparks&&mc.player.getRandom().nextInt((int)(1F+(float)tracker.progress/tracker.max*10F))==0){mc.level.random.nextInt(1);float green=(189+mc.level.random.nextInt(67))/255F,blue=(64+mc.level.random.nextInt(192))/255F;FXDispatcher.INSTANCE.drawSimpleSparkleGui(mc.level.random,xx+mc.level.random.nextGaussian()*5,yy+mc.level.random.nextGaussian()*5,mc.level.random.nextGaussian(),mc.level.random.nextGaussian(),24,1,green,blue,mc.level.random.nextInt(5),.9F,-1);}
            graphics.pose().pushPose();graphics.pose().translate(xx,yy,100+index++);graphics.pose().mulPose(Axis.ZP.rotationDegrees(-(84+random.nextInt(12))));centered(graphics,KNOW_TYPE[tracker.type.ordinal()],motion.size(),0,0,1,1,1,1,1);
            var category=tracker.category.isEmpty()?null:ResearchCategories.getResearchCategory(tracker.category);if(category!=null&&category.icon!=null){graphics.pose().translate(0,0,1);centered(graphics,category.icon,motion.size()*.75F,0,0,1,1,1,1,1);}
            if(tracker.progress>tracker.max*.9F){float q=motion.elapsed()/(tracker.max*.1F);burst(graphics,random,64*((float)Math.sin(q*Math.PI*2-Math.PI/2)*.25F+.25F));}
            if(tracker.progress<tracker.max*.1F){float q=1-(tracker.progress-partial)/(tracker.max*.1F);burst(graphics,random,32*((float)Math.sin(q*Math.PI*2-Math.PI/2)*.25F+.25F));}graphics.pose().popPose();
        }
    }
    private void burst(GuiGraphics g,Random random,float size){g.pose().mulPose(Axis.ZP.rotationDegrees(-random.nextInt(360)));random.nextInt(1);float green=(189+random.nextInt(67))/255F,blue=(64+random.nextInt(192))/255F;int frame=320+random.nextInt(16);g.flush();RenderSystem.enableBlend();RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);centered(g,PARTICLES,size,frame%64/64F,frame/64/64F,1/64F,1/64F,1,green,blue);g.flush();RenderSystem.defaultBlendFunc();}
    private void centered(GuiGraphics g,ResourceLocation texture,float size,float u,float v,float du,float dv,float r,float green,float b){g.pose().pushPose();g.pose().mulPose(Axis.ZP.rotationDegrees(90));g.pose().scale(size,size,1);g.pose().translate(-.5,-.5,0);g.setColor(r,green,b,1);g.blit(texture,0,0,1,1,u*1024,v*1024,(int)(du*1024),(int)(dv*1024),1024,1024);g.setColor(1,1,1,1);g.pose().popPose();}
    private static final class KnowledgeGainTracker {final EnumKnowledgeType type;final String category;final int max;final long seed;int progress;boolean sparks;KnowledgeGainTracker(EnumKnowledgeType type,String category,int progress,long seed){this.type=type;this.category=category;this.progress=this.max=progress;this.seed=seed;}}
}
