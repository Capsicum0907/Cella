package io.github.capsicum0907.cella;

import java.util.EnumMap;
import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CellaConfig {
    public static final int PLAYER_COLUMNS = 9;

    private static final Map<Kind, ModConfigSpec.IntValue> ROWS = new EnumMap<>(Kind.class);
    private static final Map<Kind, ModConfigSpec.IntValue> COLUMNS = new EnumMap<>(Kind.class);

    public static final ModConfigSpec SPEC = build();

    private CellaConfig() {
    }

    private static ModConfigSpec.IntValue HISTORY;

    private static ModConfigSpec build() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        HISTORY = builder.defineInRange("history", 4096, 0, 1 << 20);
        for (Kind kind : Kind.values()) {
            builder.push(kind.id());
            ROWS.put(kind, builder.defineInRange("rows", kind.defaultRows(), 1, 12));
            COLUMNS.put(kind, builder
                    .defineInRange("columns", kind.defaultColumns(), 1, 18));
            builder.pop();
        }
        return builder.build();
    }

    public static int rows(Kind kind) {
        return ROWS.get(kind).get();
    }

    public static int columns(Kind kind) {
        return COLUMNS.get(kind).get();
    }

    public static int history() {
        return HISTORY != null && HISTORY.getSpec() != null && SPEC.isLoaded() ? HISTORY.get() : 0;
    }
}
