package net.bananemdnsa.historystages.client.editor.graph;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects tinted, textured quads per texture and draws each texture's quads in one call.
 *
 * <p>The graph used to upload and draw every quad on its own: every dash of every dashed edge,
 * every arrowhead, both halves of every node ring. A pack with a few hundred dependencies put
 * tens of thousands of draw calls into a single frame, and that, not the maths, is what made the
 * map crawl. Batched, a frame is a handful of draws however many edges there are.
 *
 * <p>Quads are drawn in texture order, not in the order they were added. Fine for a layer whose
 * quads do not care which one ends up on top (all edges, all node shapes), wrong for anything
 * that has to interleave with other drawing — flush before that.
 *
 * <p>Each instance owns native memory that it keeps for the whole session, so instances are meant
 * to be long-lived (static) rather than created per screen. Freeing it on every close would only
 * mean allocating it again on the next open; it grows to the largest frame seen and stays there.
 */
final class QuadBatch {

    private static final int INITIAL_BYTES = 1 << 16;

    private final Map<ResourceLocation, ByteBufferBuilder> memory = new HashMap<>();
    private final Map<ResourceLocation, BufferBuilder> open = new LinkedHashMap<>();

    private BufferBuilder builder(ResourceLocation tex) {
        BufferBuilder b = open.get(tex);
        if (b == null) {
            ByteBufferBuilder bytes = memory.computeIfAbsent(tex, t -> new ByteBufferBuilder(INITIAL_BYTES));
            b = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            open.put(tex, b);
        }
        return b;
    }

    /** Axis-aligned quad through {@code matrix}, the whole texture stretched over it. */
    void rect(ResourceLocation tex, Matrix4f matrix, float x1, float y1, float x2, float y2, int color) {
        quad(tex, matrix, x1, y2, x2, y2, x2, y1, x1, y1, color);
    }

    /**
     * A quad given by its four corners, in the order bottom-left, bottom-right, top-right,
     * top-left of the texture. Rotated shapes pass their corners already rotated.
     */
    void quad(ResourceLocation tex, Matrix4f matrix,
              float blX, float blY, float brX, float brY, float trX, float trY, float tlX, float tlY,
              int color) {
        float a = ((color >>> 24) & 0xFF) / 255f;
        if (a == 0f) a = 1f; // colours passed without an alpha byte are fully opaque
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        BufferBuilder buf = builder(tex);
        buf.addVertex(matrix, blX, blY, 0f).setUv(0f, 1f).setColor(r, g, b, a);
        buf.addVertex(matrix, brX, brY, 0f).setUv(1f, 1f).setColor(r, g, b, a);
        buf.addVertex(matrix, trX, trY, 0f).setUv(1f, 0f).setColor(r, g, b, a);
        buf.addVertex(matrix, tlX, tlY, 0f).setUv(0f, 0f).setColor(r, g, b, a);
    }

    /** Draws everything collected so far and empties the batch. */
    void flush() {
        if (open.isEmpty()) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        for (Map.Entry<ResourceLocation, BufferBuilder> e : open.entrySet()) {
            MeshData mesh = e.getValue().build();
            if (mesh == null) continue; // nothing was added for this texture
            RenderSystem.setShaderTexture(0, e.getKey());
            BufferUploader.drawWithShader(mesh);
        }
        open.clear();
    }
}
