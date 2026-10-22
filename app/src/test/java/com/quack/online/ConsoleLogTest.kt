package com.quack.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleLogTest {

    @Test
    fun appendsWithoutRequiringAFullRerenderUntilContentIsTrimmed() {
        val log = ConsoleLog(maxLines = 3, maxCharacters = 64)

        assertFalse(log.append("first"))
        assertFalse(log.append("second"))
        assertEquals("first\nsecond\n", log.snapshot())

        assertFalse(log.append("third"))
        assertTrue(log.append("fourth"))
        assertEquals("second\nthird\nfourth\n", log.snapshot())
    }

    @Test
    fun enforcesCharacterLimitAndKeepsRecentOutput() {
        val log = ConsoleLog(maxLines = 10, maxCharacters = 8)

        assertFalse(log.append("aa"))
        assertFalse(log.append("bb"))
        assertTrue(log.append("ccc"))

        assertEquals("bb\nccc\n", log.snapshot())
    }

    @Test
    fun truncatesAnOversizedLineToItsMostRecentCharacters() {
        val log = ConsoleLog(maxLines = 5, maxCharacters = 6)

        assertTrue(log.append("abcdefgh"))

        assertEquals("defgh\n", log.snapshot())
    }

    @Test
    fun clearRemovesAllRetainedOutput() {
        val log = ConsoleLog(maxLines = 5, maxCharacters = 64)
        log.append("output")

        log.clear()

        assertEquals("", log.snapshot())
        assertFalse(log.append("new output"))
        assertEquals("new output\n", log.snapshot())
    }
}
