package thaumcraft.api.aspects;

import net.neoforged.bus.api.Event;

/** Posted on NeoForge.EVENT_BUS when addons may register item aspects. */
public class AspectRegistryEvent extends Event {
    public AspectEventProxy register;
}
