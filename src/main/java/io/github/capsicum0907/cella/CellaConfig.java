package io.github.capsicum0907.cella;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every tunable value lives here. Nothing else in the mod may hold a literal.
 *
 * <p>SERVER, not COMMON: how big a chest is is world state, and a client gets the
 * host's values.
 *
 * <p><b>These describe a chest being made, not a chest that exists.</b> A block that
 * has already been placed keeps the size it was built with — see
 * {@link CellaBlockEntity#loadAdditional}. Turning the numbers down does not reach
 * back into a world and throw away what was in the pages that would no longer fit.
 */
public final class CellaConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue ROWS = BUILDER
            .comment("Rows of nine on one page. Six is a large chest; the screen is drawn from",
                    "the vanilla chest texture, which has room for no more.")
            .push("size")
            .defineInRange("rows", 6, 1, 6);

    public static final ModConfigSpec.IntValue PAGES = BUILDER
            .comment("Pages in one chest. Eight pages of six rows is 432 slots.")
            .defineInRange("pages", 8, 1, 256);

    public static final ModConfigSpec SPEC = BUILDER.pop().build();

    private CellaConfig() {
    }

    /** Slots on one page, which is also how wide the window onto the contents is. */
    public static int pageSize() {
        return ROWS.get() * COLUMNS;
    }

    /** Nine, because the chest screen is nine wide and so is the player's inventory. */
    public static final int COLUMNS = 9;
}
