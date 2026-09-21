package io.github.capsicum0907.cella;

import java.util.function.Supplier;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ItemLike;

public enum Kind {
    LARVAL("larval", "Larval Cella", 0xB89A5E, 6, 9, 54, false, 1395, "imperfect", true, Trait.LARVA, () -> Formula
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

    IMPERFECT("imperfect", "Imperfect Cella", 0x5AA33C, 6, 9, 216, true, 1395, "", false, Trait.IMPERFECT, null),

    SEMI_PERFECT("semi_perfect", "Semi-Perfect Cella", 0x6FBA43, 6, 16, 1728, true, 5345, "", false, Trait.SEMI_PERFECT, () -> Formula
            .shaped("CCC",
                    "COC",
                    "CCC")
            .key('C', IMPERFECT)
            .key('O', () -> Blocks.OBSIDIAN)
            .done()),

    PERFECT("perfect", "Perfect Cella", 0x8ACF48, 12, 16, 13824, true, 30970, "super_perfect", false, Trait.PERFECT, () -> Formula
            .shaped("CCC",
                    "CGC",
                    "CCC")
            .key('C', SEMI_PERFECT)
            .key('G', () -> Blocks.GOLD_BLOCK)
            .done()),

    JUNIOR("junior", "Cella Jr.", 0x3FB39A, 12, 16, 3456, true, 0, "", false, Trait.PERFECT, () -> Formula
            .shaped("DDD",
                    "DCD",
                    "DDD")
            .key('D', () -> Blocks.DIAMOND_BLOCK)

            .key('C', PERFECT, "super_perfect")
            .count(7)
            .spawning()
            .done(), 0.5F),

    SUPER_PERFECT("super_perfect", "Super Perfect Cella", 0xA3E052, 12, 16, 55296, true, 149720, "", false, Trait.SUPER_PERFECT, null),

    MAX("max", "Cella Max", 0x8A3A2E, 12, 16, 221184, true, 0, "", false, Trait.SUPER_PERFECT, () -> Formula
            .shaped("CSC",
                    "STS",
                    "CSC")
            .key('C', SUPER_PERFECT)
            .key('S', () -> Items.NETHER_STAR)
            .key('T', () -> Items.TOTEM_OF_UNDYING)
            .done());

    private final float scale;
    private final String id;
    private final String name;
    private final int stain;
    private final int rows;
    private final int columns;
    private final int slots;
    private final boolean keeps;
    private final int growth;
    private final String becomes;
    private final boolean ripens;
    private final Trait trait;
    private final Supplier<Formula> formula;

    Kind(String id, String name, int stain, int rows, int columns, int slots,
            boolean keeps, int growth, String becomes, boolean ripens, Trait trait,
            Supplier<Formula> formula) {
        this(id, name, stain, rows, columns, slots, keeps, growth, becomes, ripens, trait,
                formula, 1.0F);
    }

    Kind(String id, String name, int stain, int rows, int columns, int slots,
            boolean keeps, int growth, String becomes, boolean ripens, Trait trait,
            Supplier<Formula> formula, float scale) {
        this.scale = scale;
        this.id = id;
        this.name = name;
        this.stain = stain;
        this.rows = rows;
        this.columns = columns;
        this.slots = slots;
        this.keeps = keeps;
        this.growth = growth;
        this.becomes = becomes;
        this.ripens = ripens;
        this.trait = trait;
        this.formula = formula;
    }

    public String id() {
        return id;
    }

    public float scale() {
        return scale;
    }

    public static java.util.Optional<Kind> named(String id) {
        for (Kind kind : values()) {
            if (kind.id().equals(id)) {
                return java.util.Optional.of(kind);
            }
        }
        return java.util.Optional.empty();
    }

    public static Kind biggest() {
        Kind biggest = values()[0];
        for (Kind kind : values()) {
            if (kind.slots() > biggest.slots()) {
                biggest = kind;
            }
        }
        return biggest;
    }

    public static Kind fitting(int slots) {
        Kind fits = null;
        for (Kind kind : values()) {
            if (kind.slots() >= slots && (fits == null || kind.slots() < fits.slots())) {
                fits = kind;
            }
        }
        return fits == null ? biggest() : fits;
    }

    public String displayName() {
        return name;
    }

    public int stain() {
        return stain;
    }

    public int defaultRows() {
        return rows;
    }

    public int defaultColumns() {
        return columns;
    }

    public boolean keeps() {
        return keeps;
    }

    public int growth() {
        return growth;
    }

    public boolean grows() {
        return growth > 0;
    }

    public java.util.Optional<Kind> becomes() {
        return named(becomes);
    }

    public boolean ripens() {
        return ripens;
    }

    public Trait trait() {
        return trait;
    }

    public String rawBecomes() {
        return becomes;
    }

    public java.util.Optional<Formula> formula() {
        return java.util.Optional.ofNullable(formula).map(Supplier::get);
    }

    public int pageSize() {
        return CellaConfig.rows(this) * CellaConfig.columns(this);
    }

    public int slots() {
        return slots;
    }

    public int pages() {
        int page = pageSize();
        return Math.max(1, (slots() + page - 1) / page);
    }
}
