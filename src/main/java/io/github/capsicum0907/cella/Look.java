package io.github.capsicum0907.cella;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What a player typed into the search box, on its way to the chest.
 *
 * <p><b>The searching happens on the server because the chest is there.</b> The client
 * holds one page and has never been told the rest — that is the whole shape of this mod —
 * so it cannot answer "is there any lapis in here" itself, and asking it to would mean
 * sending it 221,184 slots to find out. What it sends instead is the word.
 *
 * <p><b>One way, like {@link Room}.</b> The answer does not need a message of its own: the
 * results arrive as the ordinary slot contents, which is what a page turn already does and
 * for the same reason. See {@code CellaMenu#look}.
 *
 * <p>⚠ <b>Bounded on arrival.</b> What comes off a network is whatever somebody chose to
 * send, and a query is walked against every non-empty slot of a chest that may hold two
 * hundred thousand of them. A megabyte of text would be a megabyte compared two hundred
 * thousand times. {@link #LONGEST} is well past any word a player means to type.
 */
public record Look(String looking) implements CustomPacketPayload {
    public static final Type<Look> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "look"));

    /**
     * Longer than any name in the game and far shorter than anything worth worrying about.
     * The box on the screen stops at the same figure, so nothing honest ever meets it.
     */
    public static final int LONGEST = 50;

    public static final StreamCodec<ByteBuf, Look> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(LONGEST), Look::looking,
            Look::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
