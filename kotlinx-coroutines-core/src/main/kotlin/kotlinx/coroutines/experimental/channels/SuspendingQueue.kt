package kotlinx.coroutines.experimental.channels

/**
 * Sender's interface to [SuspendingQueue].
 */
public interface SendChannel<in E> {
    /**
     * Returns `true` if the channel is full (out of capacity) and the [send] attempt will suspend.
     */
    public val isFull: Boolean

    /**
     * Adds [element] into to this queue, suspending the caller while this queue [isFull].
     * This suspending function is cancellable.
     * If the [Job] of the current coroutine is completed while this suspending function is suspended, this function
     * immediately resumes with [CancellationException].
     */
    public suspend fun send(element: E)

    /**
     * Adds [element] into this queue if it is possible to do so immediately without violating capacity restrictions
     * and returns `true`. Otherwise, it returns `false` immediately.
     */
    public fun offer(element: E): Boolean
}

/**
 * Receiver's interface to [SuspendingQueue].
 */
public interface ReceiveChannel<out E> {
    /**
     * Returns `true` if the channel is empty (contains no elements) and the [receive] attempt will suspend.
     */
    public val isEmpty: Boolean

    /**
     * Retrieves and removes the head of this queue, suspending the caller while this queue [isEmpty].
     * This suspending function is cancellable.
     * If the [Job] of the current coroutine is completed while this suspending function is suspended, this function
     * immediately resumes with [CancellationException].
     */
    public suspend fun receive(): E

    /**
     * Retrieves and removes the head of this queue, or returns `null` if this queue [isEmpty].
     */
    public fun pool(): E?
}

/**
 * Suspending queue is a non-blocking queue with suspending operations.
 */
public interface SuspendingQueue<E> : SendChannel<E>, ReceiveChannel<E>