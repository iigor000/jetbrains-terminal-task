package org.terminal;

import org.testng.annotations.*;

import static org.testng.Assert.*;

public class TerminalBufferTest {

    private TerminalBuffer buffer;
    private static final int TEST_WIDTH = 80;
    private static final int TEST_HEIGHT = 24;
    private static final int TEST_SCROLLBACK = 100;

    @BeforeMethod
    public void setUp() {
        buffer = new TerminalBuffer(TEST_WIDTH, TEST_HEIGHT, TEST_SCROLLBACK);
    }

    // --- Initialization Tests ---

    @Test
    public void testInitializationWithScrollback() {
        assertNotNull(buffer);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 0, "Initial cursor X should be 0");
        assertEquals(cursor[1], 0, "Initial cursor Y should be 0");
    }

    @Test
    public void testInitializationWithDefaultScrollback() {
        TerminalBuffer defaultBuffer = new TerminalBuffer(TEST_WIDTH, TEST_HEIGHT);
        assertNotNull(defaultBuffer);
        int[] cursor = defaultBuffer.getCursorPosition();
        assertEquals(cursor[0], 0, "Initial cursor X should be 0");
        assertEquals(cursor[1], 0, "Initial cursor Y should be 0");
    }

    // --- Packing/Unpacking Tests ---

    @Test
    public void testPackUnpackCodepoint() {
        int codepoint = 'A';
        long packed = TerminalBuffer.pack(codepoint, 0, 0, 0);
        assertEquals(TerminalBuffer.getCodepointFromLong(packed), codepoint);
    }

    @Test
    public void testPackUnpackHighCodepoint() {
        int codepoint = 0x1F600; // 😀 emoji
        long packed = TerminalBuffer.pack(codepoint, 0, 0, 0);
        assertEquals(TerminalBuffer.getCodepointFromLong(packed), codepoint);
    }

    @Test
    public void testPackUnpackForegroundColor() {
        int fg = 12;
        long packed = TerminalBuffer.pack('A', fg, 0, 0);
        assertEquals(TerminalBuffer.getForegroundColor(packed), fg);
    }

    @Test
    public void testPackUnpackBackgroundColor() {
        int bg = 155;
        long packed = TerminalBuffer.pack('A', 0, bg, 0);
        assertEquals(TerminalBuffer.getBackgroundColor(packed), bg);
    }

    @Test
    public void testPackUnpackTextStyle() {
        int style = 3; // e.g. bold + underline
        long packed = TerminalBuffer.pack('A', 0, 0, style);
        assertEquals(TerminalBuffer.getTextStyle(packed), style);
    }

    @Test
    public void testPackUnpackAllAttributes() {
        int codepoint = 'B';
        int fg = 12;
        int bg = 5;
        int style = 3;
        long packed = TerminalBuffer.pack(codepoint, fg, bg, style);

        assertEquals(TerminalBuffer.getCodepointFromLong(packed), codepoint);
        assertEquals(TerminalBuffer.getForegroundColor(packed), fg);
        assertEquals(TerminalBuffer.getBackgroundColor(packed), bg);
        assertEquals(TerminalBuffer.getTextStyle(packed), style);
    }

    // --- Character Writing Tests ---

    @Test
    public void testPutCharMovesX() {
        buffer.putChar('A', 1, 0, 0);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 1, "Cursor X should move to 1");
    }

    @Test
    public void testPutCharStoresCorrectValue() {
        buffer.putChar('X', 1, 5, 3);
        long cell = buffer.getCellAt(0, 0);
        assertEquals(TerminalBuffer.getCodepointFromLong(cell), 'X');
        assertEquals(TerminalBuffer.getForegroundColor(cell), 1);
        assertEquals(TerminalBuffer.getBackgroundColor(cell), 5);
        assertEquals(TerminalBuffer.getTextStyle(cell), 3);
    }

    @Test
    public void testPutCharNewlineAtEndOfLine() {
        // Fill a line
        for (int i = 0; i < TEST_WIDTH; i++) {
            buffer.putChar('A', 1, 0, 0);
        }

        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 0, "Cursor X should wrap to 0");
        assertEquals(cursor[1], 1, "Cursor Y should be 1");
    }

    @Test
    public void testPutCharNewlineCharacter() {
        buffer.putChar('A', 1, 0, 0);
        buffer.putChar('\n', 1, 0, 0);

        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 0, "Cursor X should be 0 after newline");
        assertEquals(cursor[1], 1, "Cursor Y should be 1 after newline");
    }

    // --- Insert Text Tests ---

    @Test
    public void testPutText() {
        buffer.putText("Hello", 1, 0, 0);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 5, "Cursor should move 5 positions for 'Hello'");
        assertEquals(cursor[1], 0, "Cursor Y should still be 0");
    }

    @Test
    public void testPutTextWithMultipleLines() {
        buffer.putText("ABC\nDEF", 1, 0, 0);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 3, "Cursor X should be at 3 for 'DEF'");
        assertEquals(cursor[1], 1, "Cursor Y should be 1 after newline");
    }

    @Test
    public void testPutTextMultipleUTF8Characters() {
        buffer.putText("A😀B", 1, 0, 0);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 4, "Cursor should advance for each codepoint");
    }

    // --- Character No Movement Tests ---

    @Test
    public void testInsertChar() {
        buffer.putChar('A', 1, 0, 0);
        buffer.putChar('B', 1, 0, 0);
        buffer.insertChar('X', 1, 0, 0);

        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 2, "Cursor should not move after insertChar");
    }

    @Test
    public void testInsertCharOutOfBounds() {
        buffer.setCursorPosition(TEST_WIDTH + 10, TEST_HEIGHT + 10);
        buffer.insertChar('A', 1, 0, 0);

        // Cursor gets clamped to valid bounds by setCursorPosition
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], TEST_WIDTH - 1, "Cursor X should be clamped to max");
        assertEquals(cursor[1], TEST_HEIGHT - 1, "Cursor Y should be clamped to max");
    }

    // --- Cursor Control Tests ---

    @Test
    public void testSetCursorPosition() {
        buffer.setCursorPosition(10, 5);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 10);
        assertEquals(cursor[1], 5);
    }

    @Test
    public void testSetCursorPositionClampedX() {
        buffer.setCursorPosition(-5, 5);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 0);
    }

    @Test
    public void testSetCursorPositionClampedMaxX() {
        buffer.setCursorPosition(TEST_WIDTH + 100, 5);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], TEST_WIDTH - 1);
    }

    @Test
    public void testSetCursorPositionClampedY() {
        buffer.setCursorPosition(5, -5);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[1], 0);
    }

    @Test
    public void testSetCursorPositionClampedMaxY() {
        buffer.setCursorPosition(5, TEST_HEIGHT + 100);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[1], TEST_HEIGHT - 1);
    }

    @Test
    public void testMoveCursor() {
        buffer.setCursorPosition(10, 10);
        buffer.moveCursor(5, 3);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 15);
        assertEquals(cursor[1], 13);
    }

    @Test
    public void testMoveCursorNegative() {
        buffer.setCursorPosition(10, 10);
        buffer.moveCursor(-5, -3);
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 5);
        assertEquals(cursor[1], 7);
    }

    // --- Clear Screen Tests ---

    @Test
    public void testClearScreen() {
        // Fill the screen with content
        for (int y = 0; y < TEST_HEIGHT; y++) {
            buffer.putText("Text", 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        // Move cursor away
        buffer.setCursorPosition(50, 20);

        // Verify screen has content before clearing
        String screenBefore = buffer.getScreenAsString();
        System.out.println("Screen before clear:\n" + screenBefore);
        assertTrue(screenBefore.contains("Text"), "Screen should contain 'Text' before clear");

        // Clear should reset cursor and wipe all visible cells
        buffer.clearScreen();

        // Verify cursor is reset
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 0, "Cursor X should be reset");
        assertEquals(cursor[1], 0, "Cursor Y should be reset");

        // Verify all cells are cleared (no content in getScreenAsString)
        String screenAfter = buffer.getScreenAsString();
        System.out.println("Screen after clear:\n" + screenAfter);
        assertFalse(screenAfter.contains("Text"), "Screen should not contain 'Text' after clear");

        // Verify all visible cells are actually empty by checking no non-zero codepoints exist
        for (int screenY = 0; screenY < TEST_HEIGHT; screenY++) {
            for (int x = 0; x < TEST_WIDTH; x++) {
                long cell = buffer.getCellAt(x, screenY);
                int codepoint = TerminalBuffer.getCodepointFromLong(cell);
                assertEquals(codepoint, 0, "Cell at (" + x + ", " + screenY + ") should be 0");
            }
        }
    }

    @Test
    public void testClearScreenAndScrollback() {
        // Fill buffer beyond screen height
        for (int i = 0; i < TEST_HEIGHT + TEST_SCROLLBACK - 10; i++) {
            buffer.putText("Line " + i, 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        // Scroll and move cursor
        buffer.scrollUp(50);
        buffer.setCursorPosition(40, 12);

        // Verify buffer has content before clearing
        String screenBefore = buffer.getScreenAsString();
        assertTrue(screenBefore.contains("Line"), "Screen should contain content before clear");

        buffer.clearScreenAndScrollback();

        // Verify cursor is reset
        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[0], 0, "Cursor X should be reset");
        assertEquals(cursor[1], 0, "Cursor Y should be reset");

        // Verify all visible screen cells are empty
        String screenAfter = buffer.getScreenAsString();
        assertFalse(screenAfter.contains("Line"), "Screen should not contain 'Line' after clear");

        // Verify scrollback is also cleared
        String fullBuffer = buffer.getFullBufferAsString();
        assertFalse(fullBuffer.contains("Line"), "Full buffer should not contain 'Line' after clear");
    }

    // --- Scrolling Tests ---

    @Test
    public void testScrollUp() {
        // Fill buffer with content
        for (int i = 0; i < TEST_HEIGHT + 10; i++) {
            buffer.putText("Line " + i, 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        buffer.scrollUp(10);
        String screen = buffer.getScreenAsString();
        assertTrue(screen.contains("Line 1\n"), "Should see content after scroll");
    }

    @Test
    public void testScrollDown() {
        for (int i = 0; i < TEST_HEIGHT + 10; i++) {
            buffer.putText("Line " + i, 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        buffer.scrollUp(10);
        buffer.scrollDown(5);
        String screen = buffer.getScreenAsString();
        // Verify top and bottom line we should see
        System.out.println(screen);
        assertTrue(screen.contains("Line 6\n"), "Should see content after scroll");
        assertTrue(screen.contains("Line 29"), "Should see content after scroll");
    }

    @Test
    public void testScrollUpBoundary() {
        for (int i = 0; i < TEST_HEIGHT + 10; i++) {
            buffer.putText("Line " + i, 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        buffer.scrollUp(1000); // Try to scroll up more than available
        String screen = buffer.getScreenAsString();
        System.out.println(screen);
        assertTrue(screen.contains("Line 1\n"), "Should see content after scroll");
    }

    @Test
    public void testScrollDownBoundary() {
        buffer.scrollDown(100); // Try to scroll down below 0
        // Should clamp to 0
    }

    // --- Content Access Tests ---

    @Test
    public void testGetCellAt() {
        buffer.putChar('Z', 1, 5, 2);
        long cell = buffer.getCellAt(0, 0);

        assertEquals(TerminalBuffer.getCodepointFromLong(cell), 'Z');
        assertEquals(TerminalBuffer.getForegroundColor(cell), 1);
        assertEquals(TerminalBuffer.getBackgroundColor(cell), 5);
        assertEquals(TerminalBuffer.getTextStyle(cell), 2);
    }

    @Test
    public void testGetCellAtMultiLine() {
        buffer.putChar('A', 1, 0, 0);
        buffer.putChar('\n', 1, 0, 0);
        buffer.putChar('B', 1, 5, 2);

        // getCellAt takes screen coordinates, not physical rows
        long cellFirstLine = buffer.getCellAt(0, 0);
        assertEquals(TerminalBuffer.getCodepointFromLong(cellFirstLine), 'A');

        long cellSecondLine = buffer.getCellAt(0, 1);
        assertEquals(TerminalBuffer.getCodepointFromLong(cellSecondLine), 'B');
        assertEquals(TerminalBuffer.getForegroundColor(cellSecondLine), 1);
        assertEquals(TerminalBuffer.getBackgroundColor(cellSecondLine), 5);
    }

    @Test
    public void testGetCodepointAt() {
        buffer.putChar('M', 1, 0, 0);
        int codepoint = buffer.getCodepointAt(0, 0);
        assertEquals(codepoint, 'M');
    }

    @Test
    public void testGetAttributesAt() {
        buffer.putChar('A', 1, 2, 5);
        int[] attributres  = buffer.getAttributesAt(0, 0);
        assertEquals(attributres[0], 1, "Foreground color");
        assertEquals(attributres[1], 2, "Background color");
        assertEquals(attributres[2], 5, "Text style");
    }

    @Test
    public void testGetLineAsString() {
        buffer.putText("TestLine", 1, 0, 0);
        // getLineAsString takes physical row index
        // Just verify it doesn't crash and returns a string
        String line = buffer.getLineAsString(0);
        assertTrue(line.contains("TestLine"), "getScreenAsString should return a non-null string");
    }

    @Test
    public void testGetScreenAsString() {
        for (int i = 0; i < TEST_HEIGHT + 10; i++) {
            buffer.putText("Screen" + i, 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        String screen = buffer.getScreenAsString();
        // Make sure we see the last lines and not the first
        assertFalse(screen.contains("Screen1\n"));
        assertTrue(screen.contains("Screen" + (TEST_HEIGHT + 9)));
    }

    @Test
    public void testGetFullBufferAsString() {
        // Fill multiple lines
        for (int i = 0; i < TEST_HEIGHT + 10; i++) {
            buffer.putText("Buffer" + i, 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        String fullBuf = buffer.getFullBufferAsString();
        System.out.println(fullBuf);
        assertTrue(fullBuf.contains("Buffer0"));
        assertTrue(fullBuf.contains("Buffer" + (TEST_HEIGHT + 9)));
    }

    // --- Circular Buffer / Scrollback Tests ---

    @Test
    public void testCircularBufferWrapping() {
        // Fill buffer beyond capacity
        for (int i = 0; i < TEST_HEIGHT + TEST_SCROLLBACK + 50; i++) {
            buffer.putText("X", 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        // Should not crash and buffer should still contain data
        String screen = buffer.getScreenAsString();
        assertTrue(screen.contains("X"), "Buffer should still contain data after wraparound");
    }

    @Test
    public void testScrollbackPreservesContent() {
        // Add lines to fill scrollback
        for (int i = 0; i < TEST_HEIGHT + 20; i++) {
            buffer.putText("Line" + String.format("%03d", i), 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        // Scroll up to see older content
        buffer.scrollUp(20);
        String screen = buffer.getScreenAsString();
        // Should be able to see older lines
        assertFalse(screen.isEmpty(), "Should have content after scrolling up");
    }

    // --- Edge Cases ---

    @Test
    public void testEmptyBuffer() {
        String screen = buffer.getScreenAsString();
        assertNotNull(screen, "getScreenAsString should return a non-null string for empty buffer");
    }

    @Test
    public void testMaxCodepointValue() {
        int maxCodepoint = 0x10FFFF; // Max valid Unicode codepoint
        long packed = TerminalBuffer.pack(maxCodepoint, 0, 0, 0);
        // Should fit in 21 bits (max value is 0x1FFFFF)
        assertEquals(TerminalBuffer.getCodepointFromLong(packed), maxCodepoint & 0x1FFFFFL);
    }

    @Test
    public void testMultipleAttributeUpdates() {
        buffer.putChar('A', 10, 20, 1);
        buffer.putChar('B', 30, 40, 2);
        buffer.putChar('C', 50, 60, 3);

        assertEquals(buffer.getAttributesAt(0, 0)[0], 10);
        assertEquals(buffer.getAttributesAt(1, 0)[0], 30);
        assertEquals(buffer.getAttributesAt(2, 0)[0], 50);
    }

    @Test
    public void testLongTextInsertion() {
        String longText = "A".repeat(1000);
        buffer.putText(longText, 1, 0, 0);
        int[] cursor = buffer.getCursorPosition();
        // Cursor should have wrapped around multiple times and advanced
        assertTrue(cursor[0] >= 0 && cursor[0] < TEST_WIDTH, "Cursor X should be within bounds");
        assertTrue(cursor[1] > 0 && cursor[1] < TEST_HEIGHT, "Cursor Y should be within bounds");
    }

    @Test
    public void testMixedUnicodeCharacters() {
        buffer.putText("Hello世界😀🌍", 1, 0, 0);
        int[] cursor = buffer.getCursorPosition();
        // Each codepoint advances the cursor: H e l l o 世 界 😀 🌍 = 13 codepoints
        assertEquals(cursor[0], 13, "Cursor should advance for all 9 codepoints (double for the double wide");
    }

    // --- Resize Tests ---

    @Test
    public void testResizeIncreaseWidthAndHeight() {
        // Add content to the buffer
        buffer.putText("Hello", 1, 0, 0);
        buffer.putChar('\n', 1, 0, 0);
        buffer.putText("World", 1, 0, 0);

        // Verify content before resize
        String screenBefore = buffer.getScreenAsString();
        assertTrue(screenBefore.contains("Hello"));
        assertTrue(screenBefore.contains("World"));

        // Resize to larger dimensions
        buffer.resize(100, 30);

        // Verify content is preserved after resize
        String screenAfter = buffer.getFullBufferAsString();
        System.out.println(screenAfter);
        assertTrue(screenAfter.contains("Hello"), "Content should be preserved after resize");
        assertTrue(screenAfter.contains("World"), "Content should be preserved after resize");

        // Verify cursor is adjusted
        int[] cursor = buffer.getCursorPosition();
        assertTrue(cursor[0] >= 0 && cursor[0] < 100, "Cursor X should be within new bounds");
        assertTrue(cursor[1] >= 0 && cursor[1] < 30, "Cursor Y should be within new bounds");
    }

    @Test
    public void testResizeDecreaseWidthAndHeight() {
        // Fill buffer with content
        for (int i = 0; i < TEST_HEIGHT; i++) {
            buffer.putText("TestLine" + i, 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        // Verify content before resize
        String screenBefore = buffer.getScreenAsString();
        assertTrue(screenBefore.contains("TestLine"));

        // Resize to smaller dimensions
        buffer.resize(40, 12);

        // Verify content is still present after resize (though some may be clipped)
        String screenAfter = buffer.getScreenAsString();
        assertTrue(screenAfter.contains("TestLine"), "Content should be preserved even after resize to smaller");

        // Verify cursor is adjusted
        int[] cursor = buffer.getCursorPosition();
        assertTrue(cursor[0] >= 0 && cursor[0] < 40, "Cursor X should be within new bounds");
        assertTrue(cursor[1] >= 0 && cursor[1] < 12, "Cursor Y should be within new bounds");
    }

    @Test
    public void testResizePreservesContentOrder() {
        // Add numbered lines
        for (int i = 0; i < TEST_HEIGHT + 1; i++) {
            buffer.putText("Line" + i, 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        // Get content before resize
        String screenBefore = buffer.getScreenAsString();
        System.out.println(screenBefore);

        // Resize to same height but different width
        buffer.resize(90, TEST_HEIGHT);

        // Get content after resize
        String screenAfter = buffer.getScreenAsString();
        System.out.println(screenAfter);

        // Verify the order of lines is preserved (check for sequential line numbers)
        assertTrue(screenAfter.contains("Line9"), "Later lines should still appear");
        assertFalse(screenAfter.contains("Line0"), "Earlier lines should have scrolled off");
    }

    @Test
    public void testResizeReducesScrollback() {
        // Add many lines to exceed buffer
        for (int i = 0; i < TEST_SCROLLBACK + 50; i++) {
            buffer.putText("Item" + i, 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        // Resize to smaller scrollback (newBufferHeight = newHeight + (bufferHeight - height))
        // This should reduce the overall buffer capacity
        int oldBufferSize = TEST_HEIGHT + TEST_SCROLLBACK;
        buffer.resize(TEST_WIDTH, 10); // newBufferHeight = 10 + (124 - 24) = 110

        // Verify that content is still accessible (oldest lines may be lost)
        String screen = buffer.getScreenAsString();
        assertNotNull(screen, "Screen should not be null after reducing scrollback");
    }

    @Test
    public void testResizeClampsWidthToOne() {
        buffer.putText("ABC", 1, 0, 0);

        // Resize to width 1
        buffer.resize(1, TEST_HEIGHT);

        // Should not crash
        String screen = buffer.getScreenAsString();
        assertNotNull(screen, "Screen should be readable after resizing to width 1");
    }

    @Test
    public void testResizeClampsHeightToOne() {
        buffer.putText("ABC", 1, 0, 0);
        buffer.putChar('\n', 1, 0, 0);
        buffer.putText("DEF", 1, 0, 0);

        // Resize to height 1
        buffer.resize(TEST_WIDTH, 1);

        // Should not crash
        String screen = buffer.getScreenAsString();
        assertNotNull(screen, "Screen should be readable after resizing to height 1");

        int[] cursor = buffer.getCursorPosition();
        assertEquals(cursor[1], 0, "Cursor Y should be clamped to 0");
    }

    @Test
    public void testResizeWithAttributes() {
        // Add colored text with attributes
        buffer.putChar('A', 10, 20, 1);
        buffer.putChar('B', 30, 40, 2);
        buffer.putChar('C', 50, 60, 3);
        buffer.putChar('\n', 1, 0, 0);
        buffer.putChar('D', 70, 80, 4);

        // Resize
        buffer.resize(100, 30);

        // Verify attributes are preserved (check first line)
        int[] attrs0 = buffer.getAttributesAt(0, 0);
        assertEquals(attrs0[0], 10, "Foreground color should be preserved");
        assertEquals(attrs0[1], 20, "Background color should be preserved");
        assertEquals(attrs0[2], 1, "Text style should be preserved");

        int[] attrs1 = buffer.getAttributesAt(1, 0);
        assertEquals(attrs1[0], 30, "Foreground color should be preserved");
        assertEquals(attrs1[1], 40, "Background color should be preserved");
        assertEquals(attrs1[2], 2, "Text style should be preserved");
    }

    @Test
    public void testResizeWithDoubleWideCharacters() {
        // Add double-wide character (emoji)
        buffer.putChar('A', 1, 0, 0);
        buffer.putChar(0x1F600, 1, 0, 0); // Emoji takes 2 cells
        buffer.putChar('B', 1, 0, 0);
        buffer.putChar('\n', 1, 0, 0);
        buffer.putText("Test", 1, 0, 0);

        String screenBefore = buffer.getScreenAsString();

        // Resize
        buffer.resize(100, 30);

        String screenAfter = buffer.getScreenAsString();
        // Content should still be readable
        assertTrue(screenAfter.contains("Test"), "Regular text should be preserved");
    }

    @Test
    public void testResizeResetsCursorToValidPosition() {
        // Set cursor to specific position
        buffer.setCursorPosition(50, 20);

        // Resize to smaller dimensions
        buffer.resize(40, 12);

        // Verify cursor is clamped to new valid bounds
        int[] cursor = buffer.getCursorPosition();
        assertTrue(cursor[0] < 40, "Cursor X should be less than new width");
        assertTrue(cursor[1] < 12, "Cursor Y should be less than new height");
        assertTrue(cursor[0] >= 0 && cursor[1] >= 0, "Cursor should have non-negative coordinates");
    }

    @Test
    public void testResizeResetsScrollOffset() {
        // Add lots of content and scroll up
        for (int i = 0; i < TEST_SCROLLBACK + 50; i++) {
            buffer.putText("Line" + i, 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }
        buffer.scrollUp(50);

        // Resize
        buffer.resize(100, 30);

        // Verify scroll offset is reset (should see bottom of buffer)
        String screen = buffer.getScreenAsString();
        assertNotNull(screen, "Screen should be valid after resize with scroll reset");
    }

    @Test
    public void testResizePreservesLineStructure() {
        // Add multiple distinct lines
        buffer.putText("First", 1, 0, 0);
        buffer.putChar('\n', 1, 0, 0);
        buffer.putText("Second", 2, 0, 0);
        buffer.putChar('\n', 1, 0, 0);
        buffer.putText("Third", 3, 0, 0);

        // Resize
        buffer.resize(90, 28);

        // Verify all lines are still present
        String screen = buffer.getScreenAsString();
        assertTrue(screen.contains("First"), "First line should be preserved");
        assertTrue(screen.contains("Second"), "Second line should be preserved");
        assertTrue(screen.contains("Third"), "Third line should be preserved");
    }

    @Test
    public void testResizeWithNoContentDoesntCrash() {
        // Empty buffer
        buffer.resize(100, 30);

        // Should not crash
        String screen = buffer.getScreenAsString();
        assertNotNull(screen, "Empty screen should not crash");
    }

    @Test
    public void testResizeLargeToSmallWidthClipsContent() {
        // Fill a line with text
        String longText = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        buffer.putText(longText, 1, 0, 0);

        // Resize to smaller width
        buffer.resize(10, TEST_HEIGHT);

        // The text should be clipped at the right edge
        String screen = buffer.getScreenAsString();
        int firstLineLength = screen.split("\n", 2)[0].length();
        assertTrue(firstLineLength <= 10, "First line should not exceed new width");
    }

    @Test
    public void testResizeDoubleWideCharacterPreservedWhenFits() {
        // Write: A 😀 B  at columns 0,1,2(placeholder),3
        buffer.putChar('A', 1, 0, 0);
        buffer.putChar(0x1F600, 1, 0, 0); // occupies cols 1 and 2
        buffer.putChar('B', 1, 0, 0);

        // Resize to width 6 — pair at cols 1-2 fits completely
        buffer.resize(6, TEST_HEIGHT);

        long charCell = buffer.getCellAt(1, 0);
        long placeholderCell = buffer.getCellAt(2, 0);
        assertEquals(TerminalBuffer.getCodepointFromLong(charCell), 0x1F600,
                "Character cell should be preserved when the pair fits within new width");
        assertEquals(TerminalBuffer.getCodepointFromLong(placeholderCell), 0,
                "Placeholder cell should still be present");
        assertTrue((placeholderCell & (1L << 45)) != 0,
                "Placeholder DOUBLE_WIDTH_FLAG should still be set");
    }

    // Placeholder (right half) lands exactly on the last column — both cells wiped
    @Test
    public void testResizeDoubleWidePlaceholderAtLastColumn() {
        // 😀 at column 0 (char) and 1 (placeholder), then resize to width 2
        // → placeholder is the last cell, pair straddles nothing yet,
        //   so resize to width 1 cuts the placeholder off.
        buffer.putChar(0x1F600, 1, 0, 0); // cols 0 (char) and 1 (placeholder)

        // Resize to width 1: placeholder at col 1 is cut, character at col 0 must also be wiped
        buffer.resize(1, TEST_HEIGHT);

        long charCell = buffer.getCellAt(0, 0);
        assertEquals(TerminalBuffer.getCodepointFromLong(charCell), 0,
                "Character cell must be wiped when its placeholder is cut by resize");
        assertEquals(charCell & (1L << 45), 0L,
                "DOUBLE_WIDTH_FLAG must be cleared after wiping");
    }

    // Character (left half) lands exactly on the last column — that cell wiped
    @Test
    public void testResizeDoubleWideCharacterAtLastColumn() {
        // Write A then 😀: A at col 0, char-half at col 1, placeholder at col 2
        buffer.putChar('A', 1, 0, 0);
        buffer.putChar(0x1F600, 1, 0, 0); // cols 1 (char) and 2 (placeholder)

        // Resize to width 2: char-half is at the last column (col 1), placeholder is cut
        buffer.resize(2, TEST_HEIGHT);

        long charCell = buffer.getCellAt(1, 0);
        assertEquals(TerminalBuffer.getCodepointFromLong(charCell), 0,
                "Character cell must be wiped when its placeholder falls outside new width");
        assertEquals(charCell & (1L << 45), 0L,
                "DOUBLE_WIDTH_FLAG must be cleared after wiping");

        // A at col 0 must be untouched
        assertEquals(TerminalBuffer.getCodepointFromLong(buffer.getCellAt(0, 0)), 'A',
                "Preceding regular character must be unaffected");
    }

    @Test
    public void testResizeMaintainsBufferConsistency() {
        // Fill buffer with specific pattern
        for (int i = 0; i < 5; i++) {
            buffer.putText("Pattern" + i, 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        // Resize multiple times
        buffer.resize(100, 30);
        buffer.resize(50, 15);
        buffer.resize(80, 25);

        // Verify buffer is still consistent (no crash and content accessible)
        String screen = buffer.getScreenAsString();
        System.out.println(screen);
        assertTrue(screen.contains("Pattern"), "Content should survive multiple resizes");
    }

    @Test
    public void testResizePreservesLastLines() {
        // Add numbered lines to track order
        for (int i = 0; i < 20; i++) {
            buffer.putText(String.format("Line%02d", i), 1, 0, 0);
            buffer.putChar('\n', 1, 0, 0);
        }

        String screenBefore = buffer.getScreenAsString();

        // Resize to different dimensions
        buffer.resize(90, 25);

        String screenAfter = buffer.getScreenAsString();

        // The last lines should still be visible
        assertTrue(screenAfter.contains("Line19"), "Most recent content should be preserved");
    }
}

