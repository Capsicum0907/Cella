package io.github.capsicum0907.cella;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Notice(String key, List<String> args) implements CustomPacketPayload {
    public static final Type<Notice> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "notice"));

    public static final StreamCodec<ByteBuf, Notice> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Notice::key,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Notice::args,
            Notice::new);

    public static Notice of(String key, Object... args) {
        return new Notice(key, java.util.Arrays.stream(args).map(String::valueOf).toList());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
