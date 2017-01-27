package kotlinx.coroutines.experimental.internal

import org.junit.Test
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Move items from one queue to another atomically, randomly from any side (first/last).
 * Ensure that nothing is lost in the process.
 */
class AtomicStressTest {
    private data class IntNode(val i: Int) : LockFreeLinkedListNode()
    private val q1 = LockFreeLinkedListHead()
    private val q2 = LockFreeLinkedListHead()

    val n = 4
    val nThreads = 2
    val timeLimit = 3000L // 3 sec
    val thresholdOps = 10_000

    val successfulOps = AtomicInteger()
    val terminatedNormally = AtomicInteger()

    fun runTestThread(deadline: Long) {
        val rnd = Random()
        while (System.currentTimeMillis() < deadline) {
            val takeFrom = rnd.nextInt(2)
            val removeDesc = when (takeFrom) {
                0 -> q1.describeRemoveFirst()
                1 -> q2.describeRemoveFirst()
                else -> error("fail")
            }
            if (removeDesc == null) continue
            val addTo = when (takeFrom) {
                0 -> q2
                1 -> q1
                else -> error("fail")
            }
            val addDesc = when (rnd.nextInt(2)) {
                0 -> addTo.describeAddFirst { IntNode((removeDesc.affectedNode as IntNode).i) }
                1 -> addTo.describeAddLast { IntNode((removeDesc.affectedNode as IntNode).i) }
                else -> error("fail")
            }
            val operation = object : AtomicOpDescriptor() {
                override fun unwrapRef(affected: Any): Any {
                    val any = removeDesc.unwrapRef(this, affected) ?: addDesc.unwrapRef(this, affected)
                    if (any == null)
                        println("!")
                    return any!!
                }
                override fun prepare(): Boolean =
                    removeDesc.prepare(this) && addDesc.prepare(this)
                override fun finish(affected: Any?, success: Boolean) {
                    removeDesc.finish(this, success)
                    addDesc.finish(this, success)
                }
            }
            if (operation.perform(null)) {
                val cnt = successfulOps.incrementAndGet()
                print('*')
                if (cnt % 100 == 0) println()
            } else
                print('.')

        }
        terminatedNormally.incrementAndGet()
    }

    @Test
    fun testAtomicsUnderStress() {
        for (i in 0..n / 2 - 1) q1.addLast(IntNode(i))
        for (i in n / 2..n - 1) q2.addLast(IntNode(i))
        val deadline = System.currentTimeMillis() + timeLimit
        val threads = Array(nThreads) { i ->
            thread(start = false, name = "TestThread-$i") {
                runTestThread(deadline)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(nThreads, terminatedNormally.get())
        q1.validate()
        q2.validate()
        val set = mutableSetOf<Int>()
        q1.forEach<IntNode> { assertTrue(set.add(it.i)) }
        q2.forEach<IntNode> { assertTrue(set.add(it.i)) }
        assertEquals(n, set.size)
        for (i in 0..n - 1) assertTrue(set.contains(i))
        println("Performed ${successfulOps.get()} operations")
        assertTrue(successfulOps.get() > thresholdOps)
    }

}