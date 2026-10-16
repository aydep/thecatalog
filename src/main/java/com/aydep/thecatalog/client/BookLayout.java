package com.aydep.thecatalog.client;

/** Where the book sits on screen and how many item slots fit on a page, in GUI pixels. */
public record BookLayout(int x, int y, int pageWidth, int pageHeight, int cols, int rows) {
    public static final int SLOT = 18, MARGIN = 12, HEADER = 12, FOOTER = 26, TAB_WIDTH = 32;
    // MIN_PAGE_WIDTH leaves room for the widest recipe layout (4 inputs + arrow + output = 118px).
    // MIN_PAGE_HEIGHT fits the recipe header plus a 3x3 crafting grid.
    static final int MIN_PAGE_WIDTH = 142, MIN_PAGE_HEIGHT = 140;
    static final int MIN_COLUMNS = (MIN_PAGE_WIDTH - 2 * MARGIN) / SLOT, MIN_ROWS = (MIN_PAGE_HEIGHT - MARGIN - HEADER - FOOTER) / SLOT;

    public static BookLayout fit(int screenWidth, int screenHeight, int maxColumns, int maxRows, boolean tabsLeft) {
        int maxPageWidth = maxColumns * SLOT + 2 * MARGIN, maxPageHeight = maxRows * SLOT + MARGIN + HEADER + FOOTER;
        int pw = Math.clamp((screenWidth - 16 - TAB_WIDTH) / 2, MIN_PAGE_WIDTH, Math.max(MIN_PAGE_WIDTH, maxPageWidth));
        int ph = Math.clamp(screenHeight - 16, MIN_PAGE_HEIGHT, Math.max(MIN_PAGE_HEIGHT, maxPageHeight));
        int cols = (pw - 2 * MARGIN) / SLOT;
        int rows = (ph - MARGIN - HEADER - FOOTER) / SLOT;
        // x is the book's left edge; book + bookmark column are centered together
        return new BookLayout((screenWidth - 2 * pw - TAB_WIDTH) / 2 + (tabsLeft ? TAB_WIDTH : 0), (screenHeight - ph) / 2, pw, ph, cols, rows);
    }

    public int perPage() {
        return cols * rows;
    }

    /** Number of spreads (two facing pages) needed for the given page count, at least one. */
    public static int spreads(int pages) {
        return Math.max(1, (pages + 1) / 2);
    }
}
