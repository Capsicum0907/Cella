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
    /** Nine, because the chest screen is nine wide and so is the player's inventory. */
    public static final int COLUMNS = 9;

    private static final Map<Kind, ModConfigSpec.IntValue> ROWS = new EnumMap<>(Kind.class);
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
                    .comment("Rows of nine on one page. Six is a large chest; the screen is",
                            "drawn from the vanilla chest texture, which has room for no more.")
                    .defineInRange("rows", kind.defaultRows(), 1, 6));
            PAGES.put(kind, builder
                    .comment("Pages in one chest. Every slot is in the menu, not just the page",
                            "on show, so this is not free: opening a chest sends all of them",
                            "and each tick walks all of them. That is the price of other mods",
                            "being able to see the whole chest.")
                    .defineInRange("pages", kind.defaultPages(), 1, 32));
            builder.pop();
        }
        return builder.build();
    }

    public static int rows(Kind kind) {
        return ROWS.get(kind).get();
    }

    public static int pages(Kind kind) {
        return PAGES.get(kind).get();
    }
}
