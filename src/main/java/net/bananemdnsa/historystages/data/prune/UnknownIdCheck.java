package net.bananemdnsa.historystages.data.prune;

/**
 * Answers whether an id in a stage points at something that is not in the game, usually because
 * its mod was taken out of the pack. Kept as an interface so the pruning logic can be tested
 * without registries.
 *
 * <p>Implementations only see ids that passed {@link #isValidId}; a malformed id is a typo, not a
 * missing mod, and loading already reports it.
 */
public interface UnknownIdCheck {

    boolean isUnknownItem(String id);

    boolean isUnknownMod(String modId);

    boolean isUnknownEntity(String id);

    boolean isUnknownFluid(String id);

    boolean isUnknownEnchantment(String id);

    boolean isUnknownEffect(String id);

    /** A recipe only counts as unknown when its namespace is no loaded mod, see the spec. */
    boolean isUnknownRecipe(String id);

    boolean isUnknownBlock(String id);

    /**
     * Same character rules as a ResourceLocation with an explicit namespace, written out so this
     * stays free of Minecraft classes. Tags ({@code #...}) fail here on purpose.
     */
    static boolean isValidId(String id) {
        if (id == null) return false;
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) return false;
        for (int i = 0; i < colon; i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.')) return false;
        }
        for (int i = colon + 1; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' || c == '.' || c == '/')) {
                return false;
            }
        }
        return true;
    }

    /** Mod ids follow the namespace rules. */
    static boolean isValidModId(String modId) {
        return modId != null && isValidId(modId + ":x");
    }
}
