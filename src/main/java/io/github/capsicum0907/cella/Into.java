package io.github.capsicum0907.cella;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Into(int index) implements CustomPacketPayload {
    public static final Type<Into> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "into"));

    public static final StreamCodec<ByteBuf, Into> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Into::index,
            Into::new);

    public static final int NONE = -1;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
