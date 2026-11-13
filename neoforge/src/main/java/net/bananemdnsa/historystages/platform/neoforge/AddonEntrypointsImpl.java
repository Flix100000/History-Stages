package net.bananemdnsa.historystages.platform.neoforge;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.api.HistoryStagesClientPlugin;
import net.bananemdnsa.historystages.api.HistoryStagesPlugin;
import net.bananemdnsa.historystages.platform.Dist;
import net.bananemdnsa.historystages.platform.Services;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

import java.lang.annotation.ElementType;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * NeoForge side of {@code platform.AddonEntrypoints}: finds the platform {@code @EventBusSubscriber}
 * in every loaded mod's scan data, the way NeoForge finds its own annotation, so addons keep their
 * 1.21.1 shape with only the imports swapped.
 */
public final class AddonEntrypointsImpl {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Type SUBSCRIBER = Type.getType(EventBusSubscriber.class);

    private AddonEntrypointsImpl() {}

    public static void discoverSubscribers() {
        Dist side = Services.dist();
        for (ModFileScanData scan : ModList.get().getAllScanData()) {
            String fileOwner = firstModId(scan);
            scan.getAnnotations().stream()
                    .filter(data -> SUBSCRIBER.equals(data.annotationType()) && data.targetType() == ElementType.TYPE)
                    .forEach(data -> register(data.clazz().getClassName(), fileOwner, side));
        }
    }

    private static void register(String className, String fileOwner, Dist side) {
        try {
            // Not initialised: the annotation decides first whether this side may touch the class.
            Class<?> type = Class.forName(className, false, AddonEntrypointsImpl.class.getClassLoader());
            EventBusSubscriber annotation = type.getAnnotation(EventBusSubscriber.class);
            if (annotation == null || !Arrays.asList(annotation.value()).contains(side)) return;
            String owner = annotation.modid().isEmpty() ? fileOwner : annotation.modid();
            // History Stages' own handlers are on the bus already (Handlers lists); the bus skips
            // a class it has seen, so they are not registered twice.
            EventBus.register(type, owner);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.error("[HistoryStages] Could not register event subscriber {}", className, e);
        }
    }

    private static String firstModId(ModFileScanData scan) {
        List<IModFileInfo> files = scan.getIModInfoData();
        if (files.isEmpty() || files.getFirst().getMods().isEmpty()) return null;
        return files.getFirst().getMods().getFirst().getModId();
    }

    public static void forEachPlugin(BiConsumer<String, HistoryStagesPlugin> body) {
        // NeoForge addons come in through discoverSubscribers.
    }

    public static void forEachClientPlugin(BiConsumer<String, HistoryStagesClientPlugin> body) {
    }
}
