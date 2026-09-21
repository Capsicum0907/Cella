package io.github.capsicum0907.cella;

public final class ShelfHolder {
    private ShelfHolder() {
    }

    private static volatile Shelf latest = Shelf.NOTHING;

    public static void told(Shelf shelf) {
        latest = shelf;
    }

    public static Shelf latest() {
        return latest;
    }
}
