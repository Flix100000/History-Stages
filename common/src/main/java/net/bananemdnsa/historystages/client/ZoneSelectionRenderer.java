package net.bananemdnsa.historystages.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientZoneSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.bananemdnsa.historystages.platform.Dist;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.bananemdnsa.historystages.platform.event.client.SubmitCustomGeometryEvent;

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
    public static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        if (!ClientZoneSelection.hasFirst() && !ClientZoneSelection.hasSecond()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // Coordinates from another world would land somewhere arbitrary in this one.
        if (!mc.level.dimension().identifier().toString().equals(ClientZoneSelection.dimension())) {
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

        Vec3 cam = event.getCameraPosition();
        float width = WorldOverlayRenderTypes.lineWidth();
        float bx0 = x0, by0 = y0, bz0 = z0, bx1 = x1, by1 = y1, bz1 = z1;

        // Drawn over everything, as the old depth-test-off draw was.
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        event.getSubmitNodeCollector().submitCustomGeometry(pose, WorldOverlayRenderTypes.linesOnTop(),
                (p, buffer) -> edges(buffer, p, width, bx0, by0, bz0, bx1, by1, bz1));
        pose.popPose();
    }

    private static void edges(VertexConsumer buf, PoseStack.Pose m, float w,
                              float x0, float y0, float z0, float x1, float y1, float z1) {
        line(buf, m, w, x0, y0, z0, x1, y0, z0);
        line(buf, m, w, x1, y0, z0, x1, y0, z1);
        line(buf, m, w, x1, y0, z1, x0, y0, z1);
        line(buf, m, w, x0, y0, z1, x0, y0, z0);

        line(buf, m, w, x0, y1, z0, x1, y1, z0);
        line(buf, m, w, x1, y1, z0, x1, y1, z1);
        line(buf, m, w, x1, y1, z1, x0, y1, z1);
        line(buf, m, w, x0, y1, z1, x0, y1, z0);

        line(buf, m, w, x0, y0, z0, x0, y1, z0);
        line(buf, m, w, x1, y0, z0, x1, y1, z0);
        line(buf, m, w, x1, y0, z1, x1, y1, z1);
        line(buf, m, w, x0, y0, z1, x0, y1, z1);
    }

    private static void line(VertexConsumer buf, PoseStack.Pose m, float w,
                             float ax, float ay, float az, float bx, float by, float bz) {
        WorldOverlayRenderTypes.line(buf, m, w, ax, ay, az, bx, by, bz, R, G, B, A);
    }
}
