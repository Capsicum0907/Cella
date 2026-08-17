package io.github.capsicum0907.cella;

import java.util.EnumMap;
import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The tunable values. <b>How big a chest is is not one of them.</b>
 *
 * <p>It was, and it should not have been. A fusion pours what it ate into what it made,
 * and that fits because the ladder is built so that it fits exactly — eight Imperfects
 * are 1,728 slots and a Semi-Perfect is 1,728 slots. <b>Fitting is an invariant of the
 * design, not a coincidence of the numbers</b>, and a setting that lets it be broken
 * turns a failure that cannot happen into one that can: a merge with nowhere to put the
 * remainder, at a scale where the remainder is measured in thousands of item entities.
 * Nobody edits a number in a text file expecting their world to stop opening.
 *
 * <p>So capacity lives in {@link Kind} and nowhere else. What is left here is the shape
 * of a page, which <b>cannot break anything</b> — a page that does not divide the chest
 * makes the last one short, and a short page is drawn short. That is the line: a setting
 * may hold what cannot break an invariant.
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
                    .comment("Rows on one page. The screen is 114 pixels plus 18 a row, so",
                            "twelve is 330 and wants about 640x360 of GUI - a 1080p screen",
                            "at scale 3. It does NOT fit the automatic scale, which leaves",
                            "as little as 320x240; turn this down to 6 for that, or to 7 at",
                            "the very most.",
                            "This is in the wrong place and is known to be: how a chest is",
                            "cut into pages is a fact about looking at it, not about the",
                            "chest, so it belongs to whoever is looking and should follow",
                            "their window. Until it moves, one number has to suit everyone",
                            "on the server and this one suits a large one.")
                    .defineInRange("rows", kind.defaultRows(), 1, 12));
            COLUMNS.put(kind, builder
                    .comment("How wide a page is. Nine is a vanilla chest; wider is drawn by",
                            "this mod and the player's own inventory stays nine, in the",
                            "middle. The screen is 14 + 18 a column, so sixteen is 302 and",
                            "still inside the 320 that Minecraft's automatic GUI scale",
                            "leaves at its narrowest. Eighteen would be 338 and would not.")
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

}
