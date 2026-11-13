package net.bananemdnsa.historystages.client.editor.graph;

/**
 * Turns a key press on the stage graph into what it means.
 *
 * <p>GLFW names letter keys after their position on a US keyboard, so on a German one the key
 * labelled Z arrives as {@code GLFW_KEY_Y}. Strg+Z would then sit on the key labelled Y. Letters
 * and the zoom keys are therefore matched by the character printed on the key
 * ({@code glfwGetKeyName}, passed in as {@code keyName}); the key code is only the fallback when
 * there is no name.
 *
 * <p>Key codes are spelled out instead of importing LWJGL, which keeps this testable.
 */
public final class GraphKeys {

    public enum Action {
        UNDO, REDO, SELECT_ALL, SELECT_NONE, SEARCH,
        REMOVE, FIT, ZOOM_IN, ZOOM_OUT, ZOOM_RESET, HELP
    }

    static final int SPACE = 32;
    static final int DELETE = 261;
    static final int F1 = 290;
    static final int KP_0 = 320;
    static final int KP_SUBTRACT = 333;
    static final int KP_ADD = 334;

    private static final int KEY_0 = 48;
    private static final int KEY_EQUAL = 61;
    private static final int KEY_MINUS = 45;
    private static final int KEY_A = 65;
    private static final int KEY_Z = 90;

    private GraphKeys() {}

    /**
     * @param keyName the layout's character for this key, or null when GLFW has none (for
     *                non-printable keys, or when the platform cannot say)
     * @return the action, or null when the key means nothing here
     */
    public static Action resolve(int keyCode, String keyName, boolean ctrl, boolean shift) {
        char label = label(keyCode, keyName);
        if (ctrl) {
            return switch (label) {
                case 'z' -> shift ? Action.REDO : Action.UNDO;
                case 'y' -> Action.REDO;
                case 'a' -> Action.SELECT_ALL;
                case 'd' -> Action.SELECT_NONE;
                case 'f' -> Action.SEARCH;
                default -> null;
            };
        }
        switch (keyCode) {
            case SPACE: return Action.FIT;
            case F1: return Action.HELP;
            case DELETE: return Action.REMOVE;
            case KP_ADD: return Action.ZOOM_IN;
            case KP_SUBTRACT: return Action.ZOOM_OUT;
            case KP_0: return Action.ZOOM_RESET;
            default: break;
        }
        // '=' as well as '+': on a US keyboard '+' is Shift+'=', and nobody should need Shift to zoom.
        return switch (label) {
            case '+', '=' -> Action.ZOOM_IN;
            case '-' -> Action.ZOOM_OUT;
            case '0' -> Action.ZOOM_RESET;
            default -> null;
        };
    }

    /** The character on the key, lower case; 0 when there is none worth matching. */
    private static char label(int keyCode, String keyName) {
        if (keyName != null) {
            return keyName.length() == 1 ? Character.toLowerCase(keyName.charAt(0)) : 0;
        }
        if (keyCode >= KEY_A && keyCode <= KEY_Z) return (char) ('a' + keyCode - KEY_A);
        return switch (keyCode) {
            case KEY_EQUAL -> '=';
            case KEY_MINUS -> '-';
            case KEY_0 -> '0';
            default -> 0;
        };
    }
}
