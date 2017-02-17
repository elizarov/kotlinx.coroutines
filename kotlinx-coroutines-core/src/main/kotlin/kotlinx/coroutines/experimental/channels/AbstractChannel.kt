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

import kotlinx.coroutines.experimental.CancellableContinuation
import kotlinx.coroutines.experimental.internal.*
import kotlinx.coroutines.experimental.intrinsics.startUndispatchedCoroutine
import kotlinx.coroutines.experimental.removeOnCancel
import kotlinx.coroutines.experimental.select.ALREADY_SELECTED
import kotlinx.coroutines.experimental.select.SelectBuilder
import kotlinx.coroutines.experimental.select.SelectInstance
import kotlinx.coroutines.experimental.select.Selector
import kotlinx.coroutines.experimental.suspendCancellableCoroutine
import kotlin.coroutines.experimental.startCoroutine

/**
 * Abstract channel. It is a base class for buffered and unbuffered channels.
 */
public abstract class AbstractChannel<E> : Channel<E> {
    private val queue = LockFreeLinkedListHead()

    // ------ extension points for buffered channels ------

    /**
     * Returns `true` if this channel has buffer.
     */
    protected abstract val hasBuffer: Boolean

    /**
     * Returns `true` if this channel's buffer is empty.
     */
    protected abstract val isBufferEmpty: Boolean

    /**
     * Returns `true` if this channel's buffer is full.
     */
    protected abstract val isBufferFull: Boolean

    /**
     * Tries to add element to buffer or to queued receiver.
     * Return type is `OFFER_SUCCESS | OFFER_FAILED | Closed`.
     */
    protected abstract fun offerInternal(element: E): Any

    /**
     * Tries to add element to buffer or to queued receiver if select statement clause was not selected yet.
     * Return type is `ALREADY_SELECTED | OFFER_SUCCESS | OFFER_FAILED | Closed`.
     */
    protected abstract fun offerSelectInternal(element: E, select: SelectInstance<*>): Any

    /**
     * Tries to remove element from buffer or from queued sender.
     * Return type is `E | POLL_FAILED | Closed`
     */
    protected abstract fun pollInternal(): Any?

    /**
     * Tries to remove element from buffer or from queued sender if select statement clause was not selected yet.
     * Return type is `ALREADY_SELECTED | E | POLL_FAILED | Closed`
     */
    protected abstract fun pollSelectInternal(select: SelectInstance<*>): Any?

    // ------ state functions for concrete implementations ------

    /**
     * Returns non-null closed token if it is first in the queue.
     */
    protected val closedForReceive: Any? get() = queue.next as? Closed<*>

    /**
     * Returns non-null closed token if it is last in the queue.
     */
    protected val closedForSend: ReceiveOrClosed<*>? get() = queue.prev as? Closed<*>

    // ------ SendChannel ------

    public final override val isClosedForSend: Boolean get() = closedForSend != null
    public final override val isFull: Boolean get() = queue.next !is ReceiveOrClosed<*> && isBufferFull

    public final override suspend fun send(element: E) {
        // fast path -- try offer non-blocking
        if (offer(element)) return
        // slow-path does suspend
        return sendSuspend(element)
    }

    public final override fun offer(element: E): Boolean {
        val result = offerInternal(element)
        return when {
            result === OFFER_SUCCESS -> true
            result === OFFER_FAILED -> false
            result is Closed<*> -> throw result.sendException
            else -> error("offerInternal returned $result")
        }
    }

    private suspend fun sendSuspend(element: E): Unit = suspendCancellableCoroutine(true) sc@ { cont ->
        val send = SendElement(cont, element)
        loop@ while (true) {
            if (enqueueSend(send)) {
                cont.initCancellability() // make it properly cancellable
                cont.removeOnCancel(send)
                return@sc
            }
            // hm... something is not right. try to offer
            val result = offerInternal(element)
            when {
                result === OFFER_SUCCESS -> {
                    cont.resume(Unit)
                    return@sc
                }
                result === OFFER_FAILED -> continue@loop
                result is Closed<*> -> {
                    cont.resumeWithException(result.sendException)
                    return@sc
                }
                else -> error("offerInternal returned $result")
            }
        }
    }

