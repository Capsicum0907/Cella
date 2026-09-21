package io.github.capsicum0907.cella;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;

/**
 * What a form is, beyond how much it holds.
 *
 * <h2>Written as what a step arrives at, not as what it gains</h2>
 *
 * <p>The ladder is cumulative — everything a form is, the one above it also is — and there
 * are two ways to write that down. Listing each step's <em>gains</em> and adding them up
 * reads the way the design was described, and it means <b>no single line ever says what a
 * form actually is</b>: to answer "how tough is a Perfect" you have to walk the chain and
 * hope nothing further down is edited later. So each of these is the whole answer for the
 * forms that share it, and the cumulative reading is a property of the numbers rather than
 * of the lookup: every column only ever goes up.
 *
 * <h2>⚠ Super Perfect is the ceiling, and that is a rule rather than a coincidence</h2>
 *
 * <p>There are five of these and seven forms. <b>Junior is a Perfect</b> in everything but
 * size, and <b>Max is a Super Perfect on purpose</b>: it is not waiting for something of
 * its own, it is not getting one.
 *
 * <p>The reason is not that the columns happen to be full — though they are, and that is
 * worth knowing: blast resistance is at bedrock, the tool is netherite, and wither immunity
 * and surviving as an item are both simply true, so there is no headroom left on any axis
 * Super Perfect did not already take. The reason is that <b>nothing may have a property
 * Super Perfect lacks</b>. A Perfect Cell is stronger than a Cell Max, and a ladder where
 * the last rung outdoes it would be saying otherwise.
 *
 * <p>So <b>Max buys room and nothing else</b>, which is also what it is: bigger, and less.
 * If a new column is ever added, it belongs to Super Perfect first or to nobody.
 *
 * <h2>⚠ Two of these are not the mechanism their name suggests</h2>
 *
 * <p><b>Fire resistance is the absence of an entry, not the presence of one.</b> Only
 * blocks handed to {@code FireBlock#setFlammable} burn, and no Cella was ever handed to it
 * — so every form was already fireproof and "Imperfect gains fire resistance" could not be
 * implemented by giving Imperfect anything. What makes it true is <b>registering Larval as
 * flammable</b>: the larva burns, and everything above it is what a block is by default.
 *
 * <p><b>The wither does not read blast resistance.</b> {@code WitherBoss#canDestroy} asks
 * only whether the block is in {@link BlockTags#WITHER_IMMUNE}. So "obsidian-tough, and the
 * wither still gets through it" is resistance alone with no tag — which is exactly what
 * obsidian itself is — and being safe from a wither is the tag and nothing else.
 */
public enum Trait {
    /**
     * The larva. It keeps nothing, it burns, and a stone axe takes it apart.
     *
     * <p>Every one of those is the same fact: it has what the others have and cannot use
     * any of it yet. See {@link Kind#keeps}.
     */
    LARVA(true, 2.5F, false, BlockTags.MINEABLE_WITH_AXE, null, 0, false, false, false),

    /** Fireproof, and it holds on to what is inside it. Cobblestone shrugs off a creeper. */
    IMPERFECT(false, 6.0F, false, BlockTags.MINEABLE_WITH_AXE, BlockTags.NEEDS_IRON_TOOL, 4, false,
            false, false),

    /** Obsidian-tough, and it reaches further for what it is owed. */
    SEMI_PERFECT(false, 1200.0F, false, BlockTags.MINEABLE_WITH_AXE,
            BlockTags.NEEDS_DIAMOND_TOOL, 8, false, true, false),

    /**
     * Not a box any more: a pickaxe job, and the wither is the only thing left that opens
     * it by force.
     */
    PERFECT(false, 1200.0F, false, BlockTags.MINEABLE_WITH_PICKAXE,
            BlockTags.NEEDS_DIAMOND_TOOL, 8, false, true, false),

    /**
     * As hard as the world's floor, safe from a wither, and <b>done taking anything in</b>.
     *
     * <p>The absorption goes, which is the one place this ladder subtracts. Partly because
     * it has finished growing and there is nothing above it to grow into — but mostly
     * because <b>a thing that has become complete has no reason to take in whatever happens
     * to be lying about</b>. Reaching stopped being what it is.
     */
    SUPER_PERFECT(false, 3600000.0F, true, BlockTags.MINEABLE_WITH_PICKAXE,
            Tags.Blocks.NEEDS_NETHERITE_TOOL, 0, true, true, true);

    private final boolean burns;
    private final float resistance;
    private final boolean witherproof;
    private final TagKey<Block> tool;
    private final TagKey<Block> level;
    private final int reach;
    private final boolean unbreakableAsAnItem;
    private final boolean finds;
    private final boolean keptOnDeath;

