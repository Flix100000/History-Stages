package net.bananemdnsa.historystages.client;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientZoneShapes;
import net.bananemdnsa.historystages.platform.Dist;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.bananemdnsa.historystages.platform.event.client.RenderLevelStageEvent;
import net.minecraft.client.Minecraft;

/**
 * Where the force fields and the zone outline draw: the colour of the target the translucent
 * terrain went to, tested against the depth from before that terrain.
 *
 * <p>Water and stained glass write depth. Tested against it, a field behind them is hidden; drawn
 * before them, a field in front of them is painted over by the water behind it. The depth of the
 * solid world alone gets both right: blocks still hide the field, water and glass never do.
 *
 * <p>The copy is taken just before the translucent terrain, and only on frames that have
 * something to draw.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class OpaqueDepthTarget extends RenderTarget {

    static final OpaqueDepthTarget INSTANCE = new OpaqueDepthTarget();

    private TextureTarget copy;
    private boolean captured;

    private OpaqueDepthTarget() {
        super("History Stages overlays", true, GpuFormat.RGBA8_UNORM);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.BEFORE_TRANSLUCENT) {
            INSTANCE.captured = false;
            if (!LockBorderClientCache.get().isEmpty() || !ClientZoneShapes.isEmpty()) INSTANCE.capture();
        }
    }

    private void capture() {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main.getDepthTexture() == null) return;
        if (copy == null) {
            copy = new TextureTarget("History Stages opaque depth", main.width, main.height, true, GpuFormat.RGBA8_UNORM);
        } else if (copy.width != main.width || copy.height != main.height) {
            copy.resize(main.width, main.height);
        }
        copy.copyDepthFrom(main);
        width = main.width;
        height = main.height;
        captured = true;
    }

    private static RenderTarget colorSource() {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget translucent = mc.levelRenderer.translucentTarget();
        return translucent != null ? translucent : mc.gameRenderer.mainRenderTarget();
    }

    // Without a copy this frame the overlays simply test against the terrain's own depth.
    private RenderTarget depthSource() {
        return captured && copy != null ? copy : colorSource();
    }

    @Override
    public GpuTexture getColorTexture() {
        return colorSource().getColorTexture();
    }

    @Override
    public GpuTextureView getColorTextureView() {
        return colorSource().getColorTextureView();
    }

    @Override
    public GpuTexture getDepthTexture() {
        return depthSource().getDepthTexture();
    }

    @Override
    public GpuTextureView getDepthTextureView() {
        return depthSource().getDepthTextureView();
    }
}
