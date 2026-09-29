package io.github.capsicum0907.cella;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Move(int into) implements CustomPacketPayload {
    public static final Type<Move> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "move"));

    public static final StreamCodec<ByteBuf, Move> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Move::into,
            Move::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
