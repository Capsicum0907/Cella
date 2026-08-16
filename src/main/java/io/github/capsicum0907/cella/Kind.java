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
     * The first, and the one every world already has placed. <b>Its id does not change.</b>
     * Renaming it to something tiered would orphan every chest anybody has built.
     */
    CELLA("cella", "Cella", 0xA8332B, 6, 8, () -> Items.IRON_INGOT);

    private final String id;
    private final String name;
    private final int stain;
    private final int rows;
    private final int pages;
    private final Supplier<ItemLike> core;

    /**
     * @param id    the registry path, and the file name of everything belonging to it
     * @param name  what it is called on screen, before anyone translates it
     * @param stain the one colour the whole sheet is derived from; see {@code ChestSheets}
     * @param rows  rows of nine on one page
     * @param pages pages in one chest — the config may say otherwise, this is the default
     * @param core  the middle of the recipe, which is what makes it this kind rather than
     *              another. A supplier because a kind is built before the registries are,
     *              so a constant here would be read too early once one of these is a
     *              block of this mod's own rather than a vanilla ingot.
     */
    Kind(String id, String name, int stain, int rows, int pages, Supplier<ItemLike> core) {
        this.id = id;
        this.name = name;
        this.stain = stain;
        this.rows = rows;
        this.pages = pages;
        this.core = core;
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

    public int defaultPages() {
        return pages;
    }

    public ItemLike core() {
        return core.get();
    }

    /** Slots on one page, which is also how tall the screen is drawn. */
    public int pageSize() {
        return CellaConfig.rows(this) * CellaConfig.COLUMNS;
    }

    /** Slots in a whole chest of this kind, as one is made today. */
    public int slots() {
        return pageSize() * CellaConfig.pages(this);
    }
}
