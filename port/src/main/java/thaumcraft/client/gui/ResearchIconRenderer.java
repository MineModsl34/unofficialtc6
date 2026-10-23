package thaumcraft.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumcraft.client.gui.model.ResearchIconIdentity;
import thaumcraft.client.gui.model.ResearchFocusIcon;
import thaumcraft.common.lib.research.ResearchItemResolver;
import thaumcraft.common.lib.research.catalog.ResearchCatalogSpec.IconToken;

/** Finite proved item inverse + actual texture resources, never an AIR/modern-id fallback.
 * Source icons cycle every1000ms and rectangular texture strips every150ms. Resource image
 * dimensions replace the old GL texture-size query; reload clears this metadata on MAIN.
 * Full common item resolution and finite source focus-node art are read-only. Future
 * unregistered items still fail closed; this renderer does not manufacture item registrations.
 */
public final class ResearchIconRenderer {
    private static final Map<ResourceLocation, Optional<Texture>> TEXTURES = new HashMap<>();
    private record Texture(ResourceLocation id, int width, int height) {}
    private ResearchIconRenderer() {}

    public static void clearTextureMetadata() { TEXTURES.clear(); ResearchRecipeViewer.clearMetadata(); }

    public static IconToken current(List<IconToken> icons, long now) {
        return icons.isEmpty() ? null : icons.get((int) Math.floorMod(now / 1000L, icons.size()));
    }
    /** Resolves actual resource location (including source items/ -> modern item/) without
     * changing a page image descriptor or its native UV coordinate space. Null means absent.
     */
    public static ResourceLocation texturePath(ResourceLocation source) {
        if (source == null) return null;
        return TEXTURES.computeIfAbsent(source, ResearchIconRenderer::loadTexture).map(Texture::id).orElse(null);
    }

    public static boolean draw(GuiGraphics graphics, List<IconToken> icons, int x, int y, boolean dim, long now) {
        IconToken token = current(icons, now);
        if (token == null) return false;
        if (token.focusFallback() != null) return drawFocus(graphics, ResearchFocusIcon.parse(token.focusFallback()), x, y, dim);
        var player = Minecraft.getInstance().player;
        ItemStack resolved = ResearchItemResolver.resolve(token.itemCandidate(), player == null ? null : player.registryAccess());
        if (!resolved.isEmpty()) { graphics.renderItem(resolved, x, y); return true; }
        ResourceLocation itemId = ResearchIconIdentity.resolve(token.itemCandidate());
        if (itemId != null && BuiltInRegistries.ITEM.containsKey(itemId)) {
            var item = BuiltInRegistries.ITEM.get(itemId);
            if (item != Items.AIR) {
                graphics.renderItem(new ItemStack(item), x, y);
                return true;
            }
        }
        var resource = token.resourceFallback();
        if (resource == null || !resource.modernSyntax() || !resource.path().startsWith("textures/")) return false;
        return drawTexture(graphics, ResourceLocation.fromNamespaceAndPath(resource.namespace(), resource.path()), x, y, dim, now);
    }

    private static boolean drawFocus(GuiGraphics graphics, ResearchFocusIcon focus, int x, int y, boolean dim) {
        if (focus == null) return false;
        float light = dim ? 50F / 255F : 220F / 255F;
        graphics.pose().pushPose();
        graphics.pose().translate(x + 8, y + 8, 0);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-90));
        if (focus.backing() != null) {
            graphics.setColor((focus.color() >> 16 & 255) / 255F, (focus.color() >> 8 & 255) / 255F,
                (focus.color() & 255) / 255F, 1F);
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            graphics.blit(focus.backing(), -11, -11, 22, 22, 0, 0, 256, 256, 256, 256);
        }
        graphics.setColor(light, light, light, 1);
        int size = focus.backing() == null ? 24 : 12;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        graphics.blit(focus.icon(), -size / 2, -size / 2, size, size, 0, 0, 256, 256, 256, 256);
        graphics.setColor(1, 1, 1, 1); graphics.pose().popPose();
        return true;
    }

    public static boolean drawTexture(GuiGraphics graphics, ResourceLocation source, int x, int y, boolean dim, long now) {
        if (source == null) return false;
        Optional<Texture> found = TEXTURES.computeIfAbsent(source, ResearchIconRenderer::loadTexture);
        if (found.isEmpty()) return false;
        Texture texture = found.get();
        int frameWidth = texture.width(), frameHeight = texture.height(), u = 0, v = 0;
        if (frameHeight > frameWidth && frameHeight % frameWidth == 0) {
            v = (int) Math.floorMod(now / 150L, frameHeight / frameWidth) * frameWidth;
            frameHeight = frameWidth;
        } else if (frameWidth > frameHeight && frameWidth % frameHeight == 0) {
            u = (int) Math.floorMod(now / 150L, frameWidth / frameHeight) * frameHeight;
            frameWidth = frameHeight;
        }
        if (dim) graphics.setColor(.2F, .2F, .2F, 1F);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        graphics.blit(texture.id(), x, y, 16, 16, (float) u, (float) v, frameWidth, frameHeight, texture.width(), texture.height());
        graphics.setColor(1F, 1F, 1F, 1F);
        return true;
    }

    private static Optional<Texture> loadTexture(ResourceLocation source) {
        var resources = Minecraft.getInstance().getResourceManager();
        ResourceLocation actual = source;
        var resource = resources.getResource(actual);
        if (resource.isEmpty() && source.getNamespace().equals("thaumcraft") && source.getPath().startsWith("textures/items/")) {
            // Imported source bytes live under modern singular item/; no registry substitution.
            actual = source.withPath("textures/item/" + source.getPath().substring("textures/items/".length()));
            resource = resources.getResource(actual);
        }
        if (resource.isEmpty()) return Optional.empty();
        try (var stream = resource.get().open(); var image = NativeImage.read(stream)) {
            return image.getWidth() > 0 && image.getHeight() > 0
                ? Optional.of(new Texture(actual, image.getWidth(), image.getHeight())) : Optional.empty();
        } catch (IOException | RuntimeException unavailable) {
            return Optional.empty();
        }
    }
}
