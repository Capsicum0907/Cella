package io.github.capsicum0907.cella;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * What a picked-up chest is carrying: the name of it, and how full it was.
 *
 * <p><b>The name is the whole of it and the numbers are a snapshot</b> — the contents
 * themselves are in {@link Kept}, for the three ceilings written there. But the client is
 * never told any of that, so anything a player is to be shown about a chest in their hand
 * has to travel on the item.
 *
 * <p><b>A snapshot that cannot go stale.</b> Filed contents are reachable by exactly one
 * operation, {@link Kept#take}, which hands the whole chest back and forgets it. Nothing
 * can put an item into a chest that is in somebody's pocket, so the count written when it
 * was picked up is still exact when it is put down. This is a summary that happens to be
 * the truth rather than an estimate of it.
 *
 * <p><b>Slots and not stacks.</b> How full a Cella is means how many of its slots are
 * spoken for: slots are the scarce thing here, and a chest of two hundred thousand of
 * them holding one item in each is full in the only sense that matters when you go to put
 * something away.
 *
 * <p>It is also the {@link TooltipComponent} the item hands the screen — the same fact,
 * so the same record, rather than a second one copied from it.
 */
public record Held(UUID chest, int used, int slots) implements TooltipComponent {
    private static final Codec<Held> FULL = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("chest").forGetter(Held::chest),
            Codec.INT.fieldOf("used").forGetter(Held::used),
            Codec.INT.fieldOf("slots").forGetter(Held::slots))
            .apply(instance, Held::new));

    /**
     * <b>A bare name is still read.</b> The component used to be the {@code UUID} alone,
     * and items written by that version are in worlds. Refusing them would not be a
     * cosmetic loss - the item would stop naming anything and its contents would sit in
     * the store with nothing left to ask for them. So an old one loads with no counts,
     * which shows no bar and is honest about knowing nothing.
     */
    public static final Codec<Held> CODEC = Codec.withAlternative(FULL,
            UUIDUtil.CODEC.xmap(chest -> new Held(chest, 0, 0), Held::chest));

    public static final StreamCodec<ByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Held::chest,
            ByteBufCodecs.VAR_INT, Held::used,
            ByteBufCodecs.VAR_INT, Held::slots,
            Held::new);

    /** Whether it knows how full it is at all; see the note on {@link #CODEC}. */
    public boolean counted() {
        return slots > 0;
    }

    /** Nought to one. Asked by the bar under the item and by the one in the tooltip. */
    public float filled() {
        return counted() ? (float) used / slots : 0.0F;
    }
}
