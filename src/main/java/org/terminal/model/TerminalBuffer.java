package org.terminal.model;

public class TerminalBuffer {
    // Location of each property in the cell (long)
    private static final int FOREGROUND_COLOR_OFFSET = 21;
    private static final int BACKGROUND_COLOR_OFFSET = 29;
    private static final int TEXT_STYLE_OFFSET = 37;

    // Terminal width and height
    private final int width;
    private final int height;

    // Buffer height represent total heigh of the buffer,
    // including both text and scrollback
    private final int bufferHeight;

    // Circular buffer to store terminal cells as long values
    private final long[][] buffer;

    // Top of the screen
    private int head = 0;

    // Shows how far up the user scrolled
    private int scrollOffset = 0;

    // Cursor position
    private int cursorX = 0;
    private int cursorY = 0;

    public TerminalBuffer(int width, int height, int scrollBackLines) {
        this.width = width;
        this.height = height;
        this.bufferHeight = height + scrollBackLines;
        this.buffer = new long[height][width];
    }

    public TerminalBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.bufferHeight = 1000; // Default scrollback buffer size
        this.buffer = new long[height][width];
    }
}
