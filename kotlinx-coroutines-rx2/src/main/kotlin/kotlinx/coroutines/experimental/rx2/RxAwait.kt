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

package kotlinx.coroutines.experimental.rx2

import io.reactivex.*
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.experimental.CancellableContinuation
import kotlinx.coroutines.experimental.CancellationException
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.suspendCancellableCoroutine

// ------------------------ CompletableSource ------------------------

/**
 * Awaits for completion of this completable without blocking a thread.
 * Returns `Unit` or throws the corresponding exception if this completable had produced error.
 *
 * This suspending function is cancellable. If the [Job] of the invoking coroutine is completed while this
 * suspending function is suspended, this function immediately resumes with [CancellationException].
 */
public suspend fun CompletableSource.await(): Unit = suspendCancellableCoroutine { cont ->
    subscribe(object : CompletableObserver {
        override fun onSubscribe(d: Disposable) { cont.disposeOnCompletion(d) }
        override fun onComplete() { cont.resume(Unit) }
        override fun onError(e: Throwable) { cont.tryResumeWithException(e) }
    })
}

// ------------------------ SingleSource ------------------------

/**
 * Awaits for completion of the single value without blocking a thread.
 * Returns the resulting value or throws the corresponding exception if this single had produced error.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is completed while this suspending function is waiting, this function
 * immediately resumes with [CancellationException].
 */
public suspend fun <T> SingleSource<T>.await(): T = suspendCancellableCoroutine { cont ->
    subscribe(object : SingleObserver<T> {
        override fun onSubscribe(d: Disposable) { cont.disposeOnCompletion(d) }
        override fun onSuccess(t: T) { cont.resume(t) }
        override fun onError(error: Throwable) { cont.resumeWithException(error) }
    })
}

// ------------------------ ObservableSource ------------------------

/**
 * Awaits for the first value from the given observable without blocking a thread.
 * Returns the resulting value or throws the corresponding exception if this observable had produced error.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is completed while this suspending function is waiting, this function
 * immediately resumes with [CancellationException].
 */
public suspend fun <T> Observable<T>.awaitFirst(): T = firstOrError().await()

/**
 * Awaits for the last value from the given observable without blocking a thread.
 * Returns the resulting value or throws the corresponding exception if this observable had produced error.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is completed while this suspending function is waiting, this function
 * immediately resumes with [CancellationException].
 */
public suspend fun <T> Observable<T>.awaitLast(): T = lastOrError().await()

/**
 * Awaits for the single value from the given observable without blocking a thread.
 * Returns the resulting value or throws the corresponding exception if this observable had produced error.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is completed while this suspending function is waiting, this function
 * immediately resumes with [CancellationException].
 */
public suspend fun <T> Observable<T>.awaitSingle(): T = singleOrError().await()

// ------------------------ Publisher ------------------------

/**
 * Awaits for the first value from the given flowable without blocking a thread.
 * Returns the resulting value or throws the corresponding exception if this flowable had produced error.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is completed while this suspending function is waiting, this function
 * immediately resumes with [CancellationException].
 */
public suspend fun <T> Flowable<T>.awaitFirst(): T = firstOrError().await()

/**
 * Awaits for the last value from the given flowable without blocking a thread.
 * Returns the resulting value or throws the corresponding exception if this flowable had produced error.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is completed while this suspending function is waiting, this function
 * immediately resumes with [CancellationException].
 */
public suspend fun <T> Flowable<T>.awaitLast(): T = lastOrError().await()

/**
 * Awaits for the single value from the given flowable without blocking a thread.
 * Returns the resulting value or throws the corresponding exception if this flowable had produced error.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is completed while this suspending function is waiting, this function
 * immediately resumes with [CancellationException].
 */
public suspend fun <T> Flowable<T>.awaitSingle(): T = singleOrError().await()

// ------------------------ private ------------------------

private fun CancellableContinuation<*>.disposeOnCompletion(d: Disposable) {
    invokeOnCompletion { d.dispose() }
}
