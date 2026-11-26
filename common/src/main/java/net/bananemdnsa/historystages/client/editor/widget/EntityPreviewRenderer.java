package net.bananemdnsa.historystages.client.editor.widget;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableEntityList;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared cache + renderer for spinning {@link LivingEntity} previews used by the editor
 * widgets. Extracted from {@code SearchableEntityList} so non-widget screens (e.g. the
 * Auto-Trigger Editor) can show the same model previews.
 */
public final class EntityPreviewRenderer {

    private EntityPreviewRenderer() {}

    private static final Map<String, LivingEntity> CACHE = new HashMap<>();
    private static final java.util.Set<EntityType<?>> FAILED = new java.util.HashSet<>();
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /**
     * Returns a cached client-side {@link LivingEntity} instance for the given entity id,
     * or {@code null} if the id is invalid, the type is not living, or no level is loaded.
     */
    public static LivingEntity getOrCreate(String entityId) {
        if (CACHE.containsKey(entityId)) return CACHE.get(entityId);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        try {
            Identifier rl = Identifier.tryParse(entityId);
            if (rl == null) return null;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(rl);
            if (type == null) return null;
            Entity entity = create(type);
            if (entity instanceof LivingEntity living) {
                CACHE.put(entityId, living);
                return living;
            }
            if (entity != null) entity.discard();
        } catch (Exception ignored) {
        }
        CACHE.put(entityId, null);
        return null;
    }

    /** Next id for a preview entity; negative, so it can never match an entity the server sent. */
    private static int nextPreviewId = -1;

    /**
     * A client-side copy of {@code type} that is never added to the level, or null.
     *
     * <p>26.2 hands out entity ids only when an entity joins a level, and the renderers ask for
     * the id (item models seed on it), so an unadded entity threw on its first frame. The spawn
     * checks are skipped as well: since 26.2 {@code create} refuses monsters on Peaceful, and a
     * picker should list them regardless of the editing player's difficulty.
     */
    public static Entity create(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity entity = type.create(mc.level, new net.minecraft.world.entity.EntitySpawnRequest(
                net.minecraft.world.entity.EntitySpawnReason.LOAD, true));
        if (entity != null) entity.setId(nextPreviewId--);
        return entity;
    }

    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();

    /**
     * Item icon for entity types that have no living model to spin, such as boats and
     * minecarts. Uses the entity's own pick-block item; empty when there is none.
     */
    public static ItemStack iconFor(String entityId) {
        ItemStack cached = ICON_CACHE.get(entityId);
        if (cached != null) return cached;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return ItemStack.EMPTY;
        ItemStack icon = ItemStack.EMPTY;
        try {
            Identifier rl = Identifier.tryParse(entityId);
            EntityType<?> type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getValue(rl);
            Entity entity = type == null ? null : create(type);
            if (entity != null) {
                ItemStack pick = entity.getPickResult();
                if (pick != null) icon = pick;
                entity.discard();
            }
        } catch (Exception ignored) {
        }
        ICON_CACHE.put(entityId, icon);
        return icon;
    }

    /** True for rideable vehicles (boats, minecarts) — no living model, but interactable. */
    public static boolean isVehicleType(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        try {
            Entity entity = create(type);
            boolean vehicle = entity instanceof VehicleEntity;
            if (entity != null) entity.discard();
            return vehicle;
        } catch (Exception e) {
            return false;
        }
    }

    /** Renders {@code entity} spinning around the Y axis at screen position (x, y). */
    public static void renderSpinning(GuiGraphicsExtractor g, int x, int y, int scale, float angleDegrees,
                                      LivingEntity entity) {
        float origBodyRot = entity.yBodyRot;
        float origYRot = entity.getYRot();
        float origXRot = entity.getXRot();
        float origHeadRotO = entity.yHeadRotO;
        float origHeadRot = entity.yHeadRot;

        entity.yBodyRot = 180.0F;
        entity.setYRot(180.0F);
        entity.setXRot(0.0F);
        entity.yHeadRot = 180.0F;
        entity.yHeadRotO = 180.0F;

        try {
            // GUI entities are drawn as a render state the GUI renders off-screen. Feet at
            // (x, y), with the box big enough to hold the mob.
            Quaternionf flipAndSpin = new Quaternionf().rotateZ((float) Math.PI);
            flipAndSpin.mul(new Quaternionf().rotateY(angleDegrees * ((float) Math.PI / 180.0F)));

            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            net.minecraft.client.renderer.entity.EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
            net.minecraft.client.renderer.entity.state.EntityRenderState state = renderer.createRenderState(entity, 1.0F);
            state.shadowPieces.clear();
            state.outlineColor = 0;
            int half = scale * 6;
            g.entity(state, scale, new org.joml.Vector3f(), flipAndSpin, new Quaternionf(),
                    x - half, y - half, x + half, y + half);
        } catch (RuntimeException e) {
            // Never thrown on to the caller: callers open a scissor around this call, and an
            // exception skipping their disableScissor clipped the rest of the screen away.
            if (FAILED.add(entity.getType())) {
                LOGGER.warn("[HistoryStages] Could not draw a preview of {}", entity.getType(), e);
            }
        } finally {
            entity.yBodyRot = origBodyRot;
            entity.setYRot(origYRot);
            entity.setXRot(origXRot);
            entity.yHeadRotO = origHeadRotO;
            entity.yHeadRot = origHeadRot;
        }
    }
}
