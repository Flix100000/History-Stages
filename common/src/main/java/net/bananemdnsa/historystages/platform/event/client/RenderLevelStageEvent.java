package net.bananemdnsa.historystages.platform.event.client;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * Right after the translucent terrain has been drawn, inside the level pass, where a see-through
 * wall belongs (NeoForge {@code RenderLevelStageEvent.AfterTranslucentBlocks}, Fabric
 * {@code LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN}).
 *
 * <p>Handlers draw immediately ({@code client.WorldOverlayDraw}). The model-view matrix of the level
 * pass is on {@code RenderSystem}'s stack at this point, so geometry is written camera-relative and
 * needs no pose stack from the loader.
 */
public class RenderLevelStageEvent extends Event {

    private final Vec3 cameraPosition;
    private final Quaternionfc cameraOrientation;

    public RenderLevelStageEvent(Vec3 cameraPosition, Quaternionfc cameraOrientation) {
        this.cameraPosition = cameraPosition;
        this.cameraOrientation = cameraOrientation;
    }

    public Vec3 getCameraPosition() {
        return cameraPosition;
    }

    /** Where the camera looks, as vanilla's {@code Camera.forwardVector}. */
    public Vec3 getLookVector() {
        return new Vec3(new Vector3f(0.0F, 0.0F, -1.0F).rotate(cameraOrientation));
    }
}