    private fun enqueueSend(send: SendElement) =
        if (hasBuffer)
            queue.addLastIfPrevAndIf(send, { it !is ReceiveOrClosed<*> }, { isBufferFull })
        else
            queue.addLastIfPrev(send, { it !is ReceiveOrClosed<*> })

    public final override fun close(cause: Throwable?): Boolean {
        val closed = Closed<E>(cause)
        while (true) {
            val receive = takeFirstReceiveOrPeekClosed()
            if (receive == null) {
                // queue empty or has only senders -- try add last "Closed" item to the queue
                if (queue.addLastIfPrev(closed, { it !is ReceiveOrClosed<*> })) {
                    afterClose(cause)
                    return true
                }
                continue // retry on failure
            }
            if (receive is Closed<*>) return false // already marked as closed -- nothing to do
            receive as Receive<E> // type assertion
            receive.resumeReceiveClosed(closed)
        }
    }

    /**
     * Invoked after successful [close].
     */
    protected open fun afterClose(cause: Throwable?) {}

    /**
     * Retrieves first receiving waiter from the queue or returns closed token.
     */
    protected fun takeFirstReceiveOrPeekClosed(): ReceiveOrClosed<E>? =
        queue.removeFirstIfIsInstanceOfOrPeekIf<ReceiveOrClosed<E>>({ it is Closed<*> })

    // ------ createSendSelector ------

    protected fun describeTryOffer(element: E): TryOfferDesc<E> = TryOfferDesc(element, queue)

    protected class TryOfferDesc<E>(
        val element: E,
        queue: LockFreeLinkedListHead
    ) : RemoveFirstDesc<ReceiveOrClosed<E>>(queue) {
        lateinit var resumeToken: Any

        override fun failure(affected: LockFreeLinkedListNode, next: Any): Any? {
            if (affected !is ReceiveOrClosed<*>) return OFFER_FAILED
            if (affected is Closed<*>) return affected
            return null
        }

        override fun validatePrepared(node: ReceiveOrClosed<E>): Boolean {
            val token = node.tryResumeReceive(element) ?: return false
            resumeToken = token
            return true
        }
    }

    private inner class TryEnqueueSendDesc<E, R>(
        element: E,
        select: SelectInstance<R>,
        block: suspend () -> R
    ) : AddLastDesc(queue, SendSelect(element, select, block)) {
        override fun failure(affected: LockFreeLinkedListNode, next: Any): Any? {
            if (affected is ReceiveOrClosed<*>) {
                return affected as? Closed<*> ?: ENQUEUE_FAILED
            }
            return null
        }

        override fun onPrepare(affected: LockFreeLinkedListNode, next: LockFreeLinkedListNode): Any? {
            if (!isBufferFull) return ENQUEUE_FAILED
            return super.onPrepare(affected, next)
        }

        override fun finishOnSuccess(affected: LockFreeLinkedListNode, next: LockFreeLinkedListNode) {
            super.finishOnSuccess(affected, next)
            // we can actually remove on select start, but this is also Ok (it'll get removed if discovered there)
            (node as SendSelect<*>).removeOnSelectCompletion()
        }
    }

    // Result is ALREADY_SELECTED | ENQUEUE_SUCCESS | ENQUEUE_FAILED | Closed
    fun <R> enqueueSelectSend(element: E, select: SelectInstance<R>, block: suspend () -> R): Any {
        val enqueueOp = TryEnqueueSendDesc(element, select, block)
        return select.performAtomicIfNotSelected(enqueueOp) ?: ENQUEUE_SUCCESS
    }

    private fun <R> offerSelect(element: E, select: SelectInstance<R>, block: suspend () -> R): Boolean {
        val offerResult = offerSelectInternal(element, select)
        when {
            offerResult === ALREADY_SELECTED -> return true
            offerResult === OFFER_FAILED -> return false
            offerResult === OFFER_SUCCESS -> {
                block.startUndispatchedCoroutine(select.completion)
                return true
            }
            offerResult is Closed<*> -> throw offerResult.sendException
            else -> error("offerSelectInternal returned $offerResult")
        }
    }

