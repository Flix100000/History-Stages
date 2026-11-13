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
            Entity entity = type.create(mc.level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
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
            // 26.2 draws GUI entities as a render state the GUI renders off-screen; no direct
            // buffer access. Feet at (x, y) as before, with the box big enough to hold the mob.
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
        } finally {
            entity.yBodyRot = origBodyRot;
            entity.setYRot(origYRot);
            entity.setXRot(origXRot);
            entity.yHeadRotO = origHeadRotO;
            entity.yHeadRot = origHeadRot;
        }
    }
}
