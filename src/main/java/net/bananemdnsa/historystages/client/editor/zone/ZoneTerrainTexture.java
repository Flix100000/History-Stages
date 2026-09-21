package net.bananemdnsa.historystages.client.editor.zone;

import com.mojang.blaze3d.platform.NativeImage;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The sampled terrain as a picture.
 *
 * <p>A texture rather than a heap of one-pixel rectangles: a screen-filling map would be six
 * figures worth of draw calls a frame.
 *
 * <p>Has to be closed. Leaving one GL texture behind per opening of a screen is a real bug, not a
 * tidiness question — the editor is opened and closed dozens of times while building a pack.
 */
public final class ZoneTerrainTexture implements AutoCloseable {

    /**
     * Unknown samples keep the map's own backdrop, so a gap does not read as black terrain.
     *
     * <p>Blue, green, red — the order {@code NativeImage} stores and the order the map colours
     * already arrive in.
     */
    private static final int UNKNOWN = 0xFF191414;

    private final ResourceLocation id;

    private DynamicTexture texture;
    private int cols;
    private int rows;

    public ZoneTerrainTexture(String name) {
        this.id = ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID,
                "zone_terrain/" + name + "_" + Integer.toHexString(System.identityHashCode(this)));
    }

    public ResourceLocation id() {
        return id;
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    public boolean isEmpty() {
        return texture == null;
    }

    public void upload(ZoneTerrainSampler.Surface surface) {
        if (texture == null || cols != surface.cols() || rows != surface.rows()) {
            close();
            cols = surface.cols();
            rows = surface.rows();
            texture = new DynamicTexture(cols, rows, false);
            // Hard edges, no smoothing. One texel is one sample of ground, and blurring between
            // two of them turns a landscape made of blocks into a watercolour of one.
            texture.setFilter(false, false);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        }

        NativeImage image = texture.getPixels();
        if (image == null) return;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int abgr = surface.abgrAt(col, row);
                image.setPixelRGBA(col, row, abgr == 0 ? UNKNOWN : abgr);
            }
        }
        texture.upload();
    }

    @Override
    public void close() {
        if (texture == null) return;
        Minecraft.getInstance().getTextureManager().release(id);
        texture.close();
        texture = null;
        cols = 0;
        rows = 0;
    }
}
