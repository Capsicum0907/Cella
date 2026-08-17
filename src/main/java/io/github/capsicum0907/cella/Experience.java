package io.github.capsicum0907.cella;

import net.minecraft.world.entity.player.Player;

/**
 * Experience, counted in points, and taken off a player.
 *
 * <p><b>Points and not levels.</b> A level is worth a different number of points depending
 * on which level it is — seven at the bottom and over three hundred at the top — so levels
 * are a scale for reading and points are the thing there is more or less of. A chest that
 * counted levels would be worth a hundred times more to a player who had already earned
 * some, which is the opposite of what a threshold is for.
 *
 * <p><b>One way.</b> Nothing here puts experience back. What a Cella has been given is
 * what it fought for, and a chest that gave it back would be a bank — a different mod,
 * and one that would quietly become the reason to build this one.
 */
public final class Experience {
    private Experience() {
    }

    /**
     * How many points a player is holding.
     *
     * <p>Worked out from their level and how far into the next one they are, rather than
     * read off {@code totalExperience}. That field is a running tally which commands and
     * other mods can set the level out from under; the two the client draws are the ones
     * that are true.
     */
    public static int points(Player player) {
        return total(player.experienceLevel)
                + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    /**
     * The points it takes to reach a level from nothing.
     *
     * <p><b>Vanilla's own piecewise arithmetic, restated.</b> The game has no accessor for
     * it — {@code Player} exposes only the cost of the <em>next</em> level — so the three
     * pieces are written out here, and here only. They are checked against that accessor
     * in the tests at both joins, since a curve copied wrong is a curve that is only wrong
     * somewhere in the middle.
     */
    public static int total(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360.0);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220.0);
    }

    /**
     * What the level a player is standing on cost them to reach.
     *
     * <p><b>Not {@code getXpNeededForNextLevel}, which is the one above.</b> Levels are not
     * the same price going up as coming down: leaving level thirty costs 112 and arriving
     * at it cost 107, so taking the first of those drops a player two levels and leaves
     * them most of the way up the second. The points are right either way — nothing is
     * lost — but "one press, one level" stops being true, and that is what the press is
     * meant to mean.
     *
     * <p>Below the first level there is no level to give back, so it is the price of the
     * first one; {@link #take} then hands over whatever short of that they have.
     */
    public static int oneLevel(Player player) {
        int level = player.experienceLevel;
        return level > 0 ? total(level) - total(level - 1) : total(1);
    }

    /**
     * Takes up to that many points off a player, and says how many actually moved.
     *
     * <p>Less than asked for when they do not have it, and nothing at all when they have
     * none — which the caller has to handle rather than assume, because the amount is what
     * the chest is credited with. Handing over what you do not have is how a chest fills
     * itself from an empty player.
     *
     * <p>The removal is vanilla's own, negative: it is what {@code /xp remove} does, so
     * levels come down the way the game means them to.
     */
    public static int take(Player player, int wanted) {
        int taken = Math.min(wanted, points(player));
        if (taken > 0) {
            player.giveExperiencePoints(-taken);
        }
        return Math.max(0, taken);
    }
}
