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
    private static final Map<Kind, ModConfigSpec.IntValue> SLOTS = new EnumMap<>(Kind.class);

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
                    .comment("Rows on one page. Six everywhere, and six is the ceiling.",
                            "The screen is 114 pixels plus 18 a row, and Minecraft's",
                            "automatic GUI scale leaves as little as 320x240 - which this",
                            "game's own default window of 854x480 does, at scale 2. So the",
                            "budget is 240, seven rows is 240 exactly and eight is 258,",
                            "which is one row off the top. Above six is for a large screen",
                            "and nothing else.")
                    .defineInRange("rows", kind.defaultRows(), 1, 12));
            COLUMNS.put(kind, builder
                    .comment("How wide a page is. Nine is a vanilla chest; wider is drawn by",
                            "this mod and the player's own inventory stays nine, in the",
                            "middle. The screen is 14 + 18 a column, so sixteen is 302 and",
                            "still inside the 320 that Minecraft's automatic GUI scale",
                            "leaves at its narrowest. Eighteen would be 338 and would not.")
                    .defineInRange("columns", kind.defaultColumns(), 1, 18));
            SLOTS.put(kind, builder
                    .comment("How big the chest is. Fifty-four is a large chest, and the",
                            "forms are written in those: four of them, then eight times",
                            "that, and so on. It need not divide by a page - the last one",
                            "is allowed to be short, and is drawn short.",
                            "An open screen costs one page however big this is, since that",
                            "is all the menu holds and all the server sends or walks. What",
                            "does grow with it is the sort and the two movers, which read",
                            "the whole chest once per press, and the memory a placed chest",
                            "takes up.")
                    .defineInRange("slots", kind.defaultSlots(), 1, 262144));
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

    public static int slots(Kind kind) {
        return SLOTS.get(kind).get();
    }
}
