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

/**
 * How much of a chest one player's screen can show, and the message that says so.
 *
 * <p><b>How a chest is cut into pages is a fact about looking at it.</b> That sentence is
 * what the whole design rests on — the chest is a flat run of slots and a page is a way of
 * seeing them — and the mod spent a while contradicting it: the page shape was server
 * config, so the server decided how everyone's screen was cut up. One number then had to
 * suit every window on a server, and no number does. A screen 330 pixels tall wants a
 * 1080p monitor at scale 3; the window this game opens in has room for 240.
 *
 * <p>So the player says. The client works out what its window has room for, sends it, and
 * the server remembers it against that player. Two of them can read the same chest cut
 * two different ways at the same time, and nothing has to reconcile them, because there
 * was never anything to reconcile.
 *
 * <p><b>It is a ceiling, not a shape.</b> What a kind's page looks like is still the
 * kind's business - Larval is six by nine because a Larval should look like a chest -
 * and this only ever cuts that down. A big screen gets what the chest was designed to
 * show and no more; a small one gets fewer rows of it and more pages.
 */
public record Room(int rows, int columns) implements CustomPacketPayload {
    public static final Type<Room> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "room"));

    public static final StreamCodec<ByteBuf, Room> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Room::rows,
            ByteBufCodecs.VAR_INT, Room::columns,
            Room::new);

    /**
     * More than any screen will ask for, and the bound on what is believed.
     *
     * <p>Used for a player who has not said - an older client, or one that has not got
     * round to it yet - which means they get the kind's own shape and nothing is cut down.
     * That is the right default: the shape is a design decision and this is a limit on it,
     * so knowing nothing about the limit means not applying one.
     */
    public static final Room ANY = new Room(64, 64);

    /**
     * What each player last said, on the server.
     *
     * <p>Not saved. It describes a window, and a window is a fact about a session — a
     * player who comes back on a different machine says so again the moment they arrive.
     */
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

    /** What arrived is whatever was sent, so it is clamped before it is kept. */
    private Room believable() {
        return new Room(Mth.clamp(rows, 1, ANY.rows), Mth.clamp(columns, 1, ANY.columns));
    }

    /** The rows this kind is meant to have, or as many as will fit, whichever is fewer. */
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
