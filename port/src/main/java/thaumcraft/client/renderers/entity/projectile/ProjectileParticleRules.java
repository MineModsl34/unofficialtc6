package thaumcraft.client.renderers.entity.projectile;
/** Source RenderNoProjectile admission, kept registry-free for a lightweight boundary test. */
public final class ProjectileParticleRules {
    private ProjectileParticleRules() {}
    public static boolean admit(float coefficient,float previous){float difference=coefficient-previous;if(difference<0)difference++;return difference>.2;}
}
