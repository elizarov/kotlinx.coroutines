package kotlinx.coroutines.experimental

import kotlinx.coroutines.experimental.internal.AtomicOperationDescriptor
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import java.util.concurrent.atomic.AtomicLongFieldUpdater
import java.util.concurrent.locks.LockSupport
import kotlin.coroutines.CoroutineContext

internal class ThreadPool(
    maxThreads: Int,
    val name: String,
    val job: Job? = null,
    val threadFactory: ((Runnable, Int) -> Thread)? = null
) : CoroutineDispatcher() {
    init {
        require(maxThreads in 1..MAX_THREADS)
    }

    private val taskQueue = LockFreeLinkedListHead()
    private val parkedWorkers = LockFreeLinkedListHead()
    private val allWorkerBits = (1L shl maxThreads) - 1L

    @Volatile
    private var workerBits: Long = 0L

    companion object {
        private val WORKER_BITS: AtomicLongFieldUpdater<ThreadPool> =
            AtomicLongFieldUpdater.newUpdater(ThreadPool::class.java, "workerBits")

        val MAX_THREADS = 64

        val MAX_PARK_TIME_NS = 100_000_000L
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        while (true) { // lock-free loop of state
            // try to unpark parked worker
            val parkedWorker = parkedWorkers.next() as? ParkedWorker
            if (parkedWorker != null) {
                if (parkedWorker.removeWithAttachment(block)) {
                    // scheduled to the worker
                    LockSupport.unpark(parkedWorker.worker.thread)
                    return
                }
            }
            // see if can create new worker
            val workerBits = this.workerBits
            if (workerBits != allWorkerBits) {
                val id = java.lang.Long.numberOfTrailingZeros(workerBits.inv())
                if (WORKER_BITS.compareAndSet(this, workerBits, workerBits or (1L shl id))) {
                    // reserved id for new worker successfully
                    startNewWorker(id, block)
                    return
                }
            }
            // schedule task if there are no parked workers
            if (taskQueue.addLastIf(makeTask(block)) { parkedWorkers.isEmpty }) return // success
        }
    }

    private fun makeTask(block: Runnable): Task = (block as? Task) ?: Task(block)

    private class Task(block: Runnable) : LockFreeLinkedListNode(), Runnable by block

    private fun startNewWorker(id: Int, task: Runnable) {
        val worker = Worker(id, task)
        val thread = threadFactory?.invoke(worker, id)
                ?: Thread(worker, "$name-Worker-${id+1}").apply { /*isDaemon = true*/ }
        worker.thread = thread
        thread.start()
    }

    private class ParkedWorker(val worker: Worker) : LockFreeLinkedListNode()

    private inner class Worker(val id: Int, var task: Runnable?) : Runnable {
        val workerBit = 1L shl id
        var parkedWorker: ParkedWorker? = null
        lateinit var thread: Thread

        override fun run() {
            while (true) {
                try {
                    work()
                } catch (e: Throwable) {
                    val handled = job?.cancel(e) ?: false
                    if (!handled) thread.uncaughtExceptionHandler.uncaughtException(thread, e)
                }
            }
        }

        fun work() {
            if (task == null) {
                task = parkedWorker?.let {
                    // was parked -- see if we've got a task from unparker for us
                    if (it.isRemoved) {
                        parkedWorker = null
                        it.removedAttachment() as? Runnable
                    } else null
                }
                if (task == null) {
                    if (parkedWorker == null) {
                        // was not parked -- try take next task from queue
                        task = taskQueue.removeNextOrNull() as? Runnable
                    } else {
                        // was parked -- atomically remove from both taskQueue && parkedWorkers
                        taskQueue.describeRemoveNext()?.let { taskRemoveDesc ->
                            // we are here only if taskQueue has items
                            val parkedRemoveDesc = parkedWorker!!.describeRemove() ?: return // retry if already unparked
                            val operation = object : AtomicOperationDescriptor() {
                                override fun prepare(): Boolean =
                                    taskRemoveDesc.prepare(this) && parkedRemoveDesc.prepare(this)

                                override fun finish(node: Any?, success: Boolean) {
                                    taskRemoveDesc.finish(this, success)
                                    parkedRemoveDesc.finish(this, success)
                                }
                            }
                            if (!operation.perform(Unit)) return // retry on race
                        }
                    }
                }
            }
            task?.let {
                task = null
                it.run()
                return // return after task was run either successfully or not
            }
            // if was parked before, and no task -- try to unpark and remove itself from workers bits
            parkedWorker?.let {
                // can remove only if no tasks are queued
                if (it.remove()) {
                    // todo
                }
            }
            // add to parkedWorkers if needed
            if (parkedWorker == null)
                parkedWorker = ParkedWorker(this).also { parkedWorkers.addFirst(it) }
            LockSupport.parkNanos(MAX_PARK_TIME_NS)
        }
    }
}