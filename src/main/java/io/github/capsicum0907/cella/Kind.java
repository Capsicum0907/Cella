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
     * <p><b>Two of them are not crafted.</b> A Laravel that has been fed enough becomes an
     * Imperfect, and a Perfect that has been fed enough and is then ended comes back a
     * Super Perfect. That is why a formula is allowed to be absent rather than every kind
     * having to have one — and why the test asks whether every form can be <em>reached</em>
     * rather than whether every form has a recipe.
     *
     * <p><b>Capacity is what went into it.</b> A large chest is fifty-four slots. Laravel
     * is one, Imperfect is four, and each step after multiplies by what its recipe eats —
     * eight, eight, four, four — so the chain runs 1, 4, 32, 256, 1024, 4096 large chests,
     * with Junior a quarter of the Perfect it came off.
     *
     * <p><b>The column is the capacity, and the number of pages follows from it.</b> Not
     * the other way round, which is how it was written first: how big the chest is is a
     * fact about the chest, and how it is cut into pages is a fact about looking at it.
     * Writing the pages down instead made the second decide the first, and it does not
     * even divide reliably — an earlier shape put Cella Max at 2,457 pages and a last one
     * holding fifty-four of its ninety squares. A short last page is fine and is drawn
     * short; bending the capacity so the pages come out round would not be.
     *
     * <p><b>The page grows at Semi-Perfect</b>, from nine columns to sixteen. Below that a
     * Cella is a chest and is drawn like one; from there up it is not, and the screen stops
     * pretending. 1,728 slots over a nine-wide page is thirty-two of them to turn, which is
     * where turning pages stops being a way of finding anything — and that is also the rung
     * where searching arrives, for the same reason.
     *
     * <p>⚠ <b>It is not the whole answer and was never meant to be.</b> Sixteen columns
     * takes Semi-Perfect from thirty-two pages to eighteen, and Perfect to a hundred and
     * forty-four. A bigger page helps with <em>looking through</em> a chest and does nothing
     * at all for <em>finding</em> something in one: eighteen pages still has to be read
     * eighteen times to answer "is there any lapis in here".
     *
     * <p><b>A hundred and ninety-two is not chosen for looking right.</b> Every capacity
     * from Perfect up has to divide by it, and those four are 27 × 2⁷, 2⁹, 2¹¹ and 2¹³ —
     * so the page has to divide 3,456, the smallest of them. Twelve by sixteen does.
     *
     * <p>⚠ <b>Twelve rows is 330 pixels and does not fit every window.</b> That is not a
     * fact about the chest and it should not be written down here at all: how a chest is
     * cut into pages is a fact about looking at it, and the page shape being server config
     * is the mod contradicting its own principle. It is meant to move to the client, where
     * it can follow the screen. Until it does, this is a default that suits a large window
     * and the small ones turn it down. See {@code CellaConfig}.
     *
     * <p>Cella Max therefore has 1,152 pages. <b>That number is a symptom and not a
     * problem to solve with the shape of a page</b> — no page a screen can hold makes
     * 221,184 slots navigable by turning them. What that wants is searching, which is
     * open.
     *
     * <p><b>Multiplying by what a recipe eats is addition, not fusion.</b> That is the
     * open question and it is deliberately left open here: four put together giving four
     * times the room is the part that is merely arithmetic, and whatever a form is worth
     * beyond that is not room. Nothing in this list buys anything but room yet.
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
    LARAVEL("laravel", "Laravel Cella", 0xB89A5E, 6, 9, 54, false, 1395, "imperfect", true, Trait.LARVA, () -> Formula
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

    /**
     * Seven at a time, because that is how many of them there were - and the Perfect that
     * made them <b>is still standing there</b>, which is also what happened.
     *
     * <p><b>A quarter of the Perfect it came from</b>, which is sixty-four large chests.
     * Seven of those out of one Perfect is a great deal of room for eight diamond blocks,
     * and it is meant to be: what it is not is more room than the Perfect, which would
     * make the recipe a machine for making storage out of nothing rather than a strong
     * reward for reaching Perfect.
     *
     * <p><b>A Super Perfect does as well</b>, and for the same eight diamond blocks. Not a
     * better deal — a second way in for somebody who no longer has the first. Growing a
     * Perfect into a Super Perfect is the hard part of this mod, and asking somebody who
     * has done it to build another Perfect from the bottom just to have a Jr. is asking
     * them to undo their own progress on paper. ⚠ <b>Max is deliberately not here</b>:
     * that one is not crafted, and a form you cannot make is not a form you spend.
     *
     * <p>The count does not change with the parent, because <b>the parent is not spent</b>
     * either way. What is being paid is the diamond, and that is the same on both paths.
     */
    JUNIOR("junior", "Cella Jr.", 0x3FB39A, 12, 16, 3456, true, 0, "", false, Trait.PERFECT, () -> Formula
            .shaped("DDD",
                    "DCD",
                    "DDD")
            .key('D', () -> Blocks.DIAMOND_BLOCK)
            // ⚠ By id, because Super Perfect is declared below this one and an enum
            // constant's own arguments cannot name one that is - qualifying it does not
            // help and neither does the lambda. See Formula.Builder#key.
            .key('C', PERFECT, "super_perfect")
            .count(7)
            .spawning()
            .done()),

    /**
     * <b>Not made either.</b> A Perfect that has taken in everything it can use and is then
     * ended in the End comes back as this; see {@link #becomes} and {@code Blast}.
     *
     * <p>It used to be four Perfects around a nether star and a dragon egg, and that recipe
     * was wrong twice over. <b>It said this form is an aggregation</b> — four of something
     * put together — when it is one that nearly died and came back, which is a thing that
     * happens to an individual and not to a pile. And ⚠ <b>it put a dragon egg in the
     * middle</b>, of which a world has exactly one, so Cella Max — four of these — needed
     * four eggs and could not be built at all.
     */
    SUPER_PERFECT("super_perfect", "Super Perfect Cella", 0xA3E052, 12, 16, 55296, true, 149720, "", false, Trait.SUPER_PERFECT, null),

    /**
     * ⚠ <b>The elytra and three of the totems are gone.</b> The old shape wanted a dragon
     * egg by way of Super Perfect, and a world has one — see {@link #SUPER_PERFECT}. What
     * is left is four of those, four nether stars and the one totem, all of which a player
     * can go and get again.
     */
    MAX("max", "Cella Max", 0x8A3A2E, 12, 16, 221184, true, 0, "", false, Trait.SUPER_PERFECT, () -> Formula
            .shaped("CSC",
                    "STS",
                    "CSC")
            .key('C', SUPER_PERFECT)
            .key('S', () -> Items.NETHER_STAR)
            .key('T', () -> Items.TOTEM_OF_UNDYING)
            .done());

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

    /**
     * @param id    the registry path, and the file name of everything belonging to it
     * @param name  what it is called on screen, before anyone translates it
     * @param stain the one colour the whole sheet is derived from; see {@code ChestSheets}
     * @param rows  rows on one page
     * @param columns how wide a page is. <b>Nine is the vanilla chest and anything else
     *              is a screen this mod draws itself</b>, which it can, because the panel
     *              is drawn rather than blitted whole - see {@code CellaScreen}. The
     *              player's own inventory stays nine and sits in the middle. Eighteen is
     *              338 pixels across, which is the widest here.
     * @param slots how big the chest is, in slots, and <b>the only place that is said</b>.
     *              Fifty-four is a large chest and the ladder is written in those: 54 is
     *              one, 216 is four, and so on up. The pages follow from this and the page
     *              size, and need not come out whole - see the note on the list above.
     * @param keeps whether it survives being broken with its contents inside. See
     *              {@link #keeps()}.
     * @param growth how much experience this form takes before it is done growing, in
     *              points, or nought for a form that takes none. See {@link #growth()}.
     * @param ripens whether being full is the whole of it. See {@link #ripens()}.
     * @param trait what this form is made of, beyond room. See {@link Trait}.
     * @param becomes the id of what it turns into once it is full, or empty for a form
     *              that has nowhere to go. <b>An id and not the constant</b>, because
     *              this one names a form further down the list and Java will not let an
     *              enum constant refer forwards to another, in a lambda or out of it. A
     *              test walks the column so that a name with no form behind it is found by
     *              the build rather than by somebody in the End. See {@link #becomes()}.
     * @param formula how it is made, or null for the one that is not made at all. A
     *              supplier for two reasons: a kind is built before the registries are,
     *              and several of these name other kinds - which an enum constant cannot
     *              do in its own argument list, but a lambda can put off until asked.
     */
    Kind(String id, String name, int stain, int rows, int columns, int slots,
            boolean keeps, int growth, String becomes, boolean ripens, Trait trait,
            Supplier<Formula> formula) {
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

    /**
     * The form with that id, or nothing.
     *
     * <p>Here because this list is the only place a kind is written down, and because
     * <b>nothing</b> is the right answer to both ways an id can fail to name one: an id
     * saved by a version of the mod that had a form this one does not, and an id that was
     * never written at all. Neither is a reason to throw. {@link Kept} reads ids off the
     * disk and {@code KeptCommand} reads them off a player, and both would rather ask than
     * catch.
     */
    public static java.util.Optional<Kind> named(String id) {
        for (Kind kind : values()) {
            if (kind.id().equals(id)) {
                return java.util.Optional.of(kind);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * The largest form there is.
     *
     * <p>Derived rather than named, so that adding a bigger one to the list is still only
     * a line in the list.
     */
    public static Kind biggest() {
        Kind biggest = values()[0];
        for (Kind kind : values()) {
            if (kind.slots() > biggest.slots()) {
                biggest = kind;
            }
        }
        return biggest;
    }

    /**
     * The smallest form a chest of that many slots fits in, or the largest there is if
     * none of them is big enough.
     *
     * <p>Asked when an orphan has to be handed back and nothing recorded which form it
     * came from. <b>A derivation from the size rather than a guess at the history</b> —
     * what it answers is "what would hold this", which is true, and not "what was this",
     * which is not knowable. The size that comes back is the filed one either way; see
     * {@link CellaBlockEntity#restore}.
     */
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

    /** What the config starts at, not what it is: read {@link CellaConfig#rows}. */
    public int defaultRows() {
        return rows;
    }

    public int defaultColumns() {
        return columns;
    }

    /**
     * Whether breaking one keeps what is inside it.
     *
     * <p><b>Everything but Laravel.</b> The larva has what the others have and cannot use
     * any of it yet, so it is the one that spills — and the arithmetic agrees, which is
     * why the line is here and not one step further up. What spills has to be pickable
     * up: a player carries thirty-six stacks and an item on the ground lasts five
     * minutes, so 54 slots is one and a half trips and 216 is six. Semi-Perfect's 1,728
     * would be forty-eight, which is not a mess to clear up, it is the contents being
     * destroyed by the act of moving the chest.
     *
     * <p>Where the kept contents go is {@link Kept}, and it is not the item.
     */
    public boolean keeps() {
        return keeps;
    }

    /**
     * How much experience this form takes before it has finished growing, in points.
     *
     * <p><b>Experience is what it fought for.</b> Not a currency and not a battery: the
     * player earns it by fighting and hands it over, and it never comes back out. So this
     * is a threshold rather than a capacity — the number at which the form has taken in
     * everything it can use, and something else becomes possible.
     *
     * <p><b>Nought means this form takes none</b>, which is most of them today. A form
     * with no use for experience must not show a bar that never moves, so nought is read
     * as "does not grow" everywhere rather than as "needs nothing".
     *
     * <p><b>Every figure here is a whole level in vanilla's own arithmetic</b>, and there
     * is a test that says so — 30, 30, 50, 100 and 200 — so a bar reaches its end exactly
     * when a level lands rather than somewhere in the middle of one.
     *
     * <p>⚠ <b>An Imperfect is fed twice.</b> Once as a larva, to grow up, and once as
     * itself, to be worth putting on a bench — growing up spends what it ate, so the two
     * thresholds add rather than the second absorbing the first. Both being thirty levels
     * is not sixty levels of chest, it is sixty levels per Imperfect, and there are
     * sixty-four of those in a Perfect.
     *
     * <p><b>Every form that is ever an ingredient has one</b>, because a recipe will not
     * accept a Cella that has not finished growing; see {@code Formula#grown}. The noughts
     * are the two forms that are neither: Max, which has nothing above it to grow into, and
     * <b>Junior, which nothing is made out of</b> — it is a Perfect in every other respect,
     * and this is the one place that stops short of saying so, because a threshold with
     * nothing on the far side of it is a bar that fills for no reason.
     *
     * <p>⚠ <b>The figures are a first pass and want playing with.</b> They compound: eight
     * full Imperfects go into a Semi-Perfect and eight of those into a Perfect, so a
     * number changed here is a number multiplied by sixty-four two rungs up.
     */
    public int growth() {
        return growth;
    }

    /** Whether experience means anything to this form. See {@link #growth()}. */
    public boolean grows() {
        return growth > 0;
    }

    /**
     * What it comes back as after destroying itself, or empty for a form that does not.
     *
     * <p><b>This is the step that is not arithmetic.</b> Every other rung is what its
     * recipe ate — four Perfects into a Super Perfect is four times the room because four
     * went in — and that is exactly what was wrong with it. A Super Perfect is not four
     * of anything put together; it is <em>one</em> that nearly died and came back, which
     * is a thing that happens to an individual rather than to a pile.
     *
     * <p>So what it costs is not more chests. It is the experience the form spent its life
     * taking in ({@link #growth}), and it is spent: what comes back has none of it.
     *
     * <p>⚠ <b>Which means the capacity of a form with this set is not what went into
     * it</b>, and the note at the top of this list does not cover it. Nothing was eaten.
     * That is the point, and it is the one place the ladder's arithmetic is deliberately
     * broken rather than accidentally.
     */
    public java.util.Optional<Kind> becomes() {
        return named(becomes);
    }

    /**
     * Whether being full is the whole of it.
     *
     * <p><b>The two changes in this list are not the same event, and that is the source
     * material's doing rather than the code's.</b> A larva that has eaten enough grows up,
     * which is what growing up is: nothing else happens and nothing is spent but time and
     * prey. Going from Perfect to Super Perfect is not growing up — it is nearly dying and
     * coming back, and that has to be done to it.
     *
     * <p>So a form that ripens turns the moment it is fed its last point, and one that
     * does not stands there full, waiting to be ended. <b>One column rather than two
     * mechanisms</b>: both read {@link #growth} to know when they are ready and
     * {@link #becomes} to know what for, and this says only which of the two ways.
     *
     * <p>Which is also why a form that ripens can never be lit — it is never standing
     * there full for anybody to put a star to.
     */
    public boolean ripens() {
        return ripens;
    }

    /**
     * What this form is, beyond how much it holds.
     *
     * <p>Junior shares Perfect's, which is the whole of what "Cella Jr. is a Perfect in
     * everything but size" means once it is written down. Max shares Super Perfect's until
     * it is given something of its own.
     */
    public Trait trait() {
        return trait;
    }

    /** The id as written, so a test can tell "names nothing" from "names something absent". */
    public String rawBecomes() {
        return becomes;
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

    /**
     * Slots in a whole chest of this kind, as one is made today.
     *
     * <p><b>Not settable.</b> Each step is exactly what its recipe ate, so a fusion has
     * room for everything it fused and the fit is an invariant rather than an accident.
     * See {@link CellaConfig} for why that means it cannot be a setting.
     */
    public int slots() {
        return slots;
    }

    /**
     * How many pages that comes to, the last one possibly short.
     *
     * <p>Derived rather than given. A chest is a number of slots and a page is a way of
     * cutting them up; whether the second divides the first is not something the first
     * should have to care about.
     */
    public int pages() {
        int page = pageSize();
        return Math.max(1, (slots() + page - 1) / page);
    }
}
