/*
 * Copyright 2016-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kotlinx.coroutines.experimental.channels

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.select.select
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*

/**
 * Tests cancel atomicity for channel send & receive operations, including their select versions.
 */
@RunWith(Parameterized::class)
class ChannelAtomicCancelStressTest(val kind: TestChannelKind) {
    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun params(): Collection<Array<Any>> = TestChannelKind.values().map { arrayOf<Any>(it) }
    }

    val TEST_DURATION = 3000L

    val channel = kind.create()
    val senderDone = RendezvousChannel<Boolean>()
    val receiverDone = RendezvousChannel<Boolean>()

    var lastSent = 0
    var lastReceived = 0

    var stoppedSender = 0
    var stoppedReceiver = 0

    var missedCnt = 0
    var dupCnt = 0

    lateinit var sender: Job
    lateinit var receiver: Job

    @Test
    fun testAtomicCancelStress() = runBlocking {
        val deadline = System.currentTimeMillis() + TEST_DURATION
        launchSender()
        launchReceiver()
        val rnd = Random()
        while (System.currentTimeMillis() < deadline) {
            when (rnd.nextInt(3)) {
                0 -> { // cancel & restart sender
                    stopSender()
                    launchSender()
                }
                1 -> { // cancel & restart receiver
                    stopReceiver()
                    launchReceiver()
                }
                2 -> yield() // just yield (burn a little time)
            }
        }
        stopSender()
        stopReceiver()
        println("            Sent $lastSent ints to channel")
        println("        Received $lastReceived ints from channel")
        println("  Stopped sender $stoppedSender times")
        println("Stopped receiver $stoppedReceiver times")
        println("          Missed $missedCnt ints")
        println("      Duplicated $dupCnt ints")
        assertEquals(0, missedCnt)
        assertEquals(0, dupCnt)
        assertEquals(lastSent, lastReceived)
    }

    fun launchSender() {
        sender = launch(CommonPool) {
            val rnd = Random()
            try {
                while (true) {
                    val trySend = lastSent + 1
                    when (rnd.nextInt(2)) {
                        0 -> channel.send(trySend)
                        1 -> select { channel.onSend(trySend) {} }
                        else -> error("cannot happen")
                    }

                    lastSent = trySend // update on success
                }
            } finally {
                run(NonCancellable) { senderDone.send(true) }
            }
        }
    }

    suspend fun stopSender() {
        stoppedSender++
        sender.cancel()
        senderDone.receive()
    }

    fun launchReceiver() {
        receiver = launch(CommonPool) {
            val rnd = Random()
            try {
                while (true) {
                    val received = when (rnd.nextInt(2)) {
                        0 -> channel.receive()
                        1 -> select { channel.onReceive { it } }
                        else -> error("cannot happen")
                    }
                    val expected = lastReceived + 1
                    if (received > expected)
                        missedCnt++
                    if (received < expected)
                        dupCnt++
                    lastReceived = received
                }
            } finally {
                run(NonCancellable) { receiverDone.send(true) }
            }
        }
    }

    suspend fun stopReceiver() {
        stoppedReceiver++
        receiver.cancel()
        receiverDone.receive()
    }
}
