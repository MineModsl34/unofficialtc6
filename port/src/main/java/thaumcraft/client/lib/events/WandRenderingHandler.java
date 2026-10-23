package thaumcraft.client.lib.events;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.items.IArchitect;
import thaumcraft.client.lib.UtilsFX;

/** Original exposed-face/corner architecture overlay and crossed axis arrows, not wire boxes. */
public final class WandRenderingHandler {
    private static final ResourceLocation CORNER=ResourceLocation.parse("thaumcraft:textures/misc/frame_corner.png"),SIDE=ResourceLocation.parse("thaumcraft:textures/misc/frame_side.png"),ARROWS=ResourceLocation.parse("thaumcraft:textures/misc/architect_arrows.png");
    private static final int[][] MOS={{4,5,6,7},{0,1,2,3},{0,1,4,5},{2,3,6,7},{0,2,4,6},{1,3,5,7}};
    private static final int[][] ROT={{0,90,270,180},{270,180,0,90},{180,90,270,0},{0,270,90,180},{270,180,0,90},{180,270,90,0}};
    private final Set<BlockPos> blocks=new HashSet<>();
    private String sourceKey;
    public void clear(){blocks.clear();sourceKey=null;}
    private void refresh(ItemStack stack,IArchitect architect,BlockHitResult hit){var mc=Minecraft.getInstance();var pos=hit.getBlockPos();String key=pos.getX()+""+pos.getY()+""+pos.getZ()+""+hit.getDirection()+""+mc.player.tickCount/5;if(key.equals(sourceKey))return;sourceKey=key;blocks.clear();var positions=architect.getArchitectBlocks(stack,mc.level,pos,hit.getDirection(),mc.player);if(positions!=null)blocks.addAll(positions);}
    private static ItemStack held(){var player=Minecraft.getInstance().player;if(player==null)return ItemStack.EMPTY;var stack=player.getMainHandItem();return stack.getItem() instanceof IArchitect?stack:player.getOffhandItem();}
    public boolean hasOverlay(){var mc=Minecraft.getInstance();ItemStack stack=held();if(!(stack.getItem() instanceof IArchitect architect)||mc.level==null)return false;var hit=architect.getArchitectMOP(stack,mc.level,mc.player);if(!(hit instanceof BlockHitResult block)||hit.getType()!=HitResult.Type.BLOCK)return false;refresh(stack,architect,block);return !blocks.isEmpty();}
    public void render(PoseStack poses,MultiBufferSource buffers,Set<RenderType> used,float partial){var mc=Minecraft.getInstance();ItemStack stack=held();if(!(stack.getItem() instanceof IArchitect architect)||mc.level==null)return;var hit=architect.getArchitectMOP(stack,mc.level,mc.player);if(!(hit instanceof BlockHitResult target)||hit.getType()!=HitResult.Type.BLOCK)return;
        refresh(stack,architect,target);if(blocks.isEmpty())return;
        var side=UtilsFX.type(SIDE,true,true);var corner=UtilsFX.type(CORNER,true,true);used.add(side);used.add(corner);
        for(BlockPos pos:blocks){boolean[] visible=cornerVisibility(pos,blocks);for(Direction face:Direction.values()){if(blocks.contains(pos.relative(face)))continue;poses.pushPose();poses.translate(pos.getX()+.5+face.getStepX()*.5,pos.getY()+.5+face.getStepY()*.5,pos.getZ()+.5+face.getStepZ()*.5);UtilsFX.orientFace(poses,face);poses.mulPose(Axis.ZP.rotationDegrees(-90));UtilsFX.renderQuadCentered(poses,buffers.getBuffer(side),1,1,1,1,.1F,LightTexture.pack(12,12),0,0,1,1);for(int index=0;index<4;index++)if(visible[MOS[face.ordinal()][index]]){poses.pushPose();poses.mulPose(Axis.ZP.rotationDegrees(ROT[face.ordinal()][index]));UtilsFX.renderQuadCentered(poses,buffers.getBuffer(corner),1,1,1,1,.66F,LightTexture.pack(12,12),0,0,1,1);poses.popPose();}poses.popPose();}}
        boolean x=architect.showAxis(stack,mc.level,mc.player,target.getDirection(),IArchitect.EnumAxis.X),y=architect.showAxis(stack,mc.level,mc.player,target.getDirection(),IArchitect.EnumAxis.Y),z=architect.showAxis(stack,mc.level,mc.player,target.getDirection(),IArchitect.EnumAxis.Z);
        if(!x&&!y&&!z)return;var type=UtilsFX.type(ARROWS,true,true);used.add(type);BlockPos pos=target.getBlockPos();float r=(float)Math.sin(mc.player.tickCount/4F+pos.getX())*.2F+.3F,g=(float)Math.sin(mc.player.tickCount/3F+pos.getY())*.2F+.3F,b=(float)Math.sin(mc.player.tickCount/2F+pos.getZ())*.2F+.8F;poses.pushPose();poses.translate(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);poses.mulPose(Axis.XP.rotationDegrees(90));
        if(z)axis(poses,buffers,type,r,g,b);if(x){poses.pushPose();poses.mulPose(Axis.ZP.rotationDegrees(90));axis(poses,buffers,type,r,g,b);poses.popPose();}if(y){poses.pushPose();poses.mulPose(Axis.XP.rotationDegrees(90));axis(poses,buffers,type,r,g,b);poses.popPose();}poses.popPose();
    }
    private static void axis(PoseStack poses,MultiBufferSource buffers,RenderType type,float r,float g,float b){UtilsFX.renderQuadCentered(poses,buffers.getBuffer(type),1,r,g,b,1,LightTexture.pack(12,12),0,0,1,1);poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(90));UtilsFX.renderQuadCentered(poses,buffers.getBuffer(type),1,r,g,b,1,LightTexture.pack(12,12),0,0,1,1);poses.popPose();}
    public static boolean[] cornerVisibility(BlockPos pos,Set<BlockPos> selected){return new boolean[]{
        !selected.contains(pos.west())&&!selected.contains(pos.north())&&!selected.contains(pos.above()),!selected.contains(pos.east())&&!selected.contains(pos.north())&&!selected.contains(pos.above()),!selected.contains(pos.west())&&!selected.contains(pos.south())&&!selected.contains(pos.above()),!selected.contains(pos.east())&&!selected.contains(pos.south())&&!selected.contains(pos.above()),
        !selected.contains(pos.west())&&!selected.contains(pos.north())&&!selected.contains(pos.below()),!selected.contains(pos.east())&&!selected.contains(pos.north())&&!selected.contains(pos.below()),!selected.contains(pos.west())&&!selected.contains(pos.south())&&!selected.contains(pos.below()),!selected.contains(pos.east())&&!selected.contains(pos.south())&&!selected.contains(pos.below())};}
}
