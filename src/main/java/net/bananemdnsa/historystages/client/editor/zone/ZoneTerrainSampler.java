package net.bananemdnsa.historystages.client.editor.zone;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * A piece of the world from above, the way a map sees it.
 *
 * <p>Only what the client already holds is read. A missing chunk stays unknown and is never
 * requested — a map in the editor is no reason to make the server work, and a dark patch is more
 * honest than a guessed one.
 *
 * <p>The shading is the vanilla map's: each point is compared with the one to its north, and the
 * height difference picks one of the four brightness steps of the block's map colour. That is
 * where the sense of relief on a flat picture comes from.
 */
public final class ZoneTerrainSampler {

    /** How far down water is followed to judge its depth. Below that the difference stops showing. */
    private static final int MAX_WATER_DEPTH = 12;

    public static final int UNKNOWN_HEIGHT = Integer.MIN_VALUE;

    private ZoneTerrainSampler() {}

    /**
     * @param step blocks per sample
     * @param abgr zero means unknown — colour and height stand and fall together. Despite its name
     *             {@code calculateRGBColor} hands back blue, green, red in that order, which is
     *             also the order {@code NativeImage} stores; the bytes are carried through
     *             untouched rather than swapped twice.
     */
    public record Surface(int minX, int minZ, int step, int cols, int rows,
                          int[] abgr, int[] height) {

        public boolean known(int col, int row) {
            return abgr[row * cols + col] != 0;
        }

        public int abgrAt(int col, int row) {
            return abgr[row * cols + col];
        }

        public int heightAt(int col, int row) {
            return height[row * cols + col];
        }

        public boolean anyUnknown() {
            for (int value : abgr) {
                if (value == 0) return true;
            }
            return false;
        }
    }

    public static Surface sample(ClientLevel level, int minX, int minZ, int step, int cols, int rows) {
        int[] abgr = new int[cols * rows];
        int[] height = new int[cols * rows];

        // Carried down the columns rather than looked up twice: the shading needs the point to the
        // north, and the top row would otherwise have none.
        int[] northHeight = new int[cols];
        for (int col = 0; col < cols; col++) {
            northHeight[col] = surfaceHeight(level, minX + col * step, minZ - step);
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int row = 0; row < rows; row++) {
            int worldZ = minZ + row * step;
            for (int col = 0; col < cols; col++) {
                int worldX = minX + col * step;
                int index = row * cols + col;
                int surface = surfaceHeight(level, worldX, worldZ);
                height[index] = surface;
                abgr[index] = surface == UNKNOWN_HEIGHT
                        ? 0
                        : colourAt(level, pos, worldX, worldZ, surface, northHeight[col], step);
                northHeight[col] = surface;
            }
        }

        return new Surface(minX, minZ, step, cols, rows, abgr, height);
    }

    /**
     * Fills in the samples that were unknown last time, and touches nothing else.
     *
     * <p>Chunks arrive after the map has been made, and the alternative — sampling the whole thing
     * again — would redraw ground that is already correct and make the picture twitch for no gain.
     * Only the holes are asked again, so what is on screen stays exactly as it was.
     *
     * @return how many holes were filled
     */
    public static int fillUnknown(ClientLevel level, Surface surface) {
        int filled = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int row = 0; row < surface.rows(); row++) {
            for (int col = 0; col < surface.cols(); col++) {
                if (surface.known(col, row)) continue;

                int worldX = surface.minX() + col * surface.step();
                int worldZ = surface.minZ() + row * surface.step();
                int height = surfaceHeight(level, worldX, worldZ);
                if (height == UNKNOWN_HEIGHT) continue;

                int index = row * surface.cols() + col;
                surface.height()[index] = height;
                surface.abgr()[index] = colourAt(level, pos, worldX, worldZ, height,
                        northHeightOf(surface, col, row, level), surface.step());
                filled++;
            }
        }

        return filled;
    }

    /** The neighbour a filled-in sample shades against — from the grid if it is there, else asked. */
    private static int northHeightOf(Surface surface, int col, int row, ClientLevel level) {
        if (row > 0 && surface.known(col, row - 1)) return surface.heightAt(col, row - 1);
        return surfaceHeight(level, surface.minX() + col * surface.step(),
                surface.minZ() + (row - 1) * surface.step());
    }

    private static int surfaceHeight(ClientLevel level, int worldX, int worldZ) {
        ChunkAccess chunk = level.getChunkSource().getChunk(
                SectionPos.blockToSectionCoord(worldX), SectionPos.blockToSectionCoord(worldZ),
                ChunkStatus.FULL, false);
        if (chunk == null) return UNKNOWN_HEIGHT;
        return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
    }

    private static int colourAt(ClientLevel level, BlockPos.MutableBlockPos pos,
                                int worldX, int worldZ, int surface, int northHeight, int step) {
        pos.set(worldX, surface - 1, worldZ);
        BlockState state = level.getBlockState(pos);
        MapColor colour = state.getMapColor(level, pos);
        if (colour == MapColor.NONE) return 0;

        if (!state.getFluidState().isEmpty()) {
            return colour.calculateRGBColor(waterBrightness(level, pos, surface));
        }

        // Measured against the sample step, the way the vanilla map does it, so a coarse map does
        // not come out flatter than a fine one.
        double slope = (northHeight == UNKNOWN_HEIGHT ? 0 : northHeight - surface) * 4.0 / (step + 4);
        MapColor.Brightness brightness = slope > 0.6 ? MapColor.Brightness.HIGH
                : slope < -0.6 ? MapColor.Brightness.LOW
                : MapColor.Brightness.NORMAL;
        return colour.calculateRGBColor(brightness);
    }

    private static MapColor.Brightness waterBrightness(ClientLevel level,
                                                       BlockPos.MutableBlockPos pos, int surface) {
        int depth = 0;
        for (int y = surface - 1; y > surface - 1 - MAX_WATER_DEPTH; y--) {
            pos.setY(y);
            if (level.getBlockState(pos).getFluidState().isEmpty()) break;
            depth++;
        }

        if (depth < 3) return MapColor.Brightness.HIGH;
        if (depth < 7) return MapColor.Brightness.NORMAL;
        if (depth < 10) return MapColor.Brightness.LOW;
        return MapColor.Brightness.LOWEST;
    }
}
