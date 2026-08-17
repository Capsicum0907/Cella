package io.github.capsicum0907.cella.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * What this screen can show. <b>Client config, because it is about this screen.</b>
 *
 * <p>The other config is the server's and describes chests. This one describes a window,
 * so it is neither world state nor anybody else's business — two players on one server
 * can hold different values here and both be right. See {@code Room} for why that is not
 * a compromise.
 */
public final class CellaClientConfig {
    private static final ModConfigSpec.BooleanValue AUTOMATIC;
    private static final ModConfigSpec.IntValue ROWS;
    private static final ModConfigSpec.IntValue COLUMNS;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("page");
        AUTOMATIC = builder
                .comment("Work out how big a page can be from the window, every time it",
                        "changes. A larger window, or a smaller GUI scale, then shows more",
                        "of a chest and fewer pages of it.",
                        "This is a ceiling and not a shape: a chest is never cut into a",
                        "bigger page than its kind is meant to have, only a smaller one.")
                .define("automatic", true);
        ROWS = builder
                .comment("Rows to allow when automatic is false. The panel is 114 pixels",
                        "plus 18 a row, against the height your GUI scale leaves you.")
                .defineInRange("rows", 12, 1, 64);
        COLUMNS = builder
                .comment("Columns to allow when automatic is false. The panel is 14 pixels",
                        "plus 18 a column.")
                .defineInRange("columns", 16, 1, 64);
        builder.pop();
        SPEC = builder.build();
    }

    private CellaClientConfig() {
    }

    public static boolean automatic() {
        return AUTOMATIC.get();
    }

    public static int rows() {
        return ROWS.get();
    }

    public static int columns() {
        return COLUMNS.get();
    }
}
