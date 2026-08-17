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

/**
 * What a Cella's ending does to whatever is standing in it.
 *
 * <h2>Why this exists rather than borrowing the void's</h2>
 *
 * <p>It worked to hand the wave {@code damageSources().fellOutOfWorld()} — that source
 * carries exactly the bypasses this needs, which is why falling out of the world kills a
 * player in creative. But borrowing it has two prices, and both are the kind this mod does
 * not pay.
 *
 * <p><b>The screen said the wrong thing.</b> Standing in the End, watching a hundred
 * blocks of it disappear, and being told <i>fell out of the world</i>. The picture and the
 * words disagreed, which is the same fault as an empty frame drawn where there is no slot.
 *
 * <p>⚠ <b>And the behaviour was on loan.</b> Why the wave goes through invulnerability was
 * written down nowhere: it was a property of somebody else's damage type, and if that type
 * were ever retagged upstream this would quietly start behaving differently, with nothing
 * in this mod having changed. Declaring the type here puts the reason in the data, where it
 * can be read and where it is ours to keep.
 *
 * <h2>What it bypasses, and why every one of them</h2>
 *
 * <p>The instruction was that everything caught in it dies. Each of these is a way
 * something could have not died, so each is turned off: armour and enchantments and
 * resistance would reduce it, a shield would block it, <b>invulnerability</b> would spare a
 * creative-mode player and a totem, and <b>the cooldown</b> would let anything that
 * survived one hit sit out the rest behind its own invulnerability window.
 */
public final class Annihilation {
    private Annihilation() {
    }

    /** The one damage type this mod declares. */
    public static final ResourceKey<DamageType> KEY = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Cella.MODID, "annihilated"));

    /**
     * The message id, which is the second half of the translation key the death screen
     * builds: {@code death.attack.<id>}.
     */
    public static final String MESSAGE = "cella.annihilated";

    /** The type itself, as written into the mod's own datapack. See {@code CellaDataGen}. */
    public static final DamageType TYPE = new DamageType(MESSAGE, DamageScaling.NEVER, 0.0F);

    /**
     * Everything that would otherwise let something live through it.
     *
     * <p>Listed once, here, so the data generator and this class cannot come to different
     * conclusions about what the type is for.
     */
    public static final List<TagKey<DamageType>> BYPASSES = List.of(
            DamageTypeTags.BYPASSES_ARMOR,
            DamageTypeTags.BYPASSES_SHIELD,
            DamageTypeTags.BYPASSES_INVULNERABILITY,
            DamageTypeTags.BYPASSES_COOLDOWN,
            DamageTypeTags.BYPASSES_EFFECTS,
            DamageTypeTags.BYPASSES_ENCHANTMENTS,
            DamageTypeTags.BYPASSES_RESISTANCE);

    /**
     * The source, looked up in the world's own registry.
     *
     * <p>Damage types are datapack contents rather than code, so there is nothing to hold
     * on to between worlds — the holder has to be fetched from the level being played.
     */
    public static DamageSource by(Level level) {
        return new DamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(KEY));
    }
}
