package net.bananemdnsa.historystages;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-independent entry point. Each loader's own entry class calls {@link #init()}; the old
 * NeoForge main class waits in port-pending until its parts move into common.
 */
public final class HistoryStages {
    public static final String MOD_ID = "historystages";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private HistoryStages() {
    }

    public static void init() {
        LOGGER.info("History Stages common init");
    }
}
