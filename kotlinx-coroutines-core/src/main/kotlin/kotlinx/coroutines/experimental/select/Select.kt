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
import kotlinx.coroutines.experimental.internal.Symbol
import kotlinx.coroutines.experimental.intrinsics.startUndispatchedCoroutine
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineOrReturn

public interface SelectBuilder<in R> : CoroutineScope {
    public fun <E> SelectableSend<E>.onSend(element: E, block: suspend () -> R)
    public fun <E> SelectableReceive<E>.onReceive(block: suspend (E) -> R)
    public fun default(block: suspend () -> R)
}

public interface SelectableSend<in E> {
    public fun <R> createSendSelector(element: E, block: suspend () -> R): Selector<R>
}

public interface SelectableReceive<out E> {
    public fun <R> createReceiveSelector(block: suspend (E) -> R): Selector<R>
}

public interface Selector<out R> {
    // is invoked on an instance that is not selected yet, returns `true` if selected to abort further action
    public fun trySelectFastPath(select: SelectInstance<R>): Boolean

    // can be invoked on already selected instance, returns `true` if selected to abort further action
    public fun registerSelector(select: SelectInstance<R>): Boolean
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

public val ALREADY_SELECTED: Any = Symbol("ALREADY_SELECTED")

public inline suspend fun <R> select(crossinline builder: SelectBuilder<R>.() -> R): R =
    selectInternal(true, builder)

// used for debugging to check non-fast-path alternative
@PublishedApi
internal inline suspend fun <R> selectInternal(fastPath: Boolean, crossinline builder: SelectBuilder<R>.() -> R): R =
    suspendCoroutineOrReturn { cont ->
        val scope = SelectBuilderImpl(fastPath, cont, getParentJobOrAbort(cont))
        try {
            builder(scope)
        } catch (e: Throwable) {
            scope.handleBuilderException(e)
        }
        scope.registerSelectors()
    }

@PublishedApi
internal class SelectBuilderImpl<in R>(
    val fastPath: Boolean,
    delegate: Continuation<R>,
    parentJob: Job?
) : CancellableContinuationImpl<R>(delegate, parentJob, active = false), SelectBuilder<R>, SelectInstance<R> {
    private val selectors = arrayListOf<Selector<R>>()

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

    private fun trySelect(selector: Selector<R>) {
        if (fastPath) {
            if (selector.trySelectFastPath(this@SelectBuilderImpl)) return
        } else {
            if (selector.registerSelector(this@SelectBuilderImpl)) return
        }
        selectors += selector
    }

    @PublishedApi
    internal fun registerSelectors(): Any? {
        if (!isSelected) {
            initCancellability()
            if (fastPath) { // we just tried with fast path -- need to select now
                for (selector in selectors) {
                    try {
                        if (selector.registerSelector(this))
                            break
                    } catch (e: Throwable) {
                        handleBuilderException(e)
                    }
                }
            }
        }
        return getResult()
    }

    override fun <E> SelectableSend<E>.onSend(element: E, block: suspend () -> R) {
        if (isSelected) return
        trySelect(createSendSelector(element, block))
    }

    override fun <E> SelectableReceive<E>.onReceive(block: suspend (E) -> R) {
        if (isSelected) return
        trySelect(createReceiveSelector(block))
    }

    override fun default(block: suspend () -> R) {
        if (!trySelect()) return
        block.startUndispatchedCoroutine(completion)
    }
}
