package net.bananemdnsa.historystages.platform.event.client;

import net.bananemdnsa.historystages.platform.bus.Event;

public abstract class ClientTickEvent extends Event {

    protected ClientTickEvent() {}

    /** After the client has ticked, where the disguise table notices a stage change. */
    public static class Post extends ClientTickEvent {
        public Post() {}
    }
}
