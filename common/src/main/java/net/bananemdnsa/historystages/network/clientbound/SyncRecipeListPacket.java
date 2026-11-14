package net.bananemdnsa.historystages.network.clientbound;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.network.RecipeListSync;
import net.bananemdnsa.historystages.platform.IPayloadContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The server's recipe list for the editor, gzipped JSON; see {@link RecipeListSync}. */
public record SyncRecipeListPacket(byte[] gzippedJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncRecipeListPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_recipe_list"));

    public static final StreamCodec<FriendlyByteBuf, SyncRecipeListPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, msg) -> buffer.writeByteArray(msg.gzippedJson),
            buffer -> new SyncRecipeListPacket(buffer.readByteArray(RecipeListSync.MAX_COMPRESSED_BYTES)));

    public static void handle(SyncRecipeListPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> net.bananemdnsa.historystages.client.ClientRecipes.acceptServerList(msg.gzippedJson));
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
