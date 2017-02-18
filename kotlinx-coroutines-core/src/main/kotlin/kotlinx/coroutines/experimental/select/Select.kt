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
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineOrReturn

public interface SelectBuilder<in R> : CoroutineScope {
    public fun <E> SelectableSend<E>.onSend(element: E, block: suspend () -> R)
    public fun <E> SelectableReceive<E>.onReceive(block: suspend (E) -> R)
}

public interface SelectableSend<in E> {
    public fun <R> registerSelectSend(select: SelectInstance<R>, element: E, block: suspend () -> R)
}

public interface SelectableReceive<out E> {
    public fun <R> registerSelectReceive(select: SelectInstance<R>, block: suspend (E) -> R)
}

public interface SelectInstance<in R> {
    /**
     * Returns `true` when this [select] statement had already picked a clause to execute.
     */
    public val isSelected: Boolean

    public fun trySelect(): Boolean

    public fun performAtomicTrySelect(desc: AtomicDesc): Any?

    public fun performAtomicIfNotSelected(desc: AtomicDesc): Any?

    public val completion: Continuation<R>

    /**
     * Registers handler that is **synchronously** invoked on completion of this select instance.
     */
    public fun invokeOnCompletion(handler: CompletionHandler): Registration
}

public inline suspend fun <R> select(crossinline builder: SelectBuilder<R>.() -> R): R =
    suspendCoroutineOrReturn { cont ->
        val scope = SelectBuilderImpl(cont, getParentJobOrAbort(cont))
        try {
            builder(scope)
        } catch (e: Throwable) {
            scope.handleBuilderException(e)
        }
        scope.initSelectResult()
    }

@PublishedApi
internal class SelectBuilderImpl<in R>(
    delegate: Continuation<R>,
    parentJob: Job?
) : CancellableContinuationImpl<R>(delegate, parentJob, active = false), SelectBuilder<R>, SelectInstance<R> {
    public override val completion: Continuation<R> get() = this

    public override fun trySelect(): Boolean = start()

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

    override fun <E> SelectableSend<E>.onSend(element: E, block: suspend () -> R) {
        registerSelectSend(this@SelectBuilderImpl, element, block)
    }

    override fun <E> SelectableReceive<E>.onReceive(block: suspend (E) -> R) {
        registerSelectReceive(this@SelectBuilderImpl, block)
    }

}
