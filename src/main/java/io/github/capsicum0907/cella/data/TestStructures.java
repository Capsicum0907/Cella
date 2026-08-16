package io.github.capsicum0907.cella.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import com.google.common.hash.Hashing;

import io.github.capsicum0907.cella.Cella;

import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;

/**
 * The stage the game tests run on: a flat floor with clear air above it.
 *
 * <p>A game test needs a structure to be placed in, and there is no empty one to
 * borrow. Writing the NBT here rather than checking a binary into the repository
 * keeps the rule that generated files are generated, and the data version comes
 * from the game itself so it cannot drift out of date silently.
 */
public class TestStructures implements DataProvider {
    /** Referenced by {@code @GameTest(template = ...)}. */
    public static final String FLOOR = "floor";

    private static final int SIZE = 5;
    private static final String FLOOR_BLOCK = "minecraft:polished_andesite";
    private static final String AIR = "minecraft:air";

    private final PackOutput.PathProvider path;

    public TestStructures(PackOutput output) {
        this.path = output.createPathProvider(PackOutput.Target.DATA_PACK, "structure");
    }

    @Override
    public String getName() {
        return "Test Structures: " + Cella.MODID;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        Path target = path.file(ResourceLocation.fromNamespaceAndPath(Cella.MODID, FLOOR), "nbt");
        return CompletableFuture.runAsync(() -> write(output, floor(), target), Util.backgroundExecutor());
    }

    private static CompoundTag floor() {
        CompoundTag tag = new CompoundTag();
        NbtUtils.addCurrentDataVersion(tag);
        tag.put("size", vector(SIZE, SIZE, SIZE));

        ListTag palette = new ListTag();
        palette.add(named(AIR));
        palette.add(named(FLOOR_BLOCK));
        tag.put("palette", palette);

        // Every cell is listed, air included: an omitted cell is left as whatever was
        // already there, which would let one test leave something behind for the next.
        ListTag blocks = new ListTag();
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    CompoundTag block = new CompoundTag();
                    block.put("pos", vector(x, y, z));
                    block.putInt("state", y == 0 ? 1 : 0);
                    blocks.add(block);
                }
            }
        }
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static ListTag vector(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x));
        list.add(IntTag.valueOf(y));
        list.add(IntTag.valueOf(z));
        return list;
    }

    private static CompoundTag named(String block) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", block);
        return tag;
    }

    @SuppressWarnings("deprecation") // Hashing.sha1 is what CachedOutput expects
    private static void write(CachedOutput output, CompoundTag tag, Path target) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, buffer);
            byte[] bytes = buffer.toByteArray();
            output.writeIfNeeded(target, bytes, Hashing.sha1().hashBytes(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
