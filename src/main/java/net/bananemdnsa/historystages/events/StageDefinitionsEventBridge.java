package net.bananemdnsa.historystages.events;

import java.util.concurrent.atomic.AtomicLong;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.stage.StageDefinitionsChangedEvent;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.platform.bus.SubscribeEvent;
import net.bananemdnsa.historystages.platform.bus.EventBusSubscriber;
import net.bananemdnsa.historystages.platform.bus.EventBus;
import net.bananemdnsa.historystages.platform.event.tick.ServerTickEvent;

/**
 * Posts {@link StageDefinitionsChangedEvent} at the end of a tick in which the definitions moved.
 *
 * <p>Not from the write sites themselves: loading puts stages in one at a time and announces each,
 * so a listener called from there would rebuild once per stage and see a half-loaded set every
 * time but the last. {@link StageLocks#definitionsVersion()} already counts every write, so the
 * tick only has to notice that it moved.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public final class StageDefinitionsEventBridge {

    /** Shared by both sides: in single player they read the same definitions. */
    private static final AtomicLong POSTED = new AtomicLong(-1);

    private StageDefinitionsEventBridge() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        postIfChanged();
    }

    /** Also called from the client tick. Posts at most once per change, whichever side is first. */
    public static void postIfChanged() {
        long now = StageLocks.definitionsVersion();
        long seen = POSTED.get();
        if (seen != now && POSTED.compareAndSet(seen, now)) {
            EventBus.post(new StageDefinitionsChangedEvent());
        }
    }
}
