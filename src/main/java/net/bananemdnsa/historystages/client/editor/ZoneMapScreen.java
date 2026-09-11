package net.bananemdnsa.historystages.client.editor;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;

import net.bananemdnsa.historystages.api.editor.widget.SegmentBar;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.zone.ZoneMapRenderer;
import net.bananemdnsa.historystages.client.editor.zone.ZoneScene3d;
import net.bananemdnsa.historystages.client.editor.zone.ZoneTerrainMesh;
import net.bananemdnsa.historystages.client.editor.zone.ZoneTerrainSampler;
import net.bananemdnsa.historystages.client.editor.zone.ZoneTerrainTexture;
import net.bananemdnsa.historystages.data.lock.ZoneEntry;
import net.bananemdnsa.historystages.data.lock.ZoneMapView;
import net.bananemdnsa.historystages.data.lock.ZoneOrbitCamera;
import net.bananemdnsa.historystages.data.lock.ZoneRayPick;
import net.bananemdnsa.historystages.data.lock.ZoneRowText;
import net.bananemdnsa.historystages.data.lock.ZoneShape;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/**
 * A zone's map across the whole screen.
 *
 * <p>A screen of its own rather than a layer over the editor. As a layer it was right while there
 * was nothing to operate; with buttons, dragging, the wheel and the keyboard it would mean building
 * the host screen's input paths a second time.
 *
 * <p>The zone is held by reference, not copied: the working copy lives in {@link ZoneEditScreen}
 * and is taken over only on <em>Done</em>. A second copy here would lose every pick on the way
 * back.
 */
