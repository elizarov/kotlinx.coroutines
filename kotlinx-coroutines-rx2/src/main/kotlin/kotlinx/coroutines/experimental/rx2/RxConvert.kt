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

import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.launch
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.Subscription
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Converts this deferred value to the hot reactive single.
 *
 * Every subscriber gets the same completion value.
 * Unsubscribing from the resulting single **does not** affect the original deferred value in any way.
 *
 * @param context -- the coroutine context from which the resulting single is going to be signalled
 */
public fun <T> Deferred<T>.toSingle(context: CoroutineContext): Single<T> = Single.create { sub ->
    val job = launch(context) {
        val t =
            try { this@toSingle.await() }
            catch(e: Throwable) {
                sub.onError(e)
                return@launch
            }
        sub.onSuccess(t)
    }
    sub.add(JobSubscription(job))
}

/**
 * Converts produces stream of values to the hot reactive observable.
 *
 * Every subscriber receives values from this channel in **fan-out** fashion. If the are multiple subscribers,
 * they'll receive values in round-robin way.
 *
 * Note, that unsubscribing from the resulting observable cancels the `receive` operation on this channel.
 * It **does not** affect channels that are created by [produce][kotlinx.coroutines.experimental.channels.produce]
 * builder. However, if the channel was opened by [Observable.open][io.reactivex.Observable.open] extension function,
 * then cancellation of receive closes it.
 *
 * @param context -- the coroutine context from which the resulting observable is going to be signalled
 */
public fun <T> ReceiveChannel<T>.toObservable(context: CoroutineContext): Observable<T> = Observable.create { sub ->
    val job = launch(context) {
        try {
            for (t in this@toObservable)
                sub.onNext(t)
        } catch(e: Throwable) {
            sub.onError(e)
            return@launch
        }
        sub.onCompleted()
    }
    sub.add(JobSubscription(job))
}

private class JobSubscription(val job: Job) : Subscription {
    override fun isUnsubscribed(): Boolean = job.isCompleted
    override fun unsubscribe() { job.cancel() }
}
