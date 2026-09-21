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

public class CellaItem extends BlockItem {
    public CellaItem(Block block, Properties properties) {
        super(block, properties);
    }

    private static final int BAR = 13;

    private static final int BLUE = 0x3B48D8;

    @Override
    public boolean canBeHurtBy(ItemStack stack, net.minecraft.world.damagesource.DamageSource source) {
        return !kind().trait().unbreakableAsAnItem() && super.canBeHurtBy(stack, source);
    }

    private static final int WIND_AT = 32;

    @Override
    public boolean onEntityItemUpdate(ItemStack stack,
            net.minecraft.world.entity.item.ItemEntity entity) {
        if (kind().trait().unbreakableAsAnItem()
                && entity.getAge() >= Math.min(WIND_AT, entity.lifespan / 2)) {
            entity.setExtendedLifetime();
        }
        return false;
    }

    @Override
    public void onDestroyed(net.minecraft.world.entity.item.ItemEntity entity,
            net.minecraft.world.damagesource.DamageSource source) {
        Kept.destroyed(entity.level(), entity.getItem());
    }

    public Kind kind() {
        return getBlock() instanceof CellaBlock chest ? chest.kind() : Kind.values()[0];
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return stack.has(CellaRegistry.KEPT.get()) ? 1 : super.getMaxStackSize(stack);
    }

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

        return held.used() > 0 ? Math.clamp(width, 1, BAR) : 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BLUE;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.ofNullable(stack.get(CellaRegistry.KEPT.get()))
                .filter(held -> held.counted() || held.grows())
                .map(held -> held);
    }

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

        if (held.grows()) {
            tooltip.add(Component.translatable("tooltip.cella.grown",
                            Math.round(held.grown() * 100.0F))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
