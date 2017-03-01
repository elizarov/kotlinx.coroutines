# Module kotlinx-coroutines-rx1

Utilities for [RxJava 1.x](https://github.com/ReactiveX/RxJava/tree/1.x).

Coroutine builders:

| **Name**        | **Result**                              | **Scope**        | **Description**
| --------------- | --------------------------------------- | ---------------- | ---------------
| [rxCompletable] | [Completable][io.reactivex.Completable] | [CoroutineScope] | Cold completable that starts coroutine on subscribe
| [rxSingle]      | [Single][io.reactivex.Single]           | [CoroutineScope] | Cold single that starts coroutine on subscribe
| [rxObservable]  | [Observable][io.reactivex.Observable]   | [ProducerScope]  | Cold observable that starts coroutine on subscribe

Suspending extension functions and suspending iteration:

| **Name** | **Description**
| -------- | ---------------
| [CompletableSource.await][rx.CompletableSource.await] | Awaits for completion of the completable value 
| [SingleSource.await][io.reactivex.SingleSource.await] | Awaits for completion of the single value and returns it 
| [ObservableSource.awaitFirst][io.reactivex.ObservableSource.awaitFirst] | Awaits for the first value from the given observable
| [ObservableSource.awaitLast][io.reactivex.ObservableSource.awaitFirst] | Awaits for the last value from the given observable
| [ObservableSource.awaitSingle][io.reactivex.ObservableSource.awaitSingle] | Awaits for the single value from the given observable
| [ObservableSource.open][io.reactivex.Observable.open] | Subscribes to observable and returns [ReceiveChannel] 
| [ObservableSource.iterator][io.reactivex.Observable.iterator] | Subscribes to observable and returns [ChannelIterator]

Note, that [Flowable][io.reactivex.Flowable] is a subclass of [Reactive Streams](http://www.reactive-streams.org)
[Publisher][org.reactivestreams.Publisher] and extensions for it are covered by
[kotlinx-coroutines-reactive](../kotlinx-coroutines-reactive) module.

Conversion functions:

| **Name** | **Description**
| -------- | ---------------
| [Job.toCompletable][kotlinx.coroutines.experimental.Job.toCompletable] | Converts job to hot completable
| [Deferred.toSingle][kotlinx.coroutines.experimental.Deferred.toSingle] | Converts deferred value to hot single
| [ReceiveChannel.toObservable][kotlinx.coroutines.experimental.channels.ReceiveChannel.toObservable] | Converts streaming channel to hot observable

<!--- SITE_ROOT https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core -->
<!--- DOCS_ROOT kotlinx-coroutines-core/target/dokka/kotlinx-coroutines-core -->
<!--- INDEX kotlinx.coroutines.experimental -->
[CoroutineScope]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental/-coroutine-scope/index.html
<!--- INDEX kotlinx.coroutines.experimental.channels -->
[ChannelIterator]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-channel-iterator/index.html
[ProducerScope]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-producer-scope/index.html
[ReceiveChannel]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.experimental.channels/-receive-channel/index.html
<!--- SITE_ROOT https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx2 -->
<!--- DOCS_ROOT kotlinx-coroutines-rx2/target/dokka/kotlinx-coroutines-rx2 -->
<!--- INDEX kotlinx.coroutines.experimental.rx2 -->
[kotlinx.coroutines.experimental.Deferred.toSingle]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/kotlinx.coroutines.experimental.-deferred/to-single.html
[kotlinx.coroutines.experimental.channels.ReceiveChannel.toObservable]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/kotlinx.coroutines.experimental.channels.-receive-channel/to-observable.html
[io.reactivex.Observable]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-observable/index.html
[io.reactivex.Observable.awaitFirst]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-observable/await-first.html
[io.reactivex.Observable.awaitSingle]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-observable/await-single.html
[io.reactivex.Observable.iterator]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-observable/iterator.html
[io.reactivex.Observable.open]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-observable/open.html
[io.reactivex.Single]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-single/index.html
[io.reactivex.Single.await]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/io.reactivex.-single/await.html
[rxObservable]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx-observable.html
[rxSingle]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-rx1/kotlinx.coroutines.experimental.rx1/rx-single.html
<!--- END -->

# Package kotlinx.coroutines.experimental.rx2

Utilities for [RxJava 2.x](https://github.com/ReactiveX/RxJava).
