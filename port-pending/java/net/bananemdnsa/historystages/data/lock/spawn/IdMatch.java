package net.bananemdnsa.historystages.data.lock.spawn;

import java.util.List;

final class IdMatch {

    private IdMatch() {}

    /** Configured tag entries carry a leading '#', the tags a biome reports do not. */
    static boolean hits(List<String> configured, String id, List<String> tags) {
        if (id != null && configured.contains(id)) return true;
        for (String tag : tags) {
            if (configured.contains("#" + tag)) return true;
        }
        return false;
    }
}