    override fun <R> createSendSelector(element: E, block: suspend () -> R): Selector<R> = object : Selector<R> {
        override fun trySelectFastPath(select: SelectInstance<R>): Boolean {
            if (!offer(element)) return false
            block.startUndispatchedCoroutine(select.completion)
            return true
        }

        override fun registerSelector(select: SelectInstance<R>): Boolean {
            while (true) {
                val enqueueResult = enqueueSelectSend(element, select, block)
                when {
                    enqueueResult === ALREADY_SELECTED -> return true
                    enqueueResult === ENQUEUE_SUCCESS -> return false
                    enqueueResult === ENQUEUE_FAILED -> {} // retry
                    enqueueResult is Closed<*> -> throw enqueueResult.sendException
                    else -> error("enqueueSelectSend returned $enqueueResult")
                }
                if (offerSelect(element, select, block)) return true
            }
        }
    }

    // ------ ReceiveChannel ------

    public final override val isClosedForReceive: Boolean get() = closedForReceive != null && isBufferEmpty
    public final override val isEmpty: Boolean get() = queue.next !is Send && isBufferEmpty

    @Suppress("UNCHECKED_CAST")
    public final override suspend fun receive(): E {
        // fast path -- try poll non-blocking
        val result = pollInternal()
        if (result !== POLL_FAILED) return receiveResult(result)
        // slow-path does suspend
        return receiveSuspend()
    }

