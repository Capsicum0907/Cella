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

/**
 * Every picture that depends on which kind of chest it is, drawn from that kind's one
 * colour: the sheet the block is rendered with, and the flat texture its item is built
 * from.
 *
 * <p><b>Why this is Java and not a script in {@code tools/}.</b> Everything else that
 * differs between kinds is in the enum; a script would have put their colours in another
 * language and left two lists to keep in step. Here the list is asked for its colour and
 * a picture comes out, which is the same rule as every other generated file in the
 * repository — nothing under {@code src/generated} is written by hand.
 *
 * <p><b>The arrangement is vanilla's and was read off its own file.</b> Three parts on a
 * 64 by 64 sheet, laid out by the standard box unwrap, from
 * {@code ChestRenderer.createSingleBodyLayer}:
 *
 * <pre>
 *     bottom  texOffs(0, 19)  14 x 10 x 14
 *     lid     texOffs(0,  0)  14 x  5 x 14
 *     lock    texOffs(0,  0)   2 x  4 x  1
 * </pre>
 *
 * <p>and for a box {@code w x h x d} at {@code (u, v)} the six faces land at
 *
 * <pre>
 *     down  (u+d,     v)    w x d      up    (u+d+w,   v)    w x d
 *     east  (u,       v+d)  d x h      north (u+d,     v+d)  w x h
 *     west  (u+d+w,   v+d)  d x h      south (u+d+w+d, v+d)  w x h
 * </pre>
 *
 * <p><b>What is inside a face is theirs too, counted rather than assumed.</b> There are
 * no bands and no tidy lines: a face is a wash of half a dozen shades a step apart,
 * scattered pixel by pixel, with every fourth row leaning darker where one board meets
 * the next, and a one-pixel near-black edge all the way round. The first version of this
 * drew even bands — which is what a person assumes wood looks like, and beside a real
 * chest read as a striped box.
 *
 * <p>The shades are worked out from the kind's colour, so the arrangement is theirs and
 * every pixel is ours. Reading how a texture is built is not the same as shipping it.
 */
public class KindTextures implements DataProvider {
    private static final int SHEET = 64;

    /**
     * The shades a face is washed with, as weights on the kind's own colour.
     *
     * <p><b>No line among them.</b> There was one every fourth row, on the reading that
     * vanilla's faces have a periodic row - which they do. What they do not have is a
     * <em>darker</em> one: the row that repeats is #A76E1F against a body of #8F691D,
     * lighter rather than darker, and a shade a step sideways does not draw a line. Mine
     * was a fifth to a third darker and drew seven of them per face.
     *
     * <p>So there is one range and every pixel comes out of it. If a board edge is ever
     * wanted back, it belongs inside this range and not below it.
     */
    private static final float[] BODY = { 1.16F, 1.09F, 1.03F, 0.98F, 0.92F, 0.86F };
    private static final float EDGE = 0.30F;

    /**
     * The two faces you only see with the lid up, and they are built the same way.
     *
     * <p>Counted off vanilla, whose underside-of-lid and inside-floor share one frame:
     *
     * <pre>
     *     :-------------      the outer ring, dark
     *     -***##*##**#*-      then a bright ring - the lit lip of the opening
     *     -#::::::::::#-      then ten by ten of centre
     * </pre>
     *
     * <p><b>The bright ring is what makes it look recessed</b>, and it is the part that
     * was missing here. Dimming the whole face instead gave a flat dark panel: darker,
     * and no deeper. The centre is the only thing that differs between the two - dark
     * boards under the lid, and nothing at all on the floor, which is #000000 for a
     * hundred of vanilla's hundred and ninety-six pixels.
     */
    private static final float LIP = 1.15F;
    private static final float CAVITY = 0.35F;
    private static final int HOLLOW = 0xFF000000;

    /** Which of the six {@link #faces} is which: the lid's down, the body's up. */
    private static final int DOWN = 0;
    private static final int UP = 1;

    private static final int LATCH = 0xFF8C8C94;
    private static final int LATCH_LIT = 0xFFC2C2CA;
    private static final int LATCH_DARK = 0xFF5C5C64;

    private static final int CLEAR = 0x00000000;

    /** How big the flat one is, which is the size every block texture in the game is. */
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
        // The lid, then the bottom. The faces you look down at have their boards running
        // the length of the box; the ones you look at have them stacked.
        board(sheet, stain, 0, 0, 14, 5, 14, DOWN);
        board(sheet, stain, 0, 19, 14, 10, 14, UP);
        // The lock last, in the corner of the sheet the lid's faces leave empty.
        for (int[] face : faces(0, 0, 2, 4, 1)) {
            metal(sheet, face[0], face[1], face[2], face[3]);
        }
        return sheet;
    }

    /**
     * The flat one: a single face of the same boards, sixteen square.
     *
     * <p>What the item is built from, and what flies off when the block breaks. It exists
     * because otherwise every kind's item is the same picture — the block was told apart
     * by colour at a glance and the thing in your hand was not.
     */
    private static int[][] tile(int stain) {
        int[][] tile = new int[TILE][TILE];
        face(tile, stain, 0, 0, TILE, TILE);
        return tile;
    }

    /** @return each face as {@code x, y, width, height} */
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

    /**
     * A dark ring, a bright lip inside it, and a centre that is either dark boards or
     * nothing. See {@link #LIP}.
     *
     * @param empty whether the middle is a hole rather than boards - true for the floor,
     *              which is what you look down into, and false for the lid over it
     */
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

    /** Near-black all the way round, a wash inside. */
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

    /**
     * Which shade this pixel takes — the same one every time.
     *
     * <p>Deliberately not random: the file has to come out identical from one run to the
     * next or every regeneration is a diff nobody asked for. A hash of the position gives
     * the disorder without the irreproducibility.
     */
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

    // --- png ---------------------------------------------------------------

    @SuppressWarnings("deprecation") // Hashing.sha1 is what CachedOutput expects
    private static void write(CachedOutput output, int[][] sheet, Path target) {
        try {
            byte[] bytes = png(sheet);
            output.writeIfNeeded(target, bytes, Hashing.sha1().hashBytes(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Eight-bit RGBA, one filter byte of nought per row, which is all this needs. */
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
