package io.github.capsicum0907.cella;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

public record Shelf(List<Slice> slices, int assigned, int slots, int shown,
        List<Tally> tallies)
        implements CustomPacketPayload {
    public static final Type<Shelf> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cella.MODID, "shelf"));

    public static final int MOST = 32;

    public record Slice(String name, int colour, int start, int length, int used) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Slice> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(Plan.LONGEST), Slice::name,
                        ByteBufCodecs.VAR_INT, Slice::colour,
                        ByteBufCodecs.VAR_INT, Slice::start,
                        ByteBufCodecs.VAR_INT, Slice::length,
                        ByteBufCodecs.VAR_INT, Slice::used,
                        Slice::new);

        public DyeColor dye() {
            return DyeColor.byId(colour);
        }

        public int slots() {
            return length * Plan.LC;
        }

        public float filled() {
            return slots() <= 0 ? 0.0F : (float) used / slots();
        }
    }

    public record Tally(ItemStack kind, int count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Tally> STREAM_CODEC =
                StreamCodec.composite(
                        ItemStack.STREAM_CODEC, Tally::kind,
                        ByteBufCodecs.VAR_INT, Tally::count,
                        Tally::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, Shelf> STREAM_CODEC =
            StreamCodec.composite(
                    Slice.STREAM_CODEC.apply(ByteBufCodecs.list()), Shelf::slices,
                    ByteBufCodecs.VAR_INT, Shelf::assigned,
                    ByteBufCodecs.VAR_INT, Shelf::slots,
                    ByteBufCodecs.VAR_INT, Shelf::shown,
                    Tally.STREAM_CODEC.apply(ByteBufCodecs.list()), Shelf::tallies,
                    Shelf::new);

    public static final Shelf NOTHING =
            new Shelf(List.of(), Plan.NONE, 0, Peek.LIST, List.of());

    public static Shelf of(CellaBlockEntity chest, int shown) {
        int slots = chest.contents().getSlots();
        List<Plan.Partition> carved = chest.plan().over(slots);
        List<Slice> slices = new ArrayList<>();
        for (int at = 0; at < carved.size(); at++) {
            Plan.Partition one = carved.get(at);
            slices.add(new Slice(one.name(), one.colour().getId(), chest.plan().start(at),
                    one.length(), chest.contents().used(at)));
        }
        List<Tally> tallies = shown >= 0 && shown < carved.size()
                ? counted(chest, chest.plan().first(shown, slots),
                        chest.plan().past(shown, slots))
                : List.of();
        return new Shelf(slices, chest.plan().assigned(), slots, shown, tallies);
    }

    private static List<Tally> counted(CellaBlockEntity chest, int from, int end) {
        List<Tally> tallies = new ArrayList<>();
        ItemStack running = ItemStack.EMPTY;
        int count = 0;
        for (int slot = from; slot < end; slot++) {
            ItemStack stack = chest.contents().getStackInSlot(slot);
            if (stack.isEmpty()) {
                break;
            }
            if (!running.isEmpty() && Alike.same(running, stack)) {
                count += stack.getCount();
                continue;
            }
            if (!running.isEmpty()) {
                tallies.add(new Tally(running, count));
                if (tallies.size() >= MOST) {
                    return tallies;
                }
            }
            running = stack.copyWithCount(1);
            count = stack.getCount();
        }
        if (!running.isEmpty() && tallies.size() < MOST) {
            tallies.add(new Tally(running, count));
        }
        return tallies;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
