package io.github.capsicum0907.cella;

import java.util.List;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Owners(List<Integer> parts) implements CustomPacketPayload {
    public static final Type<Owners> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "owners"));

    public static final StreamCodec<ByteBuf, Owners> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Owners::parts,
            Owners::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
