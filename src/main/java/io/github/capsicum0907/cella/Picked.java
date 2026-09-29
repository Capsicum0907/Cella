package io.github.capsicum0907.cella;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Picked(List<Integer> indices) implements CustomPacketPayload {
    public static final Type<Picked> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "picked"));

    public static final StreamCodec<ByteBuf, Picked> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Picked::indices,
            Picked::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
