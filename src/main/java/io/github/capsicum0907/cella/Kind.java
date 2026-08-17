package io.github.capsicum0907.cella;

import java.util.function.Supplier;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ItemLike;

/**
 * The kinds of chest there are.
 *
 * <p><b>This list is the only place a kind is written down.</b> Everything that differs
 * between them is a column here — the id, the name, how big it is, what colour it is
 * stained, and the one ingredient that makes it that kind — and everything that reads
 * one reads it from here: registration, the block entity's size, the renderer's sheet,
 * the recipe, the language file, and the texture itself.
 *
 * <p><b>The texture too, which is why it is generated in Java.</b> It used to be drawn
 * by a script in {@code tools/}, which would have meant the colour of each kind living
 * in one language and everything else about it in another. Chest sheets are written by
 * {@code ChestSheets} at data generation now, from this. What is left in {@code tools/}
 * is the pictures that belong to no kind.
 *
 * <p>Adding one is a line here. Nothing else needs touching, and if something does, that
 * is the bug.
 */
public enum Kind {
    /**
     * The seven, along the way they grow.
     *
     * <p>A chain, not a ladder of equals, and <b>Cella Jr. comes off Perfect</b> rather
     * than sitting between two of the others: it is stronger than the second form by a
     * long way, which is why it is not where its size would otherwise put it.
     *
     * <p><b>Imperfect is not crafted.</b> Nothing makes one; a Laravel that has been fed
     * enough becomes one. That is why a formula is allowed to be absent rather than every
     * kind having to have one.
     *
     * <p><b>Capacity is meant to be what went into it.</b> Imperfect is four large chests
     * and each step after multiplies by what its recipe eats: eight, eight, four, four.
     * That reaches four thousand large chests at the top, and the numbers above are
     * <em>not</em> that yet — everything past Imperfect is still small, because the
     * screen sends every slot it has when it opens and thirteen thousand of them does not
     * fit in a packet. Raising them waits on the menu holding a page again rather than
     * the whole chest.
     *
     * <p><b>The colour is taken from the forms rather than chosen to be legible.</b> The
     * larva is sand, not green - it was drawn khaki and looks it. From there the green
     * starts dull and gets lighter and yellower every step. That ordering is the source
     * material's and it happens to solve the problem of five green chests being one green
     * chest: they are five steps of one ramp rather than five samples of one shade.
     *
     * <p>Junior is off the ramp because it is off the chain, and Max is red-brown because
     * it is barely the same creature.
     */
    LARAVEL("laravel", "Laravel Cella", 0xB89A5E, 3, 9, 1, () -> Formula
            .shaped("BPM",
                    "HCR",
                    "OSF")
            .key('B', () -> Items.BEEF)
            .key('P', () -> Items.PORKCHOP)
            .key('M', () -> Items.MUTTON)
            .key('H', () -> Items.PUFFERFISH)
            .key('C', () -> Blocks.CHEST)
            .key('R', () -> Items.RABBIT)
            .key('O', () -> Items.COD)
            .key('S', () -> Items.SPIDER_EYE)
            .key('F', () -> Items.ROTTEN_FLESH)
            .done()),

    /** Fed, not made. See {@link #formula()}. */
    IMPERFECT("imperfect", "Imperfect Cella", 0x5AA33C, 6, 9, 4, null),

    SEMI_PERFECT("semi_perfect", "Semi-Perfect Cella", 0x6FBA43, 5, 9, 5, () -> Formula
            .shaped("CCC",
                    "COC",
                    "CCC")
            .key('C', IMPERFECT)
            .key('O', () -> Blocks.OBSIDIAN)
            .done()),

    PERFECT("perfect", "Perfect Cella", 0x8ACF48, 6, 9, 8, () -> Formula
            .shaped("CCC",
                    "CGC",
                    "CCC")
            .key('C', SEMI_PERFECT)
            .key('G', () -> Blocks.GOLD_BLOCK)
            .done()),

    /**
     * Seven at a time, because that is how many of them there were - and the Perfect that
     * made them <b>is still standing there</b>, which is also what happened.
     */
    JUNIOR("junior", "Cella Jr.", 0x3FB39A, 6, 9, 6, () -> Formula
            .shaped("DDD",
                    "DCD",
                    "DDD")
            .key('D', () -> Blocks.DIAMOND_BLOCK)
            .key('C', PERFECT)
            .count(7)
            .spawning()
            .done()),

    SUPER_PERFECT("super_perfect", "Super Perfect Cella", 0xA3E052, 6, 12, 12, () -> Formula
            .shaped("CSC",
                    "SES",
                    "CSC")
            .key('C', PERFECT)
            .key('S', () -> Items.NETHER_STAR)
            .key('E', () -> Blocks.DRAGON_EGG)
            .done()),

    MAX("max", "Cella Max", 0x8A3A2E, 6, 15, 20, () -> Formula
            .shaped("CTC",
                    "TET",
                    "CTC")
            .key('C', SUPER_PERFECT)
            .key('T', () -> Items.TOTEM_OF_UNDYING)
            .key('E', () -> Items.ELYTRA)
            .done());

    private final String id;
    private final String name;
    private final int stain;
    private final int rows;
    private final int columns;
    private final int pages;
    private final Supplier<Formula> formula;

    /**
     * @param id    the registry path, and the file name of everything belonging to it
     * @param name  what it is called on screen, before anyone translates it
     * @param stain the one colour the whole sheet is derived from; see {@code ChestSheets}
     * @param rows  rows on one page
     * @param columns how wide a page is. <b>Nine is the vanilla chest and anything else
     *              is a screen this mod draws itself</b>, which it can, because the panel
     *              is drawn rather than blitted whole - see {@code CellaScreen}. The
     *              player's own inventory stays nine and sits in the middle.
     * @param pages pages in one chest — the config may say otherwise, this is the default
     * @param formula how it is made, or null for the one that is not made at all. A
     *              supplier for two reasons: a kind is built before the registries are,
     *              and several of these name other kinds - which an enum constant cannot
     *              do in its own argument list, but a lambda can put off until asked.
     */
    Kind(String id, String name, int stain, int rows, int columns, int pages,
            Supplier<Formula> formula) {
        this.id = id;
        this.name = name;
        this.stain = stain;
        this.rows = rows;
        this.columns = columns;
        this.pages = pages;
        this.formula = formula;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return name;
    }

    public int stain() {
        return stain;
    }

    /** What the config starts at, not what it is: read {@link CellaConfig#rows}. */
    public int defaultRows() {
        return rows;
    }

    public int defaultColumns() {
        return columns;
    }

    public int defaultPages() {
        return pages;
    }

    /**
     * How this one is made, or empty for the one that is not made.
     *
     * <p>Imperfect has none: a Laravel that has been fed enough becomes one, and a form
     * you grow into is not a form you lay out on a bench.
     */
    public java.util.Optional<Formula> formula() {
        return java.util.Optional.ofNullable(formula).map(Supplier::get);
    }

    /** Slots on one page, which is also how big the screen is drawn. */
    public int pageSize() {
        return CellaConfig.rows(this) * CellaConfig.columns(this);
    }

    /** Slots in a whole chest of this kind, as one is made today. */
    public int slots() {
        return pageSize() * CellaConfig.pages(this);
    }
}
