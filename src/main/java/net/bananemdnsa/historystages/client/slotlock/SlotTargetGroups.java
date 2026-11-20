package net.bananemdnsa.historystages.client.slotlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Sorts slot-lock targets into one group per mod, Minecraft first, each under a caption row. The
 * caption is a marker string in the same list, because the picker lists plain strings.
 */
public final class SlotTargetGroups {

    private static final String HEADER = "\u0000group:";

    private SlotTargetGroups() {}

    public static List<String> group(List<String> ids, Function<String, String> modName) {
        Map<String, List<String>> byNamespace = new TreeMap<>();
        for (String id : ids) {
            int colon = id.indexOf(':');
            String namespace = colon > 0 ? id.substring(0, colon) : id;
            byNamespace.computeIfAbsent(namespace, k -> new ArrayList<>()).add(id);
        }
        List<String> namespaces = new ArrayList<>(byNamespace.keySet());
        namespaces.sort(Comparator.comparing((String ns) -> !ns.equals("minecraft"))
                .thenComparing(ns -> modName.apply(ns), String.CASE_INSENSITIVE_ORDER));

        List<String> out = new ArrayList<>();
        for (String namespace : namespaces) {
            out.add(header(namespace));
            List<String> members = byNamespace.get(namespace);
            members.sort(String.CASE_INSENSITIVE_ORDER);
            out.addAll(members);
        }
        return out;
    }

    public static String header(String namespace) {
        return HEADER + namespace;
    }

    public static boolean isHeader(String entry) {
        return entry.startsWith(HEADER);
    }

    public static String namespaceOf(String header) {
        return header.substring(HEADER.length());
    }
}
