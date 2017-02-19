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

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.Job.Registration
import kotlinx.coroutines.experimental.internal.AtomicDesc
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineOrReturn

public interface SelectBuilder<in R> : CoroutineScope {
    public fun <E> SelectableSend<E>.onSend(element: E, block: suspend () -> R)
    public fun <E> SelectableReceive<E>.onReceive(block: suspend (E) -> R)
}

public interface SelectableSend<in E> {
    /**
     * @suppress **This is unstable API and it is subject to change.**
     */
    public fun <R> registerSelectSend(select: SelectInstance<R>, element: E, block: suspend () -> R)
}

public interface SelectableReceive<out E> {
    /**
     * @suppress **This is unstable API and it is subject to change.**
     */
    public fun <R> registerSelectReceive(select: SelectInstance<R>, block: suspend (E) -> R)
}

/**
 * @suppress **This is unstable API and it is subject to change.**
 */
public interface SelectInstance<in R> : Continuation<R> {
    /**
     * Returns `true` when this [select] statement had already picked a clause to execute.
     */
    public val isSelected: Boolean

    public fun trySelect(idempotent: Any?): Boolean

    public fun performAtomicTrySelect(desc: AtomicDesc): Any?

    public fun performAtomicIfNotSelected(desc: AtomicDesc): Any?

    /**
     * Registers handler that is **synchronously** invoked on completion of this select instance.
     */
    public fun invokeOnCompletion(handler: CompletionHandler): Registration
}

public inline suspend fun <R> select(crossinline builder: SelectBuilder<R>.() -> Unit): R =
    suspendCoroutineOrReturn { cont ->
        val scope = SelectBuilderImpl(cont)
        try {
            builder(scope)
        } catch (e: Throwable) {
            scope.handleBuilderException(e)
        }
        scope.initSelectResult()
    }

@PublishedApi
internal class SelectBuilderImpl<in R>(
    delegate: Continuation<R>
) : CancellableContinuationImpl<R>(delegate, active = false), SelectBuilder<R>, SelectInstance<R> {
    @PublishedApi
    internal fun handleBuilderException(e: Throwable) {
        val token = tryResumeWithException(e)
        if (token != null)
            completeResume(token)
        else
            handleCoroutineException(context, e)
    }

    @PublishedApi
    internal fun initSelectResult(): Any? {
        if (!isSelected) initCancellability()
        return getResult()
    }

    // coroutines that are started inside this select are directly subordinate to the parent job
    override val context: CoroutineContext
        get() = delegate.context

    override fun onParentCompletion(cause: Throwable?) {
        /*
           Select is cancelled only when no clause was selected yet. If a clause was selected, then
           it is the concern of the coroutine that was started by that clause to cancel on its suspension
           points.
         */
        if (trySelect(null))
            cancel(cause)
    }

    override fun <E> SelectableSend<E>.onSend(element: E, block: suspend () -> R) {
        registerSelectSend(this@SelectBuilderImpl, element, block)
    }

    override fun <E> SelectableReceive<E>.onReceive(block: suspend (E) -> R) {
        registerSelectReceive(this@SelectBuilderImpl, block)
    }
}
