package io.github.capsicum0907.cella;

import net.neoforged.fml.ModList;

public final class Mods {
    private static final String INVENTORY_PROFILES = "inventoryprofilesnext";

    private Mods() {
    }

    public static boolean inventoryProfiles() {
        return ModList.get().isLoaded(INVENTORY_PROFILES);
    }
}
