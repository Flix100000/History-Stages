package net.bananemdnsa.historystages.compat.lootr;

import noobanidus.mods.lootr.common.api.filter.ILootrFilter;
import noobanidus.mods.lootr.common.api.filter.ILootrFilterProvider;

import java.util.List;

/**
 * Hands {@link StageLootFilter} to Lootr. Found through
 * {@code META-INF/services/noobanidus.mods.lootr.common.api.filter.ILootrFilterProvider}, so the
 * public no-arg constructor is load-bearing.
 */
public final class StageLootFilterProvider implements ILootrFilterProvider {

    @Override
    public List<ILootrFilter> getFilters() {
        return List.of(new StageLootFilter());
    }
}
