package io.github.capsicum0907.cella.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CellaClientConfig {
    private static final ModConfigSpec.BooleanValue AUTOMATIC;
    private static final ModConfigSpec.IntValue ROWS;
    private static final ModConfigSpec.IntValue COLUMNS;

    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("page");
        AUTOMATIC = builder.define("automatic", true);
        ROWS = builder.defineInRange("rows", 12, 1, 64);
        COLUMNS = builder.defineInRange("columns", 16, 1, 64);
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
