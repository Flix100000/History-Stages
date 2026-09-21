package net.bananemdnsa.historystages.platform;

import net.minecraft.world.entity.player.Player;

/**
 * What a packet handler is given alongside its payload.
 *
 * <p>NeoForge hands over a context with a wide surface; all 48 handlers in this mod use exactly
 * two things from it, so that is what this carries. Keeping the parameter type and both method
 * names means not one handler body had to change.
 *
 * <p>{@link #enqueueWork(Runnable)} exists because the handlers are written to ask for it. On
 * NeoForge it hops from the network thread to the game thread. Fabric's networking API already
 * calls handlers on the game thread, so here it simply runs the work — but taking the call out of
 * 46 handlers would have been 46 chances to move a line out of the block it belonged in, and the
 * bodies would then differ from the other loader for no gain.
 */
public interface IPayloadContext {

    /**
     * The player this packet belongs to: the receiving player on the client, the sender on the
     * server. Handlers pattern-match it against ServerPlayer to tell the two apart, which is why
     * this is the common supertype rather than either specific one.
     */
    Player player();

    /** Runs work that has to be on the game thread. Already there on Fabric. */
    void enqueueWork(Runnable work);

    static IPayloadContext of(Player player) {
        return new IPayloadContext() {
            @Override
            public Player player() {
                return player;
            }

            @Override
            public void enqueueWork(Runnable work) {
                work.run();
            }
        };
    }
}
