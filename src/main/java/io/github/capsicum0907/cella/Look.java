package io.github.capsicum0907.cella;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Look(String looking) implements CustomPacketPayload {
    public static final Type<Look> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "look"));

    public static final int LONGEST = 50;

    public static final StreamCodec<ByteBuf, Look> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(LONGEST), Look::looking,
            Look::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