public final class ZoneMapScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int BAR_H = 26;
    private static final int LIST_W = 152;
    private static final int ROW_H = 12;

    /** Room kept clear at the top right for the beta mark, so the shape list starts below it. */
    private static final int BETA_H = 12;

    /**
     * Samples per axis of the one grid that gets laid down. Half a million points is a noticeable
     * pause once, when the screen opens — and nothing at all after that.
     */
    private static final int MAX_SAMPLES = 512;

    /** Smallest patch of world the grid covers, in blocks, however small the zone is. */
    private static final int MIN_COVERAGE = 320;

    /** How long between two attempts at filling the holes left by chunks that had not arrived. */
    private static final int FILL_FRAMES = 40;

    /** How much of a turn one pixel of drag is worth, in degrees. */
    private static final double ORBIT_PER_PIXEL = 0.5;

    /**
     * Looked up per call, not once into a constant: a constant is filled the first time the class
     * is touched, which can be before the language files are, and it would then keep the raw keys
     * for the rest of the session and through every language change.
     */
    private static List<String> modeLabels() {
        return List.of(
                Component.translatable("editor.historystages.zone.map.mode_flat").getString(),
                Component.translatable("editor.historystages.zone.map.mode_tilted").getString());
    }

    /** Same wash the rest of the editor lays over the world, so the two screens match. */
    private static final int BACKDROP = 0xE0101010;

    private static final int PANEL_BG = 0xD00A0A0C;
    private static final int PANEL_EDGE = 0xFF2F2F2F;
    private static final int BAR_BG = 0xFF0B0B0E;
    private static final int BAR_EDGE = 0xFF2A2A2A;
    private static final int LABEL_GREY = 0xFF777777;
    private static final int TEXT = 0xFFC8C8C8;
    private static final int ACCENT = 0xFFFFCC00;
    private static final int ROW_HOT = 0xFF1D1C12;

    private final Screen parent;
    private final ZoneEntry zone;
    private final IntConsumer onSelected;
    private final ObjIntConsumer<Screen> onOpenNumbers;

    private final ZoneTerrainTexture terrain = new ZoneTerrainTexture("map");
    @Nullable
    private ZoneTerrainSampler.Surface surface;

    private ZoneMapView view = new ZoneMapView(0, 0, 1, 1, 1);
    private int sampleAge;

    /** Flat or tipped over. They share a centre and a scale, so switching never jumps. */
    private boolean tilted;

    private ZoneOrbitCamera camera = ZoneOrbitCamera.looking(0, 64, 0, 1, 1, 1);
    private final ZoneScene3d scene = new ZoneScene3d();
    private final ZoneTerrainMesh mesh = new ZoneTerrainMesh();
    @Nullable
    private ZoneOrbitCamera sceneBuiltFor;
    private boolean sceneStale = true;

    private final SegmentBar.State modeBar = new SegmentBar.State();

    private int selected;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;

    @Nullable
    private StyledButton toMe;

    /**
     * @param onSelected    told about every pick, so the tab lights up the same row
     * @param onOpenNumbers opens a shape's numbers; the screen to come back to is handed along,
     *                      because that dialog belongs to the editor and does not know this one
     */
    public ZoneMapScreen(Screen parent, ZoneEntry zone, int selected,
                         IntConsumer onSelected, ObjIntConsumer<Screen> onOpenNumbers) {
        super(Component.translatable("editor.historystages.zone.map.title"));
        this.parent = parent;
        this.zone = zone;
        this.selected = selected;
        this.onSelected = onSelected;
        this.onOpenNumbers = onOpenNumbers;
    }

    @Override
    protected void init() {
        view = ZoneMapView.fitting(zone.getShapes(), mapW(), mapH());

        camera = ZoneOrbitCamera.from(view, shapeCentreY());

        int y = this.height - BAR_H + 3;
        int x = controlsLeft();

        addRenderableWidget(StyledButton.of(Component.literal("-"),
                b -> zoom(1.25), x, y, 20, 20));
        addRenderableWidget(StyledButton.of(Component.literal("+"),
                b -> zoom(0.8), x + 22, y, 20, 20));

        toMe = StyledButton.of(Component.translatable("editor.historystages.zone.map.to_me"),
                (Button.OnPress) b -> centreOnPlayer(), x + 48, y, 58, 20);
        toMe.active = playerHere();
        addRenderableWidget(toMe);

        addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.zone.map.to_zone"),
                b -> frameZone(), x + 110, y, 64, 20));
    }

    // -------------------------------------------------------------------------------------
    // Geometry
    // -------------------------------------------------------------------------------------

    private int mapX() {
        return MARGIN;
    }

    private int mapY() {
        return MARGIN;
    }

    private int mapW() {
        return Math.max(1, this.width - MARGIN * 2);
    }

    private int mapH() {
        return Math.max(1, this.height - MARGIN * 2 - BAR_H);
    }

    private boolean overMap(double mouseX, double mouseY) {
        return mouseX >= mapX() && mouseX < mapX() + mapW()
                && mouseY >= mapY() && mouseY < mapY() + mapH();
    }

    private int listX() {
        return mapX() + mapW() - LIST_W - 6;
    }

    private int listY() {
        return mapY() + 6 + BETA_H;
    }

    private int listH() {
        return ROW_H + zone.getShapes().size() * ROW_H;
    }

    private boolean overList(double mouseX, double mouseY) {
        return !zone.getShapes().isEmpty()
                && mouseX >= listX() && mouseX < listX() + LIST_W
                && mouseY >= listY() && mouseY < listY() + listH();
    }

    private boolean playerHere() {
        return this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.level().dimension().location().toString()
                        .equals(zone.getDimension());
    }

    private void centreOnPlayer() {
        if (this.minecraft == null || this.minecraft.player == null) return;

        view = view.centredOn(this.minecraft.player.getX(), this.minecraft.player.getZ());
        camera = camera.centredOn(this.minecraft.player.getX(), this.minecraft.player.getY(),
                this.minecraft.player.getZ());
        sceneStale = true;
    }

    private void frameZone() {
        view = ZoneMapView.fitting(zone.getShapes(), mapW(), mapH());
        camera = camera.centredOn(view.centreX(), shapeCentreY(), view.centreZ())
                .zoomed(view.blocksPerPixel() / camera.blocksPerPixel());
        sceneStale = true;
    }

    private void zoom(double factor) {
        if (tilted) {
            camera = camera.zoomed(factor);
            view = new ZoneMapView(view.centreX(), view.centreZ(), camera.blocksPerPixel(),
                    view.width(), view.height());
        } else {
            view = view.zoomed(factor);
            camera = camera.zoomed(view.blocksPerPixel() / camera.blocksPerPixel());
        }
        sceneStale = true;
    }

    /**
     * The height the tilted view turns around. Taken from the shapes rather than from the ground:
     * a zone hanging in the air would otherwise sit at the top edge of the picture.
     */
    private double shapeCentreY() {
        List<ZoneShape> shapes = zone.getShapes();
        if (shapes.isEmpty()) return 80;

        double sum = 0;
        for (ZoneShape shape : shapes) sum += shape.fromY();
        return sum / shapes.size() + 8;
    }

    /** Both views are kept on the same centre and scale, so the switch itself changes nothing. */
    private void setTilted(boolean value) {
        if (tilted == value) return;

        if (value) {
            camera = new ZoneOrbitCamera(view.centreX(), shapeCentreY(), view.centreZ(),
                    camera.yawDegrees(), camera.pitchDegrees(), view.blocksPerPixel(),
                    mapW(), mapH());
        } else {
            view = new ZoneMapView(camera.centreX(), camera.centreZ(), camera.blocksPerPixel(),
                    mapW(), mapH());
        }
        tilted = value;
        sceneStale = true;
    }

    // -------------------------------------------------------------------------------------
    // Terrain
    // -------------------------------------------------------------------------------------

    /**
     * Measures the ground once and then leaves it alone.
     *
     * <p>The first attempt followed the view: pan or zoom, and the ground was measured again at a
     * different resolution and from a different starting point. Cheaper on paper — only what is on
     * screen gets sampled — but it meant the landscape was never twice the same, and a map that
     * redraws itself under the cursor is worse than one that stops at an edge.
     *
     * <p>So one grid is laid over the zone and its surroundings when the screen opens, and that is
     * the map for as long as it stays open. Panning and zooming only move and scale a picture that
     * already exists. The only thing that ever changes afterwards is that holes get filled as their
     * chunks arrive — never anything already drawn.
     */
    private void refreshTerrain() {
        if (this.minecraft == null || this.minecraft.level == null || !playerHere()) {
            surface = null;
            return;
        }

        if (surface == null) {
            buildSurface();
            return;
        }

        if (!surface.anyUnknown() || ++sampleAge <= FILL_FRAMES) return;

        sampleAge = 0;
        if (ZoneTerrainSampler.fillUnknown(this.minecraft.level, surface) > 0) {
            terrain.upload(surface);
            mesh.upload(surface);
            sceneStale = true;
        }
    }

    /**
     * Lays the grid over the zone, its surroundings and wherever the player happens to be.
     *
     * <p>The player is taken in on purpose: <em>to me</em> would otherwise land on bare grid
     * whenever they are standing outside the zone, which is most of the time while building one.
     */
    private void buildSurface() {
        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        for (ZoneShape shape : zone.getShapes()) {
            ZoneMapView.Footprint f = ZoneMapView.footprint(shape);
            minX = Math.min(minX, f.minX());
            minZ = Math.min(minZ, f.minZ());
            maxX = Math.max(maxX, f.maxX());
            maxZ = Math.max(maxZ, f.maxZ());
        }
        if (this.minecraft.player != null) {
            minX = Math.min(minX, this.minecraft.player.getX());
            minZ = Math.min(minZ, this.minecraft.player.getZ());
            maxX = Math.max(maxX, this.minecraft.player.getX());
            maxZ = Math.max(maxZ, this.minecraft.player.getZ());
        }
        if (minX > maxX) {
            minX = -MIN_COVERAGE / 2.0;
            minZ = -MIN_COVERAGE / 2.0;
            maxX = MIN_COVERAGE / 2.0;
            maxZ = MIN_COVERAGE / 2.0;
        }

        double centreX = (minX + maxX) / 2;
        double centreZ = (minZ + maxZ) / 2;
        double span = Math.max(MIN_COVERAGE, Math.max(maxX - minX, maxZ - minZ) * 1.6);

        int step = 1;
        while (span / step > MAX_SAMPLES) step <<= 1;
        int cells = Math.min(MAX_SAMPLES, (int) Math.ceil(span / step) + 1);

        int originX = Math.floorDiv((int) Math.floor(centreX - span / 2), step) * step;
        int originZ = Math.floorDiv((int) Math.floor(centreZ - span / 2), step) * step;

        sampleAge = 0;
        surface = ZoneTerrainSampler.sample(this.minecraft.level, originX, originZ,
                step, cells, cells);
        terrain.upload(surface);
        sceneStale = true;
    }

    // -------------------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------------------

    /**
     * Own background, drawn in render — this dodges 1.21's menu blur, as the rest of the editor
     * does. Left to the default it would blur the world <em>and</em> lay itself over the map,
     * because {@code Screen#render} paints the background after everything drawn before it.
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, BACKDROP);

        view = view.resized(mapW(), mapH());
        camera = camera.resized(mapW(), mapH());
        refreshTerrain();
        if (toMe != null) toMe.active = playerHere();

        ZoneMapRenderer.backdrop(g, mapX(), mapY(), mapW(), mapH());

        g.enableScissor(mapX() + 1, mapY() + 1, mapX() + mapW() - 1, mapY() + mapH() - 1);
        if (tilted) {
            renderScene(g);
        } else {
            renderFlat(g);
        }
        g.disableScissor();

        if (!tilted) ZoneMapRenderer.scaleBar(g, this.font, mapX(), mapY(), mapW(), mapH(), view);
        renderBetaMark(g);
        renderList(g, mouseX, mouseY);
        renderBar(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderFlat(GuiGraphics g) {
        ZoneMapRenderer.terrain(g, mapX(), mapY(), view, terrain, surface);
        ZoneMapRenderer.grid(g, this.font, mapX(), mapY(), mapW(), mapH(), view, true);
        ZoneMapRenderer.shapes(g, this.font, mapX(), mapY(), view, zone.getShapes(), selected, true);

        if (playerHere() && this.minecraft.player != null) {
            boolean clamped = ZoneMapRenderer.player(g, mapX(), mapY(), mapW(), mapH(), view,
                    this.minecraft.player.getX(), this.minecraft.player.getZ(),
                    this.minecraft.player.getYRot());
            if (clamped) drawDistance(g);
        }
    }

    /**
     * The scene is rebuilt only when the camera has moved — while dragging that is every frame, and
     * standing still it is none.
     */
    private void renderScene(GuiGraphics g) {
        if (sceneStale || !camera.equals(sceneBuiltFor)) {
            boolean here = playerHere() && this.minecraft.player != null;
            scene.build(camera, zone.getShapes(), selected,
                    here, here ? this.minecraft.player.getX() : 0,
                    here ? this.minecraft.player.getY() : 0,
                    here ? this.minecraft.player.getZ() : 0,
                    here ? this.minecraft.player.getYRot() : 0,
                    minY(), maxY());
            sceneBuiltFor = camera;
            sceneStale = false;
        }

        // Wiped here rather than inside either drawer, because both put their pieces into the same
        // depth and only one of them may start by throwing it away. The clear obeys the scissor, so
        // only the map's own rectangle is touched.
        g.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, false);

        mesh.draw(g, camera, mapX(), mapY());

        // The map's own coordinates: the scene projects into the same box the flat view fills.
        g.pose().pushPose();
        g.pose().translate(mapX(), mapY(), 0);
        scene.draw(g);
        g.pose().popPose();
    }

    /** Written into the corner rather than beside the arrow: at the frame there is no room for it. */
    private void drawDistance(GuiGraphics g) {
        if (this.minecraft == null || this.minecraft.player == null) return;

        double dx = this.minecraft.player.getX() - view.centreX();
        double dz = this.minecraft.player.getZ() - view.centreZ();
        Component text = Component.translatable("editor.historystages.zone.map.player_away",
                (int) Math.round(Math.sqrt(dx * dx + dz * dz)));
        g.drawString(this.font, text, mapX() + 5, mapY() + mapH() - 12, LABEL_GREY, true);
    }

    /**
     * Sits over the map rather than beside it: this screen has no title bar to hang it from, and
     * the shape list occupies the same corner, so it goes above both with a shadow behind it.
     */
    private void renderBetaMark(GuiGraphics g) {
        Component mark = Component.translatable("editor.historystages.zone.beta");
        g.drawString(this.font, mark, mapX() + mapW() - this.font.width(mark) - 5, mapY() + 5,
                ACCENT, true);
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        List<ZoneShape> shapes = zone.getShapes();
        if (shapes.isEmpty()) return;

        int x = listX();
        int y = listY();
        g.fill(x, y, x + LIST_W, y + listH(), PANEL_BG);
        g.fill(x, y, x + LIST_W, y + 1, PANEL_EDGE);
        g.drawString(this.font, Component.translatable("editor.historystages.zone.section.shapes"),
                x + 5, y + 3, LABEL_GREY, false);

        for (int i = 0; i < shapes.size(); i++) {
            int rowY = y + ROW_H + i * ROW_H;
            boolean hot = i == selected;
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= rowY && mouseY < rowY + ROW_H;
            if (hot || hover) g.fill(x + 1, rowY, x + LIST_W - 1, rowY + ROW_H, ROW_HOT);
            g.drawString(this.font, (i + 1) + " · " + ZoneRowText.describeShape(shapes.get(i)),
                    x + 5, rowY + 2, hot ? ACCENT : TEXT, false);
        }
    }

    private void renderBar(GuiGraphics g, int mouseX, int mouseY) {
        int y = this.height - BAR_H;
        g.fill(0, y, this.width, this.height, BAR_BG);
        g.fill(0, y, this.width, y + 1, BAR_EDGE);

        int barY = y + (BAR_H - SegmentBar.height()) / 2;
        int hovered = SegmentBar.segmentAt(this.font, MARGIN, barY, mouseX, mouseY, modeLabels());
        modeBar.update(tilted ? 1 : 0, hovered, modeLabels().size());
        SegmentBar.draw(g, this.font, MARGIN, barY, modeLabels(), tilted ? 1 : 0, modeBar,
                new boolean[modeLabels().size()]);

        // Sat next to the buttons rather than pinned to the far edge. Right-aligned it drifted off
        // on its own whenever the window was wide, and a reading that belongs to the controls should
        // not look like it belongs to the window frame.
        Component readout = readout(mouseX, mouseY);
        int left = controlsRight() + 12;
        if (left + this.font.width(readout) <= this.width - MARGIN) {
            g.drawString(this.font, readout, left, y + 9, LABEL_GREY, false);
        }
    }

    /** Right edge of everything in the bar, so the readout knows where it may start. */
    private int controlsRight() {
        return controlsLeft() + 174;
    }

    private int controlsLeft() {
        return MARGIN + SegmentBar.width(this.font, modeLabels()) + 8;
    }

    /**
     * Tipped over there is no coordinate to give: a point on the screen is a line through the
     * world, and picking a spot on it would mean guessing which one the author meant.
     */
    private Component readout(int mouseX, int mouseY) {
        if (!playerHere()) return Component.translatable("editor.historystages.zone.map.other_world");
        if (tilted) return Component.translatable("editor.historystages.zone.map.orbit_hint");
        if (!overMap(mouseX, mouseY)) return Component.translatable("editor.historystages.zone.map.close");

        return Component.translatable("editor.historystages.zone.map.readout",
                (int) Math.floor(view.worldX(mouseX - mapX())),
                (int) Math.floor(view.worldZ(mouseY - mapY())),
                heightUnder(mouseX, mouseY));
    }

    /** The ground height under the cursor, or a question mark for a chunk nobody has loaded. */
    private String heightUnder(int mouseX, int mouseY) {
        if (surface == null) return "?";

        int col = Math.floorDiv((int) Math.floor(view.worldX(mouseX - mapX())) - surface.minX(),
                surface.step());
        int row = Math.floorDiv((int) Math.floor(view.worldZ(mouseY - mapY())) - surface.minZ(),
                surface.step());
        if (col < 0 || row < 0 || col >= surface.cols() || row >= surface.rows()) return "?";
        return surface.known(col, row) ? Integer.toString(surface.heightAt(col, row)) : "?";
    }

    // -------------------------------------------------------------------------------------
    // Input
    // -------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int barY = this.height - BAR_H + (BAR_H - SegmentBar.height()) / 2;
        int segment = SegmentBar.segmentAt(this.font, MARGIN, barY, mouseX, mouseY, modeLabels());
        if (segment >= 0) {
            setTilted(segment == 1);
            return true;
        }

        if (overList(mouseX, mouseY)) {
            int row = (int) ((mouseY - listY() - ROW_H) / ROW_H);
            if (row >= 0 && row < zone.getShapes().size()) choose(row);
            return true;
        }

        if (!overMap(mouseX, mouseY)) return false;

        int hit = shapeAt(mouseX, mouseY);
        if (hit >= 0) {
            choose(hit);
            return true;
        }

        dragging = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    /**
     * Which shape lies under the cursor.
     *
     * <p>Flat on that is a straight drop, and the topmost shape in the column is the one the author
     * sees. Tipped over the same question needs the line the camera actually looks along, which is
     * why the pick was written for a ray in the first place rather than for a rectangle.
     */
    private int shapeAt(double mouseX, double mouseY) {
        if (!tilted) {
            return ZoneRayPick.nearest(zone.getShapes(),
                    view.worldX(mouseX - mapX()), 1e6, view.worldZ(mouseY - mapY()),
                    0, -1, 0, minY(), maxY());
        }

        ZoneOrbitCamera.Vec origin = camera.rayOrigin(mouseX - mapX(), mouseY - mapY());
        ZoneOrbitCamera.Vec forward = camera.forward();
        return ZoneRayPick.nearest(zone.getShapes(), origin.x(), origin.y(), origin.z(),
                forward.x(), forward.y(), forward.z(), minY(), maxY());
    }

    /** First click picks, a second on the same shape opens its numbers. */
    private void choose(int index) {
        if (selected == index) {
            onOpenNumbers.accept(this, index);
            return;
        }
        selected = index;
        sceneStale = true;
        onSelected.accept(index);
    }

    private int minY() {
        return this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.getMinBuildHeight() : -64;
    }

    private int maxY() {
        return this.minecraft != null && this.minecraft.level != null
                ? this.minecraft.level.getMaxBuildHeight() : 320;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (!dragging) return super.mouseDragged(mouseX, mouseY, button, dx, dy);

        double movedX = mouseX - lastMouseX;
        double movedY = mouseY - lastMouseY;
        if (tilted) {
            // Dragging turns rather than shoves: on a tipped picture a shove has no one meaning,
            // because the ground plane no longer lines up with the screen.
            camera = camera.turnedBy(-movedX * ORBIT_PER_PIXEL, movedY * ORBIT_PER_PIXEL);
        } else {
            view = view.movedByPixels(movedX, movedY);
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!overMap(mouseX, mouseY)) return false;

        zoom(scrollY > 0 ? 0.8 : 1.25);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        terrain.close();
        mesh.close();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
