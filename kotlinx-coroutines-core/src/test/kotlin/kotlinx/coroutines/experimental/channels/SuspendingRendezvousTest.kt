package kotlinx.coroutines.experimental.channels

import kotlinx.coroutines.experimental.TestBase
import kotlinx.coroutines.experimental.join
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test

class SuspendingRendezvousTest : TestBase() {
    @Test
    fun testSimple() = runBlocking {
        val q = SuspendingRendezvous<Int>()
        check(q.isEmpty && q.isFull)
        expect(1)
        launch(context) {
            expect(2)
            q.send(1)
            expect(5)
            q.send(2)
            expect(7)
        }
        expect(3)
        check(!q.isEmpty && q.isFull)
        launch(context) {
            expect(4)
            check(q.receive() == 1)
            expect(6)
            check(q.receive() == 2)
            expect(8)
        }
        finish(9)
        check(q.isEmpty && q.isFull)
    }

    @Test
    fun testStress() = runBlocking {
        val n = 100_000
        val q = SuspendingRendezvous<Int>()
        val sender = launch(context) {
            for (i in 1..n) q.send(i)
            expect(1)
        }
        val receiver = launch(context) {
            for (i in 1..n) check(q.receive() == i)
            expect(2)
        }
        expect(3)
        sender.join()
        receiver.join()
        finish(4)
    }
}