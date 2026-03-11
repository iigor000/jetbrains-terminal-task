### A long represents each cell:
- the first 21 bits are the codepoint (supports full UTF range)
- the next 8 bits represent the foreground color (can be expanded from the given 16 terminal colors to a full 256-color range)
- the next 8 bits are the background color
- the next 8 bits are text styles
- the 45th bit is the double-wide marker

### A cell could have been packed into a 32-bit integer, but I didn't go for this approach for two main reasons:
- full UTF range can't be supported (and the colors and styles are also limited)
- the difference in performance on a 64-bit JVM is negligible, so this approach gives better future proofing if we want to add more data to each cell

Cursor position starts at (0,0), the top left, and the user can move it across the screen as they wish and insert a character on the cursor location.

Writing character: It takes binary representations of each filed (codepoint, foreground, background, attribute). This can easily be expanded to fit the needs of any terminal, based on how many colors they offer and what attributes are available. If the codepoint is \n, we make a new line. Then we check if the codepoint is double wide, if it is and it's at the end of the row, we go to the next line and insert the value into the cell, and then after it insert an empty character with the double_wide flag, moving the cursor for each. For a normal character we just insert it into the buffer and at the end check if the cursor is at the end (make a new line).

Writing text: Uses the method for writing a character, just for each codepoint in text.

Inserting text on a line: Works about the same as writing, except it moves already existing text before writing.

Creating a new empty line at the bottom: Moves cursor over to the next line, checks if it needs to move head (deleting the oldest line)

Clearing screen: Creates a new line for the whole height of the screen, so the user can scroll to their earlier history.

Clearing the whole buffer: Fills the whole buffer with null values, resets all the buffer variables.

Getting characters and lines from the screen and scrollback: Works by mapping values to the oldest line. Getting the first character we wrote in the terminal is (0,0) and so on. We get characters from the head (which represents the oldest written character).

Getting the entire screen and buffer: Getting the screen uses the same method for mapping screen coordinates to the buffer as does the writing characters method. Getting the entire buffer works with getting characters from the oldest line, the same as the previous methods.

Screen resizing is handled by creating a new buffer and copying the contents of the current buffer to it. This clips the existing lines when making the screen narrower and leaves gaps when widening it. This could have been done differently by having a list of dynamic length rows, with flags for marking if the line is wrapped because of \n or the lack of space. This, however, would slow down the general use of the terminal buffer, as well as resizing.

All functionality was tested with testNG, there are suites for all tests.
