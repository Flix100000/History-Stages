package net.bananemdnsa.historystages.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * 1.21.1 GUI calls that 26.2 removed or reshaped, kept in one place so the editor code reads the
 * way it did on the main branch.
 */
public final class LegacyGui {

    private LegacyGui() {}

    /** {@code I18n.exists}: 26.2 only has the language's own lookup. */
    public static boolean i18nExists(String key) {
        return Language.getInstance().has(key);
    }

    // Screen.isPaste & co. took only the key code and read the modifiers themselves.
    private static boolean shortcut(int keyCode, int key) {
        Minecraft mc = Minecraft.getInstance();
        return keyCode == key && mc.hasControlDown() && !mc.hasShiftDown() && !mc.hasAltDown();
    }

    public static boolean isPaste(int keyCode) {
        return shortcut(keyCode, GLFW.GLFW_KEY_V);
    }

    public static boolean isCopy(int keyCode) {
        return shortcut(keyCode, GLFW.GLFW_KEY_C);
    }

    public static boolean isCut(int keyCode) {
        return shortcut(keyCode, GLFW.GLFW_KEY_X);
    }

    public static boolean isSelectAll(int keyCode) {
        return shortcut(keyCode, GLFW.GLFW_KEY_A);
    }

    /** 1.21.1 {@code blit(texture, x, y, u, v, width, height, textureWidth, textureHeight)}. */
    public static void blit(GuiGraphicsExtractor g, Identifier texture, int x, int y, float u, float v,
                            int width, int height, int textureWidth, int textureHeight) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
    }

    /** 1.21.1 {@code blit(texture, x, y, width, height, u, v, srcWidth, srcHeight, textureWidth, textureHeight)}. */
    public static void blit(GuiGraphicsExtractor g, Identifier texture, int x, int y, int width, int height,
                            float u, float v, int srcWidth, int srcHeight, int textureWidth, int textureHeight) {
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, srcWidth, srcHeight,
                textureWidth, textureHeight);
    }
}
