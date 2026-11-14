package net.bananemdnsa.historystages.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies a compat mixin only when the mod it targets is installed. With {@code defaultRequire: 1}
 * a compat mixin whose target is missing would stop the game; everything outside the packages
 * below always applies.
 *
 * <p>Asks the loader directly by reflection: mixin configs are read before Architectury or any
 * mod list of ours exists, and common may not name either loader.
 */
public final class HistoryStagesMixinPlugin implements IMixinConfigPlugin {

    /** Sub-package of {@code mixin} to the mod id it needs. Task 13 adds the "jetzt" integrations. */
    private static final Map<String, String> COMPAT_PACKAGES = Map.of(
            "jei", "jei",
            "bettercombat", "bettercombat",
            "emi", "emi",
            "fastsuite", "fastsuite");

    private String mixinPackage = "";

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage + ".";
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(mixinPackage)) return true;
        String relative = mixinClassName.substring(mixinPackage.length());
        int dot = relative.indexOf('.');
        if (dot < 0) return true;
        String modId = COMPAT_PACKAGES.get(relative.substring(0, dot));
        return modId == null || LOADED.computeIfAbsent(modId, HistoryStagesMixinPlugin::isModLoaded);
    }

    private static final Map<String, Boolean> LOADED = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean isModLoaded(String modId) {
        try {
            Class<?> fabric = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = fabric.getMethod("getInstance").invoke(null);
            return (boolean) fabric.getMethod("isModLoaded", String.class).invoke(loader, modId);
        } catch (ClassNotFoundException notFabric) {
            // NeoForge below.
        } catch (ReflectiveOperationException e) {
            return false;
        }
        try {
            Class<?> list = Class.forName("net.neoforged.fml.loading.LoadingModList");
            Object mods = list.getMethod("get").invoke(null);
            return mods != null && list.getMethod("getModFileById", String.class).invoke(mods, modId) != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
