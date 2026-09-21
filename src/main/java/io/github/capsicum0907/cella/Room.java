package io.github.capsicum0907.cella;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public record Room(int rows, int columns) implements CustomPacketPayload {
    public static final Type<Room> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "room"));

    public static final StreamCodec<ByteBuf, Room> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Room::rows,
            ByteBufCodecs.VAR_INT, Room::columns,
            Room::new);

    public static final Room ANY = new Room(64, 64);

    private static final Map<UUID, Room> TOLD = new ConcurrentHashMap<>();

    public static void remember(UUID who, Room room) {
        TOLD.put(who, room.believable());
    }

    public static void forget(UUID who) {
        TOLD.remove(who);
    }

    public static Room of(Player player) {
        return TOLD.getOrDefault(player.getUUID(), ANY);
    }

    private Room believable() {
        return new Room(Mth.clamp(rows, 1, ANY.rows), Mth.clamp(columns, 1, ANY.columns));
    }

    public int rowsFor(Kind kind) {
        return Math.min(CellaConfig.rows(kind), rows);
    }

    public int columnsFor(Kind kind) {
        return Math.min(CellaConfig.columns(kind), columns);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
