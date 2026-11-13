package net.bananemdnsa.historystages.client.cache;

import java.util.Set;

/** The local player's view of permanently lost stages. Cleared on disconnect. */
public final class ClientLostStages {

    private static volatile Set<String> global = Set.of();
    private static volatile Set<String> individual = Set.of();

    private ClientLostStages() {}

    public static void set(Set<String> g, Set<String> i) {
        global = Set.copyOf(g);
        individual = Set.copyOf(i);
    }

    public static boolean isLost(String stageId, boolean individualStage) {
        return (individualStage ? individual : global).contains(stageId);
    }

    public static void clear() { set(Set.of(), Set.of()); }
}
