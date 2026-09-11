package net.bananemdnsa.historystages.client.editor.widget;

import java.util.List;

import net.bananemdnsa.historystages.client.editor.zone.ZoneMapRenderer;
import net.bananemdnsa.historystages.client.editor.zone.ZoneTerrainSampler;
import net.bananemdnsa.historystages.client.editor.zone.ZoneTerrainTexture;
import net.bananemdnsa.historystages.data.lock.ZoneMapView;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A zone's shapes seen from above, so the author can tell where the thing actually is.
 *
 * <p>Read-only on purpose, and small: this is the thumbnail in the Shapes tab, the full map is a
 * screen of its own. No grid labels and no scale bar either — at this size the numbers would be
 * unreadable and the lines would eat the picture.
 *
 * <p>The arithmetic lives in {@link ZoneMapView} and is tested without the game running; the
 * painting is {@link ZoneMapRenderer}, shared with the full map. Neither of the two can therefore
 * disagree with the other about where a shape sits.
 */
public final class ZoneShapePreview {

    private static final int HINT = 0xFF777777;

    private ZoneShapePreview() {}

    /**
     * @param highlighted index of the shape to pick out, or -1 for none
     * @param playerHere  whether the player stands in the world the zone lives in; false leaves the
     *                    marker off rather than putting it somewhere it does not belong
     * @param terrain     may be null — a zone in another dimension gets grid and shapes only
     */
    public static void render(GuiGraphics g, Font font, int x, int y, int w, int h,
                              List<ZoneShape> shapes, int highlighted, boolean playerHere,
                              @Nullable ZoneTerrainTexture terrain,
                              @Nullable ZoneTerrainSampler.Surface surface) {
        ZoneMapRenderer.backdrop(g, x, y, w, h);

        ZoneMapView view = ZoneMapView.fitting(shapes, w, h);

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        ZoneMapRenderer.terrain(g, x, y, view, terrain, surface);
        ZoneMapRenderer.grid(g, font, x, y, w, h, view, false);

        if (shapes.isEmpty()) {
            g.disableScissor();
            g.drawCenteredString(font, Component.translatable("editor.historystages.zone.preview.empty"),
                    x + w / 2, y + h / 2 - 4, HINT);
            return;
        }

        ZoneMapRenderer.shapes(g, font, x, y, view, shapes, highlighted, false);

        Minecraft mc = Minecraft.getInstance();
        if (playerHere && mc.player != null) {
            ZoneMapRenderer.player(g, x, y, w, h, view,
                    mc.player.getX(), mc.player.getZ(), mc.player.getYRot());
        }

        g.disableScissor();
    }
}
