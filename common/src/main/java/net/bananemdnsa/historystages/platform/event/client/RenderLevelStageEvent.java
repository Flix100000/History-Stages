package net.bananemdnsa.historystages.platform.event.client;

import net.bananemdnsa.historystages.platform.bus.Event;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/**
 * Fired twice inside the level pass, around the translucent terrain (see {@link Stage}).
 *
 * <p>Handlers draw immediately ({@code client.WorldOverlayDraw}). The model-view matrix of the level
 * pass is on {@code RenderSystem}'s stack at this point, so geometry is written camera-relative and
 * needs no pose stack from the loader.
 */
public class RenderLevelStageEvent extends Event {

    public enum Stage {
        /**
         * Right before the translucent terrain (NeoForge {@code AfterTranslucentFeatures}, Fabric
         * {@code BEFORE_TRANSLUCENT_TERRAIN}). Water and stained glass write depth, so a see-through
         * wall drawn after them would be hidden behind them; drawn here, they blend over it.
         */
        BEFORE_TRANSLUCENT,
        /**
         * Right after it (NeoForge {@code AfterTranslucentBlocks}, Fabric
         * {@code AFTER_TRANSLUCENT_TERRAIN}), for what is drawn on top of everything anyway.
         */
        AFTER_TRANSLUCENT
    }

    private final Stage stage;
    private final Vec3 cameraPosition;
    private final Quaternionfc cameraOrientation;

    public RenderLevelStageEvent(Stage stage, Vec3 cameraPosition, Quaternionfc cameraOrientation) {
        this.stage = stage;
        this.cameraPosition = cameraPosition;
        this.cameraOrientation = cameraOrientation;
    }

    public Stage getStage() {
        return stage;
    }

    public Vec3 getCameraPosition() {
        return cameraPosition;
    }

    /** Where the camera looks, as vanilla's {@code Camera.forwardVector}. */
    public Vec3 getLookVector() {
        return new Vec3(new Vector3f(0.0F, 0.0F, -1.0F).rotate(cameraOrientation));
    }
}
