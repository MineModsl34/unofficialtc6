package thaumcraft.client;
import java.util.*;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumcraft.api.golems.*;
import thaumcraft.api.golems.seals.ISealConfigArea;
import thaumcraft.common.golems.seals.*;
/** Actual held-bell/seal visibility, animated atlas icons, source fade/color/area/corner geometry.
 * Native shader batches replace GL1 state; crouching keeps source through-wall visibility.
 */
@EventBusSubscriber(modid="thaumcraft",value=Dist.CLIENT)
public final class SealClientEvents {
 private static final ResourceLocation MIDDLE=ResourceLocation.parse("thaumcraft:textures/misc/seal_area.png"),FRAME=ResourceLocation.parse("thaumcraft:textures/misc/frame_corner.png");
 private static final Map<String,RenderType> TYPES=new HashMap<>();
 private static final Direction[][] FACES={{Direction.DOWN,Direction.NORTH,Direction.WEST},{Direction.UP,Direction.NORTH,Direction.WEST},{Direction.DOWN,Direction.NORTH,Direction.EAST},{Direction.UP,Direction.NORTH,Direction.EAST},{Direction.DOWN,Direction.SOUTH,Direction.EAST},{Direction.UP,Direction.SOUTH,Direction.EAST},{Direction.DOWN,Direction.SOUTH,Direction.WEST},{Direction.UP,Direction.SOUTH,Direction.WEST}};
 private static final int[][] ROTATIONS={{0,270,0},{270,180,270},{90,0,90},{180,90,180},{180,180,0},{90,270,270},{270,90,90},{0,0,180}};
 private SealClientEvents(){}
 public static void clearRenderCache(){TYPES.clear();}
 private static RenderType type(ResourceLocation texture,boolean through){return TYPES.computeIfAbsent(texture+"|"+through,key->RenderType.create("tc_seal",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,true,RenderType.CompositeState.builder().setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER).setTextureState(new RenderStateShard.TextureStateShard(texture,false,false)).setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY).setCullState(RenderStateShard.NO_CULL).setLightmapState(RenderStateShard.LIGHTMAP).setOverlayState(RenderStateShard.OVERLAY).setWriteMaskState(RenderStateShard.COLOR_WRITE).setDepthTestState(through?RenderStateShard.NO_DEPTH_TEST:RenderStateShard.LEQUAL_DEPTH_TEST).createCompositeState(false)));}
 @SubscribeEvent public static void render(RenderLevelStageEvent event){if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;var mc=Minecraft.getInstance();var player=mc.player;if(player==null||mc.level==null||!(player.getMainHandItem().getItem() instanceof ISealDisplayer)&&!(player.getOffhandItem().getItem() instanceof ISealDisplayer))return;
  var poses=event.getPoseStack();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();boolean through=player.isShiftKeyDown();float tick=event.getPartialTick().getGameTimeDeltaPartialTick(false);var used=new HashSet<RenderType>();poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);
  for(var seal:SealHandler.client(mc.level.dimension()).values()){double distance=player.distanceToSqr(Vec3.atCenterOf(seal.sealPos.pos));if(distance>256)continue;float alpha=1-(float)(distance/256);var pos=seal.sealPos.pos;var face=seal.sealPos.face;boolean inactive=seal.isStoppedByRedstone(mc.level);if(inactive&&mc.level.random.nextFloat()<tick/12F){thaumcraft.client.fx.FXDispatcher.INSTANCE.spark(pos.getX()+.5+face.getStepX()*.66,pos.getY()+.5+face.getStepY()*.66,pos.getZ()+.5+face.getStepZ()*.66,2,.8F-mc.level.random.nextFloat()*.2F,0,0,1);inactive=false;}var icon=mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(seal.seal.getSealIcon());var atlas=type(InventoryMenu.BLOCK_ATLAS,through);used.add(atlas);var out=buffers.getBuffer(atlas);poses.pushPose();poses.translate(pos.getX()+.5+face.getStepX()*.55,pos.getY()+.5+face.getStepY()*.55,pos.getZ()+.5+face.getStepZ()*.55);orient(poses,face);float shade=inactive?.5F:1F;quad(poses,out,.5F,shade,shade,shade,alpha,icon.getU0(),icon.getV0(),icon.getU1(),icon.getV1());if(!through)thickness(poses,out,icon,shade,alpha);poses.popPose();
   float r,g,b;if(seal.getColor()>0){int rgb=DyeColor.byId(seal.getColor()-1).getTextureDiffuseColor();r=(rgb>>16&255)/255F;g=(rgb>>8&255)/255F;b=(rgb&255)/255F;}else{r=.7F+(float)Math.sin((player.tickCount+tick+pos.getX())/4)*.1F;g=.7F+(float)Math.sin((player.tickCount+tick+pos.getY())/5)*.1F;b=.7F+(float)Math.sin((player.tickCount+tick+pos.getZ())/6)*.1F;}
   var middle=type(MIDDLE,through);used.add(middle);poses.pushPose();poses.translate(pos.getX()+.5+face.getStepX()*.51,pos.getY()+.5+face.getStepY()*.51,pos.getZ()+.5+face.getStepZ()*.51);orient(poses,face);poses.mulPose(Axis.ZP.rotationDegrees(player.tickCount%360+tick));quad(poses,buffers.getBuffer(middle),.9F,r,g,b,alpha*.8F,0,0,1,1);poses.popPose();
   if(seal.seal instanceof ISealConfigArea){var area=GolemHelper.getBoundsForArea(seal);double[][] corners={{area.minX,area.minY,area.minZ},{area.minX,area.maxY-1,area.minZ},{area.maxX-1,area.minY,area.minZ},{area.maxX-1,area.maxY-1,area.minZ},{area.maxX-1,area.minY,area.maxZ-1},{area.maxX-1,area.maxY-1,area.maxZ-1},{area.minX,area.minY,area.maxZ-1},{area.minX,area.maxY-1,area.maxZ-1}};var frame=type(FRAME,through);used.add(frame);for(int q=0;q<8;q++)for(int w=0;w<3;w++){var f=FACES[q][w];poses.pushPose();poses.translate(corners[q][0]+.5+f.getStepX()*.49,corners[q][1]+.5+f.getStepY()*.49,corners[q][2]+.5+f.getStepZ()*.49);orient(poses,f);poses.mulPose(Axis.ZP.rotationDegrees(ROTATIONS[q][w]-90));quad(poses,buffers.getBuffer(frame),1,r,g,b,alpha*.7F,0,0,1,1);poses.popPose();}}
  }poses.popPose();for(var render:used)buffers.endBatch(render);
 }
 private static void orient(PoseStack p,Direction face){switch(face){case UP->p.mulPose(Axis.XP.rotationDegrees(-90));case DOWN->p.mulPose(Axis.XP.rotationDegrees(90));case NORTH->p.mulPose(Axis.YP.rotationDegrees(180));case EAST->p.mulPose(Axis.YP.rotationDegrees(90));case WEST->p.mulPose(Axis.YP.rotationDegrees(-90));default->{}}}
 private static void quad(PoseStack p,VertexConsumer out,float size,float r,float g,float b,float alpha,float u0,float v0,float u1,float v1){float h=size/2;vertex(out,p.last(),-h,-h,0,r,g,b,alpha,u0,v1,0,0,1);vertex(out,p.last(),h,-h,0,r,g,b,alpha,u1,v1,0,0,1);vertex(out,p.last(),h,h,0,r,g,b,alpha,u1,v0,0,0,1);vertex(out,p.last(),-h,h,0,r,g,b,alpha,u0,v0,0,0,1);}
 /** Same pixel-strip extrusion as source renderItemIn2D, scaled .5 × source .1 depth. */
 private static void thickness(PoseStack poses,VertexConsumer out,TextureAtlasSprite sprite,float shade,float alpha){poses.pushPose();poses.translate(0,0,-.05);quad(poses,out,.5F,shade,shade,shade,alpha,sprite.getU0(),sprite.getV0(),sprite.getU1(),sprite.getV1());poses.popPose();var p=poses.last();int width=sprite.contents().width(),height=sprite.contents().height();for(int i=0;i<width;i++){float x=i*.5F/width-.25F,x2=(i+1)*.5F/width-.25F,u=sprite.getU((i+.5F)/width);for(float xx:new float[]{x,x2}){vertex(out,p,xx,-.25F,-.05F,shade,shade,shade,alpha,u,sprite.getV1(),1,0,0);vertex(out,p,xx,-.25F,0,shade,shade,shade,alpha,u,sprite.getV1(),1,0,0);vertex(out,p,xx,.25F,0,shade,shade,shade,alpha,u,sprite.getV0(),1,0,0);vertex(out,p,xx,.25F,-.05F,shade,shade,shade,alpha,u,sprite.getV0(),1,0,0);}}for(int i=0;i<height;i++){float y=i*.5F/height-.25F,y2=(i+1)*.5F/height-.25F,v=sprite.getV(1-(i+.5F)/height);for(float yy:new float[]{y,y2}){vertex(out,p,-.25F,yy,0,shade,shade,shade,alpha,sprite.getU0(),v,0,1,0);vertex(out,p,-.25F,yy,-.05F,shade,shade,shade,alpha,sprite.getU0(),v,0,1,0);vertex(out,p,.25F,yy,-.05F,shade,shade,shade,alpha,sprite.getU1(),v,0,1,0);vertex(out,p,.25F,yy,0,shade,shade,shade,alpha,sprite.getU1(),v,0,1,0);}}}
 private static void vertex(VertexConsumer out,PoseStack.Pose p,float x,float y,float z,float r,float g,float b,float a,float u,float v,float nx,float ny,float nz){out.addVertex(p,x,y,z).setColor(r,g,b,a).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.pack(12,12)).setNormal(p,nx,ny,nz);}
}
