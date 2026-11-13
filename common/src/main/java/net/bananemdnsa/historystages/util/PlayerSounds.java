package net.bananemdnsa.historystages.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * 1.21.1's {@code ServerPlayer.playNotifySound}, which 26.2 dropped: a sound only this player
 * hears, at their own position. Same packet the vanilla method sent.
 */
public final class PlayerSounds {

    private PlayerSounds() {}

    public static void notify(ServerPlayer player, SoundEvent sound, SoundSource source, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source,
                player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }
}
