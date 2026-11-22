package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared cache + renderer for spinning {@link LivingEntity} previews used by the editor
 * widgets. Extracted from {@code SearchableEntityList} so non-widget screens (e.g. the
 * Auto-Trigger Editor) can show the same model previews.
 *
 * <p>On 1.20.1 {@link RenderSystem#getModelViewStack()} returns a {@link PoseStack}
 * (push/pop via {@code pushPose}/{@code popPose}), unlike 1.21 where it is a
 * {@code Matrix4fStack}; the spinning-render path below uses the 1.20.1 form.
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
            ResourceLocation rl = ResourceLocation.tryParse(entityId);
            if (rl == null) return null;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
            if (type == null) return null;
            Entity entity = type.create(mc.level);
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
            ResourceLocation rl = ResourceLocation.tryParse(entityId);
            EntityType<?> type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.get(rl);
            Entity entity = type == null ? null : type.create(mc.level);
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
            Entity entity = type.create(mc.level);
            boolean vehicle = entity instanceof Boat || entity instanceof AbstractMinecart;
            if (entity != null) entity.discard();
            return vehicle;
        } catch (Exception e) {
            return false;
        }
    }

    /** Renders {@code entity} spinning around the Y axis at screen position (x, y). */
    public static void renderSpinning(GuiGraphics g, int x, int y, int scale, float angleDegrees,
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

        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        try {
            modelViewStack.translate(0.0F, 0.0F, 1500.0F);
            RenderSystem.applyModelViewMatrix();

            PoseStack poseStack = new PoseStack();
            poseStack.translate((double) x, (double) y, -950.0D);
            poseStack.scale((float) scale, (float) scale, (float) scale);

            Quaternionf flipAndSpin = new Quaternionf().rotateZ((float) Math.PI);
            flipAndSpin.mul(new Quaternionf().rotateY(angleDegrees * ((float) Math.PI / 180.0F)));
            poseStack.mulPose(flipAndSpin);

            Lighting.setupForEntityInInventory();
            RenderSystem.disableDepthTest();

            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            dispatcher.overrideCameraOrientation(new Quaternionf());
            dispatcher.setRenderShadow(false);

            MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F,
                    poseStack, bufferSource, 15728880));
            bufferSource.endBatch();
            dispatcher.setRenderShadow(true);
            RenderSystem.enableDepthTest();
        } finally {
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();

            entity.yBodyRot = origBodyRot;
            entity.setYRot(origYRot);
            entity.setXRot(origXRot);
            entity.yHeadRotO = origHeadRotO;
            entity.yHeadRot = origHeadRot;
        }
    }
}
