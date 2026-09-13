package net.bananemdnsa.historystages.data.lock.spawn;

import java.util.List;

public record IdFilter(FilterMode mode, List<String> ids) {

    public IdFilter {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }

    public static IdFilter only(List<String> ids) {
        return new IdFilter(FilterMode.ONLY, ids);
    }

    public boolean allows(String id, List<String> tags) {
        return (mode == FilterMode.ONLY) == IdMatch.hits(ids, id, tags);
    }
}
