package kotlinx.coroutines.experimental

import kotlin.coroutines.CoroutineContext

/**
 * Creates new coroutine execution context with the a single thread and built-in [yield] and [delay] support.
 * Resources of this pool (its thread) are reclaimed when job of this context is cancelled.
 * The specified [name] defines the name of the new thread.
 * An optional [parent] job may be specified upon creation.
 */
fun newSingleThreadContext(name: String, parent: Job? = null): CoroutineContext =
    newFixedThreadPoolContext(1, name, parent)

/**
 * Creates new coroutine execution context with the fixed-size thread-pool and built-in [yield] and [delay] support.
 * Resources of this pool (its threads) are reclaimed when job of this context is cancelled.
 * The specified [name] defines the names of the threads.
 * An optional [parent] job may be specified upon creation.
 */
fun newFixedThreadPoolContext(nThreads: Int, name: String, parent: Job? = null): CoroutineContext {
    require(nThreads >= 1) { "Expected at least one thread, but $nThreads specified" }
    val job = Job(parent)
    return job + ThreadPool(nThreads, name, job)
}

