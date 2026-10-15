package com.aydep.thecatalog.client;

/** Where the book sits on screen and how many item slots fit on a page, in GUI pixels. */
public record BookLayout(int x, int y, int pageWidth, int pageHeight, int cols, int rows) {
    public static final int SLOT = 18, MARGIN = 12, HEADER = 12, FOOTER = 26, TAB_WIDTH = 26;
    // MIN_PAGE_WIDTH leaves room for the widest recipe layout (4 inputs + arrow + output = 118px).
    static final int MIN_PAGE_WIDTH = 142, MAX_PAGE_WIDTH = 200, MIN_PAGE_HEIGHT = 180, MAX_PAGE_HEIGHT = 240;

    public static BookLayout fit(int screenWidth, int screenHeight) {
        int pw = Math.clamp((screenWidth - 16 - TAB_WIDTH) / 2, MIN_PAGE_WIDTH, MAX_PAGE_WIDTH);
        int ph = Math.clamp(screenHeight - 16, MIN_PAGE_HEIGHT, MAX_PAGE_HEIGHT);
        int cols = (pw - 2 * MARGIN) / SLOT;
        int rows = (ph - MARGIN - HEADER - FOOTER) / SLOT;
        return new BookLayout((screenWidth - 2 * pw - TAB_WIDTH) / 2, (screenHeight - ph) / 2, pw, ph, cols, rows);
    }

    public int perPage() {
        return cols * rows;
    }

    /** Number of spreads (two facing pages) needed for the given page count, at least one. */
    public static int spreads(int pages) {
        return Math.max(1, (pages + 1) / 2);
    }
}
