package net.bananemdnsa.historystages.platform.event.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * The point in the world render at which a mod hands over its own geometry.
 *
 * <p>26.2 has no immediate-mode drawing in the level any more: geometry is submitted to the
 * collector and drawn later with everything else (NeoForge {@code SubmitCustomGeometryEvent},
 * Fabric {@code LevelRenderEvents.COLLECT_SUBMITS}). The pose stack is camera-relative and carries
 * no rotation, as vanilla's entity renderers expect.
 */
public class SubmitCustomGeometryEvent extends Event {

    private final SubmitNodeCollector collector;
    private final PoseStack poseStack;
    private final Vec3 cameraPosition;
    private final Quaternionfc cameraOrientation;

    public SubmitCustomGeometryEvent(SubmitNodeCollector collector, PoseStack poseStack, Vec3 cameraPosition,
                                     Quaternionfc cameraOrientation) {
        this.collector = collector;
        this.poseStack = poseStack;
        this.cameraPosition = cameraPosition;
        this.cameraOrientation = cameraOrientation;
    }

    public SubmitNodeCollector getSubmitNodeCollector() {
        return collector;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public Vec3 getCameraPosition() {
        return cameraPosition;
    }

    /** Where the camera looks, as vanilla's {@code Camera.forwardVector}. */
    public Vec3 getLookVector() {
        return new Vec3(new Vector3f(0.0F, 0.0F, -1.0F).rotate(cameraOrientation));
    }
}
