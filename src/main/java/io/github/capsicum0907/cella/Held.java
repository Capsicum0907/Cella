package io.github.capsicum0907.cella;

import java.util.List;
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
 * <p><b>A snapshot, and it is exact for whichever item gets there first.</b> Filed contents
 * are reachable by exactly one operation, {@link Kept#take}, which hands the whole chest
 * back and forgets it. Nothing can put an item into a chest that is in somebody's pocket,
 * so the count written when it was picked up is still what comes out when it is put down.
 *
 * <p>⚠ <b>This used to say the snapshot could not go stale, and that is no longer true.</b>
 * It was true while the only way to hold a name was to have picked the chest up: one chest,
 * one item, and two of them only if some other mod duplicated a stack. {@code /cella kept
 * give} hands out a name without spending it, so the mod itself can now mint a second item
 * for a chest that already has one — and the loser of that race carries a tooltip
 * describing contents it will not deliver. The figures on it were true when it was written
 * and stay written; what changed is that something else may have taken them since.
 *
 * <p>Nothing on the item can fix that, because the item is on a client and the client is
 * never told what the store holds. What can be done is not to make the state quietly:
 * {@code KeptCommand} says so when it hands out a name that has been handed out before.
 *
 * <p><b>Slots and not stacks.</b> How full a Cella is means how many of its slots are
 * spoken for: slots are the scarce thing here, and a chest of two hundred thousand of
 * them holding one item in each is full in the only sense that matters when you go to put
 * something away.
 *
 * <p>It is also the {@link TooltipComponent} the item hands the screen — the same fact,
 * so the same record, rather than a second one copied from it.
 */
public record Held(List<UUID> chests, int used, int slots, int experience, int growth)
        implements TooltipComponent {
    /**
     * <b>Several names, because a fusion carries what it ate.</b> Eight Imperfects go into
     * a Semi-Perfect and their eight chests go with them — poured in when the new one is
     * put down, not when it is crafted, so that laying the ingredients on a bench to look
     * at the result does not file anything anywhere. See {@link Fusing}.
     *
     * <p><b>The two experience figures are optional rather than a fourth alternative.</b>
     * Items written before a Cella could be fed have neither, and a missing field reading
     * as nought is exactly right for them: nothing had been fed, and nothing was growing.
     * An alternative codec would have said the same thing at four times the width.
     */
    private static final Codec<Held> FULL = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.listOf().fieldOf("chests").forGetter(Held::chests),
            Codec.INT.fieldOf("used").forGetter(Held::used),
            Codec.INT.fieldOf("slots").forGetter(Held::slots),
            Codec.INT.optionalFieldOf("experience", 0).forGetter(Held::experience),
            Codec.INT.optionalFieldOf("growth", 0).forGetter(Held::growth))
            .apply(instance, Held::new));

    /** The one-name form this had before fusions existed. */
    private static final Codec<Held> ONE = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("chest").forGetter(held -> held.chests().getFirst()),
            Codec.INT.fieldOf("used").forGetter(Held::used),
            Codec.INT.fieldOf("slots").forGetter(Held::slots))
            .apply(instance, (chest, used, slots) -> new Held(List.of(chest), used, slots, 0, 0)));

    /**
     * <b>Older forms are still read.</b> This was a bare {@code UUID} first and a single
     * named chest after that, and items written by both are in worlds. Refusing one would
     * not be a cosmetic loss - the item would stop naming anything and its contents would
     * sit in the store with nothing left to ask for them. A bare name loads with no
     * counts, which shows no bar and is honest about knowing nothing.
     */
    public static final Codec<Held> CODEC = Codec.withAlternative(
            Codec.withAlternative(FULL, ONE),
            UUIDUtil.CODEC.xmap(chest -> new Held(List.of(chest), 0, 0, 0, 0),
                    held -> held.chests().getFirst()));

    /**
     * <b>No older forms here.</b> A stream codec speaks to a client of this same version,
     * so there is nothing to be compatible with — unlike {@link #CODEC}, which speaks to
     * a save file written by whatever came before.
     */
    public static final StreamCodec<ByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), Held::chests,
            ByteBufCodecs.VAR_INT, Held::used,
            ByteBufCodecs.VAR_INT, Held::slots,
            ByteBufCodecs.VAR_INT, Held::experience,
            ByteBufCodecs.VAR_INT, Held::growth,
            Held::new);

    /** One chest is put back as it was; several are poured together. See {@code CellaBlock}. */
    public boolean fused() {
        return chests.size() > 1;
    }

    /** Whether it knows how full it is at all; see the note on {@link #CODEC}. */
    public boolean counted() {
        return slots > 0;
    }

    /**
     * Whether there is a growth bar to draw.
     *
     * <p><b>The threshold travels with the item and is not looked up.</b> It is the same
     * decision as {@link #slots}: the client is never told what the block knows, and a
     * chest keeps what it was made with — so a save whose numbers have since been tuned
     * shows the bar it was actually filling rather than one against today's figure.
     */
    public boolean grows() {
        return growth > 0;
    }

    /** Nought to one, against what the form it came from could use. */
    public float grown() {
        return grows() ? Math.min(1.0F, (float) experience / growth) : 0.0F;
    }

    /** Nought to one. Asked by the bar under the item and by the one in the tooltip. */
    public float filled() {
        return counted() ? (float) used / slots : 0.0F;
    }

    /**
     * A slot count as it is shown to anybody.
     *
     * <p>Grouped, because 221,184 is not a number read at a glance. Here rather than in
     * either of the two places that show one — the item's tooltip and {@code KeptCommand}
     * — so that a chest reads the same whichever of them is describing it.
     */
    public static String count(long slots) {
        return String.format(java.util.Locale.ROOT, "%,d", slots);
    }
}
