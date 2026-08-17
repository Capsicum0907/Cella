package io.github.capsicum0907.cella;

import java.util.EnumMap;
import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Every tunable value lives here. Nothing else in the mod may hold a literal.
 *
 * <p>SERVER, not COMMON: how big a chest is is world state, and a client gets the host's
 * values.
 *
 * <p><b>One section per kind</b>, built by walking {@link Kind} rather than written out,
 * so a kind added there arrives here without anybody remembering to. The defaults come
 * from the kind itself.
 *
 * <p><b>These describe a chest being made, not a chest that exists.</b> A block that has
 * already been placed keeps the size it was built with — see
 * {@link CellaBlockEntity#loadAdditional}. Turning the numbers down does not reach back
 * into a world and throw away what was in the pages that would no longer fit.
 */
public final class CellaConfig {
    /** The player's own inventory, which is nine wide whatever the chest is. */
    public static final int PLAYER_COLUMNS = 9;

    private static final Map<Kind, ModConfigSpec.IntValue> ROWS = new EnumMap<>(Kind.class);
    private static final Map<Kind, ModConfigSpec.IntValue> COLUMNS = new EnumMap<>(Kind.class);
    private static final Map<Kind, ModConfigSpec.IntValue> PAGES = new EnumMap<>(Kind.class);

    public static final ModConfigSpec SPEC = build();

    private CellaConfig() {
    }

    /**
     * <p>The push and the pop have to pair up around every kind or the sections nest
     * inside one another — a config that loads and reads the wrong numbers, which is
     * worse than one that refuses. Hence the loop rather than a list of fields.
     */
    private static ModConfigSpec build() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        for (Kind kind : Kind.values()) {
            builder.push(kind.id());
            ROWS.put(kind, builder
                    .comment("Rows on one page. Six is a large chest and the screen is drawn",
                            "to fit, so more would work and would also be very tall.")
                    .defineInRange("rows", kind.defaultRows(), 1, 6));
            COLUMNS.put(kind, builder
                    .comment("How wide a page is. Nine is a vanilla chest; wider is drawn by",
                            "this mod and the player's own inventory stays nine, in the middle.")
                    .defineInRange("columns", kind.defaultColumns(), 1, 15));
            PAGES.put(kind, builder
                    .comment("Pages in one chest. What an open screen costs is one page,",
                            "however many there are - that is all the menu holds and all",
                            "the server sends or walks. The whole chest is only ever read",
                            "by the sort and the two movers, which is once per press.",
                            "So the ceiling here is a design question rather than a",
                            "technical one, and it has not been settled.")
                    .defineInRange("pages", kind.defaultPages(), 1, 32));
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

    public static int pages(Kind kind) {
        return PAGES.get(kind).get();
    }
}
