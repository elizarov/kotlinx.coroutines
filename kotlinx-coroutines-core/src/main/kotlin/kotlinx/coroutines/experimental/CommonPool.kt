package kotlinx.coroutines.experimental

/**
 * Represents common pool of shared threads as coroutine dispatcher for compute-intensive tasks.
 */
val CommonPool : CoroutineDispatcher = ThreadPool(Integer.getInteger("kotlinx.coroutines.CommonPool.maxThreads",
        (Runtime.getRuntime().availableProcessors().coerceAtLeast(1).coerceAtMost(ThreadPool.MAX_THREADS))), "CommonPool")
