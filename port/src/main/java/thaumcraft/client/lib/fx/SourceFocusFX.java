package thaumcraft.client.lib.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import thaumcraft.client.fx.FXDispatcher;
import thaumcraft.client.fx.ParticleEngine;
import thaumcraft.client.fx.particles.FXGeneric;

/** Literal BETA26 renderParticleFX bodies; only World/random and particle infrastructure are adapted. */
public final class SourceFocusFX {
    private SourceFocusFX() {}
    public static void render(ClientLevel world,String key,double x,double y,double z,double vx,double vy,double vz) {
        double posX=x,posY=y,posZ=z,motionX=vx,motionY=vy,motionZ=vz;
        switch(key) {
            case "thaumcraft.AIR" -> {

        FXDispatcher.GenPart pp = new FXDispatcher.GenPart();
        pp.grav = -0.1F;
        pp.age = 20 + world.random.nextInt(10);
        pp.alpha = new float[]{0.5F, 0.0F};
        pp.grid = 32;
        pp.partStart = 337;
        pp.partInc = 1;
        pp.partNum = 5;
        pp.slowDown = 0.75;
        pp.rot = (float)world.random.nextGaussian() / 2.0F;
        float s = (float)(2.0 + world.random.nextGaussian() * 0.5);
        pp.scale = new float[]{s, s * 2.0F};
        FXDispatcher.INSTANCE.drawGenericParticles(posX, posY, posZ, motionX, motionY, motionZ, pp);
    
            }
            case "thaumcraft.BREAK" -> {

        FXGeneric fb = new FXGeneric(world, posX, posY, posZ, motionX, motionY, motionZ);
        fb.setMaxAge(6 + world.random.nextInt(6));
        int q = world.random.nextInt(4);
        fb.setParticles(704 + q * 3, 3, 1);
        fb.setSlowDown(0.8);
        fb.setScale((float)(1.7F + world.random.nextGaussian() * 0.3F));
        ParticleEngine.addEffect(world, fb);
    
            }
            case "thaumcraft.CURSE" -> {

        FXGeneric fb = new FXGeneric(world, posX, posY, posZ, motionX, motionY, motionZ);
        fb.setMaxAge(8);
        fb.setRBGColorF(0.41F + world.random.nextFloat() * 0.2F, 0.0F, 0.019F + world.random.nextFloat() * 0.2F);
        fb.setAlphaF(0.0F, world.random.nextFloat(), world.random.nextFloat(), world.random.nextFloat(), 0.0F);
        fb.setGridSize(16);
        fb.setParticles(72 + world.random.nextInt(4), 1, 1);
        fb.setScale(2.0F + world.random.nextFloat() * 4.0F);
        fb.setLoop(false);
        fb.setSlowDown(0.9);
        fb.setGravity(0.0F);
        fb.setRotationSpeed(world.random.nextFloat(), 0.0F);
        ParticleEngine.addEffectWithDelay(world, fb, world.random.nextInt(4));
    
            }
            case "thaumcraft.EARTH" -> {

        FXDispatcher.GenPart pp = new FXDispatcher.GenPart();
        pp.grav = 0.4F;
        pp.layer = 1;
        pp.age = 20 + world.random.nextInt(10);
        pp.alpha = new float[]{1.0F, 0.0F};
        pp.partStart = 75 + world.random.nextInt(4);
        pp.partInc = 1;
        pp.partNum = 1;
        pp.slowDown = 0.9;
        pp.rot = (float)world.random.nextGaussian();
        float s = (float)(1.0 + world.random.nextGaussian() * 0.2F);
        pp.scale = new float[]{s, s / 2.0F};
        FXDispatcher.INSTANCE.drawGenericParticles(posX, posY, posZ, motionX, motionY, motionZ, pp);
    
            }
            case "thaumcraft.EXCHANGE" -> {

        FXGeneric fb = new FXGeneric(
            world,
            x,
            y,
            z,
            vx + world.random.nextGaussian() * 0.01,
            vy + world.random.nextGaussian() * 0.01,
            vz + world.random.nextGaussian() * 0.01
        );
        fb.setMaxAge(9);
        fb.setRBGColorF(
            0.25F + world.random.nextFloat() * 0.25F, 0.25F + world.random.nextFloat() * 0.25F, 0.25F + world.random.nextFloat() * 0.25F
        );
        fb.setAlphaF(0.0F, 0.6F, 0.6F, 0.0F);
        fb.setGridSize(64);
        fb.setParticles(448, 9, 1);
        fb.setScale(0.5F, 0.25F);
        fb.setGravity((float)(world.random.nextGaussian() * 0.01F));
        fb.setRandomMovementScale(0.0025F, 0.0025F, 0.0025F);
        ParticleEngine.addEffect(world, fb);
    
            }
            case "thaumcraft.FIRE" -> {

        FXDispatcher.GenPart pp = new FXDispatcher.GenPart();
        pp.grav = -0.2F;
        pp.age = 10;
        pp.alpha = new float[]{0.7F};
        pp.partStart = 640;
        pp.partInc = 1;
        pp.partNum = 10;
        pp.slowDown = 0.75;
        pp.scale = new float[]{(float)(1.5 + world.random.nextGaussian() * 0.2F)};
        FXDispatcher.INSTANCE.drawGenericParticles(posX, posY, posZ, motionX, motionY, motionZ, pp);
    
            }
            case "thaumcraft.FLUX" -> {

        FXGeneric fb = new FXGeneric(
            world,
            x,
            y,
            z,
            vx + world.random.nextGaussian() * 0.01,
            vy + world.random.nextGaussian() * 0.01,
            vz + world.random.nextGaussian() * 0.01
        );
        fb.setMaxAge((int)(15.0F + 10.0F * world.random.nextFloat()));
        fb.setRBGColorF(0.25F + world.random.nextFloat() * 0.25F, 0.0F, 0.25F + world.random.nextFloat() * 0.25F);
        fb.setAlphaF(0.0F, 1.0F, 1.0F, 0.0F);
        fb.setGridSize(64);
        fb.setParticles(128, 14, 1);
        fb.setScale(2.0F + world.random.nextFloat(), 0.25F + world.random.nextFloat() * 0.25F);
        fb.setLoop(true);
        fb.setSlowDown(0.9);
        fb.setGravity((float)(world.random.nextGaussian() * 0.1F));
        fb.setRandomMovementScale(0.0125F, 0.0125F, 0.0125F);
        fb.setRotationSpeed((float)world.random.nextGaussian());
        ParticleEngine.addEffectWithDelay(world, fb, world.random.nextInt(4));
    
            }
            case "thaumcraft.FROST" -> {

        FXGeneric fb = new FXGeneric(world, posX, posY, posZ, motionX, motionY, motionZ);
        fb.setMaxAge(40 + world.random.nextInt(40));
        fb.setAlphaF(1.0F, 0.0F);
        fb.setParticles(8, 1, 1);
        fb.setGravity(0.033F);
        fb.setSlowDown(0.8);
        fb.setRandomMovementScale(0.0025F, 1.0E-4F, 0.0025F);
        fb.setScale((float)(0.7F + world.random.nextGaussian() * 0.3F));
        fb.setRotationSpeed(world.random.nextFloat() * 3.0F, (float)world.random.nextGaussian() / 4.0F);
        ParticleEngine.addEffectWithDelay(world, fb, 0);
    
            }
            case "thaumcraft.HEAL" -> {

        FXGeneric fb = new FXGeneric(
            world,
            x,
            y,
            z,
            vx + world.random.nextGaussian() * 0.01,
            vy + world.random.nextGaussian() * 0.01,
            vz + world.random.nextGaussian() * 0.01
        );
        fb.setMaxAge((int)(10.0F + 10.0F * world.random.nextFloat()));
        fb.setRBGColorF(1.0F, 1.0F, 1.0F);
        fb.setAlphaF(0.0F, 0.7F, 0.7F, 0.0F);
        fb.setGridSize(64);
        fb.setParticles(0, 1, 1);
        fb.setScale(world.random.nextFloat() * 2.0F, world.random.nextFloat());
        fb.setSlowDown(0.8);
        fb.setGravity((float)(world.random.nextGaussian() * 0.1F));
        fb.setRandomMovementScale(0.0125F, 0.0125F, 0.0125F);
        fb.setRotationSpeed((float)world.random.nextGaussian());
        ParticleEngine.addEffectWithDelay(world, fb, world.random.nextInt(4));
    
            }
            case "thaumcraft.RIFT" -> {

        FXGeneric fb = new FXGeneric(world, posX, posY, posZ, motionX, motionY, motionZ);
        fb.setMaxAge(16 + world.random.nextInt(16));
        fb.setParticles(384 + world.random.nextInt(16), 1, 1);
        fb.setSlowDown(0.75);
        fb.setAlphaF(1.0F, 0.0F);
        fb.setScale((float)(0.7F + world.random.nextGaussian() * 0.3F));
        fb.setRBGColorF(0.25F, 0.25F, 1.0F);
        fb.setRandomMovementScale(0.01F, 0.01F, 0.01F);
        ParticleEngine.addEffectWithDelay(world, fb, 0);
    
            }
            default -> {} // Unknown/non-effect focus keys had no source particles.
        }
    }
}

