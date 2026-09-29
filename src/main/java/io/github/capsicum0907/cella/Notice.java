package io.github.capsicum0907.cella;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Notice(String key) implements CustomPacketPayload {
    public static final Type<Notice> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "notice"));

    public static final StreamCodec<ByteBuf, Notice> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Notice::key,
            Notice::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
