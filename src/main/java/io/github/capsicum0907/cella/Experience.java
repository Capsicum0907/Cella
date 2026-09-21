package io.github.capsicum0907.cella;

import net.minecraft.world.entity.player.Player;

public final class Experience {
    private Experience() {
    }

    public static int points(Player player) {
        return total(player.experienceLevel)
                + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    public static int total(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360.0);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220.0);
    }

    public static int take(Player player, int wanted) {
        int taken = Math.min(wanted, points(player));
        if (taken > 0) {
            player.giveExperiencePoints(-taken);
        }
        return Math.max(0, taken);
    }
}
