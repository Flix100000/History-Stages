package net.bananemdnsa.historystages.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientZoneSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws the two corners the player has marked out, as a wireframe box.
 *
 * <p>Only ever the viewer's own selection, and only while they are in the world it was marked in.
 *
 * <p>A renderer of its own rather than a second caller of {@link LockBorderRenderer}: that one is
 * fed by {@link LockBorderClientCache}, whose contents are locked structure bounds, and it derives
 * face masks from exactly those boxes. Feeding a selection through it would tangle two things that
 * are about to diverge further, since round 2 gives zones a force field of their own.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class ZoneSelectionRenderer {

    private static final float R = 1.0f;
    private static final float G = 0.85f;
    private static final float B = 0.2f;
    private static final float A = 0.9f;

    /** How far a lone corner marker reaches from the block it sits on. */
    private static final float SINGLE_CORNER_PADDING = 0.05f;

    private ZoneSelectionRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!ClientZoneSelection.hasFirst() && !ClientZoneSelection.hasSecond()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // Coordinates from another world would land somewhere arbitrary in this one.
        if (!mc.level.dimension().location().toString().equals(ClientZoneSelection.dimension())) {
            return;
        }

        float x0;
        float y0;
        float z0;
        float x1;
        float y1;
        float z1;

        if (ClientZoneSelection.isComplete()) {
            x0 = Math.min(ClientZoneSelection.firstX(), ClientZoneSelection.secondX());
            y0 = Math.min(ClientZoneSelection.firstY(), ClientZoneSelection.secondY());
            z0 = Math.min(ClientZoneSelection.firstZ(), ClientZoneSelection.secondZ());
            // +1 because both corners are inclusive: a selection from 100 to 100 is one block.
            x1 = Math.max(ClientZoneSelection.firstX(), ClientZoneSelection.secondX()) + 1;
            y1 = Math.max(ClientZoneSelection.firstY(), ClientZoneSelection.secondY()) + 1;
            z1 = Math.max(ClientZoneSelection.firstZ(), ClientZoneSelection.secondZ()) + 1;
        } else {
            // One corner alone still gets drawn, slightly proud of the block, so it is visible
            // that something is set and where — otherwise the first click looks like it did
            // nothing.
            int cx = ClientZoneSelection.hasFirst() ? ClientZoneSelection.firstX() : ClientZoneSelection.secondX();
            int cy = ClientZoneSelection.hasFirst() ? ClientZoneSelection.firstY() : ClientZoneSelection.secondY();
            int cz = ClientZoneSelection.hasFirst() ? ClientZoneSelection.firstZ() : ClientZoneSelection.secondZ();
            x0 = cx - SINGLE_CORNER_PADDING;
            y0 = cy - SINGLE_CORNER_PADDING;
            z0 = cz - SINGLE_CORNER_PADDING;
            x1 = cx + 1 + SINGLE_CORNER_PADDING;
            y1 = cy + 1 + SINGLE_CORNER_PADDING;
            z1 = cz + 1 + SINGLE_CORNER_PADDING;
        }

        Vec3 cam = event.getCamera().getPosition();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        edges(buffer, matrix, x0, y0, z0, x1, y1, z1);

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        pose.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void edges(BufferBuilder buf, Matrix4f m,
                              float x0, float y0, float z0, float x1, float y1, float z1) {
        line(buf, m, x0, y0, z0, x1, y0, z0);
        line(buf, m, x1, y0, z0, x1, y0, z1);
        line(buf, m, x1, y0, z1, x0, y0, z1);
        line(buf, m, x0, y0, z1, x0, y0, z0);

        line(buf, m, x0, y1, z0, x1, y1, z0);
        line(buf, m, x1, y1, z0, x1, y1, z1);
        line(buf, m, x1, y1, z1, x0, y1, z1);
        line(buf, m, x0, y1, z1, x0, y1, z0);

        line(buf, m, x0, y0, z0, x0, y1, z0);
        line(buf, m, x1, y0, z0, x1, y1, z0);
        line(buf, m, x1, y0, z1, x1, y1, z1);
        line(buf, m, x0, y0, z1, x0, y1, z1);
    }

    private static void line(BufferBuilder buf, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz) {
        buf.addVertex(m, ax, ay, az).setColor(R, G, B, A);
        buf.addVertex(m, bx, by, bz).setColor(R, G, B, A);
    }
}
