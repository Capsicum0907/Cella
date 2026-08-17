package io.github.capsicum0907.cella;

import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * The chest as an item. It stacks while it is empty and not once it holds a chest.
 *
 * <p><b>Derived, not stamped.</b> The answer is worked out from whether the name is on
 * the stack, every time it is asked, rather than written onto the item at the moment it
 * is filled. Acervus had this the other way round and it was the bug: a value that has to
 * be written is a value that has somewhere it can be forgotten, and there is always one
 * more place that writes than the person counting them thinks.
 *
 * <p>Why refuse to stack at all, when {@link Kept} means there is nothing on the item to
 * duplicate: two items naming one chest are two views of one chest, and the first of them
 * to be placed takes the contents. That is defensible when a mod does it and unpleasant
 * when a stack of two does it in a player's hand. So they do not stack.
 */
public class CellaItem extends BlockItem {
    public CellaItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** Vanilla's own bar is thirteen pixels of it. */
    private static final int BAR = 13;

    private static final int BLUE = 0x3B48D8;

    /**
     * What a dropped one can be destroyed by, which for the top of the ladder is nothing.
     *
     * <p>{@code fireResistant()} covers fire and lava and stops there: an item entity is
     * also killed by explosions and by touching a cactus, and those arrive as ordinary
     * damage. ⚠ Which means the promise "it does not disappear" cannot be kept by the item
     * properties alone, and the half that is missing is the half that matters in a blast.
     *
     * <p>Blanket rather than a list of the three. A form that survives being set on fire
     * and blown up but not something added in a later version would be a promise with a
     * hole in it, and the hole would be wherever the game grew next.
     */
    @Override
    public boolean canBeHurtBy(ItemStack stack, net.minecraft.world.damagesource.DamageSource source) {
        return !kind().trait().unbreakableAsAnItem() && super.canBeHurtBy(stack, source);
    }

    /**
     * The five minutes a dropped item has, taken away.
     *
     * <p>⚠ <b>The commonest way a Cella is lost is not fire or a creeper, it is waiting.</b>
     * Everything else on this axis was already closed for the top of the ladder — burning,
     * blowing up, touching a cactus — and an item that survived all three and then timed
     * out would be the same loss arriving five minutes later.
     *
     * <p>It is also the biggest source of the orphans {@code /cella kept} exists to rescue,
     * and it is closed for exactly the chests worth rescuing: a Super Perfect is four
     * hundred thousand points of somebody's fighting, and there will never be many of them
     * lying about.
     *
     * <p>Set every tick rather than once, which costs a comparison and cannot be missed by
     * an item that arrived some other way.
     */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack,
            net.minecraft.world.entity.item.ItemEntity entity) {
        if (kind().trait().unbreakableAsAnItem()) {
            entity.setUnlimitedLifetime();
        }
        return false;
    }

    /** Which form this is. Everything that differs between them is asked of it. */
    private Kind kind() {
        return getBlock() instanceof CellaBlock chest ? chest.kind() : Kind.values()[0];
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stack.has(CellaRegistry.KEPT.get()) ? 1 : super.getMaxStackSize(stack);
    }

    /**
     * The little bar under the icon, so a full one can be told from an empty one without
     * hovering over it.
     *
     * <p>An item picked up empty carries nothing and shows nothing, which is the whole
     * distinction: a bar means there is a chest in there. An item written by an older
     * version carries a name and no counts, and shows nothing either - it does not know,
     * and drawing a bar at nought would be saying that it does.
     */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        Held held = stack.get(CellaRegistry.KEPT.get());
        return held != null && held.counted();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        Held held = stack.get(CellaRegistry.KEPT.get());
        if (held == null) {
            return 0;
        }
        int width = Math.round(held.filled() * BAR);
        // Something is not nothing. A single item in a Cella Max is a thousandth of a
        // pixel, and rounding it away would draw a chest with things in it as empty.
        return held.used() > 0 ? Math.clamp(width, 1, BAR) : 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BLUE;
    }

    /** The wide bar, drawn by {@code FillBar} on the client. */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.ofNullable(stack.get(CellaRegistry.KEPT.get()))
                .filter(held -> held.counted() || held.grows())
                .map(held -> held);
    }

    /**
     * The figures the bar cannot give: how many slots, out of how many.
     *
     * <p>A percent alone is no use at this size - six percent of a Cella Max is thirteen
     * thousand slots - and the raw pair is hard to picture, which is what the bar is for.
     * Neither replaces the other.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        Held held = stack.get(CellaRegistry.KEPT.get());
        if (held == null) {
            return;
        }
        if (held.counted()) {
            tooltip.add(Component.translatable("tooltip.cella.filled",
                            Held.count(held.used()), Held.count(held.slots()),
                            Math.round(held.filled() * 100.0F))
                    .withStyle(ChatFormatting.GRAY));
        }
        // A percent on its own, because the points behind it are not a figure anybody has
        // a feel for - a player knows how many levels they handed over and not how many
        // points those were worth.
        if (held.grows()) {
            tooltip.add(Component.translatable("tooltip.cella.grown",
                            Math.round(held.grown() * 100.0F))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
