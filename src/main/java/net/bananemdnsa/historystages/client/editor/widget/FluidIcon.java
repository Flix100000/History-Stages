package net.bananemdnsa.historystages.client.editor.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;

/**
 * Draws a fluid the way a recipe viewer does: its own still texture, in its own colour.
 *
 * <p>The bucket would have been cheaper and is what most editors settle for, but a bucket is a
 * different thing from what it holds — and plenty of the fluids a modpack adds have no bucket at
 * all.
 *
 * <p>Only source fluids ever reach this. Every id comes from a recipe's own serialised form, and
 * a recipe names the source — the flowing twin the registry keeps beside it appears in no recipe,
 * so the duplicate-entry problem the fluid picker had to solve explicitly cannot arise here.
 *
 * <p>The texture reference a fluid hands out is already in atlas form
 * ({@code minecraft:block/water_still}), so it resolves against the block atlas the same way the
 * config editor's texture rows resolve theirs. Drawing the {@code .png} by its own path instead
 * would make the game load and upload it as a standalone texture, once per slot, while the user
 * scrolls.
 */
public final class FluidIcon {

    private FluidIcon() {
    }

    /**
     * Draws the fluid at {@code (x, y)}, {@code size} pixels square. Draws nothing at all for an
     * unknown id or a fluid with no usable texture — a missing-texture chequerboard inside a
     * recipe card would read as a fault in the recipe.
     */
    public static void draw(GuiGraphics g, String fluidId, int x, int y, int size) {
        Fluid fluid = fluidOf(fluidId);
        if (fluid == null) return;

        FluidVariant variant = FluidVariant.of(fluid);
        // The transfer api hands back the sprite rather than its name, so there is no atlas
        // lookup here. It answers null for a fluid whose mod never said what it looks like.
        TextureAtlasSprite sprite = FluidVariantRendering.getSprite(variant);
        if (sprite == null) return;

        int tint = FluidVariantRendering.getColor(variant);
        float alpha = (tint >> 24 & 0xFF) / 255.0f;
        float red = (tint >> 16 & 0xFF) / 255.0f;
        float green = (tint >> 8 & 0xFF) / 255.0f;
        float blue = (tint & 0xFF) / 255.0f;
        // Water's texture is grey; the tint is what makes it water. A tint with no alpha channel
        // set would otherwise render the fluid invisible rather than opaque.
        g.blit(x, y, 0, size, size, sprite, red, green, blue, alpha == 0 ? 1.0f : alpha);
    }

    /** The fluid's display name, or its id when there is no fluid behind the id. */
    public static String nameOf(String fluidId) {
        Fluid fluid = fluidOf(fluidId);
        if (fluid == null) return fluidId;
        return FluidVariantAttributes.getName(FluidVariant.of(fluid)).getString();
    }

    private static Fluid fluidOf(String fluidId) {
        if (fluidId == null || fluidId.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(fluidId);
        if (id == null) return null;
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        return fluid == null || fluid == Fluids.EMPTY ? null : fluid;
    }
}
