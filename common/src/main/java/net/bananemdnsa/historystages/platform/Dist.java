package net.bananemdnsa.historystages.platform;

/**
 * Which side of the game a piece of code belongs to, named like NeoForge's {@code Dist} so the
 * {@code @EventBusSubscriber(value = Dist.CLIENT)} lines read the same as on NeoForge.
 */
public enum Dist {
    CLIENT,
    DEDICATED_SERVER;

    public boolean isClient() {
        return this == CLIENT;
    }

    public boolean isDedicatedServer() {
        return this == DEDICATED_SERVER;
    }
}
