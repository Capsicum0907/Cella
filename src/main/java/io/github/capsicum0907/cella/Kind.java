package io.github.capsicum0907.cella;

import java.util.function.Supplier;

import net.minecraft.world.item.Items;
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
     * <p>A chain, not a ladder of equals: each is made from the one before it, and
     * <b>Cella Jr. comes off Perfect</b> rather than sitting between two of the others.
     * It is stronger than the second form by a long way, which is why it is not where its
     * size would otherwise put it.
     *
     * <p>Capacity climbs with the form. The last stops at thirty-two pages because that
     * is where the config stops, and the config stops there because every slot is in the
     * menu — see the README.
     *
     * <p><b>So does the colour, and that is taken from the forms rather than chosen to be
     * legible.</b> The larva is sand, not green - it was drawn khaki and looks it. From
     * there the green starts dull and olive and gets lighter and yellower every step, up
     * to the two Perfects. That ordering is the source material's and it happens to solve
     * the problem of five green chests being one green chest: they are five steps of one
     * ramp rather than five samples of one shade.
     *
     * <p>The greens are saturated, not olive. A first pass muted them towards grey on the
     * reasoning that a texture wants to be quiet; the drawings are not quiet, and beside
     * them the muted ones read as dusty rather than as another form.
     *
     * <p>Junior is off the ramp because it is off the chain, and Max is red-brown because
     * it is barely the same creature.
     */
    LARAVEL("laravel", "Laravel Cella", 0xB89A5E, 3, 9, 1, () -> Items.COPPER_INGOT, null, 1),
    IMPERFECT("imperfect", "Imperfect Cella", 0x5AA33C, 4, 9, 3,
            () -> Items.GOLD_INGOT, () -> LARAVEL, 1),
    SEMI_PERFECT("semi_perfect", "Semi-Perfect Cella", 0x6FBA43, 5, 9, 5,
            () -> Items.EMERALD, () -> IMPERFECT, 1),
    PERFECT("perfect", "Perfect Cella", 0x8ACF48, 6, 9, 8,
            () -> Items.DIAMOND, () -> SEMI_PERFECT, 1),
    /**
     * Seven at a time, because that is how many of them there were.
     *
     * <p>Each is smaller than the Perfect it came from and seven of them are half as much
     * again, which is the whole reason to take the branch.
     */
    JUNIOR("junior", "Cella Jr.", 0x3FB39A, 6, 9, 6, () -> Items.EGG, () -> PERFECT, 7),
    SUPER_PERFECT("super_perfect", "Super Perfect Cella", 0xA3E052, 6, 12, 12,
            () -> Items.NETHERITE_INGOT, () -> PERFECT, 1),
    MAX("max", "Cella Max", 0x8A3A2E, 6, 15, 20,
            () -> Items.NETHER_STAR, () -> SUPER_PERFECT, 1);

    private final String id;
    private final String name;
    private final int stain;
    private final int rows;
    private final int columns;
    private final int pages;
    private final Supplier<ItemLike> core;
    private final Supplier<Kind> from;
    private final int count;

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
     * @param core  what surrounds the one before it, and what makes this kind this kind.
     *              A supplier because a kind is built before the registries are, so a
     *              constant here would be read too early.
     * @param from  the form this one grows out of, or null for the one that grows out of
     *              nothing. Also a supplier, and for a duller reason: an enum constant
     *              cannot name another in its own argument list, and a lambda puts the
     *              looking-up off until somebody asks.
     * @param count how many come out at once. One, except where seven did.
     */
    Kind(String id, String name, int stain, int rows, int columns, int pages,
            Supplier<ItemLike> core,
            Supplier<Kind> from, int count) {
        this.id = id;
        this.name = name;
        this.stain = stain;
        this.rows = rows;
        this.columns = columns;
        this.pages = pages;
        this.core = core;
        this.from = from;
        this.count = count;
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

    public ItemLike core() {
        return core.get();
    }

    /** What this grows out of, or empty for the one that grows out of nothing. */
    public java.util.Optional<Kind> from() {
        return java.util.Optional.ofNullable(from).map(Supplier::get);
    }

    public int count() {
        return count;
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
