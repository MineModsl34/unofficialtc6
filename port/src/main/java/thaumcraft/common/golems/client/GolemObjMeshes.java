package thaumcraft.common.golems.client;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.client.renderers.models.entity.ConstructObjMesh;

/** Real source OBJ faces/groups and modern resource-pack reload, not generated cube surrogates. */
public final class GolemObjMeshes {
    private static final ConcurrentHashMap<ResourceLocation,ConstructObjMesh> CACHE = new ConcurrentHashMap<>();
    private GolemObjMeshes() {}
    public static ConstructObjMesh get(ResourceLocation location) {
        return CACHE.computeIfAbsent(location,key -> {
            try (var reader = Minecraft.getInstance().getResourceManager().getResource(key).orElseThrow(() -> new IOException("Missing golem OBJ " + key)).openAsReader()) { return ConstructObjMesh.read(reader); }
            catch (IOException invalid) { throw new IllegalStateException("Invalid actual golem model " + key,invalid); }
        });
    }
    public static void clear() { CACHE.clear(); }
}
