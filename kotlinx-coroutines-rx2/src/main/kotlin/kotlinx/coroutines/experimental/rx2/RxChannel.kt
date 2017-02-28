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

import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.RendezvousChannel
import io.reactivex.Observable
import io.reactivex.ObservableSource
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import org.reactivestreams.Subscription

/**
 * Return type for [Observable.open] that can be used to [receive] elements from the
 * subscription and to manually [cancel] it.
 */
public interface SubscriptionReceiveChannel<out T> : ReceiveChannel<T>, Subscription

/**
 * Subscribes to this [Observable] and returns a channel to receive elements emitted by it.
 *
 * Note, that [Observable] contract does not conceptually support cancellation of an interest
 * to receive an element. Once the element was requested, there is no way to unrequest it.
 * So, albeit that resulting channel returned by this function supports cancellation of
 * receive invocations, a cancelled receive forces the whole subscription channel to be closed.
 */
public fun <T> ObservableSource<T>.open(): SubscriptionReceiveChannel<T> {
    val channel = SubscriptionChannel<T>()
    val subscription = subscribe(channel.subscriber)
    channel.subscription = subscription
    if (channel.isClosedForSend) subscription.unsubscribe()
    return channel
}

/**
 * Subscribes to this [Observable] and returns an iterator to receive elements emitted by it.
 *
 * This is a shortcut for `open().iterator()`. See [open] if you need an ability to manually
 * unsubscribe from the observable.
 */
public operator fun <T> Observable<T>.iterator() = open().iterator()

private class SubscriptionChannel<T> : RendezvousChannel<T>(), SubscriptionReceiveChannel<T> {
    val subscriber: ChannelSubscriber = ChannelSubscriber()

    @Volatile
    var subscription: Subscription? = null

    // AbstractChannel overrides
    override fun onEnqueuedReceive() = subscriber.requestOne()
    override fun onCancelledReceive() = unsubscribe()
    override fun afterClose(cause: Throwable?) { subscription?.unsubscribe() }

    // Subscription overrides
    override fun cancel() { close() }

    inner class ChannelSubscriber: Observer<T>() {
        fun requestOne() {
            request(1)
        }

        override fun onSubscribe(d: Disposable) {
            // todo:
        }

        override fun onNext(t: T) {
            check(offer(t)) { "Unrequested onNext invocation with $t" }
        }

        override fun onComplete() {
            check(close()) { "onComplete on a closed channel"}
        }

        override fun onError(e: Throwable?) {
            check(close(e)) { "onError on a closed channel with $e"}
        }
    }
}

