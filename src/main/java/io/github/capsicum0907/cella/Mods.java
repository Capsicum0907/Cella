package io.github.capsicum0907.cella;

import net.neoforged.fml.ModList;

/**
 * What else is installed, asked in one place.
 *
 * <p>Nothing here changes what this mod <em>does</em> — only where it puts a button.
 * {@code @IPNPlayerSideOnly} already sorts out who owns which half of the screen; this
 * is the smaller question of who owns which end of a row.
 */
public final class Mods {
    /** Read from the mod's own metadata rather than from the file name it ships under. */
    private static final String INVENTORY_PROFILES = "inventoryprofilesnext";

    private Mods() {
    }

    /**
     * Whether Inventory Profiles Next is here.
     *
     * <p>Asked because it puts its own buttons at the right-hand end of the row beside
     * the player's inventory, and two mods in one corner is what started all of this.
     * When it is here, ours go on the left of that row and leave the corner alone; when
     * it is not, the corner is free and they sit where every other control on this
     * screen sits.
     *
     * <p>{@code isLoaded} and nothing more: no class of theirs is touched either way, so
     * there is no door for this guard to be standing behind.
     */
    public static boolean inventoryProfiles() {
        return ModList.get().isLoaded(INVENTORY_PROFILES);
    }
}
