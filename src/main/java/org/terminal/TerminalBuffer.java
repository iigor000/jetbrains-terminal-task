package org.terminal;

public class TerminalBuffer {
    // Location of each property in the cell (long)
    private static final int FOREGROUND_COLOR_OFFSET = 21;
    private static final int BACKGROUND_COLOR_OFFSET = 29;
    private static final int TEXT_STYLE_OFFSET = 37;
    private static final long DOUBLE_WIDTH_FLAG = 1L << 45; // Mark second cell of double-wide character

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

    // Total number of characters in the buffer (for scroll limit)
    private int contentSize = 0;

    // Cursor position
    private int cursorX = 0;
    private int cursorY = 0;

    public TerminalBuffer(int width, int height, int scrollBackSize) {
        this.width = width;
        this.height = height;
        this.bufferHeight = height + scrollBackSize;
        this.buffer = new long[bufferHeight][width];
    }

    public TerminalBuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.bufferHeight = 1000; // Default scrollback buffer size
        this.buffer = new long[bufferHeight][width];
    }

    // --- Utility Methods ---

    public int getPhysicalRow(int screenY) {
        // 1. Calculate the 'logical' row based on current scroll position
        // When scrollOffset is 0, we see the last 'viewportHeight' lines.
        int logicalRow = (bufferHeight - height - scrollOffset) + screenY;

        // 2. Map to the circular physical array
        return (head + logicalRow + bufferHeight) % bufferHeight;
    }

    public static long pack(int codepoint, int fg, int bg, int attr) {
        return (codepoint & 0x1FFFFFL) |
                ((long)(fg & 0xFF) << FOREGROUND_COLOR_OFFSET) |
                ((long)(bg & 0xFF) << BACKGROUND_COLOR_OFFSET) |
                ((long)(attr & 0xFF) << TEXT_STYLE_OFFSET);
    }

    public static int getCodepointFromLong(long data) {
        return (int)(data & 0x1FFFFFL);
    }

    public static int getForegroundColor(long data) {
        return (int)((data >> FOREGROUND_COLOR_OFFSET) & 0xFF);
    }

    public static int getBackgroundColor(long data) {
        return (int)((data >> BACKGROUND_COLOR_OFFSET) & 0xFF);
    }

    public static int getTextStyle(long data) {
        return (int)((data >> TEXT_STYLE_OFFSET) & 0xFF);
    }

    private boolean isDoubleWide(int codepoint) {
        // Check for common double-wide character ranges
        return (codepoint >= 0x1100 && codepoint <= 0x115F) ||  // Hangul Jamo
               (codepoint >= 0x2329 && codepoint <= 0x232A) ||  // Angle brackets
               (codepoint >= 0x2E80 && codepoint <= 0xA4CF) ||  // CJK
               (codepoint >= 0xAC00 && codepoint <= 0xD7A3) ||  // Hangul Syllables
               (codepoint >= 0xF900 && codepoint <= 0xFAFF) ||  // CJK Compatibility
               (codepoint >= 0x1F000 && codepoint <= 0x1F9FF);  // Emojis
    }

    // --- Buffer Manipulation ---

    public void putChar(int codepoint, int fg, int bg, int attr) {
        // If user is scrolled up, many terminals 'snap' to bottom on new input
        // scrollOffset = 0;

        if (codepoint == '\n') {
            newLine();
            return;
        }

        boolean isDouble = isDoubleWide(codepoint);

        // If double-wide and would overflow, move to next line
        if (isDouble && cursorX >= width - 1) {
            newLine();
        }

        int physY = getPhysicalRow(cursorY);
        long cellValue = pack(codepoint, fg, bg, attr);

        if (isDouble) {
            cellValue |= DOUBLE_WIDTH_FLAG; // Mark as double-wide
        }

        buffer[physY][cursorX] = cellValue;
        cursorX++;

        if (isDouble) {
            // Mark the next cell as the second half of the double-wide character
            buffer[physY][cursorX] = pack(0, fg, bg, attr) | DOUBLE_WIDTH_FLAG;
            cursorX++;
        }

        if (cursorX >= width) {
            newLine();
        }
    }

    private void newLine() {
        cursorX = 0;
        contentSize++;
        if (cursorY < height - 1) {
            cursorY++;
        } else {
            scrollBuffer();
        }
    }

    private void scrollBuffer() {
        // The current 'head' is the oldest line.
        // We wipe it so it can become the newest 'bottom' line.
        for (int x = 0; x < width; x++) {
            buffer[head][x] = 0;
        }
        // Advance head: The ring spins.
        head = (head + 1) % bufferHeight;
    }

    public void putText(String text, int fg, int bg, int attr) {
        text.codePoints().forEach(codepoint -> putChar(codepoint, fg, bg, attr));
    }

    public void insertChar(int codepoint, int fg, int bg, int attr) {
        if (cursorX >= width || cursorY >= height) {
            return; // Out of bounds
        }

        int physY = getPhysicalRow(cursorY);
        long existingCell = buffer[physY][cursorX];

        // If inserting on the second cell of a double-wide, remove the first
        if ((existingCell & DOUBLE_WIDTH_FLAG) != 0 && getCodepointFromLong(existingCell) == 0) {
            if (cursorX > 0) {
                buffer[physY][cursorX - 1] = 0; // Clear the first cell
            } else {
                buffer[physY - 1][cursorX] = 0; // Clear the upper cell
            }
        }

        buffer[physY][cursorX] = pack(codepoint, fg, bg, attr);
    }

    public void clearScreen() {
        for (int y = 0; y < height + 2; y++) {
            newLine();
        }
        cursorX = 0;
        cursorY = 0;
    }

    public void clearScreenAndScrollback() {
        for (int y = 0; y < bufferHeight; y++) {
            for (int x = 0; x < width; x++) {
                buffer[y][x] = 0;
            }
        }
        head = 0;
        scrollOffset = 0;
        cursorX = 0;
        cursorY = 0;
    }

    // --- Scrolling Controls ---

    public void scrollUp(int lines) {
        // Don't scroll up further than the history we've actually written
        scrollOffset = Math.min(scrollOffset + lines, contentSize - height);
    }

    public void scrollDown(int lines) {
        scrollOffset = Math.max(scrollOffset - lines, 0);
    }

    // Get cell for the renderer (respects scrollOffset)
    public long getCellAt(int x, int screenY) {
        return buffer[getPhysicalRow(screenY)][x];
    }

    // --- Cursor Controls ---

    public void setCursorPosition(int x, int y) {
        this.cursorX = Math.max(0, Math.min(x, width - 1));
        this.cursorY = Math.max(0, Math.min(y, height - 1));
    }

    public int[] getCursorPosition() {
        return new int[]{cursorX, cursorY};
    }

    public void moveCursor(int dx, int dy) {
        setCursorPosition(cursorX + dx, cursorY + dy);
    }

    // -- Content Access --

    public int getCodepointAt(int x, int y){
        long data = buffer[y][x];
        return getCodepointFromLong(data);
    }

    public int[] getAttributesAt(int x, int y){
        long data = buffer[y][x];
        int foregroundColor = getForegroundColor(data);
        int backgroundColor = getBackgroundColor(data);
        int textStyle = getTextStyle(data);
        return new int[]{foregroundColor, backgroundColor, textStyle};
    }

    public String getLineAsString(int y) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < width; x++) {
            long cell = buffer[y][x];
            int codepoint = getCodepointFromLong(cell);

            // Skip the second cell of double-wide characters
            if ((cell & DOUBLE_WIDTH_FLAG) != 0 && codepoint == 0) {
                continue;
            }

            if (codepoint != 0) { // Skip empty cells
                sb.appendCodePoint(codepoint);
            }
        }
        return sb.toString();
    }

    public String getScreenAsString() {
        StringBuilder sb = new StringBuilder();
        for (int screenY = 0; screenY < height; screenY++) {
            int physY = getPhysicalRow(screenY);
            StringBuilder lineSb = new StringBuilder();
            for (int x = 0; x < width; x++) {
                long cell = buffer[physY][x];
                int codepoint = getCodepointFromLong(cell);

                // Skip if this is the second cell of a double-wide character
                if ((cell & DOUBLE_WIDTH_FLAG) != 0 && codepoint == 0) {
                    continue;
                }

                if (codepoint != 0) {
                    lineSb.appendCodePoint(codepoint);
                }
            }
            sb.append(lineSb);
            if (screenY < height - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public String getFullBufferAsString() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < bufferHeight; y++) {
            for (int x = 0; x < width; x++) {
                long cell = buffer[y][x];
                int codepoint = getCodepointFromLong(cell);

                // Skip if this is the second cell of a double-wide character
                if ((cell & DOUBLE_WIDTH_FLAG) != 0 && codepoint == 0) {
                    continue;
                }

                if (codepoint != 0) {
                    sb.appendCodePoint(codepoint);
                }
            }
            if (y < bufferHeight - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }
}
