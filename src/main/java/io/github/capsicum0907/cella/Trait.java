package io.github.capsicum0907.cella;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;

public enum Trait {
    LARVA(true, 2.5F, false, BlockTags.MINEABLE_WITH_AXE, null, 0, false, false, false, false),

    IMPERFECT(false, 6.0F, false, BlockTags.MINEABLE_WITH_AXE, BlockTags.NEEDS_IRON_TOOL, 4, false,
            false, false, false),

    SEMI_PERFECT(false, 1200.0F, false, BlockTags.MINEABLE_WITH_AXE,
            BlockTags.NEEDS_DIAMOND_TOOL, 8, false, true, false, true),

    PERFECT(false, 1200.0F, false, BlockTags.MINEABLE_WITH_PICKAXE,
            BlockTags.NEEDS_DIAMOND_TOOL, 8, false, true, false, true),

    SUPER_PERFECT(false, 3600000.0F, true, BlockTags.MINEABLE_WITH_PICKAXE,
            Tags.Blocks.NEEDS_NETHERITE_TOOL, 0, true, true, true, true);

    private final boolean burns;
    private final float resistance;
    private final boolean witherproof;
    private final TagKey<Block> tool;
    private final TagKey<Block> level;
    private final int reach;
    private final boolean unbreakableAsAnItem;
    private final boolean finds;
    private final boolean keptOnDeath;
    private final boolean divides;

    Trait(boolean burns, float resistance, boolean witherproof, TagKey<Block> tool,
            TagKey<Block> level, int reach, boolean unbreakableAsAnItem, boolean finds,
            boolean keptOnDeath, boolean divides) {
        this.divides = divides;
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

    public java.util.Optional<TagKey<Block>> level() {
        return java.util.Optional.ofNullable(level);
    }

    public boolean particular() {
        return level != null;
    }

    public int reach() {
        return reach;
    }

    public boolean unbreakableAsAnItem() {
        return unbreakableAsAnItem;
    }

    public boolean survivesAnnihilation() {
        return resistance >= Blocks.BEDROCK.getExplosionResistance();
    }

    public boolean divides() {
        return divides;
    }

    public boolean finds() {
        return finds;
    }

    public boolean keptOnDeath() {
        return keptOnDeath;
    }
}
