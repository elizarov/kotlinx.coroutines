package examples

import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.defer
import kotlinx.coroutines.experimental.delay
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    println("Start")

    // Start a coroutine
    defer(CommonPool) {
        delay(1, TimeUnit.SECONDS)
        println("Hello")
    }

    println("Stop")

    for (i in 1..5) {
        val n = Thread.activeCount()
        val threads = Array<Thread?>(n) { null }
        Thread.enumerate(threads)
        println("---------- $i ------------")
        threads.forEach { println("$it, daemon=${it?.isDaemon}") }
        Thread.sleep(250L)
    }
}


