package thaumcraft.api.research.theorycraft;

/** Source aid contract. Unported objects may be null and cannot be chosen on the server. */
public interface ITheorycraftAid {
    Object getAidObject();
    String[] getCardKeys();
    default String translationKey() { return "tc.theory.aid." + getClass().getSimpleName(); }
}
