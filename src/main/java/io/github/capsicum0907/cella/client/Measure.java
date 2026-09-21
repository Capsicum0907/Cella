package io.github.capsicum0907.cella.client;

import io.github.capsicum0907.cella.CellaMenu;
import io.github.capsicum0907.cella.Room;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class Measure {
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

    private static Room wanted(Minecraft client) {
        if (!CellaClientConfig.automatic()) {
            return new Room(CellaClientConfig.rows(), CellaClientConfig.columns());
        }
        return new Room(
                Math.max(1, CellaMenu.rowsIn(client.getWindow().getGuiScaledHeight())),
                Math.max(1, CellaMenu.columnsIn(client.getWindow().getGuiScaledWidth())));
    }
}