    /**
     * @param burns whether fire takes it — see the note above, this is the only true entry
     * @param resistance blast resistance: 2.5 is a chest, 6 is cobblestone, 1200 is
     *              obsidian, 3,600,000 is bedrock
     * @param witherproof whether it goes in {@link BlockTags#WITHER_IMMUNE}, which is the
     *              only thing a wither reads
     * @param tool which tag says what opens it — an axe while it is a box, a pickaxe once
     *              it is not
     * @param level how good that tool has to be, or null for "any"
     * @param reach how far it pulls experience orbs in, in blocks, or nought for a form
     *              that does not
     * @param unbreakableAsAnItem whether the dropped item goes away at all — <b>by any
     *              means, including time</b>. Fire and lava are vanilla's
     *              {@code fireResistant()}; explosions and cactus arrive as ordinary damage
     *              and are {@code CellaItem#canBeHurtBy}; and the five minutes every dropped
     *              item has left are {@code CellaItem#onEntityItemUpdate}.
     *              <p>⚠ One column rather than three, because a promise that a thing does
     *              not disappear is one promise. Splitting it would invite a form that
     *              survives being blown up and then quietly times out, which is the same
     *              loss arriving later.
     *              <p>⚠ <b>The void is the exception, and it is not a hole in the
     *              implementation.</b> Below the world the game calls {@code discard} on
     *              anything at all — a removal and not damage, so nothing an item can be
     *              made of refuses it. What the promise still covers is the part worth
     *              covering: the contents were filed the moment the chest was picked up,
     *              the void reports nothing, so they stay in {@link Kept} and
     *              {@code /cella kept give} can put them on a new item. <b>The name goes
     *              and the chest does not.</b>
     * @param finds whether its screen has a search box. ⚠ <b>It arrives where turning
     *              pages stops working</b>, not where that becomes unbearable: 1,728 slots
     *              is eighteen pages, and making somebody climb to 144 before offering
     *              relief is charging them for having got that far. A Larval is one page
     *              and an Imperfect four, and a box over those is a control that answers a
     *              question nobody had
     * @param keptOnDeath whether it gets up again with whoever was carrying it. ⚠ <b>Not a
     *              reading of {@code unbreakableAsAnItem}</b>: dying does not destroy what
     *              was being carried, it leaves it where the person was standing — which
     *              this mod's own ending makes a crater a hundred blocks across with
     *              nothing under it. See {@link Carried}
     */
    Trait(boolean burns, float resistance, boolean witherproof, TagKey<Block> tool,
            TagKey<Block> level, int reach, boolean unbreakableAsAnItem, boolean finds,
            boolean keptOnDeath) {
        this.burns = burns;
        this.resistance = resistance;
        this.witherproof = witherproof;
        this.tool = tool;
        this.level = level;
        this.reach = reach;
        this.unbreakableAsAnItem = unbreakableAsAnItem;
        this.finds = finds;
        this.keptOnDeath = keptOnDeath;
    }

    public boolean burns() {
        return burns;
    }

    public float resistance() {
        return resistance;
    }

    public boolean witherproof() {
        return witherproof;
    }

    public TagKey<Block> tool() {
        return tool;
    }

    /** The tag for how good a tool it takes, or empty for any. */
    public java.util.Optional<TagKey<Block>> level() {
        return java.util.Optional.ofNullable(level);
    }

    /** Whether a tool of the right sort is needed for it to drop anything at all. */
    public boolean particular() {
        return level != null;
    }

    /** How far it pulls experience orbs in. Nought is a form that does not reach. */
    public int reach() {
        return reach;
    }

    public boolean unbreakableAsAnItem() {
        return unbreakableAsAnItem;
    }

    /**
     * Whether the wave goes round it rather than through it — standing as a block, and
     * lying as an item.
     *
     * <p><b>Read off {@link #resistance} rather than written down a second time.</b> The
     * top of the ladder is set as hard as the world's floor, and "as hard as the world's
     * floor" is exactly the thing worth asking here: a form that was ever given that number
     * would survive for the reason it was given it, and every form below it is a box in the
     * way. Nothing else about a Cella earns it — <b>there is no reason for the wave to spare
     * a chest for being a chest.</b>
     *
     * <p>⚠ <b>Not the arithmetic an explosion does.</b> A vanilla explosion takes each
     * block's resistance off the ray passing through it and stops when the ray runs out,
     * which is why a hundred blocks cannot be blown up at any radius; see {@link Blast}.
     * This is a threshold and nothing is spent: the front crosses everything under the
     * floor's hardness at the same speed, and is turned aside by nothing else.
     */
    public boolean survivesAnnihilation() {
        return resistance >= Blocks.BEDROCK.getExplosionResistance();
    }

    /** Whether its screen offers a way to search it. */
    public boolean finds() {
        return finds;
    }

    /**
     * Whether it gets up again with whoever was carrying it when they died.
     *
     * <p><b>A different question from {@link #unbreakableAsAnItem}</b>, and not a reading
     * of it: a death does not destroy what was being carried, it puts it where the person
     * was standing. That is ordinarily somewhere they can walk back to — and it is not
     * here, because this mod's own ending kills them in a crater a hundred blocks across
     * with nothing under it. See {@link Carried}.
     */
    public boolean keptOnDeath() {
        return keptOnDeath;
    }
}
