package thaumcraft.client.renderers.entity;

/** Source special item glow envelope, independent of a client world. */
public final class SpecialItemGeometry {
    private SpecialItemGeometry(){}
    public static float growth(int age){return Math.min(age,10)/10F/30F;}
    public static int rays(boolean fancy){return fancy?10:5;}
}
