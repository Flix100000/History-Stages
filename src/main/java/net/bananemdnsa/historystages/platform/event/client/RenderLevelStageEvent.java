package net.bananemdnsa.historystages.platform.event.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.bananemdnsa.historystages.platform.bus.Event;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;

/**
 * A point during the world render at which a mod may draw.
 *
 * <p>Only the stage this mod actually draws in exists here. Naming the others would suggest they
 * are available, and each one is a separate hook that would have to be wired for real.
 */
@Environment(EnvType.CLIENT)
public class RenderLevelStageEvent extends Event {

    public enum Stage {
        /** After the translucent block pass, which is where a see-through wall belongs. */
        AFTER_TRANSLUCENT_BLOCKS
    }

    private final Stage stage;
    private final PoseStack poseStack;
    private final Camera camera;

    public RenderLevelStageEvent(Stage stage, PoseStack poseStack, Camera camera) {
        this.stage = stage;
        this.poseStack = poseStack;
        this.camera = camera;
    }

    public Stage getStage() {
        return stage;
    }

    public PoseStack getPoseStack() {
        return poseStack;
    }

    public Camera getCamera() {
        return camera;
    }
}
