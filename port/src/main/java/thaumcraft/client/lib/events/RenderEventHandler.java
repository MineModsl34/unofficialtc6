package thaumcraft.client.lib.events;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Axis;
import java.util.HashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import thaumcraft.api.aspects.*;
import thaumcraft.api.items.IGogglesDisplayExtended;
import thaumcraft.client.lib.ClientThaumometerHooks;
import thaumcraft.client.lib.UtilsFX;
import thaumcraft.common.blocks.devices.BlockWorldDevice;
import thaumcraft.common.config.ModConfig;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;
import thaumcraft.common.lib.events.WearableEvents;
import thaumcraft.common.tiles.devices.TileSignalDevice;

/** Source aspect billboards, goggles text/note/relay readouts, tooltip tags and reset lifecycle. */
@EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class RenderEventHandler {
    public static final HudHandler hudHandler=new HudHandler();
    public static final WandRenderingHandler wandHandler=new WandRenderingHandler();
    public static float tagscale;
    private RenderEventHandler() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.level!=null&&!mc.isPaused())hudHandler.tick();}
    @SubscribeEvent public static void livingTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event){var mc=Minecraft.getInstance();if(event.getEntity() instanceof net.minecraft.world.entity.LivingEntity mob&&mob.level().isClientSide&&mob.level()==mc.level&&mob.isAlive()&&thaumcraft.common.entities.monster.mods.ChampionSystem.sourceCreature(mob))ChampionVisuals.showFX(mob,thaumcraft.common.entities.monster.mods.ChampionSystem.modifier(mob));}
    @SubscribeEvent public static void highlight(RenderHighlightEvent.Block event){var mc=Minecraft.getInstance();if(mc.player==null)return;var stack=mc.player.getMainHandItem();if(!(stack.getItem() instanceof thaumcraft.api.items.IArchitect))stack=mc.player.getOffhandItem();if(stack.getItem() instanceof thaumcraft.api.items.IArchitect architect&&architect.useBlockHighlight(stack)&&wandHandler.hasOverlay())event.setCanceled(true);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){hudHandler.clear();wandHandler.clear();tagscale=0;UtilsFX.clear();thaumcraft.client.SealClientEvents.clearRenderCache();}
    @SubscribeEvent public static void knowledgeFrame(RenderFrameEvent.Post event){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||!hudHandler.hasKnowledgeFeedback())return;
        // RenderTick.END source feedback stays above screens, including an open inventory.
        com.mojang.blaze3d.systems.RenderSystem.backupProjectionMatrix();var model=com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();model.pushMatrix();
        try{com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new org.joml.Matrix4f().setOrtho(0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0,1000,3000),com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);model.identity().translate(0,0,-2000);com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();var graphics=new GuiGraphics(mc,mc.renderBuffers().bufferSource());hudHandler.renderKnowledgeFeedback(graphics,event.getPartialTick().getGameTimeDeltaPartialTick(false));graphics.flush();}
        finally{model.popMatrix();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();com.mojang.blaze3d.systems.RenderSystem.restoreProjectionMatrix();}
    }
    @SubscribeEvent public static void tooltip(RenderTooltipEvent.GatherComponents event){var mc=Minecraft.getInstance();if(!(mc.screen instanceof AbstractContainerScreen<?>)||mc.player==null||!HudGeometry.showTags(Screen.hasShiftDown(),ModConfig.CONFIG_GRAPHICS.showTags)||mc.mouseHandler.isMouseGrabbed()||event.getItemStack().isEmpty())return;
        AspectList tags=ThaumcraftCraftingManager.getObjectTags(event.getItemStack());if(tags!=null&&tags.size()>0)event.getTooltipElements().add(Either.right(new AspectTooltip(tags.copy())));
    }
    public record AspectTooltip(AspectList tags) implements TooltipComponent {}
    public static final class AspectTooltipRenderer implements ClientTooltipComponent {
        private final AspectList tags;public AspectTooltipRenderer(AspectTooltip value){tags=value.tags();}
        @Override public int getHeight(){return 18;}
        @Override public int getWidth(Font font){return tags.size()*18;}
        @Override public void renderImage(Font font,int x,int y,GuiGraphics graphics){int index=0;for(Aspect aspect:tags.getAspectsSortedByAmount())UtilsFX.drawTag(graphics,x+index++*18,y,aspect,tags.getAmount(aspect));}
    }
    @SubscribeEvent public static void renderLast(RenderLevelStageEvent event){if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);UtilsFX.sysPartialTicks=partial;if(tagscale>0)tagscale-=.005F;
        var poses=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();var used=new HashSet<RenderType>();Vec3 camera=event.getCamera().getPosition();poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);
        if(mc.hitResult instanceof BlockHitResult hit&&mc.level.hasChunkAt(hit.getBlockPos())){
            BlockPos pos=hit.getBlockPos();var tile=mc.level.getBlockEntity(pos);var state=mc.level.getBlockState(pos);
            if(tile instanceof TileSignalDevice relay&&relay.kind()==BlockWorldDevice.Kind.RELAY){Vec3 local=hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)).subtract(.5,0,.5);float angle=switch(relay.facing()){case WEST->90;case NORTH->180;case EAST->270;default->0;};local=local.yRot((float)Math.toRadians(angle));String value=local.x>=-.375&&local.x<=-.125&&local.z>=-.375&&local.z<=-.125?"Out: "+relay.out:local.x>=-.125&&local.x<=.125&&local.z>=.125&&local.z<=.375?"In: "+relay.in:null;if(value!=null)drawTextInAir(poses,buffers,pos.getX()+.5,pos.getY()+.8,pos.getZ()+.5,value);}
            if(WearableEvents.hasGoggles(mc.player)){
                IGogglesDisplayExtended text=tile instanceof IGogglesDisplayExtended display?display:state.getBlock() instanceof IGogglesDisplayExtended display?display:null;
                if(text!=null){String[] lines=text.getIGogglesText();Vec3 offset=text.getIGogglesTextOffset();if(lines!=null)for(int i=0;i<Math.min(256,lines.length);i++)if(lines[i]!=null){double dy=(i-lines.length/2F)/5.5F;if(tile instanceof IGogglesDisplayExtended)dy=-dy;drawTextInAir(poses,buffers,pos.getX()+.5+offset.x,pos.getY()+.5+offset.y+dy,pos.getZ()+.5+offset.z,lines[i]);}}
                int note=-1;if(state.is(Blocks.NOTE_BLOCK))note=state.getValue(NoteBlock.NOTE);else if(tile instanceof TileSignalDevice signal&&(signal.kind()==BlockWorldDevice.Kind.EAR||signal.kind()==BlockWorldDevice.Kind.EAR_TOGGLE))note=signal.note;
                else if(tile instanceof IAspectContainer container){AspectList aspects=container.getAspects();if(aspects!=null&&aspects.size()>0){tagscale=HudGeometry.tagScale(tagscale,.3F);boolean space=mc.level.isEmptyBlock(pos.above());drawTagsOnContainer(poses,buffers,used,pos.getX()+.5,pos.getY()+.5+(space?.4:0),pos.getZ()+.5,aspects,space?Direction.UP:hit.getDirection());}}
                // Modern note state/ear BE are already owner-synced; old PacketNote refresh is unnecessary.
                if(note>=0)drawTextInAir(poses,buffers,pos.getX()+.5,pos.getY()+1.5,pos.getZ()+.5,"Note: "+note);
            }
        }
        var target=ClientThaumometerHooks.currentTarget();if(target!=null){var aspects=AspectHelper.getEntityAspects(target);if(aspects!=null&&aspects.size()>0){tagscale=HudGeometry.tagScale(tagscale,.5F);Vec3 point=target.getPosition(partial);drawTagsOnContainer(poses,buffers,used,point.x,point.y+target.getBbHeight()+.5,point.z,aspects,null);}}
        wandHandler.render(poses,buffers,used,partial);poses.popPose();for(RenderType type:used)buffers.endBatch(type);
    }
    public static void drawTextInAir(PoseStack poses,MultiBufferSource buffers,double x,double y,double z,String text){var mc=Minecraft.getInstance();Vec3 camera=mc.gameRenderer.getMainCamera().getPosition();float yaw=(float)Math.toDegrees(Math.atan2(camera.x-x,camera.z-z));poses.pushPose();poses.translate(x,y,z);poses.mulPose(Axis.YP.rotationDegrees(yaw+180));poses.mulPose(Axis.ZP.rotationDegrees(180));poses.scale(.0125F,.0125F,.0125F);mc.font.drawInBatch(text,1-mc.font.width(text)/2F,1,-1,true,poses.last().pose(),buffers,Font.DisplayMode.SEE_THROUGH,0,LightTexture.pack(13,13));poses.popPose();}
    public static void drawTagsOnContainer(PoseStack poses,MultiBufferSource buffers,java.util.Set<RenderType> used,double x,double y,double z,AspectList tags,Direction direction){var mc=Minecraft.getInstance();Vec3 camera=mc.gameRenderer.getMainCamera().getPosition();int current=0,row=0,left=tags.size();for(Aspect aspect:tags.getAspects()){
        if(current>=5){current=0;row++;left-=5;}int div=Math.min(left,5);float shift=(current-div/2F+.5F)*tagscale*4*tagscale;
        int c=aspect.getColor();RenderType type=UtilsFX.type(aspect.getImage(),false,true);used.add(type);poses.pushPose();poses.translate(x+(direction==null?0:tagscale*2*direction.getStepX()),y+row*tagscale*1.05F+(direction==null?0:tagscale*2*direction.getStepY()),z+(direction==null?0:tagscale*2*direction.getStepZ()));poses.mulPose(Axis.YP.rotationDegrees((float)Math.toDegrees(Math.atan2(camera.x-x,camera.z-z))+180));poses.translate(shift,0,0);poses.mulPose(Axis.ZP.rotationDegrees(90));poses.scale(tagscale,tagscale,tagscale);UtilsFX.renderQuadCentered(poses,buffers.getBuffer(type),1,(c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F,.75F,LightTexture.pack(13,13),0,0,1,1);
        int amount=tags.getAmount(aspect);if(amount>=0){poses.mulPose(Axis.ZP.rotationDegrees(90));poses.scale(.04F,.04F,.04F);poses.translate(0,6,-.1);String text=Integer.toString(amount);int width=mc.font.width(text);mc.font.drawInBatch(text,14-width,1,0xff111111,false,poses.last().pose(),buffers,Font.DisplayMode.SEE_THROUGH,0,LightTexture.pack(13,13));poses.translate(0,0,-.1);mc.font.drawInBatch(text,13-width,0,-1,false,poses.last().pose(),buffers,Font.DisplayMode.SEE_THROUGH,0,LightTexture.pack(13,13));}poses.popPose();current++;
    }}
}
