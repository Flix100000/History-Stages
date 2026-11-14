package net.bananemdnsa.historystages.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Draws a batch of overlay geometry straight away, in the middle of the level pass.
 *
 * <p>26.2's submit path would draw it before the translucent terrain, so water and stained glass
 * would paint over the force field. This draws where 1.21.1 drew, right after that terrain, the way
 * vanilla's weather does: build the mesh, upload it, one render pass with the render type's state.
 */
public final class WorldOverlayDraw {

    private static final Map<RenderType, GpuBuffer> VERTEX_BUFFERS = new IdentityHashMap<>();

    private WorldOverlayDraw() {}

    public static void draw(RenderType type, PoseStack.Pose pose, BiConsumer<PoseStack.Pose, VertexConsumer> geometry) {
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(type.format().getVertexSize() * 1024)) {
            BufferBuilder builder = new BufferBuilder(bytes, type.primitiveTopology(), type.format());
            geometry.accept(pose, builder);
            try (MeshData mesh = builder.build()) {
                if (mesh == null) return;
                GpuBuffer vertices = upload(type, mesh.vertexBuffer());
                RenderSystem.AutoStorageIndexBuffer indices =
                        RenderSystem.getSequentialBuffer(mesh.drawState().primitiveTopology());
                int indexCount = mesh.drawState().indexCount();
                type.prepare().drawFromBuffer(vertices, indices.getBuffer(indexCount), indices.type(), 0, 0, indexCount);
            }
        }
    }

    // One buffer per render type, grown when a frame needs more, as vanilla's weather keeps its own.
    private static GpuBuffer upload(RenderType type, ByteBuffer data) {
        GpuBuffer buffer = VERTEX_BUFFERS.get(type);
        if (buffer == null || buffer.size() < data.remaining()) {
            if (buffer != null) buffer.close();
            buffer = RenderSystem.getDevice().createBuffer(() -> "History Stages " + type,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, data.remaining());
            VERTEX_BUFFERS.put(type, buffer);
        }
        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        return buffer;
    }
}
