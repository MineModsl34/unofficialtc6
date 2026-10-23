package thaumcraft.client.lib.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.common.lib.network.fx.PacketSourceFX;
import thaumcraft.common.tiles.crafting.TileCrucible;
import thaumcraft.common.tiles.devices.TileMirror;

/** Device source animations, kept on the client and reached through the common sided bridge. */
final class SourceDeviceFX {
    private SourceDeviceFX() {}
    static void render(ClientLevel world,PacketSourceFX p){
        var fx=FXDispatcher.INSTANCE;var random=world.random;BlockPos pos=BlockPos.containing(p.x(),p.y(),p.z());
        switch(p.key()){
            case "sparkle_hole" -> fx.sparkle((float)p.x(),(float)p.y(),(float)p.z(),.25F,.25F,1);
            case "condenser" -> fx.spark(p.x(),p.y(),p.z(),4.5F+random.nextFloat(),.33F+random.nextFloat()*.66F,.33F+random.nextFloat()*.66F,.33F+random.nextFloat()*.66F,.8F);
            case "recharge" -> fx.visSparkle(pos.getX()+random.nextInt(3)-random.nextInt(3),pos.getY()+1+random.nextInt(3),pos.getZ()+random.nextInt(3)-random.nextInt(3),pos.getX(),pos.getY()+1,pos.getZ(),p.extra());
            case "sprayer" -> {
                Direction face=Direction.from3DDataValue(p.extra());float x=.1F-random.nextFloat()*.2F,z=.1F-random.nextFloat()*.2F,y=.1F-random.nextFloat()*.2F;
                float vx=(float)(random.nextGaussian()*.06),vz=(float)(random.nextGaussian()*.06),vy=(float)(random.nextGaussian()*.06);
                fx.drawVentParticles2(pos.getX()+.5F+x+face.getStepX()/2F,pos.getY()+.5F+y+face.getStepY()/2F,pos.getZ()+.5F+z+face.getStepZ()/2F,vx+face.getStepX()*.25,vy+face.getStepY()*.25,vz+face.getStepZ()*.25,p.color(),4);
            }
            case "water" -> fx.waterTrailFx(pos,BlockPos.containing(p.tx(),p.ty(),p.tz()),p.extra(),2650102,.1F);
            case "mirror" -> {
                if(world.getBlockEntity(pos) instanceof TileMirror mirror){var face=mirror.facing();
                    double x=pos.getX()+.33+random.nextFloat()*.33F-face.getStepX()/2D,y=pos.getY()+.33+random.nextFloat()*.33F-face.getStepY()/2D,z=pos.getZ()+.33+random.nextFloat()*.33F-face.getStepZ()/2D;
                    fx.drawWispyMotes(x,y,z,face.getStepX()/50D+random.nextGaussian()*.01,face.getStepY()/50D+random.nextGaussian()*.01,face.getStepZ()/50D+random.nextGaussian()*.01,10+random.nextInt(21),random.nextFloat()/3,0,random.nextFloat()/2,(float)(random.nextGaussian()*.01));
                }
            }
            case "crucible" -> {if(world.getBlockEntity(pos) instanceof TileCrucible tile)crucible(world,pos,tile);}
            case "crucible_event" -> {
                if(world.getBlockEntity(pos) instanceof TileCrucible tile){
                    if(p.color()==99)fx.drawBamf(pos.getX()+.5,pos.getY()+1.25F,pos.getZ()+.5,true,true,Direction.UP);
                    else if(p.color()==1)fx.drawBamf(pos.above(),true,true,Direction.UP);
                    else if(p.color()==2)for(int n=0;n<10;n++)fx.crucibleBoil(pos,tile,p.extra());
                }
            }
            case "paving" -> paving(world,pos,p.extra());
            case "rift_blast" -> fx.drawCurlyWisp(p.x(),p.y(),p.z(),0,0,0,Float.intBitsToFloat(p.color()),(float)p.tx(),(float)p.ty(),(float)p.tz(),.5F,null,1,p.extra(),0);
            default -> {if(!SourceToolFX.render(world,p)&&!SourceProjectileFX.render(world,p))SourceMiscFX.render(world,p);}
        }
    }
    private static void crucible(ClientLevel world,BlockPos pos,TileCrucible tile){
        var fx=FXDispatcher.INSTANCE;var random=world.random;
        if(tile.heat>150){fx.crucibleFroth(pos.getX()+.2F+random.nextFloat()*.6F,pos.getY()+tile.getFluidHeight(),pos.getZ()+.2F+random.nextFloat()*.6F);
            if(tile.aspects.visSize()>500)for(int n=0;n<2;n++){
                fx.crucibleFrothDown(pos.getX(),pos.getY()+1,pos.getZ()+random.nextFloat());fx.crucibleFrothDown(pos.getX()+1,pos.getY()+1,pos.getZ()+random.nextFloat());
                fx.crucibleFrothDown(pos.getX()+random.nextFloat(),pos.getY()+1,pos.getZ());fx.crucibleFrothDown(pos.getX()+random.nextFloat(),pos.getY()+1,pos.getZ()+1);
            }
        }
        if(random.nextInt(6)==0&&tile.aspects.size()>0){int color=tile.aspects.getAspects()[random.nextInt(tile.aspects.size())].getColor();int x=5+random.nextInt(22),z=5+random.nextInt(22);
            random.nextInt(10); // Original stores an unused delay, but still consumes this RNG draw.
            fx.crucibleBubble(pos.getX()+x/32F+.015625F,pos.getY()+.05F+tile.getFluidHeight(),pos.getZ()+z/32F+.015625F,(color>>16&255)/255F,(color>>8&255)/255F,(color&255)/255F);
        }
    }
    private static void paving(ClientLevel world,BlockPos pos,int branch){
        var fx=FXDispatcher.INSTANCE;var random=world.random;
        if(branch==1)for(int n=0;n<4;n++)fx.blockRunes(pos.getX(),pos.getY()+.7F,pos.getZ(),.2F+random.nextFloat()*.4F,random.nextFloat()*.3F,.8F+random.nextFloat()*.2F,20,-.02F);
        else if(branch==2)for(int n=0;n<6;n++)fx.blockRunes(pos.getX(),pos.getY()+.7F,pos.getZ(),.9F+random.nextFloat()*.1F,random.nextFloat()*.3F,random.nextFloat()*.3F,24,-.02F);
        else for(var entity:world.getEntities((net.minecraft.world.entity.Entity)null,new net.minecraft.world.phys.AABB(pos).inflate(1)))
            if(entity instanceof net.minecraft.world.entity.LivingEntity&&!(entity instanceof net.minecraft.world.entity.player.Player)){
                fx.blockRunes(pos.getX(),pos.getY()+.6F+random.nextFloat()*Math.max(.8F,entity.getEyeHeight()),pos.getZ(),.6F+random.nextFloat()*.4F,0,.3F+random.nextFloat()*.7F,20,0);break;
            }
    }
}
