package com.quack.online.network

import org.junit.Test

class RegexBenchmarkTest {
    @Test
    fun benchmarkRegex() {
        val regexWithLookahead = Regex("^(?=.{1,253}\$)(?:(?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+[a-zA-Z]{2,63}\$")
        val regexWithoutLookahead = Regex("^(?:(?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+[a-zA-Z]{2,63}\$")

        val testStr = "www.google.com"

        // Warmup
        for(i in 1..10000) {
            regexWithLookahead.matches(testStr)
            regexWithoutLookahead.matches(testStr)
        }

        val t1 = System.nanoTime()
        for(i in 1..100000) {
            regexWithLookahead.matches(testStr)
        }
        val timeWith = System.nanoTime() - t1

        val t2 = System.nanoTime()
        for(i in 1..100000) {
            regexWithoutLookahead.matches(testStr)
        }
        val timeWithout = System.nanoTime() - t2

        println("With lookahead:    ${timeWith / 1000000} ms")
        println("Without lookahead: ${timeWithout / 1000000} ms")
    }
}
