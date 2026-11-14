package net.bananemdnsa.historystages.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * The render types the world overlays (lock and zone borders, zone selection) submit into.
 *
 * <p>1.21.1 set blend, depth and shader state by hand around an immediate draw. 26.2 bakes that
 * state into a pipeline, so the two combinations vanilla has no type for get one here: a textured
 * see-through quad with depth test but no depth write (the old force field) and lines drawn on top
 * of everything (the old selection box). Built on first use, in a class of its own, so nothing that
 * merely clears a cache on disconnect pulls the render system in.
 */
public final class WorldOverlayRenderTypes {

    private WorldOverlayRenderTypes() {}

    public static final Identifier FORCEFIELD_TEXTURE = Identifier.withDefaultNamespace("textures/misc/forcefield.png");

    /**
     * Drawn right after the translucent terrain, so into the target that terrain went to. With
     * Fabulous graphics that is the translucent layer (water and glass in front still cover the
     * overlay when the layers are combined); otherwise it falls back to the main target.
     */
    private static final OutputTarget TRANSLUCENT = new OutputTarget("historystages_translucent",
            () -> Minecraft.getInstance().levelRenderer.translucentTarget());

    private static final class Holder {
        // Same shader and vertex layout as the old GameRenderer::getPositionTexColorShader.
        // Depth is reversed in 26.2, hence GREATER_THAN_OR_EQUAL as in vanilla's own pipelines.
        static final RenderPipeline FORCEFIELD_PIPELINE = RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withLocation(HistoryStages.location("pipeline/forcefield"))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader("core/position_tex_color")
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withCull(false)
                .build();

        static final RenderType FORCEFIELD = RenderType.create("historystages_forcefield",
                RenderSetup.builder(FORCEFIELD_PIPELINE)
                        // Explicitly repeating: the UVs run over many tiles, and a clamped sampler
                        // stretches the transparent edge texel across the whole wall.
                        .withTexture("Sampler0", FORCEFIELD_TEXTURE,
                                () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST))
                        .setOutputTarget(TRANSLUCENT)
                        .createRenderSetup());

        // Vanilla's line pipeline without the depth test: the selection used to be drawn with
        // depth test off so a corner behind a hill still shows.
        static final RenderPipeline LINES_ON_TOP_PIPELINE = RenderPipeline.builder()
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withLocation(HistoryStages.location("pipeline/lines_on_top"))
                .withVertexShader("core/rendertype_lines")
                .withFragmentShader("core/rendertype_lines")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
                .withPrimitiveTopology(PrimitiveTopology.LINES)
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build();

        static final RenderType LINES_ON_TOP = RenderType.create("historystages_lines_on_top",
                RenderSetup.builder(LINES_ON_TOP_PIPELINE)
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .setOutputTarget(TRANSLUCENT)
                        .createRenderSetup());

        static final RenderType LINES = RenderType.create("historystages_lines",
                RenderSetup.builder(RenderPipelines.LINES_TRANSLUCENT)
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .setOutputTarget(TRANSLUCENT)
                        .createRenderSetup());
    }

    /** Force-field texture, translucent, both sides, behind blocks hidden, writes no depth. */
    public static RenderType forcefield() {
        return Holder.FORCEFIELD;
    }

    /** Translucent lines hidden behind blocks (the old zone outline). Vanilla's pipeline, our target. */
    public static RenderType lines() {
        return Holder.LINES;
    }

    /** The pipelines that are ours, for precompiling at client start so a broken one shows in the log. */
    public static List<RenderPipeline> ownPipelines() {
        return List.of(Holder.FORCEFIELD_PIPELINE, Holder.LINES_ON_TOP_PIPELINE);
    }

    /** Translucent lines in front of everything (the old selection box). */
    public static RenderType linesOnTop() {
        return Holder.LINES_ON_TOP;
    }

    /**
     * One line segment for the line types above. 26.2 widens lines in the shader along the normal,
     * which is the segment's own direction, so a line now needs both the normal and a width.
     */
    public static void line(VertexConsumer buf, PoseStack.Pose pose, float width,
                            float ax, float ay, float az, float bx, float by, float bz,
                            float r, float g, float b, float a) {
        float nx = bx - ax, ny = by - ay, nz = bz - az;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0E-6f) return;
        nx /= len;
        ny /= len;
        nz /= len;
        buf.addVertex(pose, ax, ay, az).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(width);
        buf.addVertex(pose, bx, by, bz).setColor(r, g, b, a).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    /**
     * Compiles our pipelines now instead of at the first draw (Fabric; NeoForge registers them with
     * RegisterRenderPipelinesEvent and preloads them with vanilla's). A broken one is logged here, so
     * a client start shows it even when nothing is drawn.
     */
    public static void precompile() {
        for (RenderPipeline pipeline : ownPipelines()) {
            if (!RenderSystem.getDevice().precompilePipeline(pipeline).isValid()) {
                com.mojang.logging.LogUtils.getLogger().error("[HistoryStages] Render pipeline {} failed to compile",
                        pipeline.getLocation());
            }
        }
    }

    /** The width vanilla gives its own lines for this window. */
    public static float lineWidth() {
        return Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
    }
}
