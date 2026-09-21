package io.github.capsicum0907.cella.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

import com.google.common.hash.Hashing;

import io.github.capsicum0907.cella.Cella;
import io.github.capsicum0907.cella.Kind;

import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;

public class KindTextures implements DataProvider {
    private static final int SHEET = 64;

    private static final float[] BODY = { 1.16F, 1.09F, 1.03F, 0.98F, 0.92F, 0.86F };
    private static final float EDGE = 0.30F;

    private static final float LIP = 1.15F;
    private static final float CAVITY = 0.35F;
    private static final int HOLLOW = 0xFF000000;

    private static final int DOWN = 0;
    private static final int UP = 1;

    private static final int LATCH = 0xFF8C8C94;
    private static final int LATCH_LIT = 0xFFC2C2CA;
    private static final int LATCH_DARK = 0xFF5C5C64;

    private static final int CLEAR = 0x00000000;

    private static final int TILE = 16;

    private final PackOutput.PathProvider sheets;
    private final PackOutput.PathProvider tiles;

    public KindTextures(PackOutput output) {
        this.sheets = output.createPathProvider(PackOutput.Target.RESOURCE_PACK,
                "textures/entity/chest");
        this.tiles = output.createPathProvider(PackOutput.Target.RESOURCE_PACK,
                "textures/block");
    }

    @Override
    public String getName() {
        return "Kind Textures: " + Cella.MODID;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        List<CompletableFuture<?>> writing = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            ResourceLocation named =
                    ResourceLocation.fromNamespaceAndPath(Cella.MODID, kind.id());
            int[][] sheet = draw(kind.stain());
            int[][] tile = tile(kind.stain());
            writing.add(CompletableFuture.runAsync(
                    () -> write(output, sheet, sheets.file(named, "png")),
                    Util.backgroundExecutor()));
            writing.add(CompletableFuture.runAsync(
                    () -> write(output, tile, tiles.file(named, "png")),
                    Util.backgroundExecutor()));
        }
        return CompletableFuture.allOf(writing.toArray(CompletableFuture[]::new));
    }

    private static int[][] draw(int stain) {
        int[][] sheet = new int[SHEET][SHEET];
        for (int[] row : sheet) {
            java.util.Arrays.fill(row, CLEAR);
        }

        board(sheet, stain, 0, 0, 14, 5, 14, DOWN);
        board(sheet, stain, 0, 19, 14, 10, 14, UP);

        for (int[] face : faces(0, 0, 2, 4, 1)) {
            metal(sheet, face[0], face[1], face[2], face[3]);
        }
        return sheet;
    }

    private static int[][] tile(int stain) {
        int[][] tile = new int[TILE][TILE];
        face(tile, stain, 0, 0, TILE, TILE);
        return tile;
    }

    private static int[][] faces(int u, int v, int w, int h, int d) {
        return new int[][] {
                { u + d, v, w, d },
                { u + d + w, v, w, d },
                { u, v + d, d, h },
                { u + d, v + d, w, h },
                { u + d + w, v + d, d, h },
                { u + d + w + d, v + d, w, h },
        };
    }

    private static void board(int[][] sheet, int stain, int u, int v, int w, int h, int d,
            int inwards) {
        int[][] faces = faces(u, v, w, h, d);
        for (int at = 0; at < faces.length; at++) {
            int[] face = faces[at];
            if (at == inwards) {
                inward(sheet, stain, face[0], face[1], face[2], face[3], inwards == UP);
            } else {
                face(sheet, stain, face[0], face[1], face[2], face[3]);
            }
        }
    }

    private static void inward(int[][] sheet, int stain, int x, int y, int w, int h,
            boolean empty) {
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int ring = Math.min(Math.min(dx, dy), Math.min(w - 1 - dx, h - 1 - dy));
                float wash = BODY[scatter(x + dx, y + dy, BODY.length)];
                sheet[y + dy][x + dx] = switch (ring) {
                    case 0 -> shade(stain, EDGE);
                    case 1 -> shade(stain, LIP * wash);
                    default -> empty ? HOLLOW : shade(stain, CAVITY * wash);
                };
            }
        }
    }

    private static void face(int[][] sheet, int stain, int x, int y, int w, int h) {
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                boolean rim = dx == 0 || dx == w - 1 || dy == 0 || dy == h - 1;
                sheet[y + dy][x + dx] = shade(stain,
                        rim ? EDGE : BODY[scatter(x + dx, y + dy, BODY.length)]);
            }
        }
    }

    private static void metal(int[][] sheet, int x, int y, int w, int h) {
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                sheet[y + dy][x + dx] = dy == 0 || dx == 0 ? LATCH_LIT
                        : dy == h - 1 || dx == w - 1 ? LATCH_DARK : LATCH;
            }
        }
    }

    private static int scatter(int x, int y, int of) {
        int mixed = x * 73_856_093 ^ y * 19_349_663;
        mixed ^= mixed >>> 13;
        return Math.floorMod(mixed, of);
    }

    private static int shade(int colour, float weight) {
        int r = clamp(((colour >> 16) & 0xFF) * weight);
        int g = clamp(((colour >> 8) & 0xFF) * weight);
        int b = clamp((colour & 0xFF) * weight);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    @SuppressWarnings("deprecation")
    private static void write(CachedOutput output, int[][] sheet, Path target) {
        try {
            byte[] bytes = png(sheet);
            output.writeIfNeeded(target, bytes, Hashing.sha1().hashBytes(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] png(int[][] sheet) throws IOException {
        ByteBuffer raw = ByteBuffer.allocate(sheet.length * (sheet.length * 4 + 1));
        for (int[] row : sheet) {
            raw.put((byte) 0);
            for (int pixel : row) {
                raw.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel)
                        .put((byte) (pixel >>> 24));
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' });
        chunk(out, "IHDR", ByteBuffer.allocate(13)
                .putInt(sheet.length).putInt(sheet.length)
                .put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0).array());
        chunk(out, "IDAT", deflate(raw.array()));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String kind, byte[] data)
            throws IOException {
        byte[] name = kind.getBytes(StandardCharsets.US_ASCII);
        out.write(ByteBuffer.allocate(4).putInt(data.length).array());
        out.write(name);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.write(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
