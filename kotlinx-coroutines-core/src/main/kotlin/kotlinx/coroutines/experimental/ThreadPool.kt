package kotlinx.coroutines.experimental

import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import java.util.concurrent.atomic.AtomicLongFieldUpdater
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.locks.LockSupport
import kotlin.coroutines.CoroutineContext

internal class ThreadPool(
    val maxThreads: Int,
    val name: String,
    val job: Job? = null
) : CoroutineDispatcher() {
    init {
        require(maxThreads in 1..(WORKER_MASK.toInt() - 1))
    }

    private val tasks = LockFreeLinkedListHead()

    private val workers = AtomicReferenceArray<Worker?>(maxThreads + 1) // ids starts at 1

    @Volatile
    private var state: Long = 0L

    private companion object {
        val STATE: AtomicLongFieldUpdater<ThreadPool> =
            AtomicLongFieldUpdater.newUpdater(ThreadPool::class.java, "state")

        const val WORKER_BITS = 16
        const val WORKER_MASK = (1L shl WORKER_BITS) - 1L

        const val VERSION_SHIFT = WORKER_BITS
        const val VERSION_BITS = 16
        const val VERSION_INC = 1L shl VERSION_SHIFT
        const val VERSION_MASK = ((1L shl VERSION_BITS) - 1L) shl VERSION_SHIFT

        const val THREADS_SHIFT = VERSION_SHIFT + VERSION_BITS
        const val THREADS_BITS = 16
        const val THREADS_MASK = ((1L shl THREADS_BITS) - 1L) shl THREADS_SHIFT

        @JvmStatic
        fun Long.nextVersion() = ((this + VERSION_INC) and VERSION_MASK) or (this and VERSION_MASK.inv())

        @JvmStatic
        fun Long.withWorker(worker: Int) = (this and WORKER_MASK.inv()) or worker.toLong()

        @JvmStatic
        fun Long.withThreads(threads: Int) = (this and THREADS_MASK.inv()) or (threads.toLong() shl THREADS_SHIFT)
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        while (true) { // lock-free loop of state
            val state = this.state // volatile read
            val worker = takeParkedWorker(state)
            if (worker == null) {
                // no parked workers or failed to take one due to contention
                // ... see if there's room to create new one
                val threads = ((state and THREADS_MASK) shr THREADS_SHIFT).toInt()
                if (threads < maxThreads) {
                    // we can try to create new one!
                    if (STATE.compareAndSet(this, state, state.nextVersion().withThreads(threads + 1))) {
                        newWorker().scheduleAndStart(block)
                        return
                    }
                } else {
                    // all threads are busy or high contention... just queue a task
                    tasks.addLast(makeTask(block))
                    // recheck state if there is a parked thread now
                    takeParkedWorker(this.state)?.let { LockSupport.unpark(it) }
                    // done? todo:
                    return
                }
            } else {
                // there is a parked worker we can try to use!
                if (STATE.compareAndSet(this, state, state.nextVersion().withWorker(worker.next))) {
                    worker.scheduleAndUnpark(block)
                    return
                }
            }
        }
    }

    private fun takeParkedWorker(state: Long): Worker? {
        val workerId = (state and WORKER_MASK).toInt()
        if (workerId == 0) return null
        val worker = workers.get(workerId)!!
        if (STATE.compareAndSet(this, state, state.nextVersion().withWorker(worker.next))) return worker
        return null
    }

    private fun makeTask(block: Runnable): Task = (block as? Task) ?: Task(block)

    private class Task(block: Runnable) : LockFreeLinkedListNode(), Runnable by block

    private fun newWorker(): Worker {
        // todo
        return Worker(1)
    }


    private inner class Worker(val id: Int) : Thread("$name-Worker-$id") {
        var task: Runnable? = null
        var next: Int = 0

        override fun run() {
            // todo
        }

        fun scheduleAndStart(block: Runnable) {
            task = block
            start()
        }

        fun scheduleAndUnpark(block: Runnable) {
            task = block
            LockSupport.unpark(this)
        }
    }
}