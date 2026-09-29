package io.github.capsicum0907.cella;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record Assign(int index) implements CustomPacketPayload {
    public static final Type<Assign> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "assign"));

    public static final StreamCodec<ByteBuf, Assign> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Assign::index,
            Assign::new);

    public static Assign toggled(int index, int assigned) {
        return new Assign(index == assigned ? Plan.NONE : index);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
