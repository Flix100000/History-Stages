package net.bananemdnsa.historystages.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.cache.ClientZoneShapes;
import net.bananemdnsa.historystages.data.graph.GraphColors;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import net.bananemdnsa.historystages.data.lock.ZoneShapeType;
import net.bananemdnsa.historystages.data.lock.ZoneSurfaceRuns;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Draws the wall of a locked zone the player is close to, in two parts.
 *
 * <p>The force field on the faces of the zone's outermost blocks is the part everyone gets: the
 * same texture the structure lock uses, either on the stretch of wall in front of the player or
 * across the whole zone at once, depending on the setting. The wireframe outline of the shapes is
 * a second, separate layer, off by default — it says where an area is and how big long before the
 * wall is readable, which is worth more while building a pack than while playing one.
 *
 * <p>The faces of a sphere or a cylinder are bent onto the true surface before they are drawn, so
 * a round zone comes out round rather than as a staircase. That is the one place where what is
 * drawn and what stops the player part company: the barrier is made of whole blocks, so on a round
 * shape it holds up to half a block off the line that gets drawn. Bending the barrier to match
 * instead is not on offer — the server judges a player by the block they stand in, and a client
 * that let them stand somewhere the server calls inside would hand back the shove this whole thing
 * was built to get rid of.
 *
 * <p>A renderer of its own rather than a second caller of {@link LockBorderRenderer}. That one is
 * fed by {@link LockBorderClientCache}, whose contents are structure bounding boxes, and it derives
 * its face masks from exactly those boxes — a sphere has no faces to mask. Sharing it would mean
 * generalising a piece of working code with a documented history of leaking between worlds.
 *
 * <p>The settings are the zone's own, matching the four the structure lock has rather than
 * borrowing them. Sharing was the first arrangement and it did not hold up: a pack can gate a whole
 * region as a zone and a single hut as a structure, and wanting to see the region from further off
 * says nothing about the hut. On top of those four sit the three a structure has no use for — the
 * whole-wall view, the outline, and the colour.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class ZoneBorderRenderer {

    private static final ResourceLocation FORCEFIELD =
            ResourceLocation.parse("minecraft:textures/misc/forcefield.png");

    /** Distance at which the outline starts to appear, in blocks. */
    private static final double FADE_START = 48.0;

    /** Distance at which it is fully drawn. */
    private static final double FADE_FULL = 12.0;

    /** Used when the colour in the settings is not a colour. */
    private static final int DEFAULT_RGB = 0xE61414;

    /**
     * The colour to draw in, read once at the top of the frame.
     *
     * <p>Held here rather than passed down because it would otherwise have to travel through every
     * box, circle and corner helper below to reach the one line that uses it. Only the render
     * thread ever touches this.
     */
    private static int borderRgb = DEFAULT_RGB;

    /**
     * Roughly how long one segment of a circle should be, in blocks.
     *
     * <p>A fixed number of segments makes a small circle wasteful and a large one a polygon, and
     * zones come in both sizes — a well is three blocks across, a walled-off region can be two
     * hundred.
     */
    private static final double SEGMENT_BLOCKS = 1.5;

    /** How many blocks one texture tile covers, as on the structure lock's field. */
    private static final float TILE_BLOCKS = 4.0f;

    /** Alpha of the wall right in front of the camera; falls off linearly from there. */
    private static final float WALL_ALPHA = 0.9f;

    /**
     * How far the wall reaches once the viewer has asked to see the whole thing.
     *
     * <p>Not the horizon, and not as far as the server is willing to send. Every block of surface
     * within reach becomes a face that is written out again every frame, so this is the point
     * where more wall turns into fewer frames. Far enough that an ordinary zone is visible end to
     * end, and the fade takes care of the rest.
     */
    private static final int WALL_FULL_REACH = 96;

    /**
     * The last part of the full-view reach, over which the wall fades out.
     *
     * <p>The close-up field fades the whole way, which is the point of it — it is a patch that
     * appears as you walk up to a wall. Doing the same over ninety blocks would make everything
     * past the first twenty too faint to count as seeing it, which is the one thing the switch is
     * for. So it stays at strength and only lets go at the end.
     */
    private static final double FULL_VIEW_TAIL = 0.35;

    private static final ZoneWallMesh WALL = new ZoneWallMesh();

    private ZoneBorderRenderer() {}

    /** Called when the client leaves a world, so no wall survives into the next one. */
    public static void forgetWall() {
        WALL.forget();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (ClientZoneShapes.isEmpty()) return;

        // Whether a zone has a border at all is the zone's own business; this only says how
        // far it carries, and nought blocks is how a player says they would rather not see it.
        double distance = Config.VISUAL.zoneBorderDistance.get();
        double reach = distance <= 0 ? 0
                : Config.VISUAL.zoneBorderFullView.get() ? WALL_FULL_REACH : distance;
        boolean outline = Config.VISUAL.zoneBorderOutline.get();
        if (reach <= 0 && !outline) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        borderRgb = GraphColors.parse(Config.VISUAL.zoneBorderColor.get(), DEFAULT_RGB);

        Vec3 cam = event.getCamera().getPosition();
        int minY = mc.level.getMinBuildHeight();
        int maxY = mc.level.getMaxBuildHeight();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();

        if (reach > 0) {
            drawWall(matrix, cam, new Vec3(event.getCamera().getLookVector()), reach,
                    reach > distance, minY, maxY);
        }
        if (outline) drawOutlines(matrix, cam, minY, maxY);

        pose.popPose();
    }

    // --- The force field up close ---

    /**
     * How far off the line of sight a face may sit and still be written out.
     *
     * <p>Deliberately well past a right angle. This is not a substitute for the frustum test the
     * game does on its own geometry, only a way of not paying for the wall behind the player, and
     * a tight limit would pop the edges of the screen every time they turned.
     */
    private static final double BEHIND = -0.3;

    /**
     * Below this distance nothing is dropped for facing the wrong way.
     *
     * <p>The test above measures to the middle of a face, and a face that has swallowed its
     * neighbours can be a good deal wider than that middle suggests. Close up, one end of it can
     * be squarely on screen while the middle is off to the side and behind — so close up, the test
     * does not get a vote. Twice the longest a face can be, which is the worst that gap can get.
     */
    private static final double CULL_FROM = 2.0 * ZoneSurfaceRuns.CAP;

    private static void drawWall(Matrix4f matrix, Vec3 cam, Vec3 look, double reach,
                                 boolean full, int minY, int maxY) {
        WALL.refresh(ClientZoneShapes.get(),
                (int) Math.floor(cam.x), (int) Math.floor(cam.y), (int) Math.floor(cam.z),
                (int) Math.ceil(reach), minY, maxY);
        if (WALL.count() == 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, FORCEFIELD);

        // The drift is part of what makes the close-up patch read as a force field. Across a
        // whole wall it reads as the wall sliding sideways instead, so there it stands still.
        float scroll = full ? 0f : (float) ((Util.getMillis() % 3000L) / 3000.0);

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        boolean any = false;
        for (int i = 0; i < WALL.count(); i++) {
            any |= quad(buffer, matrix, cam, look, reach, full, scroll,
                    WALL.face(i, 0), WALL.face(i, 1), WALL.face(i, 2), WALL.face(i, 3),
                    WALL.face(i, 5), WALL.projector(i));
        }

        MeshData mesh = buffer.build();
        if (any && mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Scratch for the face being written: four corners as x, y, z plus the texture u and v. */
    private static final float[] CORNERS = new float[20];

    /** Scratch for the direction that face is turned. */
    private static final double[] NORMAL = new double[3];

    /**
     * How far from the true rim a corner of a cylinder lid still counts as part of that rim.
     *
     * <p>A block boundary sits at most 0.71 from the circle it belongs to, so this catches every
     * corner on the rim and nothing further in. Without the limit the whole lid would be pulled
     * outwards onto the circle and collapse into a ring.
     */
    private static final double RIM = 1.0;

    /**
     * One face of the wall, or nothing when the camera is too far from it or behind it.
     *
     * <p>The side test matters more than it looks: without it a sphere is drawn twice over, front
     * and back, and comes out at double strength against everything else.
     */
    private static boolean quad(BufferBuilder buf, Matrix4f m, Vec3 cam, Vec3 look,
                                double reach, boolean full, float scroll,
                                int x, int y, int z, int dir, int run, ZoneWallMesh.Round on) {
        corners(x, y, z, dir, run);
        if (on != null) roundOff(on, dir);

        float cx = (CORNERS[0] + CORNERS[5] + CORNERS[10] + CORNERS[15]) / 4f;
        float cy = (CORNERS[1] + CORNERS[6] + CORNERS[11] + CORNERS[16]) / 4f;
        float cz = (CORNERS[2] + CORNERS[7] + CORNERS[12] + CORNERS[17]) / 4f;

        double dx = cam.x - cx;
        double dy = cam.y - cy;
        double dz = cam.z - cz;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > reach) return false;

        if (distance > CULL_FROM
                && (-dx * look.x - dy * look.y - dz * look.z) / distance < BEHIND) {
            return false;
        }

        normalOf(on, dir, cx, cy, cz);
        if (dx * NORMAL[0] + dy * NORMAL[1] + dz * NORMAL[2] <= 0) return false;

        double left = 1.0 - distance / reach;
        if (full) left = Math.min(1.0, left / FULL_VIEW_TAIL);
        int alpha = (int) (WALL_ALPHA * left * 255.0);
        if (alpha <= 2) return false;

        for (int corner = 0; corner < 4; corner++) {
            int at = corner * 5;
            buf.addVertex(m, CORNERS[at], CORNERS[at + 1], CORNERS[at + 2])
                    .setUv(CORNERS[at + 3] / TILE_BLOCKS + scroll,
                            CORNERS[at + 4] / TILE_BLOCKS - scroll)
                    .setColor((borderRgb >> 16) & 0xFF, (borderRgb >> 8) & 0xFF,
                            borderRgb & 0xFF, alpha);
        }
        return true;
    }

    /**
     * The four corners of a block face, with the texture pinned to the world rather than to the
     * quad, so neighbouring faces continue each other instead of each starting the tile over. The
     * pinning survives the rounding below, because it is read off the block and not off the corner.
     */
    private static void corners(int x, int y, int z, int dir, int run) {
        for (int corner = 0; corner < 4; corner++) {
            float a = corner >= 2 ? run : 0;
            float b = corner == 1 || corner == 2 ? 1 : 0;
            int at = corner * 5;
            switch (dir) {
                case ZoneWallMesh.DIR_DOWN, ZoneWallMesh.DIR_UP -> {
                    CORNERS[at] = x + a;
                    CORNERS[at + 1] = ZoneWallMesh.STEP_Y[dir] > 0 ? y + 1 : y;
                    CORNERS[at + 2] = z + b;
                    CORNERS[at + 3] = x + a;
                    CORNERS[at + 4] = z + b;
                }
                case ZoneWallMesh.DIR_NORTH, ZoneWallMesh.DIR_SOUTH -> {
                    CORNERS[at] = x + a;
                    CORNERS[at + 1] = y + b;
                    CORNERS[at + 2] = ZoneWallMesh.STEP_Z[dir] > 0 ? z + 1 : z;
                    CORNERS[at + 3] = x + a;
                    CORNERS[at + 4] = y + b;
                }
                default -> {
                    CORNERS[at] = ZoneWallMesh.STEP_X[dir] > 0 ? x + 1 : x;
                    CORNERS[at + 1] = y + b;
                    CORNERS[at + 2] = z + a;
                    CORNERS[at + 3] = z + a;
                    CORNERS[at + 4] = y + b;
                }
            }
        }
    }

    /**
     * Pulls the corners of a face onto the surface it belongs to, which is what turns a staircase
     * of block faces into a sphere.
     *
     * <p>It stays watertight because a corner lands in the same place whichever face asks for it:
     * the move depends on the corner and the shape, on nothing else. The faces that stood at right
     * angles to the surface — the risers of the staircase — squeeze down to slivers and fill the
     * gaps between the ones that were already facing outwards.
     */
    private static void roundOff(ZoneWallMesh.Round on, int dir) {
        ZoneShape shape = on.shape();
        double r = shape.radius() + 0.5;

        if (shape.type() == ZoneShapeType.SPHERE) {
            double sx = shape.fromX() + 0.5;
            double sy = shape.fromY() + 0.5;
            double sz = shape.fromZ() + 0.5;
            for (int corner = 0; corner < 4; corner++) {
                int at = corner * 5;
                double dx = CORNERS[at] - sx;
                double dy = CORNERS[at + 1] - sy;
                double dz = CORNERS[at + 2] - sz;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1.0E-6) continue;
                double f = r / len;
                CORNERS[at] = (float) (sx + dx * f);
                CORNERS[at + 1] = (float) (sy + dy * f);
                CORNERS[at + 2] = (float) (sz + dz * f);
            }
            return;
        }

        boolean lid = dir == ZoneWallMesh.DIR_UP || dir == ZoneWallMesh.DIR_DOWN;
        double ax = shape.fromX() + 0.5;
        double az = shape.fromZ() + 0.5;
        for (int corner = 0; corner < 4; corner++) {
            int at = corner * 5;
            double dx = CORNERS[at] - ax;
            double dz = CORNERS[at + 2] - az;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-6) continue;
            if (lid && Math.abs(len - r) > RIM) continue;
            double f = r / len;
            CORNERS[at] = (float) (ax + dx * f);
            CORNERS[at + 2] = (float) (az + dz * f);
        }
    }

    /**
     * Which way the finished face is turned.
     *
     * <p>Taken from the shape rather than from the block, because rounding tilts a face away from
     * the side it started on — a sliver can end up at right angles to where it began, and asking
     * the block would drop it and leave a crack in the surface.
     */
    private static void normalOf(ZoneWallMesh.Round on, int dir, float cx, float cy, float cz) {
        ZoneShape shape = on == null ? null : on.shape();
        boolean lid = dir == ZoneWallMesh.DIR_UP || dir == ZoneWallMesh.DIR_DOWN;

        if (shape != null && !(shape.type() == ZoneShapeType.CYLINDER && lid)) {
            double dx = cx - (shape.fromX() + 0.5);
            double dy = shape.type() == ZoneShapeType.SPHERE ? cy - (shape.fromY() + 0.5) : 0;
            double dz = cz - (shape.fromZ() + 0.5);
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len >= 1.0E-6) {
                // An inverted zone is the world with a hole in it, so it is seen from inside.
                double f = on.inverted() ? -1.0 / len : 1.0 / len;
                NORMAL[0] = dx * f;
                NORMAL[1] = dy * f;
                NORMAL[2] = dz * f;
                return;
            }
        }

        NORMAL[0] = ZoneWallMesh.STEP_X[dir];
        NORMAL[1] = ZoneWallMesh.STEP_Y[dir];
        NORMAL[2] = ZoneWallMesh.STEP_Z[dir];
    }

    // --- The outline further out ---

    private static void drawOutlines(Matrix4f matrix, Vec3 cam, int minY, int maxY) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        boolean drewAnything = false;
        for (ClientZoneShapes.Visible zone : ClientZoneShapes.get()) {
            if (!zone.border()) continue;
            for (ZoneShape shape : zone.shapes()) {
                float alpha = alphaFor(shape, cam, minY, maxY);
                if (alpha <= 0.01f) continue;
                drawShape(buffer, matrix, shape, alpha, minY, maxY);
                drewAnything = true;
            }
        }

        MeshData mesh = buffer.build();
        if (drewAnything && mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /**
     * Fades in as the player approaches.
     *
     * <p>Drawn at full strength from any distance a zone the size of a village would be a cage of
     * red lines across the sky. Close up it is a wall; far away it should be nothing.
     */
    private static float alphaFor(ZoneShape shape, Vec3 cam, int minY, int maxY) {
        double distance = distanceTo(shape, cam, minY, maxY);
        if (distance >= FADE_START) return 0f;
        if (distance <= FADE_FULL) return 0.85f;
        return (float) (0.85 * (FADE_START - distance) / (FADE_START - FADE_FULL));
    }

    private static double distanceTo(ZoneShape shape, Vec3 cam, int minY, int maxY) {
        double[] centre = centreOf(shape, minY, maxY);
        double dx = cam.x - centre[0];
        double dy = cam.y - centre[1];
        double dz = cam.z - centre[2];
        double toCentre = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Math.max(0, toCentre - radiusOf(shape, minY, maxY));
    }

    private static double[] centreOf(ZoneShape shape, int minY, int maxY) {
        return switch (shape.type()) {
            case CUBE -> new double[]{
                    (Math.min(shape.fromX(), shape.toX()) + Math.max(shape.fromX(), shape.toX()) + 1) / 2.0,
                    shape.fullHeight() ? (minY + maxY) / 2.0
                            : (Math.min(shape.fromY(), shape.toY()) + Math.max(shape.fromY(), shape.toY()) + 1) / 2.0,
                    (Math.min(shape.fromZ(), shape.toZ()) + Math.max(shape.fromZ(), shape.toZ()) + 1) / 2.0 };
            case SPHERE -> new double[]{ shape.fromX() + 0.5, shape.fromY() + 0.5, shape.fromZ() + 0.5 };
            case CYLINDER -> new double[]{ shape.fromX() + 0.5,
                    shape.fullHeight() ? (minY + maxY) / 2.0 : shape.fromY() + shape.height() / 2.0,
                    shape.fromZ() + 0.5 };
        };
    }

    /** Rough enclosing radius — only ever used to decide how strongly to draw. */
    private static double radiusOf(ZoneShape shape, int minY, int maxY) {
        return switch (shape.type()) {
            case CUBE -> {
                double w = Math.abs(shape.toX() - shape.fromX()) + 1;
                double d = Math.abs(shape.toZ() - shape.fromZ()) + 1;
                double h = shape.fullHeight() ? maxY - minY
                        : Math.abs(shape.toY() - shape.fromY()) + 1;
                yield Math.sqrt(w * w + h * h + d * d) / 2.0;
            }
            case SPHERE -> shape.radius() + 1;
            case CYLINDER -> {
                double h = shape.fullHeight() ? maxY - minY : shape.height();
                double r = shape.radius() + 1;
                yield Math.sqrt(r * r + (h / 2) * (h / 2));
            }
        };
    }

    private static void drawShape(BufferBuilder buf, Matrix4f m, ZoneShape shape, float a,
                                  int minY, int maxY) {
        switch (shape.type()) {
            case CUBE -> {
                float x0 = Math.min(shape.fromX(), shape.toX());
                float z0 = Math.min(shape.fromZ(), shape.toZ());
                float x1 = Math.max(shape.fromX(), shape.toX()) + 1;
                float z1 = Math.max(shape.fromZ(), shape.toZ()) + 1;
                float y0 = shape.fullHeight() ? minY : Math.min(shape.fromY(), shape.toY());
                float y1 = shape.fullHeight() ? maxY : Math.max(shape.fromY(), shape.toY()) + 1;
                box(buf, m, x0, y0, z0, x1, y1, z1, a);
            }
            case SPHERE -> {
                float cx = shape.fromX() + 0.5f;
                float cy = shape.fromY() + 0.5f;
                float cz = shape.fromZ() + 0.5f;
                float r = shape.radius() + 0.5f;
                // Three great circles read as a sphere without a surface to shade.
                circleXZ(buf, m, cx, cy, cz, r, a);
                circleXY(buf, m, cx, cy, cz, r, a);
                circleYZ(buf, m, cx, cy, cz, r, a);
            }
            case CYLINDER -> {
                float cx = shape.fromX() + 0.5f;
                float cz = shape.fromZ() + 0.5f;
                float r = shape.radius() + 0.5f;
                float y0 = shape.fullHeight() ? minY : shape.fromY();
                float y1 = shape.fullHeight() ? maxY : shape.fromY() + shape.height() + 1;
                circleXZ(buf, m, cx, y0, cz, r, a);
                circleXZ(buf, m, cx, y1, cz, r, a);
                // A few uprights, so the two rings read as one solid rather than two hoops.
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4;
                    float x = cx + (float) (Math.cos(angle) * r);
                    float z = cz + (float) (Math.sin(angle) * r);
                    line(buf, m, x, y0, z, x, y1, z, a);
                }
            }
        }
    }

    private static void box(BufferBuilder buf, Matrix4f m,
                            float x0, float y0, float z0, float x1, float y1, float z1, float a) {
        line(buf, m, x0, y0, z0, x1, y0, z0, a);
        line(buf, m, x1, y0, z0, x1, y0, z1, a);
        line(buf, m, x1, y0, z1, x0, y0, z1, a);
        line(buf, m, x0, y0, z1, x0, y0, z0, a);

        line(buf, m, x0, y1, z0, x1, y1, z0, a);
        line(buf, m, x1, y1, z0, x1, y1, z1, a);
        line(buf, m, x1, y1, z1, x0, y1, z1, a);
        line(buf, m, x0, y1, z1, x0, y1, z0, a);

        line(buf, m, x0, y0, z0, x0, y1, z0, a);
        line(buf, m, x1, y0, z0, x1, y1, z0, a);
        line(buf, m, x1, y0, z1, x1, y1, z1, a);
        line(buf, m, x0, y0, z1, x0, y1, z1, a);
    }

    private static void circleXZ(BufferBuilder buf, Matrix4f m,
                                 float cx, float cy, float cz, float r, float a) {
        int segments = segmentsFor(r);
        for (int i = 0; i < segments; i++) {
            double t0 = i * 2 * Math.PI / segments;
            double t1 = (i + 1) * 2 * Math.PI / segments;
            line(buf, m,
                    cx + (float) (Math.cos(t0) * r), cy, cz + (float) (Math.sin(t0) * r),
                    cx + (float) (Math.cos(t1) * r), cy, cz + (float) (Math.sin(t1) * r), a);
        }
    }

    private static void circleXY(BufferBuilder buf, Matrix4f m,
                                 float cx, float cy, float cz, float r, float a) {
        int segments = segmentsFor(r);
        for (int i = 0; i < segments; i++) {
            double t0 = i * 2 * Math.PI / segments;
            double t1 = (i + 1) * 2 * Math.PI / segments;
            line(buf, m,
                    cx + (float) (Math.cos(t0) * r), cy + (float) (Math.sin(t0) * r), cz,
                    cx + (float) (Math.cos(t1) * r), cy + (float) (Math.sin(t1) * r), cz, a);
        }
    }

    private static void circleYZ(BufferBuilder buf, Matrix4f m,
                                 float cx, float cy, float cz, float r, float a) {
        int segments = segmentsFor(r);
        for (int i = 0; i < segments; i++) {
            double t0 = i * 2 * Math.PI / segments;
            double t1 = (i + 1) * 2 * Math.PI / segments;
            line(buf, m,
                    cx, cy + (float) (Math.cos(t0) * r), cz + (float) (Math.sin(t0) * r),
                    cx, cy + (float) (Math.cos(t1) * r), cz + (float) (Math.sin(t1) * r), a);
        }
    }

    /** Never fewer than a dozen, or a small circle turns into a triangle. */
    private static int segmentsFor(float r) {
        return (int) Math.max(12, Math.min(256, Math.round(2 * Math.PI * r / SEGMENT_BLOCKS)));
    }

    private static void line(BufferBuilder buf, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz, float a) {
        float r = ((borderRgb >> 16) & 0xFF) / 255f;
        float g = ((borderRgb >> 8) & 0xFF) / 255f;
        float b = (borderRgb & 0xFF) / 255f;
        buf.addVertex(m, ax, ay, az).setColor(r, g, b, a);
        buf.addVertex(m, bx, by, bz).setColor(r, g, b, a);
    }
}
