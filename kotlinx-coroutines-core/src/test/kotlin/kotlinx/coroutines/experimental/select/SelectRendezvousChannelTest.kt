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

package kotlinx.coroutines.experimental.select

import kotlinx.coroutines.experimental.TestBase
import kotlinx.coroutines.experimental.channels.RendezvousChannel
import kotlinx.coroutines.experimental.intrinsics.startUndispatchedCoroutine
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.coroutines.experimental.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectRendezvousChannelTest : TestBase() {
    @Test
    fun testSelectSendSuccess() = runBlocking<Unit> {
        expect(1)
        val channel = RendezvousChannel<String>()
        launch(context) {
            expect(2)
            assertEquals("OK", channel.receive())
            finish(6)
        }
        yield() // to launched coroutine
        expect(3)
        select<Unit> {
            channel.onSend("OK") {
                expect(4)
            }
        }
        expect(5)
    }

    @Test
    fun testSelectSendSuccessWithDefault() = runBlocking<Unit> {
        expect(1)
        val channel = RendezvousChannel<String>()
        launch(context) {
            expect(2)
            assertEquals("OK", channel.receive())
            finish(6)
        }
        yield() // to launched coroutine
        expect(3)
        select<Unit> {
            channel.onSend("OK") {
                expect(4)
            }
            default {
                expectUnreached()
            }
        }
        expect(5)
    }

    @Test
    fun testSelectSendWaitWithDefault() = runBlocking<Unit> {
        expect(1)
        val channel = RendezvousChannel<String>()
        select<Unit> {
            channel.onSend("OK") {
                expectUnreached()
            }
            default {
                expect(2)
            }
        }
        expect(3)
        // make sure receive blocks (select above is over)
        launch(context) {
            expect(5)
            assertEquals("CHK", channel.receive())
            finish(8)
        }
        expect(4)
        yield()
        expect(6)
        channel.send("CHK")
        expect(7)
    }

    @Test
    fun testSelectSendWait() = runBlocking<Unit> {
        expect(1)
        val channel = RendezvousChannel<String>()
        launch(context) {
            expect(3)
            assertEquals("OK", channel.receive())
            expect(4)
        }
        expect(2)
        select<Unit> {
            channel.onSend("OK") {
                expect(5)
            }
        }
        finish(6)
    }

    @Test
    fun testSelectReceiveSuccess() = runBlocking<Unit> {
        expect(1)
        val channel = RendezvousChannel<String>()
        launch(context) {
            expect(2)
            channel.send("OK")
            finish(6)
        }
        yield() // to launched coroutine
        expect(3)
        select<Unit> {
            channel.onReceive { v ->
                expect(4)
                assertEquals("OK", v)
            }
        }
        expect(5)
    }

    @Test
    fun testSelectReceiveSuccessWithDefault() = runBlocking<Unit> {
        expect(1)
        val channel = RendezvousChannel<String>()
        launch(context) {
            expect(2)
            channel.send("OK")
            finish(6)
        }
        yield() // to launched coroutine
        expect(3)
        select<Unit> {
            channel.onReceive { v ->
                expect(4)
                assertEquals("OK", v)
            }
            default {
                expectUnreached()
            }
        }
        expect(5)
    }

    @Test
    fun testSelectReceiveWaitWithDefault() = runBlocking<Unit> {
        expect(1)
        val channel = RendezvousChannel<String>()
        select<Unit> {
            channel.onReceive { v ->
                expectUnreached()
            }
            default {
                expect(2)
            }
        }
        expect(3)
        // make sure send blocks (select above is over)
        launch(context) {
            expect(5)
            channel.send("CHK")
            finish(8)
        }
        expect(4)
        yield()
        expect(6)
        assertEquals("CHK", channel.receive())
        expect(7)
    }

    @Test
    fun testSelectReceiveWait() = runBlocking<Unit> {
        expect(1)
        val channel = RendezvousChannel<String>()
        launch(context) {
            expect(3)
            channel.send("OK")
            expect(4)
        }
        expect(2)
        select<Unit> {
            channel.onReceive { v ->
                expect(5)
                assertEquals("OK", v)
            }
        }
        finish(6)
    }

    @Test
    fun testSelectSendResourceCleanup() = runBlocking<Unit> {
        val channel = RendezvousChannel<Int>()
        val n = 10_000_000
        expect(1)
        repeat(n) { i ->
            select {
                channel.onSend(i) { expectUnreached() }
                default { expect(i + 2) }
            }
        }
        finish(n + 2)
    }

    @Test
    fun testSelectReceiveResourceCleanup() = runBlocking<Unit> {
        val channel = RendezvousChannel<Int>()
        val n = 10_000_000
        expect(1)
        repeat(n) { i ->
            select {
                channel.onReceive { v -> expectUnreached() }
                default { expect(i + 2) }
            }
        }
        finish(n + 2)
    }

    // only for debugging
    internal fun <R> SelectBuilder<R>.default(block: suspend () -> R) {
        this as SelectBuilderImpl // type assertion
        if (!trySelect()) return
        block.startUndispatchedCoroutine(completion)
    }
}