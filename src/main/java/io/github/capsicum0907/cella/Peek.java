package io.github.capsicum0907.cella;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Peek(int index, boolean open) implements CustomPacketPayload {
    public static final Type<Peek> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "peek"));

    public static final StreamCodec<ByteBuf, Peek> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Peek::index,
            ByteBufCodecs.BOOL, Peek::open,
            Peek::new);

    public static final int LIST = -1;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
