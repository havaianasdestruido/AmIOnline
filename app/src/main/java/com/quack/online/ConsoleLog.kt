package com.quack.online

import java.util.ArrayDeque

/** Keeps retained console output bounded so long sessions cannot grow the TextView indefinitely. */
internal class ConsoleLog(
    private val maxLines: Int = DEFAULT_MAX_LINES,
    private val maxCharacters: Int = DEFAULT_MAX_CHARACTERS
) {
    private val lines = ArrayDeque<String>()
    private var characterCount = 0

    init {
        require(maxLines > 0) { "maxLines must be positive" }
        require(maxCharacters > 0) { "maxCharacters must be positive" }
    }

    /**
     * Appends one output line.
     *
     * @return true if content was evicted or truncated, so the rendered copy should be replaced
     *         with [snapshot].
     */
    fun append(line: String): Boolean {
        var contentChanged = false
        val maxLineLength = maxCharacters - 1 // Reserve one character for the trailing newline.
        val retainedLine = if (line.length > maxLineLength) {
            contentChanged = true
            line.takeLast(maxLineLength)
        } else {
            line
        }

        lines.addLast(retainedLine)
        characterCount += retainedLine.length + 1

        while (lines.size > maxLines || characterCount > maxCharacters) {
            val removedLine = lines.removeFirst()
            characterCount -= removedLine.length + 1
            contentChanged = true
        }

        return contentChanged
    }

    fun clear() {
        lines.clear()
        characterCount = 0
    }

    fun snapshot(): String = buildString(characterCount) {
        lines.forEach { line ->
            append(line)
            append('\n')
        }
    }

    private companion object {
        const val DEFAULT_MAX_LINES = 500
        const val DEFAULT_MAX_CHARACTERS = 32 * 1024
    }
}
