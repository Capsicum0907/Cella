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

public record Held(List<UUID> chests, int used, int slots, int experience, int growth)
        implements TooltipComponent {
    private static final Codec<Held> FULL = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.listOf().fieldOf("chests").forGetter(Held::chests),
            Codec.INT.fieldOf("used").forGetter(Held::used),
            Codec.INT.fieldOf("slots").forGetter(Held::slots),
            Codec.INT.optionalFieldOf("experience", 0).forGetter(Held::experience),
            Codec.INT.optionalFieldOf("growth", 0).forGetter(Held::growth))
            .apply(instance, Held::new));

    private static final Codec<Held> ONE = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("chest").forGetter(held -> held.chests().getFirst()),
            Codec.INT.fieldOf("used").forGetter(Held::used),
            Codec.INT.fieldOf("slots").forGetter(Held::slots))
            .apply(instance, (chest, used, slots) -> new Held(List.of(chest), used, slots, 0, 0)));

    public static final Codec<Held> CODEC = Codec.withAlternative(
            Codec.withAlternative(FULL, ONE),
            UUIDUtil.CODEC.xmap(chest -> new Held(List.of(chest), 0, 0, 0, 0),
                    held -> held.chests().getFirst()));

    public static final StreamCodec<ByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), Held::chests,
            ByteBufCodecs.VAR_INT, Held::used,
            ByteBufCodecs.VAR_INT, Held::slots,
            ByteBufCodecs.VAR_INT, Held::experience,
            ByteBufCodecs.VAR_INT, Held::growth,
            Held::new);

    public boolean fused() {
        return chests.size() > 1;
    }

    public boolean counted() {
        return slots > 0;
    }

    public boolean grows() {
        return growth > 0;
    }

    public float grown() {
        return grows() ? Math.min(1.0F, (float) experience / growth) : 0.0F;
    }

    public float filled() {
        return counted() ? (float) used / slots : 0.0F;
    }

    public static String count(long slots) {
        return String.format(java.util.Locale.ROOT, "%,d", slots);
    }
}
