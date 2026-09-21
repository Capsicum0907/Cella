package io.github.capsicum0907.cella;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

public final class Annihilation {
    private Annihilation() {
    }

    public static final ResourceKey<DamageType> KEY = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Cella.MODID, "annihilated"));

    public static final String MESSAGE = "cella.annihilated";

    public static final DamageType TYPE = new DamageType(MESSAGE, DamageScaling.NEVER, 0.0F);

    public static final List<TagKey<DamageType>> BYPASSES = List.of(
            DamageTypeTags.BYPASSES_ARMOR,
            DamageTypeTags.BYPASSES_SHIELD,
            DamageTypeTags.BYPASSES_INVULNERABILITY,
            DamageTypeTags.BYPASSES_COOLDOWN,
            DamageTypeTags.BYPASSES_EFFECTS,
            DamageTypeTags.BYPASSES_ENCHANTMENTS,
            DamageTypeTags.BYPASSES_RESISTANCE);

    public static DamageSource by(Level level) {
        return new DamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(KEY));
    }
}
