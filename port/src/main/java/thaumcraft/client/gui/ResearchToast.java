package thaumcraft.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.client.gui.model.ResearchToastOwner;
import thaumcraft.client.lib.ClientResearchPresentation;

/** Source-timed notification with the same cyclic item/texture/focus renderer as the book.
 * Metadata and knowledge are read from the current accepted owner view, not the ambient API.
 */
public final class ResearchToast implements Toast {
    private static final ResourceLocation HUD = ResourceLocation.parse("thaumcraft:textures/gui/hud.png");
    private final String key;
    private final ResearchToastOwner owner;

    public ResearchToast(ResearchEntry entry) {
        key = entry.getKey();
        var minecraft = Minecraft.getInstance();
        var listener = minecraft.getConnection();
        owner = new ResearchToastOwner(listener == null ? null : listener.getConnection(), minecraft.player);
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toasts, long time) {
        var minecraft = toasts.getMinecraft();
        var listener = minecraft.getConnection();
        if (!owner.current(listener == null ? null : listener.getConnection(), minecraft.player)) return Visibility.HIDE;
        var snapshot = ClientResearchPresentation.view();
        var entry = snapshot == null || !snapshot.knows(key) ? null : snapshot.spec().entriesByKey().get(key);
        if (entry == null) return Visibility.HIDE;
        var font = minecraft.font;
        graphics.blit(HUD, 0, 0, 0, 224, 160, 32);
        if (entry.icons() != null && !ResearchIconRenderer.draw(graphics, entry.icons().values(), 6, 8, false, System.currentTimeMillis()))
            graphics.drawString(font, "?", 10, 12, 0xFFCCB997, false);
        graphics.drawString(font, Component.translatable("research.complete"), 30, 7, 10631665, false);
        Component name = Component.translatable(entry.name());
        float scale = Math.min(1.0F, 124.0F / Math.max(1, font.width(name)));
        graphics.pose().pushPose();
        graphics.pose().translate(30, 18, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, name, 0, 0, 16755465, false);
        graphics.pose().popPose();
        return time < 5000L ? Visibility.SHOW : Visibility.HIDE;
    }
}
