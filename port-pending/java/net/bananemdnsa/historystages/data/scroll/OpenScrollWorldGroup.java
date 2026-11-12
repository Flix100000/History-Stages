package net.bananemdnsa.historystages.data.scroll;

import java.util.List;

/**
 * One labelled block in the world chapter. {@code labelKey} is a lang key and also what tells the
 * screen how to name the ids. Those are mostly raw registry ids, but not all: zones are listed by
 * their pack-given name, and trade rows carry an encoded entry — see {@link OpenScrollContent}.
 */
public record OpenScrollWorldGroup(String labelKey, List<String> ids) {

    public OpenScrollWorldGroup {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}
