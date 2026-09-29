package io.github.capsicum0907.cella;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Pick(int index, boolean toggle) implements CustomPacketPayload {
    public static final Type<Pick> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "pick"));

    public static final StreamCodec<ByteBuf, Pick> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Pick::index,
            ByteBufCodecs.BOOL, Pick::toggle,
            Pick::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
