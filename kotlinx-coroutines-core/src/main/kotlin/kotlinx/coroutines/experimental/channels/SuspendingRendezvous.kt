package kotlinx.coroutines.experimental.channels

import kotlinx.coroutines.experimental.CancellableContinuation
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListHead
import kotlinx.coroutines.experimental.internal.LockFreeLinkedListNode
import kotlinx.coroutines.experimental.removeOnCompletion
import kotlinx.coroutines.experimental.suspendCancellableCoroutine

/**
 * Suspending rendezvous.
 */
public class SuspendingRendezvous<E> : SuspendingQueue<E> {
    private val waiters = LockFreeLinkedListHead()

    // ------ SendChannel ------

    override val isFull: Boolean get() = waiters.next() !is ReceiveWaiter<*>

    suspend override fun send(element: E) {
        // fast path if receive is already waiting for rendezvous
        takeFirstReceiveWaiter()?.apply {
            resumeReceive(element)
            return
        }
        // slow-path does suspend
        return suspendCancellableCoroutine sc@ { cont ->
            val waiter = SendWaiter<E>(cont, element)
            while (true) {
                if (waiters.addLastIfPrev(waiter) { it !is ReceiveWaiter<*> }) {
                    cont.removeOnCompletion(waiter) // make it properly cancellable
                    return@sc
                }
                // hm... there are already receivers (maybe), so try taking first
                takeFirstReceiveWaiter()?.apply {
                    resumeReceive(element)
                    cont.resume(Unit)
                    return@sc
                }
            }
        }
    }

    override fun offer(element: E): Boolean {
        takeFirstReceiveWaiter()?.apply {
            resumeReceive(element)
            return true
        }
        return false
    }

    private fun takeFirstReceiveWaiter() = waiters.removeFirstIfIsInstanceOf<ReceiveWaiter<E>>()

    // ------ ReceiveChannel ------

    override val isEmpty: Boolean get() = waiters.next() !is SendWaiter<*>

    suspend override fun receive(): E {
        // fast path if send is already waiting for rendezvous
        takeFirstSendWaiter()?.apply {
            resumeSend()
            return element
        }
        // slow-path does suspend
        return suspendCancellableCoroutine sc@ { cont ->
            val waiter = ReceiveWaiter(cont)
            while (true) {
                if (waiters.addLastIfPrev(waiter) { it !is SendWaiter<*> }) {
                    cont.removeOnCompletion(waiter) // make it properly cancellable
                    return@sc
                }
                // hm... there are already senders (maybe), so try taking first
                takeFirstSendWaiter()?.apply {
                    resumeSend()
                    cont.resume(element)
                    return@sc
                }
            }
        }
    }

    override fun pool(): E? {
        takeFirstSendWaiter()?.apply {
            resumeSend()
            return element
        }
        return null
    }

    private fun takeFirstSendWaiter() = waiters.removeFirstIfIsInstanceOf<SendWaiter<E>>()
}

private class SendWaiter<E>(
    val cont: CancellableContinuation<Unit>,
    val element: E
) : LockFreeLinkedListNode() {
    fun resumeSend() = cont.resume(Unit)
}

private class ReceiveWaiter<E>(val cont: CancellableContinuation<E>) : LockFreeLinkedListNode() {
    fun resumeReceive(element: E) = cont.resume(element)
}
