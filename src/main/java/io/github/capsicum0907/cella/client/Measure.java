package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.CellaMenu;
import io.github.capsicum0907.cella.Room;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Measures the window and tells the server what will fit on it.
 *
 * <p><b>Every tick, and sent only when the answer changes.</b> The obvious place to send
 * this is once, on joining, and that is wrong for the one thing it exists for: the window
 * is resized, made fullscreen, and has its GUI scale changed, all while the player is
 * standing there. A value sent once is a value that is wrong from the first time they
 * drag a corner. Comparing two small integers twenty times a second costs nothing; a
 * packet only leaves when they differ.
 *
 * <p>Nothing waits for the answer. A chest opened in the same tick as a resize is cut to
 * the previous size, which is one screen out of date and then correct again. It is a
 * ceiling on a layout, not a fact anything depends on being exact.
 */
public final class Measure {
    /** What was last sent, so that nothing is sent twice. Cleared on leaving a world. */
    private static Room told;

    private Measure() {
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            told = null;
            return;
        }
        Room room = wanted(client);
        if (!room.equals(told)) {
            told = room;
            PacketDistributor.sendToServer(room);
        }
    }

    /**
     * As much as the window has room for, or what was asked for by hand.
     *
     * <p>The arithmetic is {@code CellaMenu}'s, run backwards - it is the same rectangle
     * the screen is drawn into, so there is one place that knows how big a panel of a
     * given number of rows comes out.
     */
    private static Room wanted(Minecraft client) {
        if (!CellaClientConfig.automatic()) {
            return new Room(CellaClientConfig.rows(), CellaClientConfig.columns());
        }
        return new Room(
                Math.max(1, CellaMenu.rowsIn(client.getWindow().getGuiScaledHeight())),
                Math.max(1, CellaMenu.columnsIn(client.getWindow().getGuiScaledWidth())));
    }
}
