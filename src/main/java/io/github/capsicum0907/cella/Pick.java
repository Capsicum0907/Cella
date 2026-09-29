package io.github.capsicum0907.cella;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Pick(int index, int how) implements CustomPacketPayload {
    public static final int TOGGLE = 0;
    public static final int ADD = 1;
    public static final int KIND = 2;

    public static final Type<Pick> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "pick"));

    public static final StreamCodec<ByteBuf, Pick> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Pick::index,
            ByteBufCodecs.VAR_INT, Pick::how,
            Pick::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
