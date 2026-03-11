package org.terminal;

public class TerminalBuffer {
    // Location of each property in the cell (long)
    private static final int FOREGROUND_COLOR_OFFSET = 21;
    private static final int BACKGROUND_COLOR_OFFSET = 29;
    private static final int TEXT_STYLE_OFFSET = 37;
    private static final long DOUBLE_WIDTH_FLAG = 1L << 45; // Mark second cell of double-wide character

    // Terminal width and height
    private int width;
    private int height;

    // Buffer height represent total heigh of the buffer,
    // including both text and scrollback
    private int bufferHeight;

    // Circular buffer to store terminal cells as long values
    private long[][] buffer;

    // Index of the oldest line in the circular buffer
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
        // 1. Where does the visible screen start in our history?
        // If we have 100 lines and a 24-line screen, the screen starts at index 76.
        int screenStartInHistory = Math.max(0, contentSize - height);

        // 2. Adjust for scrolling
        int targetLine = screenStartInHistory + screenY - scrollOffset;

        // 3. Map to circular physical array
        return (head + targetLine + bufferHeight) % bufferHeight;
    }

    public int getHistoryPhysicalRow(int historyY) {
        // Absolute history: 0 is the oldest line, totalLines-1 is the newest.
        return (head + historyY) % bufferHeight;
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
        if (contentSize < bufferHeight) {
            // There is still free space in the scrollback — just grow into it.
            contentSize++;
            if (cursorY < height - 1) {
                cursorY++;
            }
            // If cursorY is already at height - 1, the screen window slides down
            // automatically via getPhysicalRow (contentSize just grew), so the
            // cursor visually stays at the bottom row — no adjustment needed.
        } else {
            // Buffer is completely full: recycle the oldest row.
            scrollBuffer();
            // cursorY stays at height - 1; the screen window has shifted by one.
        }
    }

    private void scrollBuffer() {
        // The current 'head' is the oldest line.
        // Wipe it so it becomes the new blank bottom line.
        for (int x = 0; x < width; x++) {
            buffer[head][x] = 0;
        }
        head = (head + 1) % bufferHeight;
    }

    public void putText(String text, int fg, int bg, int attr) {
        text.codePoints().forEach(codepoint -> putChar(codepoint, fg, bg, attr));
    }

    /**
     * Fills the entire current cursor row with the given codepoint (or blanks when
     * codepoint == 0).  Double-wide characters are placed in pairs; if the width is
     * odd, the trailing cell is left blank.  The cursor X is reset to 0 afterwards;
     * cursorY is not changed.
     */
    public void fillLine(int codepoint, int fg, int bg, int attr) {
        int physY = getPhysicalRow(cursorY);
        boolean isDouble = codepoint != 0 && isDoubleWide(codepoint);

        int x = 0;
        while (x < width) {
            if (codepoint == 0) {
                buffer[physY][x] = 0;
                x++;
            } else if (isDouble) {
                if (x + 1 < width) {
                    buffer[physY][x]     = pack(codepoint, fg, bg, attr) | DOUBLE_WIDTH_FLAG;
                    buffer[physY][x + 1] = pack(0, fg, bg, attr) | DOUBLE_WIDTH_FLAG;
                    x += 2;
                } else {
                    // Last cell can't fit a double-wide pair — leave it blank
                    buffer[physY][x] = 0;
                    x++;
                }
            } else {
                buffer[physY][x] = pack(codepoint, fg, bg, attr);
                x++;
            }
        }
        cursorX = 0;
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
            }
        }

        buffer[physY][cursorX] = pack(codepoint, fg, bg, attr);
    }

    /**
     * Inserts text at the current cursor position on the current line, shifting
     * existing content to the right.  Content pushed beyond the line width is
     * discarded (no new lines are created).  The cursor advances by the number of
     * cells written (double-wide characters consume two cells each).
     * <p>
     * A {@code '\n'} inside {@code text} is treated as a literal newline request:
     * the remainder of the string continues on the next line from column 0, which
     * is also an insert-at-cursor operation (no overwrite of what was already on
     * that next line).
     */
    public void insertText(String text, int fg, int bg, int attr) {
        int physY = getPhysicalRow(cursorY);

        // Resolve any split double-wide character at the insertion point before we
        // start shifting: if the cursor sits on the placeholder half, clear both halves.
        if (cursorX < width) {
            long cell = buffer[physY][cursorX];
            if ((cell & DOUBLE_WIDTH_FLAG) != 0 && getCodepointFromLong(cell) == 0 && cursorX > 0) {
                buffer[physY][cursorX - 1] = 0;
                buffer[physY][cursorX]     = 0;
            }
        }

        int[] codepoints = text.codePoints().toArray();
        for (int i = 0; i < codepoints.length; i++) {
            int cp = codepoints[i];

            if (cp == '\n') {
                // Move to the next line and continue inserting there
                newLine();
                physY = getPhysicalRow(cursorY);
                continue;
            }

            if (cursorX >= width) {
                // No more room on this line — skip the rest of the line's characters
                // until we hit a newline
                continue;
            }

            boolean isDouble = isDoubleWide(cp);
            int cellsNeeded = isDouble ? 2 : 1;

            // If a double-wide would straddle the line boundary, skip it
            if (isDouble && cursorX + 1 >= width) {
                continue;
            }

            // Shift existing cells to the right to make room
            int shiftFrom = width - cellsNeeded - 1;
            for (int x = shiftFrom; x >= cursorX; x--) {
                buffer[physY][x + cellsNeeded] = buffer[physY][x];
            }

            // Write the character
            long cellValue = pack(cp, fg, bg, attr);
            if (isDouble) {
                buffer[physY][cursorX]     = cellValue | DOUBLE_WIDTH_FLAG;
                buffer[physY][cursorX + 1] = pack(0, fg, bg, attr) | DOUBLE_WIDTH_FLAG;
                cursorX += 2;
            } else {
                buffer[physY][cursorX] = cellValue;
                cursorX++;
            }
        }
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
        long data = buffer[getHistoryPhysicalRow(y)][x];
        return getCodepointFromLong(data);
    }

    public int[] getAttributesAt(int x, int y){
        long data = buffer[getHistoryPhysicalRow(y)][x];
        int foregroundColor = getForegroundColor(data);
        int backgroundColor = getBackgroundColor(data);
        int textStyle = getTextStyle(data);
        return new int[]{foregroundColor, backgroundColor, textStyle};
    }

    public String getLineAsString(int y) {
        StringBuilder sb = new StringBuilder();
        for (int x = 0; x < width; x++) {
            long cell = buffer[getHistoryPhysicalRow(y)][x];

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
                long cell = buffer[getHistoryPhysicalRow(y)][x];
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

    public void resize(int newWidth, int newHeight) {
        int newBufferHeight = newHeight + (this.bufferHeight - this.height);
        long[][] newBuffer = new long[newBufferHeight][newWidth];

        // 1. How many lines actually have data?
        // contentSize counts how many newlines have been issued, but the cursor's
        // current row also has data and hasn't triggered a newLine() yet.
        // screenStartInHistory is the history index of the top visible row,
        // so (screenStartInHistory + cursorY + 1) is the total filled row count.
        int screenStartInHistory = Math.max(0, contentSize - height);
        int totalFilledLines = screenStartInHistory + cursorY + 1;

        // 2. How many lines are we moving?
        // We can't move more than the new buffer can hold.
        int linesToCopy = Math.min(totalFilledLines, newBufferHeight);

        // 3. Find where the "data" starts (the oldest line currently kept)
        // If we are shrinking the buffer, we might lose the oldest history lines.
        int startLineIndex = Math.max(0, totalFilledLines - linesToCopy);

        // 3. Copy lines into the new buffer starting at index 0
        int copyWidth = Math.min(width, newWidth);
        for (int i = 0; i < linesToCopy; i++) {
            int oldPhysY = getHistoryPhysicalRow(startLineIndex + i);
            for (int x = 0; x < copyWidth; x++) {
                newBuffer[i][x] = buffer[oldPhysY][x];
            }

            // Double-wide clipping: if the line was narrowed and the cell now sitting
            // at the last column is one half of a double-wide pair, the pair is split.
            // Wipe whichever half(s) landed at or beyond the boundary.
            if (newWidth < width && newWidth > 0) {
                long lastCell = newBuffer[i][newWidth - 1];
                if ((lastCell & DOUBLE_WIDTH_FLAG) != 0 && getCodepointFromLong(lastCell) != 0) {
                    // Either the character itself or its placeholder is at newWidth-1.
                    // Either way the pair straddles the boundary — erase the last cell.
                    newBuffer[i][newWidth - 1] = 0;
                    // If this was the placeholder (codepoint == 0), also erase the
                    // character in the column to the left.
                }
            }
        }

        // 4. Reset State
        this.buffer = newBuffer;
        this.width = newWidth;
        this.height = newHeight;
        this.bufferHeight = newBufferHeight;
        // After compaction all linesToCopy lines sit at indices 0..linesToCopy-1.
        // contentSize counts how many lines have been opened (= linesToCopy).
        this.contentSize = linesToCopy;
        this.head = 0; // Data now starts perfectly at index 0
        this.scrollOffset = 0;

        // 5. Adjust Cursor — it lands on the last copied row
        this.cursorY = Math.min(linesToCopy - 1, newHeight - 1);
        this.cursorX = Math.min(cursorX, newWidth - 1);
    }
}
