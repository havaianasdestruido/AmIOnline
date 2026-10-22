package com.quack.online.network

import org.junit.Test

/**
 * Manual micro-benchmark for the hostname pattern. It has no assertions: run it locally with
 * `./gradlew testDebugUnitTest --tests '*RegexBenchmarkTest*' --info` to compare timings.
 *
 * The patterns use raw strings so the backslashes reach the regex engine unchanged (a regular
 * Kotlin string needs `\\z`, which is easy to get wrong).
 */
class RegexBenchmarkTest {
    @Test
    fun benchmarkRegex() {
        val regexWithLookahead = Regex(
            """^(?=.{1,253}\z)(?:(?!-)[a-zA-Z0-9-]{1,63}(?<!-)\.)+[a-zA-Z]{2,63}\z"""
        )
        val regexWithoutLookahead = Regex(
            """^(?:(?!-)[a-zA-Z0-9-]{1,63}(?<!-)\.)+[a-zA-Z]{2,63}\z"""
        )

        val testStr = "www.google.com"

        // Warmup
        for (i in 1..10000) {
            regexWithLookahead.matches(testStr)
            regexWithoutLookahead.matches(testStr)
        }

        val t1 = System.nanoTime()
        for (i in 1..100000) {
            regexWithLookahead.matches(testStr)
        }
        val timeWith = System.nanoTime() - t1

        val t2 = System.nanoTime()
        for (i in 1..100000) {
            regexWithoutLookahead.matches(testStr)
        }
        val timeWithout = System.nanoTime() - t2

        println("With lookahead:    ${timeWith / 1000000} ms")
        println("Without lookahead: ${timeWithout / 1000000} ms")
    }
}
