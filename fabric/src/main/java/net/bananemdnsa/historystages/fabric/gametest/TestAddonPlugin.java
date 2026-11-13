package net.bananemdnsa.historystages.fabric.gametest;

import net.bananemdnsa.historystages.api.HistoryStagesPlugin;
import net.bananemdnsa.historystages.api.lock.RegisterLockCategoriesEvent;
import net.bananemdnsa.historystages.data.diagnostics.ApiOwners;

/** Stand-in addon under the historystages entrypoint for AddonRegistrationTests. Records the call; registers nothing. */
public final class TestAddonPlugin implements HistoryStagesPlugin {
    static volatile boolean received;
    static volatile String owner;

    @Override
    public void registerLockCategories(RegisterLockCategoriesEvent event) {
        received = true;
        owner = ApiOwners.active();
    }
}
