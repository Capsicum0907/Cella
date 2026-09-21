package io.github.capsicum0907.cella;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

public record Edit(int index, String name, int colour, int start, int length, boolean drop)
        implements CustomPacketPayload {
    public static final Type<Edit> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "edit"));

    public static final StreamCodec<ByteBuf, Edit> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Edit::index,
            ByteBufCodecs.stringUtf8(Plan.LONGEST), Edit::name,
            ByteBufCodecs.VAR_INT, Edit::colour,
            ByteBufCodecs.VAR_INT, Edit::start,
            ByteBufCodecs.VAR_INT, Edit::length,
            ByteBufCodecs.BOOL, Edit::drop,
            Edit::new);

    public static final int ADDING = -1;

    public static Edit removing(int index) {
        return new Edit(index, "", 0, 0, 1, true);
    }

    public static Edit adding(String name, DyeColor colour, int start, int length) {
        return new Edit(ADDING, name, colour.getId(), start, length, false);
    }

    public static Edit setting(int index, String name, DyeColor colour, int start, int length) {
        return new Edit(index, name, colour.getId(), start, length, false);
    }

    public Plan.Partition wanted() {
        return new Plan.Partition(name, DyeColor.byId(colour), start, Math.max(1, length));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