    @Suppress("UNCHECKED_CAST")
    private fun receiveResult(result: Any?): E {
        if (result is Closed<*>) throw result.receiveException
        return result as E
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun receiveSuspend(): E = suspendCancellableCoroutine(true) sc@ { cont ->
        val receive = ReceiveNonNull(cont)
        while (true) {
            if (enqueueReceive(receive)) {
                cont.initCancellability() // make it properly cancellable
                removeReceiveOnCancel(cont, receive)
                return@sc
            }
            // hm... something is not right. try to poll
            val result = pollInternal()
            if (result is Closed<*>) {
                cont.resumeWithException(result.receiveException)
                return@sc
            }
            if (result !== POLL_FAILED) {
                cont.resume(result as E)
                return@sc
            }
        }
    }

    private fun enqueueReceive(receive: Receive<E>): Boolean {
        val result = if (hasBuffer)
            queue.addLastIfPrevAndIf(receive, { it !is Send }, { isBufferEmpty }) else
            queue.addLastIfPrev(receive, { it !is Send })
        if (result) onEnqueuedReceive()
        return result
    }

    override fun <R> SelectBuilder<R>.onReceive(block: suspend (E) -> R) {
        TODO("not implemented")
    }

    @Suppress("UNCHECKED_CAST")
    public final override suspend fun receiveOrNull(): E? {
        // fast path -- try poll non-blocking
        val result = pollInternal()
        if (result !== POLL_FAILED) return receiveOrNullResult(result)
        // slow-path does suspend
        return receiveOrNullSuspend()
    }

    @Suppress("UNCHECKED_CAST")
    private fun receiveOrNullResult(result: Any?): E? {
        if (result is Closed<*>) {
            if (result.closeCause != null) throw result.receiveException
            return null
        }
        return result as E
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun receiveOrNullSuspend(): E? = suspendCancellableCoroutine(true) sc@ { cont ->
        val receive = ReceiveOrNull(cont)
        while (true) {
            if (enqueueReceive(receive)) {
                cont.initCancellability() // make it properly cancellable
                removeReceiveOnCancel(cont, receive)
                return@sc
            }
            // hm... something is not right. try to poll
            val result = pollInternal()
            if (result is Closed<*>) {
                if (result.closeCause == null)
                    cont.resume(null)
                else
                    cont.resumeWithException(result.receiveException)
                return@sc
            }
            if (result !== POLL_FAILED) {
                cont.resume(result as E)
                return@sc
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    public final override fun poll(): E? {
        val result = pollInternal()
        return if (result === POLL_FAILED) null else receiveOrNullResult(result)
    }

    public final override fun iterator(): ChannelIterator<E> = Iterator(this)

    /**
     * Retrieves first sending waiter from the queue or returns closed token.
     */
    protected fun takeFirstSendOrPeekClosed(): Send? =
        queue.removeFirstIfIsInstanceOfOrPeekIf<Send> { it is Closed<*> }

    // ------ createReceiveSelector ------

    protected fun describeTryPoll(): TryPollDesc<E> = TryPollDesc(queue)

    protected class TryPollDesc<E>(
            queue: LockFreeLinkedListHead
    ) : RemoveFirstDesc<Send>(queue) {
        lateinit var resumeToken: Any
        var pollResult: E? = null

        override fun failure(affected: LockFreeLinkedListNode, next: Any): Any? {
            if (affected is Closed<*>) return affected
            if (affected !is Send) return POLL_FAILED
            return null
        }

        @Suppress("UNCHECKED_CAST")
        override fun validatePrepared(node: Send): Boolean {
            val token = node.tryResumeSend() ?: return false
            resumeToken = token
            pollResult = node.pollResult as E
            return true
        }
    }

    private inner class TryEnqueueReceiveDesc<E, R>(
            select: SelectInstance<R>,
            block: suspend (E) -> R
    ) : AddLastDesc(queue, ReceiveSelect(select, block)) {
        override fun failure(affected: LockFreeLinkedListNode, next: Any): Any? {
            if (affected is Send) return ENQUEUE_FAILED
            return null
        }

        override fun onPrepare(affected: LockFreeLinkedListNode, next: LockFreeLinkedListNode): Any? {
            if (!isBufferEmpty) return ENQUEUE_FAILED
            return super.onPrepare(affected, next)
        }

        override fun finishOnSuccess(affected: LockFreeLinkedListNode, next: LockFreeLinkedListNode) {
            super.finishOnSuccess(affected, next)
            // we can actually remove on select start, but this is also Ok (it'll get removed if discovered there)
            (node as ReceiveSelect<*, *>).removeOnSelectCompletion()
        }
    }

    // Result is ALREADY_SELECTED | ENQUEUE_SUCCESS | ENQUEUE_FAILED
    fun <R> enqueueSelectReceive(select: SelectInstance<R>, block: suspend (E) -> R): Any {
        val enqueueOp = TryEnqueueReceiveDesc(select, block)
        return select.performAtomicIfNotSelected(enqueueOp) ?: ENQUEUE_SUCCESS
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R> pollSelect(select: SelectInstance<R>, block: suspend (E) -> R): Boolean {
        val pollResult = pollSelectInternal(select)
        when {
            pollResult === ALREADY_SELECTED -> return true
            pollResult === POLL_FAILED -> return false
            pollResult is Closed<*> -> throw pollResult.receiveException
            else -> {
                block.startUndispatchedCoroutine(pollResult as E, select.completion)
                return true
            }
        }
    }

    override fun <R> createReceiveSelector(block: suspend (E) -> R): Selector<R> = object : Selector<R> {
        override fun trySelectFastPath(select: SelectInstance<R>): Boolean {
            val result = pollInternal()
            if (result === POLL_FAILED) return false
            block.startUndispatchedCoroutine(receiveResult(result), select.completion)
            return true
        }

        override fun registerSelector(select: SelectInstance<R>): Boolean {
            while (true) {
                val enqueueResult = enqueueSelectReceive(select, block)
                when {
                    enqueueResult === ALREADY_SELECTED -> return true
                    enqueueResult === ENQUEUE_SUCCESS -> return false
                    enqueueResult === ENQUEUE_FAILED -> {} // retry
                    else -> error("enqueueSelectReceive returned $enqueueResult")
                }
                if (pollSelect(select, block)) return true
            }
        }
    }

    // ------ protected ------

    protected companion object {
        private const val DEFAULT_CLOSE_MESSAGE = "Channel was closed"

        @JvmStatic
        val OFFER_SUCCESS: Any = Symbol("OFFER_SUCCESS")
        @JvmStatic
        val OFFER_FAILED: Any = Symbol("OFFER_FAILED")

        @JvmStatic
        val POLL_FAILED: Any = Symbol("POLL_FAILED")

        @JvmStatic
        val ENQUEUE_SUCCESS: Any = Symbol("ENQUEUE_SUCCESS")
        @JvmStatic
        val ENQUEUE_FAILED: Any = Symbol("ENQUEUE_FAILED")

        @JvmStatic
        private val SELECT_STARTED: Any = Symbol("SELECT_STARTED")
        @JvmStatic
        private val NULL_VALUE: Any = Symbol("NULL_VALUE")

        @JvmStatic
        fun isClosed(result: Any?): Boolean = result is Closed<*>
    }

    /**
     * Invoked when receiver is successfully enqueued to the queue of waiting receivers.
     */
    protected open fun onEnqueuedReceive() {}

    /**
     * Invoked when enqueued receiver was successfully cancelled.
     */
    protected open fun onCancelledReceive() {}

    // ------ private ------

    private fun removeReceiveOnCancel(cont: CancellableContinuation<*>, receive: Receive<*>) {
        cont.invokeOnCompletion {
            if (cont.isCancelled && receive.remove())
                onCancelledReceive()
        }
    }

    private class Iterator<E>(val channel: AbstractChannel<E>) : ChannelIterator<E> {
        var result: Any? = POLL_FAILED // E | POLL_FAILED | Closed

        suspend override fun hasNext(): Boolean {
            // check for repeated hasNext
            if (result !== POLL_FAILED) return hasNextResult(result)
            // fast path -- try poll non-blocking
            result = channel.pollInternal()
            if (result !== POLL_FAILED) return hasNextResult(result)
            // slow-path does suspend
            return hasNextSuspend()
        }

        private fun hasNextResult(result: Any?): Boolean {
            if (result is Closed<*>) {
                if (result.closeCause != null) throw result.receiveException
                return false
            }
            return true
        }

        private suspend fun hasNextSuspend(): Boolean = suspendCancellableCoroutine(true) sc@ { cont ->
            val receive = ReceiveHasNext(this, cont)
            while (true) {
                if (channel.enqueueReceive(receive)) {
                    cont.initCancellability() // make it properly cancellable
                    channel.removeReceiveOnCancel(cont, receive)
                    return@sc
                }
                // hm... something is not right. try to poll
                val result = channel.pollInternal()
                this.result = result
                if (result is Closed<*>) {
                    if (result.closeCause == null)
                        cont.resume(false)
                    else
                        cont.resumeWithException(result.receiveException)
                    return@sc
                }
                if (result !== POLL_FAILED) {
                    cont.resume(true)
                    return@sc
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        suspend override fun next(): E {
            val result = this.result
            if (result is Closed<*>) throw result.receiveException
            if (result !== POLL_FAILED) {
                this.result = POLL_FAILED
                return result as E
            }
            // rare case when hasNext was not invoked yet -- just delegate to receive (leave state as is)
            return channel.receive()
        }
    }

    /**
     * Represents sending waiter in the queue.
     */
    protected interface Send {
        val pollResult: Any? // E | Closed
        fun tryResumeSend(): Any?
        fun completeResumeSend(token: Any)
    }

    /**
     * Represents receiver waiter in the queue or closed token.
     */
    protected interface ReceiveOrClosed<in E> {
        val offerResult: Any // OFFER_SUCCESS | Closed
        fun tryResumeReceive(value: E): Any?
        fun completeResumeReceive(token: Any)
    }

    @Suppress("UNCHECKED_CAST")
    private class SendElement(
            val cont: CancellableContinuation<Unit>,
            override val pollResult: Any?
    ) : LockFreeLinkedListNode(), Send {
        override fun tryResumeSend(): Any? = cont.tryResume(Unit)
        override fun completeResumeSend(token: Any) = cont.completeResume(token)
    }

    private class SendSelect<R>(
        override val pollResult: Any?,
        val select: SelectInstance<R>,
        val block: suspend () -> R
    ) : LockFreeLinkedListNode(), Send {
        override fun tryResumeSend(): Any? = if (select.trySelect()) SELECT_STARTED else null
        override fun completeResumeSend(token: Any) {
            check(token === SELECT_STARTED)
            block.startCoroutine(select.completion)
        }

        fun removeOnSelectCompletion() {
            select.invokeOnCompletion { remove() }
        }
    }

    /**
     * Represents closed channel.
     */
    protected class Closed<in E>(
        val closeCause: Throwable?
    ) : LockFreeLinkedListNode(), Send, ReceiveOrClosed<E> {
        @Volatile
        var _sendException: Throwable? = null

        val sendException: Throwable get() = _sendException ?:
            (closeCause ?: ClosedSendChannelException(DEFAULT_CLOSE_MESSAGE))
                    .also { _sendException = it }

        @Volatile
        var _receiveException: Throwable? = null

        val receiveException: Throwable get() = _receiveException ?:
            (closeCause ?: ClosedReceiveChannelException(DEFAULT_CLOSE_MESSAGE))
                    .also { _receiveException = it }

        override val offerResult get() = this
        override val pollResult get() = this
        override fun tryResumeSend(): Boolean = true
        override fun completeResumeSend(token: Any) {}
        override fun tryResumeReceive(value: E): Any? = throw sendException
        override fun completeResumeReceive(token: Any) = throw sendException
    }

    private abstract class Receive<in E> : LockFreeLinkedListNode(), ReceiveOrClosed<E> {
        override val offerResult get() = OFFER_SUCCESS
        abstract fun resumeReceiveClosed(closed: Closed<*>)
    }

    private class ReceiveNonNull<in E>(val cont: CancellableContinuation<E>) : Receive<E>() {
        override fun tryResumeReceive(value: E): Any? = cont.tryResume(value)
        override fun completeResumeReceive(token: Any) = cont.completeResume(token)
        override fun resumeReceiveClosed(closed: Closed<*>) = cont.resumeWithException(closed.receiveException)
    }

    private class ReceiveOrNull<in E>(val cont: CancellableContinuation<E?>) : Receive<E>() {
        override fun tryResumeReceive(value: E): Any? = cont.tryResume(value)
        override fun completeResumeReceive(token: Any) = cont.completeResume(token)
        override fun resumeReceiveClosed(closed: Closed<*>) {
            if (closed.closeCause == null)
                cont.resume(null)
            else
                cont.resumeWithException(closed.receiveException)
        }
    }

    private class ReceiveHasNext<E>(
        val iterator: Iterator<E>,
        val cont: CancellableContinuation<Boolean>
    ) : Receive<E>() {
        override fun tryResumeReceive(value: E): Any? {
            val token = cont.tryResume(true)
            if (token != null) iterator.result = value
            return token
        }

        override fun completeResumeReceive(token: Any) = cont.completeResume(token)

        override fun resumeReceiveClosed(closed: Closed<*>) {
            val token = if (closed.closeCause == null)
                cont.tryResume(false)
            else
                cont.tryResumeWithException(closed.receiveException)
            if (token != null) {
                iterator.result = closed
                cont.completeResume(token)
            }
        }
    }

    private class ReceiveSelect<R, in E>(
        val select: SelectInstance<R>,
        val block: suspend (E) -> R
    ) : Receive<E>() {
        override fun tryResumeReceive(value: E): Any?  =
            if (select.trySelect()) (value ?: NULL_VALUE) else null
        @Suppress("UNCHECKED_CAST")
        override fun completeResumeReceive(token: Any) {
            val value: E = (if (token === NULL_VALUE) null else token) as E
            block.startCoroutine(value, select.completion)
        }
        override fun resumeReceiveClosed(closed: Closed<*>) {
            select.completion.resumeWithException(closed.receiveException)
        }

        fun removeOnSelectCompletion() {
            select.invokeOnCompletion { remove() }
        }
    }
}
